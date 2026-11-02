package io.github.nodyssey.data.attendance

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.Site
import io.github.nodyssey.data.AssetsRepository
import io.github.nodyssey.data.AttendanceBoardEntry
import io.github.nodyssey.data.AttendanceMode
import io.github.nodyssey.data.AttendanceResult
import io.github.nodyssey.data.AttendanceStatus
import io.github.nodyssey.data.GrowthSnapshot
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.UserProfile
import io.github.nodyssey.data.session.SessionRepository
import io.github.plaza.core.AppClock
import io.github.plaza.core.net.SessionCookieStore
import io.github.plaza.core.net.SessionCookies
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class AutoAttendanceTest {
    @Test
    fun `disabled and signed out accounts make no requests`() = runTest {
        val fixture = Fixture(backgroundScope)
        assertEquals(AutoAttendanceOutcome.SKIPPED, fixture.runner().run())
        fixture.store.setEnabled(true)
        fixture.cookies.header = ""
        assertEquals(AutoAttendanceOutcome.BLOCKED, fixture.runner().run())
        assertEquals(0, fixture.profiles.loads)
        assertEquals(0, fixture.assets.checks)
        assertEquals(0, fixture.assets.signs)
    }

    @Test
    fun `foreground and background racing submit once and survive process recreation`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.store.setEnabled(true)
        fixture.store.setMode(AttendanceMode.FIXED_FIVE)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        fixture.assets.beforeSign = {
            entered.complete(Unit)
            release.await()
        }
        val runner = fixture.runner()
        val foreground = async { runner.run() }
        entered.await()
        val background = async { runner.run() }
        release.complete(Unit)
        assertEquals(AutoAttendanceOutcome.COMPLETED, foreground.await())
        assertEquals(AutoAttendanceOutcome.COMPLETED, background.await())
        assertEquals(AutoAttendanceOutcome.COMPLETED, fixture.runner().run())
        assertEquals(1, fixture.assets.signs)
        assertEquals(AttendanceMode.FIXED_FIVE, fixture.assets.mode)
        assertEquals(1, fixture.profiles.loads)
    }

    @Test
    fun `leaving an iOS screen does not cancel the graph owned foreground request`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.store.setEnabled(true)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        fixture.assets.beforeSign = {
            entered.complete(Unit)
            release.await()
        }
        val runner = fixture.runner()
        val screen = launch {
            runner.request()
            awaitCancellation()
        }
        entered.await()
        screen.cancelAndJoin()
        release.complete(Unit)
        assertEquals(AutoAttendanceOutcome.COMPLETED, runner.run())
        assertEquals(1, fixture.assets.signs)
    }

    @Test
    fun `an expired server session stays paused until signing in again`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.store.setEnabled(true)
        fixture.profiles.failure = SiteException(SiteError.LoginRequired)
        val runner = fixture.runner()
        assertEquals(AutoAttendanceOutcome.BLOCKED, runner.run())
        assertEquals(AutoAttendanceFailure.SIGN_IN, runner.state.value.failure)
        fixture.now += 24 * 60 * 60 * 1000L
        assertEquals(AutoAttendanceOutcome.BLOCKED, fixture.runner().run())
        assertEquals(1, fixture.profiles.loads)
        fixture.profiles.failure = null
        fixture.signIn("renewed", uid = 10)
        assertEquals(AutoAttendanceOutcome.COMPLETED, runner.run())
        assertEquals(1, fixture.assets.signs)
    }

    @Test
    fun `Beijing midnight starts a new day while UTC date stays unchanged`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.store.setEnabled(true)
        fixture.runner().run()
        fixture.now += 10 * 60 * 1000L
        fixture.runner().run()
        assertEquals(2, fixture.assets.signs)
    }

    @Test
    fun `manual or web attendance prevents another write`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.store.setEnabled(true)
        fixture.assets.alreadySigned = true
        val runner = fixture.runner()
        assertEquals(AutoAttendanceOutcome.COMPLETED, runner.run())
        assertEquals(0, fixture.assets.signs)
        assertEquals(7, runner.state.value.gain)
    }

    @Test
    fun `new accounts sign independently and returning with new cookies retains the receipt`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.store.setEnabled(true)
        val runner = fixture.runner()
        runner.run()
        fixture.signIn("second", uid = 20)
        runner.run()
        fixture.signIn("first-renewed", uid = 10)
        runner.run()
        assertEquals(2, fixture.assets.signs)
    }

    @Test
    fun `network failures are spaced and capped even across process restarts`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.now = Instant.parse("2026-11-01T02:00:00Z").toEpochMilliseconds()
        fixture.store.setEnabled(true)
        fixture.profiles.failure = SiteException(SiteError.Network)
        assertEquals(AutoAttendanceOutcome.RETRY, fixture.runner().run())
        fixture.runner().run()
        assertEquals(1, fixture.profiles.loads)
        fixture.now += AutoAttendance.RETRY_DELAY_MILLIS
        assertEquals(AutoAttendanceOutcome.RETRY, fixture.runner().run())
        fixture.now += AutoAttendance.RETRY_DELAY_MILLIS
        assertEquals(AutoAttendanceOutcome.BLOCKED, fixture.runner().run())
        fixture.now += AutoAttendance.RETRY_DELAY_MILLIS
        val runner = fixture.runner()
        runner.run()
        assertTrue(runner.state.value.exhausted)
        assertEquals(3, fixture.profiles.loads)
        fixture.now += 24 * 60 * 60 * 1000L
        assertEquals(AutoAttendanceOutcome.RETRY, fixture.runner().run())
        assertEquals(4, fixture.profiles.loads)
    }

    @Test
    fun `challenge pauses requests across days until the browser changes the session`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.store.setEnabled(true)
        fixture.assets.failure = SiteException(SiteError.Cloudflare("https://www.nodeseek.com/board"))
        fixture.runner().run()
        fixture.now += 24 * 60 * 60 * 1000L
        fixture.assets.failure = null
        val runner = fixture.runner()
        assertEquals(AutoAttendanceOutcome.BLOCKED, runner.run())
        assertEquals(AutoAttendanceFailure.VERIFICATION, runner.state.value.failure)
        assertEquals(1, fixture.assets.checks)
        fixture.cookies.header += "; cf_clearance=verified"
        fixture.session.sync()
        assertEquals(AutoAttendanceOutcome.COMPLETED, runner.run())
        assertEquals(1, fixture.assets.signs)
    }

    @Test
    fun `a lost response is reconciled with server attendance before retrying the write`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.store.setEnabled(true)
        fixture.assets.beforeSign = {
            fixture.assets.alreadySigned = true
            throw SiteException(SiteError.Network)
        }
        assertEquals(AutoAttendanceOutcome.RETRY, fixture.runner().run())
        fixture.now += AutoAttendance.RETRY_DELAY_MILLIS
        assertEquals(AutoAttendanceOutcome.COMPLETED, fixture.runner().run())
        assertEquals(1, fixture.assets.signs)
        assertEquals(2, fixture.assets.checks)
    }

    @Test
    fun `sign out while checking prevents a write for the next account`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.store.setEnabled(true)
        fixture.assets.beforeCheck = {
            fixture.cookies.header = ""
            fixture.session.sync()
        }
        assertEquals(AutoAttendanceOutcome.SKIPPED, fixture.runner().run())
        assertEquals(0, fixture.assets.signs)
        assertFalse(fixture.store.records().any { it.completed })
    }

    @Test
    fun `disabling while checking prevents the pending sign in`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.store.setEnabled(true)
        fixture.assets.beforeCheck = { fixture.store.setEnabled(false) }
        assertEquals(AutoAttendanceOutcome.SKIPPED, fixture.runner().run())
        assertEquals(0, fixture.assets.signs)
    }

    @Test
    fun `expiration propagates cancellation without a completed receipt`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.store.setEnabled(true)
        fixture.assets.beforeSign = { throw CancellationException("expired") }
        val runner = fixture.runner()
        assertFailsWith<CancellationException> { runner.run() }
        assertFalse(runner.state.value.running)
        assertFalse(fixture.store.records().any { it.completed })
    }

    @Test
    fun `same account numbers and cookies on different sites do not share receipts or consent`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.store.setEnabled(true)
        fixture.runner().run()
        val otherSite = AutoAttendanceStore(fixture.preferences, Site.DEEPFLOOD)
        val runner = fixture.runner(otherSite)
        assertEquals(AutoAttendanceOutcome.SKIPPED, runner.run())
        otherSite.setEnabled(true)
        assertEquals(AutoAttendanceOutcome.COMPLETED, runner.run())
        assertEquals(2, fixture.assets.signs)
    }

    private class Fixture(private val scope: CoroutineScope) {
        val preferences = MemoryPreferences()
        val store = AutoAttendanceStore(preferences, Site.NODESEEK)
        val cookies = Cookies()
        val session = SessionRepository(SessionCookies(NodeSeekSite.CONFIG, cookies))
        var now = Instant.parse("2026-10-31T15:55:00Z").toEpochMilliseconds()
        val profiles = Profiles()
        val assets = Assets { now }
        fun runner(store: AutoAttendanceStore = this.store) =
            AutoAttendance(store, session, profiles, assets, AppClock { now }, scope)
        fun signIn(cookie: String, uid: Long) {
            cookies.header = "session=$cookie"
            profiles.uid = uid
            session.sync()
        }
    }

    private class MemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        private val mutex = Mutex()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = mutex.withLock {
            transform(data.value).also { data.value = it }
        }
    }

    private class Cookies : SessionCookieStore {
        var header = "session=first"
        override fun cookieHeader(url: String) = header
        override fun setCookie(url: String, cookie: String) = Unit
        override suspend fun removeAll() {
            header = ""
        }
        override fun flush() = Unit
    }

    private class Profiles : ProfileRepository {
        var uid = 10L
        var loads = 0
        var failure: Exception? = null
        override suspend fun profile(refresh: Boolean): UserProfile {
            loads++
            failure?.let { throw it }
            return UserProfile(uid, "reader", "")
        }
        override suspend fun profile(uid: Long): UserProfile = error("unused")
        override suspend fun refreshProfile(sessionFingerprint: Int) = Unit
    }

    private class Assets(private val now: () -> Long) : AssetsRepository {
        var checks = 0
        var signs = 0
        var mode: AttendanceMode? = null
        var alreadySigned = false
        var failure: Exception? = null
        var beforeCheck: suspend () -> Unit = {}
        var beforeSign: suspend () -> Unit = {}
        override fun observeAttendanceStatus() = MutableStateFlow<AttendanceStatus?>(null)
        override suspend fun growth(): GrowthSnapshot = error("unused")
        override suspend fun attendanceBoard(page: Int): List<AttendanceBoardEntry> = error("unused")
        override suspend fun refreshAttendanceStatus(uid: Long): AttendanceStatus {
            checks++
            beforeCheck()
            failure?.let { throw it }
            val date = Instant.fromEpochMilliseconds(now()).toLocalDateTime(TimeZone.of("Asia/Shanghai")).date
            return AttendanceStatus(uid, date, alreadySigned, if (alreadySigned) 7 else null)
        }
        override suspend fun signInForToday(mode: AttendanceMode): AttendanceResult {
            signs++
            this.mode = mode
            beforeSign()
            return AttendanceResult(gain = 5, message = null)
        }
    }
}
