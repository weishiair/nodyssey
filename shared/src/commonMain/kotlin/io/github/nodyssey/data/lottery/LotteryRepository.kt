package io.github.nodyssey.data.lottery

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.nodyssey.core.LotteryResults
import io.github.nodyssey.core.LuckyDraw
import io.github.nodyssey.core.LuckyDrawParams
import io.github.nodyssey.core.Site
import io.github.nodyssey.core.net.JsonSource
import io.github.nodyssey.data.UserProfile
import io.github.plaza.core.AppClock
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.HttpRequest
import io.github.plaza.core.net.HttpTransport
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class LotteryOutcome { WON, LOST, NOT_ENTERED }

@Serializable
enum class LotteryFailure { NETWORK, VERIFICATION, SIGN_IN, RESPONSE, SERVICE, OTHER }

@Serializable
enum class LotteryFailureSource { FORUM, BEACON }

@Serializable
data class LotteryEntry(
    val draw: LuckyDrawParams,
    val title: String,
    val ownerUid: Long,
    val ownerName: String,
    val origin: String,
    val outcome: LotteryOutcome? = null,
    val winningFloors: List<Int> = emptyList(),
    val checkedAtMillis: Long? = null,
    val nextCheckMillis: Long = 0,
    val failure: LotteryFailure? = null,
    val verificationUrl: String? = null,
    val blockedFingerprint: Int? = null,
    val resultNotified: Boolean = false,
    val failureSource: LotteryFailureSource? = null,
    val failureHttpStatus: Int? = null,
    val resultRead: Boolean = false,
) {
    val key: String get() = draw.key
    val postUrl: String get() = origin + "/post-${draw.postId}-1"
}

/** The local owner of a subscription is captured on creation, never inferred at draw time. */
class LotteryStore(dataStore: DataStore<Preferences>, site: Site) {
    private val dataStore = dataStore
    private val key = stringPreferencesKey("lottery_entries${site.storageSuffix}")
    private val json = Json { ignoreUnknownKeys = true }
    val entries: Flow<List<LotteryEntry>> = dataStore.data.map { decode(it[key]) }.distinctUntilChanged()

    suspend fun all(): List<LotteryEntry> = entries.first()

    suspend fun add(entry: LotteryEntry) {
        dataStore.edit { values ->
            val old = decode(values[key])
            // Do not overwrite a subscription's account or checked result on a double tap.
            if (old.none { it.key == entry.key }) {
                if (old.count { it.outcome == null } >= MAX_PENDING) throw LotteryLimitException()
                values[key] = json.encodeToString(old + entry)
            }
        }
    }

    suspend fun remove(id: String) {
        dataStore.edit { it[key] = json.encodeToString(decode(it[key]).filterNot { row -> row.key == id }) }
    }

    suspend fun markResultsRead(entries: List<LotteryEntry>) {
        val viewed = entries.filter { it.outcome != null && !it.resultRead }.associateBy { it.key }
        if (viewed.isEmpty()) return
        dataStore.edit { values ->
            val old = decode(values[key])
            val updated = old.map { row ->
                val seen = viewed[row.key]
                // Only acknowledge the result the screen actually received, including its owner.
                if (seen != null && row.ownerUid == seen.ownerUid && row.outcome == seen.outcome && row.checkedAtMillis == seen.checkedAtMillis) {
                    row.copy(resultRead = true)
                } else {
                    row
                }
            }
            if (updated != old) values[key] = json.encodeToString(updated)
        }
    }

    internal suspend fun update(id: String, ownerUid: Long, transform: (LotteryEntry) -> LotteryEntry): LotteryEntry? {
        var updated: LotteryEntry? = null
        dataStore.edit { values ->
            values[key] = json.encodeToString(
                decode(values[key]).map { row ->
                    if (row.key == id && row.ownerUid == ownerUid) transform(row).also { updated = it } else row
                },
            )
        }
        return updated
    }

    private fun decode(raw: String?): List<LotteryEntry> =
        raw?.let { json.decodeFromString<List<LotteryEntry>>(it) } ?: emptyList()

    companion object {
        const val MAX_PENDING = 50
    }
}

class LotteryLimitException : IllegalStateException()

/** Scheduling and delivery belong to the shell; the result calculation never does. */
interface LotteryPlatform {
    suspend fun sync(entries: List<LotteryEntry>)
    suspend fun notifyResult(entry: LotteryEntry): Boolean
}

interface LotteryRepository {
    val entries: Flow<List<LotteryEntry>>
    suspend fun watch(draw: LuckyDrawParams, title: String)
    suspend fun remove(key: String)
    suspend fun markResultsRead(entries: List<LotteryEntry>)
    suspend fun refreshDue(key: String? = null, force: Boolean = false)
    suspend fun synchronize()
}

class NetworkLotteryRepository(
    private val store: LotteryStore,
    private val source: JsonSource,
    /** Cookie-free, with the same third-party proxy policy as the app's other public requests. */
    private val beaconTransport: HttpTransport,
    private val platform: LotteryPlatform,
    private val currentProfile: suspend () -> UserProfile,
    private val sessionFingerprint: () -> Int,
    private val clock: AppClock,
    private val site: Site,
    private val dispatchers: AppDispatchers,
) : LotteryRepository {
    private val mutex = Mutex()
    private val schedulingMutex = Mutex()
    override val entries: Flow<List<LotteryEntry>> = store.entries

    override suspend fun watch(draw: LuckyDrawParams, title: String) {
        require(LuckyDraw.parse(LuckyDraw.link(draw, site), site) == draw)
        val fingerprint = sessionFingerprint()
        val profile = currentProfile()
        if (sessionFingerprint() != fingerprint) throw SiteException(SiteError.LoginRequired)
        store.add(LotteryEntry(draw, title, profile.uid, profile.name, site.origin))
        synchronize()
        refreshDue(draw.key)
    }

    override suspend fun remove(key: String) {
        store.remove(key)
        synchronize()
    }

    override suspend fun markResultsRead(entries: List<LotteryEntry>) = store.markResultsRead(entries)

    override suspend fun synchronize() = schedulingMutex.withLock { platform.sync(store.all()) }

    override suspend fun refreshDue(key: String?, force: Boolean) = mutex.withLock {
        val now = clock.nowMillis()
        val rows = store.all()
        val blocked = rows.any { it.outcome == null && it.blockedFingerprint == sessionFingerprint() }
        val beacons = mutableMapOf<Long, String>()
        for (entry in rows) {
            if (key != null && entry.key != key) continue
            if (entry.outcome != null) {
                deliver(entry)
                continue
            }
            if (now <= entry.draw.drawAtMillis) continue
            if (!force && (blocked || now < entry.nextCheckMillis)) continue
            val requestFingerprint = sessionFingerprint()
            var failureSource = LotteryFailureSource.FORUM
            try {
                val draw = entry.draw
                val path = "/api/content/floor-data?postId=${draw.postId}&time=${draw.drawAtMillis}"
                val floors = LotteryResults.floors(source.getJson(path, referer = LuckyDraw.link(draw, site)))
                val winners = if (floors.isEmpty()) {
                    emptyList()
                } else {
                    failureSource = LotteryFailureSource.BEACON
                    val round = LotteryResults.round(draw.drawAtMillis)
                    val randomness = beacons[round] ?: run {
                        // OkHttpTransport.execute is blocking; callers include the foreground UI.
                        val response = withContext(dispatchers.io) {
                            beaconTransport.execute(HttpRequest(LotteryResults.BEACON_BASE + round))
                        }
                        if (!response.isSuccessful) throw SiteException(SiteError.Http(response.code))
                        LotteryResults.randomness(response.body, round).also { beacons[round] = it }
                    }
                    LotteryResults.winners(draw, floors, randomness)
                }
                val winningFloors = winners.filter { it.uid == entry.ownerUid }.map { it.floor }
                val outcome = when {
                    winningFloors.isNotEmpty() -> LotteryOutcome.WON
                    floors.none { it.uid == entry.ownerUid && it.floor >= draw.startFloor } -> LotteryOutcome.NOT_ENTERED
                    else -> LotteryOutcome.LOST
                }
                // A removal while HTTP was in flight must not resurrect a subscription.
                store.update(entry.key, entry.ownerUid) {
                    it.copy(
                        outcome = outcome,
                        winningFloors = winningFloors,
                        checkedAtMillis = now,
                        failure = null,
                        verificationUrl = null,
                        blockedFingerprint = null,
                        failureSource = null,
                        failureHttpStatus = null,
                    )
                }?.let { deliver(it) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val reason = when ((failure as? SiteException)?.error) {
                    is SiteError.Cloudflare -> LotteryFailure.VERIFICATION
                    SiteError.Network -> LotteryFailure.NETWORK
                    SiteError.LoginRequired, SiteError.SessionUnrecognised -> LotteryFailure.SIGN_IN
                    SiteError.Unparsable -> LotteryFailure.RESPONSE
                    is SiteError.Http -> LotteryFailure.SERVICE
                    else -> LotteryFailure.OTHER
                }
                store.update(entry.key, entry.ownerUid) {
                    it.copy(
                        failure = reason,
                        verificationUrl = ((failure as? SiteException)?.error as? SiteError.Cloudflare)?.url,
                        nextCheckMillis = now + RETRY_MILLIS,
                        blockedFingerprint = if (reason == LotteryFailure.VERIFICATION) requestFingerprint else null,
                        failureSource = failureSource,
                        failureHttpStatus = ((failure as? SiteException)?.error as? SiteError.Http)?.statusCode,
                    )
                }
                // A challenge is site-wide: do not send the rest of the queue at the same wall.
                if (reason == LotteryFailure.VERIFICATION) break
            }
        }
    }

    private suspend fun deliver(entry: LotteryEntry) {
        if (!entry.resultNotified && runCatchingExceptCancellation { platform.notifyResult(entry) }.getOrDefault(false)) {
            store.update(entry.key, entry.ownerUid) { it.copy(resultNotified = true) }
        }
    }

    companion object {
        const val RETRY_MILLIS = 15 * 60 * 1000L
    }
}
