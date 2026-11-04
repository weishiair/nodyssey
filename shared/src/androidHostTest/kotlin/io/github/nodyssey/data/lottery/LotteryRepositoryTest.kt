package io.github.nodyssey.data.lottery

import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import io.github.nodyssey.core.LuckyDrawParams
import io.github.nodyssey.core.Site
import io.github.nodyssey.core.html.Fixtures
import io.github.nodyssey.core.net.JsonSource
import io.github.nodyssey.data.UserProfile
import io.github.plaza.core.AppClock
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.HttpRequest
import io.github.plaza.core.net.HttpResponse
import io.github.plaza.core.net.HttpTransport
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import io.github.plaza.core.net.UploadProgress
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LotteryRepositoryTest {
    private val draw = LuckyDrawParams(1024765, 1793546010000, 10, 1, false)
    private val directory = Files.createTempDirectory("lottery-test").toFile().apply { deleteOnExit() }

    private inner class Harness(
        scope: CoroutineScope,
        name: String = "draw",
        val io: CoroutineDispatcher = scope.coroutineContext[ContinuationInterceptor] as CoroutineDispatcher,
    ) {
        var now = draw.drawAtMillis - 10_000
        var fingerprint = 1
        var profile = UserProfile(7767, "winner", "")
        var loadProfile: suspend () -> UserProfile = { profile }
        var floorBody = Fixtures.load("lottery-1024765-floors.json")
        var beaconBody = Fixtures.load("lottery-6603832-beacon.json")
        var floorFailure: SiteError? = null
        var duringFetch: suspend () -> Unit = {}
        var floorCalls = 0
        var beaconCalls = 0
        var beaconFailure: SiteError? = null
        val notified = mutableListOf<LotteryEntry>()
        val dataStore = PreferenceDataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { File(directory, "$name.preferences_pb").toOkioPath() },
            scope = scope,
        )
        val store = LotteryStore(dataStore, Site.NODESEEK)
        private val platform = object : LotteryPlatform {
            override suspend fun sync(entries: List<LotteryEntry>) = Unit
            override suspend fun notifyResult(entry: LotteryEntry): Boolean {
                notified += entry
                return true
            }
        }
        val repository = newRepository()

        fun newRepository() = NetworkLotteryRepository(
            store = store,
            source = object : JsonSource {
                override suspend fun getJson(path: String, referer: String): String {
                    floorCalls++
                    duringFetch()
                    floorFailure?.let { throw SiteException(it) }
                    return floorBody
                }
            },
            beaconTransport = object : HttpTransport {
                override suspend fun execute(request: HttpRequest, onUploadProgress: UploadProgress?): HttpResponse {
                    beaconCalls++
                    // Model Android's rejection of blocking HTTP when entered from the UI dispatcher.
                    check(currentCoroutineContext()[ContinuationInterceptor] == io) { "Blocking HTTP on the caller dispatcher" }
                    beaconFailure?.let { throw SiteException(it) }
                    return HttpResponse(200, request.url, emptyMap(), beaconBody)
                }
            },
            platform = platform,
            currentProfile = { loadProfile() },
            sessionFingerprint = { fingerprint },
            clock = AppClock { now },
            site = Site.NODESEEK,
            dispatchers = AppDispatchers(io = io),
        )
    }

    @Test
    fun `foreground checks dispatch blocking randomness requests off the caller and recover old failures`() = runTest {
        val h = Harness(backgroundScope, io = StandardTestDispatcher(testScheduler, "lottery IO"))
        val reported = LuckyDrawParams(1022241, 1793592000000, 10, 1, true)
        h.profile = UserProfile(48929, "winner", "")
        h.floorBody = Fixtures.load("lottery-1022241-floors.json")
        h.beaconBody = Fixtures.load("lottery-6605365-beacon.json")
        h.now = reported.drawAtMillis - 1_000
        h.repository.watch(reported, "draw")
        h.now = reported.drawAtMillis + 1_000
        h.beaconFailure = SiteError.Network
        h.repository.refreshDue(force = true)
        val failed = h.store.all().single()
        assertNull(failed.outcome)
        assertEquals(LotteryFailure.NETWORK, failed.failure)
        assertEquals(LotteryFailureSource.BEACON, failed.failureSource)
        h.beaconFailure = null
        // Version 119 stored this unclassified failure without a source or HTTP status.
        h.store.update(reported.key, 48929) { it.copy(failure = LotteryFailure.OTHER, failureSource = null) }
        h.repository.refreshDue(force = true)
        val recovered = h.store.all().single()
        assertEquals(LotteryOutcome.WON, recovered.outcome)
        assertEquals(listOf(20), recovered.winningFloors)
        assertNull(recovered.failure)
        assertNull(recovered.failureSource)
    }

    @Test
    fun `account changes after subscribing cannot change whose result is checked or notify twice`() = runTest {
        val h = Harness(backgroundScope)
        h.repository.watch(draw, "draw")
        h.profile = UserProfile(71411, "another account", "")
        h.fingerprint = 2
        h.now = draw.drawAtMillis + 1_000
        h.repository.refreshDue()
        h.newRepository().refreshDue(force = true)
        val result = h.store.all().single()
        assertEquals(7767L, result.ownerUid)
        assertEquals(LotteryOutcome.WON, result.outcome)
        assertEquals(listOf(14), result.winningFloors)
        assertEquals(1, h.floorCalls)
        assertEquals(1, h.notified.size)
    }

    @Test
    fun `network failure stays pending and foreground checks respect the persisted retry time`() = runTest {
        val h = Harness(backgroundScope)
        h.repository.watch(draw, "draw")
        assertEquals(0, h.floorCalls)
        h.now = draw.drawAtMillis + 1_000
        h.floorFailure = SiteError.Network
        h.repository.refreshDue()
        assertNull(h.store.all().single().outcome)
        assertEquals(LotteryFailure.NETWORK, h.store.all().single().failure)
        h.now += 5_000
        h.newRepository().refreshDue()
        assertEquals(1, h.floorCalls)
        assertEquals(0, h.notified.size)
        h.now += NetworkLotteryRepository.RETRY_MILLIS
        h.floorFailure = null
        h.repository.refreshDue()
        assertEquals(LotteryOutcome.WON, h.store.all().single().outcome)
    }

    @Test
    fun `a challenge is not retried until browser intervention changes the session`() = runTest {
        val h = Harness(backgroundScope)
        h.repository.watch(draw, "draw")
        h.now = draw.drawAtMillis + 1_000
        h.floorFailure = SiteError.Cloudflare("https://www.nodeseek.com/lucky")
        h.repository.refreshDue()
        assertEquals("https://www.nodeseek.com/lucky", h.store.all().single().verificationUrl)
        h.now += 24 * 60 * 60 * 1000
        h.repository.refreshDue()
        assertEquals(1, h.floorCalls)
        h.fingerprint++
        h.floorFailure = null
        h.repository.refreshDue()
        assertEquals(LotteryOutcome.WON, h.store.all().single().outcome)
    }

    @Test
    fun `removing a draw during its fetch cannot resurrect it or send a notification`() = runTest {
        val h = Harness(backgroundScope)
        h.repository.watch(draw, "draw")
        h.now = draw.drawAtMillis + 1_000
        h.duringFetch = { h.repository.remove(draw.key) }
        h.repository.refreshDue()
        assertEquals(emptyList(), h.store.all())
        assertEquals(emptyList(), h.notified)
    }

    @Test
    fun `resubscribing with a different account during a fetch cannot inherit the old result`() = runTest {
        val h = Harness(backgroundScope)
        h.repository.watch(draw, "draw")
        val original = h.store.all().single()
        h.now = draw.drawAtMillis + 1_000
        h.duringFetch = {
            h.repository.remove(draw.key)
            h.store.add(original.copy(ownerUid = 71411, ownerName = "another"))
        }
        h.repository.refreshDue()
        assertEquals(71411L, h.store.all().single().ownerUid)
        assertNull(h.store.all().single().outcome)
        assertEquals(emptyList(), h.notified)
    }

    @Test
    fun `a site challenge pauses the entire queue rather than hitting it once per draw`() = runTest {
        val h = Harness(backgroundScope)
        h.repository.watch(draw, "first")
        h.repository.watch(draw.copy(prizeCount = 1), "second")
        h.now = draw.drawAtMillis + 1_000
        h.floorFailure = SiteError.Cloudflare("https://www.nodeseek.com/lucky")
        h.repository.refreshDue()
        assertEquals(1, h.floorCalls)
        h.now += 24 * 60 * 60 * 1000
        h.newRepository().refreshDue()
        assertEquals(1, h.floorCalls)
        assertEquals(true, h.store.all().all { it.outcome == null })
    }

    @Test
    fun `missing participation is distinct from an eligible losing floor`() = runTest {
        val h = Harness(backgroundScope)
        h.profile = UserProfile(71411, "loser", "")
        h.repository.watch(draw, "draw")
        h.now = draw.drawAtMillis + 1_000
        h.repository.refreshDue()
        assertEquals(LotteryOutcome.LOST, h.store.all().single().outcome)
        h.repository.remove(draw.key)
        h.profile = UserProfile(999999, "absent", "")
        h.repository.watch(draw, "draw")
        assertEquals(LotteryOutcome.NOT_ENTERED, h.store.all().single().outcome)
    }

    @Test
    fun `a sign-out while loading the owner cannot attach a reminder to the wrong account`() = runTest {
        val h = Harness(backgroundScope)
        h.loadProfile = {
            h.fingerprint++
            h.profile
        }
        assertFailsWith<SiteException> { h.repository.watch(draw, "draw") }
        assertEquals(emptyList(), h.store.all())
    }

    @Test
    fun `acknowledging a snapshot leaves later results unread and preserves notification delivery`() = runTest {
        val h = Harness(backgroundScope)
        val second = draw.copy(prizeCount = 1)
        h.repository.watch(draw, "first")
        h.repository.watch(second, "second")
        h.now = draw.drawAtMillis + 1_000
        h.repository.refreshDue(draw.key)
        val viewed = h.store.all()
        h.repository.refreshDue(second.key)
        // Notification bookkeeping may change after the screen receives the result.
        h.store.update(draw.key, 7767) { it.copy(resultNotified = false) }
        h.repository.markResultsRead(viewed)
        val results = h.store.all().associateBy { it.key }
        assertEquals(true, results.getValue(draw.key).resultRead)
        assertEquals(false, results.getValue(second.key).resultRead)
        assertEquals(LotteryOutcome.WON, results.getValue(draw.key).outcome)
        h.repository.refreshDue()
        assertEquals(3, h.notified.size)
        assertEquals(false, h.store.all().first { it.key == second.key }.resultRead)
    }

    @Test
    fun `a stale read acknowledgement cannot affect a removed or replacement subscription`() = runTest {
        val h = Harness(backgroundScope)
        h.repository.watch(draw, "draw")
        h.now = draw.drawAtMillis + 1_000
        h.repository.refreshDue()
        val viewed = h.store.all()
        val original = viewed.single()
        h.repository.remove(draw.key)
        h.repository.markResultsRead(viewed)
        assertEquals(emptyList(), h.store.all())
        h.store.add(original.copy(ownerUid = 71411))
        h.repository.markResultsRead(viewed)
        assertEquals(false, h.store.all().single().resultRead)
        h.repository.remove(draw.key)
        h.store.add(original.copy(checkedAtMillis = h.now + 1_000))
        h.repository.markResultsRead(viewed)
        assertEquals(false, h.store.all().single().resultRead)
    }

    @Test
    fun `completed results and notification receipts survive reopening the real store`() = runTest {
        val firstJob = Job()
        val firstScope = CoroutineScope(coroutineContext + firstJob)
        val h = Harness(firstScope)
        h.repository.watch(draw, "draw")
        h.now = draw.drawAtMillis + 1_000
        h.repository.refreshDue()
        assertEquals(false, h.store.all().single().resultRead)
        h.repository.markResultsRead(h.store.all())
        firstJob.cancelAndJoin()
        val reopened = PreferenceDataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { File(directory, "draw.preferences_pb").toOkioPath() },
            scope = backgroundScope,
        )
        val saved = LotteryStore(reopened, Site.NODESEEK).all().single()
        assertEquals(LotteryOutcome.WON, saved.outcome)
        assertEquals(listOf(14), saved.winningFloors)
        assertEquals(true, saved.resultNotified)
        assertEquals(true, saved.resultRead)
        assertEquals(emptyList(), LotteryStore(reopened, Site.DEEPFLOOD).all())
    }
}
