package io.github.nodyssey.data.calculator

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.nodyssey.core.VpsCalculatorDraft
import io.github.nodyssey.core.VpsCurrency
import io.github.plaza.core.AppClock
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.HttpRequest
import io.github.plaza.core.net.HttpTransport
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import okio.IOException

sealed interface ExchangeRateResult {
    data class Success(val rate: Double, val fetchedAtMillis: Long, val fromCache: Boolean) : ExchangeRateResult
    data class RateLimited(val cachedRate: Double?, val cachedAtMillis: Long?) : ExchangeRateResult
    data class Failure(val cachedRate: Double?, val cachedAtMillis: Long?) : ExchangeRateResult
}

data class VpsCalculatorSnapshot(
    val draft: VpsCalculatorDraft = VpsCalculatorDraft(),
    val cachedRate: ExchangeRateResult.Success? = null,
)

interface VpsCalculatorRepository {
    val draft: Flow<VpsCalculatorDraft>
    fun cachedSnapshot(): VpsCalculatorSnapshot? = null
    suspend fun loadSnapshot(): VpsCalculatorSnapshot = VpsCalculatorSnapshot(draft = draft.first())
    suspend fun saveDraft(draft: VpsCalculatorDraft)
    suspend fun getExchangeRate(currency: VpsCurrency, forceRefresh: Boolean = false): ExchangeRateResult
}

class DefaultVpsCalculatorRepository(
    private val dataStore: DataStore<Preferences>,
    private val http: HttpTransport,
    private val dispatchers: AppDispatchers,
    private val clock: AppClock,
    private val endpointUrl: String = DEFAULT_ENDPOINT,
) : VpsCalculatorRepository {
    private val preferences = dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    // Preferences is immutable after DataStore publishes it and already contains every rate and timestamp.
    private data class Memory(val draft: VpsCalculatorDraft, val preferences: Preferences)

    private var memory: Memory? = null
    private val snapshotMutex = Mutex()

    private fun decodeDraft(prefs: Preferences): VpsCalculatorDraft =
        prefs[DRAFT]?.let { runCatching { json.decodeFromString<VpsCalculatorDraft>(it) }.getOrNull() }
            ?: VpsCalculatorDraft()

    override val draft: Flow<VpsCalculatorDraft> = preferences.map(::decodeDraft).distinctUntilChanged()

    private fun Memory.snapshot(): VpsCalculatorSnapshot {
        val currency = VpsCurrency.fromCode(draft.currencyCode)
        val keys = rateKeys[currency]
        val custom = draft.customRateCurrencyCode == currency.code && draft.customRateText.isNotBlank()
        val rate = keys?.let { preferences[it.rate] }
        val fetchedAt = keys?.let { preferences[it.fetchedAt] }
        val cached = if (!custom && rate != null && fetchedAt != null && clock.nowMillis() - fetchedAt in 0 until CACHE_TTL_MILLIS) {
            ExchangeRateResult.Success(rate, fetchedAt, fromCache = true)
        } else {
            null
        }
        return VpsCalculatorSnapshot(draft, cached)
    }

    override fun cachedSnapshot(): VpsCalculatorSnapshot? {
        // If an edit is being committed, loadSnapshot waits for it rather than restoring an old draft.
        if (!snapshotMutex.tryLock()) return null
        return try {
            memory?.snapshot()
        } finally {
            snapshotMutex.unlock()
        }
    }

    private suspend fun readMemory(): Memory = snapshotMutex.withLock {
        val prefs = preferences.first()
        Memory(decodeDraft(prefs), prefs).also { memory = it }
    }

    override suspend fun loadSnapshot(): VpsCalculatorSnapshot = readMemory().snapshot()

    private suspend fun editPreferences(draft: VpsCalculatorDraft? = null, edit: (MutablePreferences) -> Unit) {
        snapshotMutex.withLock {
            val prefs = dataStore.edit { edit(it) }
            memory = Memory(draft ?: decodeDraft(prefs), prefs)
        }
    }

    override suspend fun saveDraft(draft: VpsCalculatorDraft) {
        editPreferences(draft) { it[DRAFT] = json.encodeToString(draft) }
    }

    override suspend fun getExchangeRate(currency: VpsCurrency, forceRefresh: Boolean): ExchangeRateResult {
        if (currency.isBaseCny) return ExchangeRateResult.Success(1.0, clock.nowMillis(), fromCache = true)
        return refreshMutex.withLock {
            val prefs = readMemory().preferences
            val now = clock.nowMillis()
            val keys = rateKeys.getValue(currency)
            val cached = prefs[keys.rate]
            val cachedAt = prefs[keys.fetchedAt]
            if (!forceRefresh && cached != null && cachedAt != null && now - cachedAt in 0 until CACHE_TTL_MILLIS) {
                return@withLock ExchangeRateResult.Success(cached, cachedAt, fromCache = true)
            }

            val resetAt = prefs[RESET_AT] ?: now
            val count = if (now < resetAt) prefs[MANUAL_COUNT] ?: 0 else 0
            if (forceRefresh && count >= MAX_MANUAL_REFRESHES_PER_WINDOW) {
                return@withLock ExchangeRateResult.RateLimited(cached, cachedAt)
            }

            runCatchingExceptCancellation {
                val response = withContext(dispatchers.io) { http.execute(HttpRequest(endpointUrl)) }
                if (!response.isSuccessful) return@runCatchingExceptCancellation null
                val rates = withContext(dispatchers.default) { parseRates(response.body) }
                if (rates.isEmpty()) return@runCatchingExceptCancellation null
                val rate = rates[currency]
                val fetchedAt = clock.nowMillis()
                // Commit the snapshot and manual charge together. A failed or cancelled fetch costs no quota.
                editPreferences { updated ->
                    for ((unit, fresh) in rates) {
                        val unitKeys = rateKeys.getValue(unit)
                        updated[unitKeys.rate] = fresh
                        updated[unitKeys.fetchedAt] = fetchedAt
                    }
                    if (forceRefresh && rate != null) {
                        updated[MANUAL_COUNT] = count + 1
                        updated[RESET_AT] = if (now >= resetAt) now + CACHE_TTL_MILLIS else resetAt
                    }
                }
                rate?.let { ExchangeRateResult.Success(it, fetchedAt, fromCache = false) }
            }.getOrNull() ?: ExchangeRateResult.Failure(cached, cachedAt)
        }
    }

    private fun parseRates(body: String): Map<VpsCurrency, Double> {
        val response = json.decodeFromString<RateResponse>(body)
        if (response.result != "success") return emptyMap()
        return buildMap {
            for (unit in rateKeys.keys) {
                val raw = (response.rates[unit.code] as? JsonPrimitive)?.doubleOrNull ?: continue
                if (raw.isFinite() && raw > 0) {
                    val rate = 1.0 / raw
                    if (rate.isFinite()) put(unit, rate)
                }
            }
        }
    }

    @Serializable
    private data class RateResponse(val result: String, val rates: JsonObject)

    private class RateKeys(currency: VpsCurrency) {
        val rate = doublePreferencesKey("vps_calculator_rate_${currency.code}")
        val fetchedAt = longPreferencesKey("vps_calculator_rates_fetched_at_${currency.code}")
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://open.er-api.com/v6/latest/CNY"
        const val CACHE_TTL_MILLIS = 12L * 60 * 60 * 1000
        const val MAX_MANUAL_REFRESHES_PER_WINDOW = 2

        private val json = Json { ignoreUnknownKeys = true }

        // Repositories sharing the settings store must check the quota before issuing the next request.
        private val refreshMutex = Mutex()
        private val DRAFT = stringPreferencesKey("vps_calculator_draft")
        private val MANUAL_COUNT = intPreferencesKey("vps_calculator_manual_count")
        private val RESET_AT = longPreferencesKey("vps_calculator_manual_reset_at")
        private val rateKeys = VpsCurrency.entries.filterNot { it.isBaseCny }.associateWith(::RateKeys)
    }
}
