package io.github.nodyssey.data.calculator

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.nodyssey.core.VpsCalculatorDraft
import io.github.nodyssey.core.VpsCurrency
import io.github.nodyssey.core.VpsQuoteEditedField
import io.github.plaza.core.AppClock
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.HttpRequest
import io.github.plaza.core.net.HttpResponse
import io.github.plaza.core.net.HttpTransport
import io.github.plaza.core.net.RecordingTransport
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.UploadProgress
import io.github.plaza.core.net.httpResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class VpsCalculatorRepositoryTest {
    @Test
    fun `snapshot matches the draft currency custom rate and cache lifetime across visits`() = runTest {
        val fixture = Fixture(this, RecordingTransport(httpResponse(USD_RATES)))
        val repo = fixture.repository()
        assertNull(repo.cachedSnapshot())
        val draft = VpsCalculatorDraft(priceText = "39")
        repo.saveDraft(draft)
        repo.getExchangeRate(VpsCurrency.USD)
        val expected = VpsCalculatorSnapshot(draft, ExchangeRateResult.Success(8.0, fixture.now, true))
        assertEquals(expected, repo.cachedSnapshot())
        val reopened = fixture.repository()
        assertEquals(expected, reopened.loadSnapshot())
        assertEquals(expected, reopened.cachedSnapshot())
        reopened.saveDraft(draft.copy(customRateText = "7", customRateCurrencyCode = "USD"))
        assertNull(reopened.cachedSnapshot()?.cachedRate)
        reopened.saveDraft(draft.copy(currencyCode = "CNY"))
        assertNull(reopened.cachedSnapshot()?.cachedRate)
        reopened.saveDraft(draft)
        fixture.now += DefaultVpsCalculatorRepository.CACHE_TTL_MILLIS
        assertEquals(VpsCalculatorSnapshot(draft), reopened.cachedSnapshot())
        assertEquals(VpsCalculatorSnapshot(draft), reopened.loadSnapshot())
    }

    @Test
    fun `reopening during a draft write waits for the new trade date`() = runTest {
        val fixture = Fixture(this, RecordingTransport(httpResponse(USD_RATES)))
        val repo = fixture.repository()
        val previous = VpsCalculatorDraft(tradeDateIso = "2026-11-25")
        val updated = previous.copy(tradeDateIso = "2026-11-27")
        repo.saveDraft(previous)

        val finishWrite = CompletableDeferred<Unit>()
        fixture.beforeWrite = { finishWrite.await() }
        val save = async(start = CoroutineStart.UNDISPATCHED) { repo.saveDraft(updated) }

        assertNull(repo.cachedSnapshot())
        val reopen = async(start = CoroutineStart.UNDISPATCHED) { repo.loadSnapshot() }
        assertFalse(reopen.isCompleted)
        finishWrite.complete(Unit)
        save.await()
        assertEquals(updated, reopen.await().draft)
    }

    @Test
    fun `one response populates every foreign currency and the snapshot expires at twelve hours`() = runTest {
        val transport = RecordingTransport(
            httpResponse(
                """{"result":"success","base_code":"CNY","rates":{"USD":0.125,"EUR":0.1,"GBP":0.08,"JPY":20,
                    "HKD":1.1,"AUD":0.2,"SGD":0.18,"KRW":180,"TWD":4.5,"CAD":0.19,"CNY":1,"CHF":0.1}}""",
            ),
            httpResponse("""{"result":"success","rates":{"USD":0.25}}"""),
        )
        val fixture = Fixture(this, transport)
        val repo = fixture.repository()
        val start = fixture.now
        assertEquals(ExchangeRateResult.Success(8.0, start, false), repo.getExchangeRate(VpsCurrency.USD))
        fixture.now += 12 * 60 * 60 * 1000 - 1
        val reopened = fixture.repository()
        val raw = listOf(0.125, 0.1, 0.08, 20.0, 1.1, 0.2, 0.18, 180.0, 4.5, 0.19)
        for ((currency, rate) in VpsCurrency.entries.filterNot { it.isBaseCny }.zip(raw)) {
            assertEquals(ExchangeRateResult.Success(1.0 / rate, start, true), reopened.getExchangeRate(currency))
        }
        assertEquals(HttpRequest(ENDPOINT), transport.request)
        fixture.now++
        assertEquals(ExchangeRateResult.Success(4.0, fixture.now, false), reopened.getExchangeRate(VpsCurrency.USD))
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun `only successful manual fetches use the shared quota and it resets at the window boundary`() = runTest {
        val transport = RecordingTransport(httpResponse("", 503), httpResponse(USD_RATES))
        val fixture = Fixture(this, transport)
        val repo = fixture.repository()
        val now = fixture.now
        repeat(3) {
            assertEquals(ExchangeRateResult.Success(1.0, now, true), repo.getExchangeRate(VpsCurrency.CNY, true))
        }
        assertEquals(ExchangeRateResult.Failure(null, null), repo.getExchangeRate(VpsCurrency.USD, true))
        val requests = List(3) { async { fixture.repository().getExchangeRate(VpsCurrency.USD, true) } }.awaitAll()
        assertEquals(2, requests.count { it == ExchangeRateResult.Success(8.0, now, false) })
        assertEquals(1, requests.count { it == ExchangeRateResult.RateLimited(8.0, now) })
        assertEquals(ExchangeRateResult.RateLimited(null, null), repo.getExchangeRate(VpsCurrency.EUR, true))
        fixture.now += 12 * 60 * 60 * 1000 - 1
        assertEquals(ExchangeRateResult.RateLimited(8.0, now), repo.getExchangeRate(VpsCurrency.USD, true))
        assertEquals(3, transport.requests.size)
        fixture.now++
        assertEquals(ExchangeRateResult.Success(8.0, fixture.now, false), repo.getExchangeRate(VpsCurrency.USD, true))
    }

    @Test
    fun `failed refreshes keep the prior quote and its actual timestamp`() = runTest {
        val invalid = listOf(
            httpResponse(USD_RATES, 503),
            httpResponse("not JSON"),
            httpResponse("""{"result":"error","rates":{"USD":0.25}}"""),
            httpResponse("""{"result":"success","rates":{"EUR":0.5}}"""),
            httpResponse("""{"result":"success","rates":{"USD":0}}"""),
            httpResponse("""{"result":"success","rates":{"USD":-1}}"""),
            httpResponse("""{"result":"success","rates":{"USD":"NaN"}}"""),
            httpResponse("""{"result":"success","rates":{"USD":1e999}}"""),
        )
        val transport = RecordingTransport(httpResponse(USD_RATES), *invalid.toTypedArray())
        val fixture = Fixture(this, transport)
        val repo = fixture.repository()
        val fetched = fixture.now
        repo.getExchangeRate(VpsCurrency.USD)
        fixture.now += DefaultVpsCalculatorRepository.CACHE_TTL_MILLIS
        for (response in invalid) {
            assertEquals(ExchangeRateResult.Failure(8.0, fetched), repo.getExchangeRate(VpsCurrency.USD), response.body)
        }
        transport.failWith = SiteError.Network
        assertEquals(ExchangeRateResult.Success(2.0, fixture.now, true), repo.getExchangeRate(VpsCurrency.EUR))
        assertEquals(ExchangeRateResult.Failure(8.0, fetched), repo.getExchangeRate(VpsCurrency.USD))
    }

    @Test
    fun `cancelled requests propagate and leave both manual refreshes available`() = runTest {
        var cancel = true
        val transport = object : HttpTransport {
            override suspend fun execute(request: HttpRequest, onUploadProgress: UploadProgress?): HttpResponse {
                if (cancel) throw CancellationException("cancelled")
                return httpResponse(USD_RATES)
            }
        }
        val fixture = Fixture(this, transport)
        val repo = fixture.repository()
        assertFailsWith<CancellationException> { repo.getExchangeRate(VpsCurrency.USD, true) }
        cancel = false
        repeat(2) {
            assertEquals(ExchangeRateResult.Success(8.0, fixture.now, false), repo.getExchangeRate(VpsCurrency.USD, true))
        }
    }

    @Test
    fun `draft restores partial input and recovers from malformed or older stored data`() = runTest {
        val fixture = Fixture(this, RecordingTransport(httpResponse(USD_RATES)))
        val repo = fixture.repository()
        assertNull(repo.draft.first().dueDateIso)
        val draft = VpsCalculatorDraft(
            priceText = "10.",
            cycleDays = 90,
            currencyCode = "JPY",
            customRateCurrencyCode = "JPY",
            customRateText = "0.0500",
            premiumText = "-",
            salePriceText = "20",
            lastEditedQuote = VpsQuoteEditedField.SALE_PRICE,
            dueDateIso = "2026-12-01",
        )
        repo.saveDraft(draft)
        assertEquals(draft, fixture.repository().draft.first())
        val key = stringPreferencesKey("vps_calculator_draft")
        fixture.store.edit { it[key] = """{"priceText":"5","unknown":true}""" }
        assertEquals(VpsCalculatorDraft(priceText = "5"), repo.draft.first())
        fixture.store.edit { it[key] = "{" }
        assertEquals(VpsCalculatorDraft(), repo.draft.first())
    }

    private class Fixture(scope: TestScope, val transport: HttpTransport) {
        var beforeWrite: suspend () -> Unit = {}

        val store = object : DataStore<Preferences> {
            override val data = MutableStateFlow(emptyPreferences())
            private val mutex = Mutex()
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = mutex.withLock {
                beforeWrite()
                transform(data.value).also { data.value = it }
            }
        }
        var now = 1000L
        private val dispatcher = StandardTestDispatcher(scope.testScheduler)
        fun repository() = DefaultVpsCalculatorRepository(
            store,
            transport,
            AppDispatchers(dispatcher, dispatcher, dispatcher),
            AppClock { now },
            ENDPOINT,
        )
    }

    private companion object {
        const val ENDPOINT = "https://example.invalid/rates"
        const val USD_RATES = """{"result":"success","rates":{"USD":0.125}}"""
    }
}
