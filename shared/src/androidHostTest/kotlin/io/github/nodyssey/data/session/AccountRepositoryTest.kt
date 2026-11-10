package io.github.nodyssey.data.session

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.NodeSeekJsonClient
import io.github.nodyssey.data.AssetsRepository
import io.github.nodyssey.data.AttendanceMode
import io.github.nodyssey.data.NetworkAssetsRepository
import io.github.nodyssey.data.NetworkCreditRepository
import io.github.nodyssey.data.NetworkProfileRepository
import io.github.nodyssey.data.NoOpPostRepository
import io.github.nodyssey.data.NotificationCounts
import io.github.nodyssey.data.OfflineLibrary
import io.github.nodyssey.data.OfflineSettings
import io.github.nodyssey.data.OfflineState
import io.github.nodyssey.data.OfflineUsage
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.UserProfile
import io.github.nodyssey.data.attendance.AttendanceSession
import io.github.nodyssey.data.attendance.AttendanceWriteGate
import io.github.nodyssey.data.attendance.AutoAttendance
import io.github.nodyssey.data.attendance.AutoAttendanceOutcome
import io.github.nodyssey.data.attendance.MultiAccountAttendance
import io.github.nodyssey.data.mail.DefaultMailRepository
import io.github.nodyssey.data.mail.MailCredentialStore
import io.github.nodyssey.data.security.SecretCipher
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.data.testPreferenceStore
import io.github.plaza.core.AppClock
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.HttpRequest
import io.github.plaza.core.net.HttpResponse
import io.github.plaza.core.net.HttpTransport
import io.github.plaza.core.net.SessionCookies
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import io.github.plaza.core.net.SiteHtmlClient
import io.github.plaza.core.net.UploadProgress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.encoding.Base64
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AccountRepositoryTest {
    // Bug: a failed fingerprint permanently suppresses saving a now-readable login.
    @Test
    fun `a failed cookie snapshot can be saved again under the same session`() = runTest {
        val fixture = Fixture(this)
        fixture.complete { fixture.accounts.add() }
        fixture.captureFailure = IllegalStateException("private cookie content")
        fixture.login(1)
        assertTrue(fixture.session.peek().isSignedIn)
        assertTrue(fixture.accounts.state.value.accounts.isEmpty())
        assertTrue(fixture.accounts.state.value.adding)
        assertEquals(AccountFailure.STORAGE, fixture.accounts.state.value.failure)
        fixture.captureFailure = null
        fixture.accounts.remember(UserProfile(1, "account-1", ""), fixture.session.peek().fingerprint)
        assertEquals(1L, fixture.accounts.state.value.activeUid)
        assertFalse(fixture.accounts.state.value.adding)
        assertEquals(1L, fixture.vault.read().activeUid)
    }

    // Bug: adding again after a save failure clears the valid session and asks for another login.
    @Test
    fun `adding after a failed save preserves and retries the readable login`() = runTest {
        val fixture = Fixture(this)
        fixture.complete { fixture.accounts.add() }
        fixture.captureFailure = IllegalStateException("capture failed")
        fixture.login(1)
        fixture.captureFailure = null
        fixture.accounts.add()
        fixture.accounts.state.first { it.activeUid == 1L }
        assertEquals("session=1", fixture.cookies.capture())
        assertFalse(fixture.accounts.state.value.adding)
        assertNull(fixture.accounts.state.value.failure)
    }

    // Bug: the login screen reports success when writing the account vault failed.
    @Test
    fun `explicit login completion reports write failure and can recover without another sign in`() = runTest {
        val fixture = Fixture(this)
        fixture.complete { fixture.accounts.add() }
        fixture.cipher.canEncrypt = false
        fixture.login(1)
        assertFalse(fixture.accounts.saveCurrentLogin())
        assertTrue(fixture.session.peek().isSignedIn)
        fixture.cipher.canEncrypt = true
        assertTrue(fixture.accounts.saveCurrentLogin())
        assertEquals(1L, fixture.vault.read().activeUid)
        assertFalse(fixture.accounts.state.value.adding)
    }

    // Bug: switching forum accounts keeps the previous account's mail session and visible data.
    @Test
    fun `switching accounts invalidates the active mail connection before the next account is shown`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var saved = "web:example-session"
        val mail = DefaultMailRepository(
            credentials = object : MailCredentialStore {
                override suspend fun read() = saved
                override suspend fun save(key: String) {
                    saved = key
                }
                override suspend fun readDefaultMailboxId(): Long? = null
                override suspend fun saveDefaultMailboxId(id: Long?) = Unit
                override suspend fun clearDefaultMailboxId() = Unit
                override suspend fun clear() {
                    saved = ""
                }
            },
            http = object : HttpTransport {
                override suspend fun execute(request: HttpRequest, onUploadProgress: UploadProgress?): HttpResponse =
                    error("Account cleanup must not need the mail server")
            },
            dispatchers = AppDispatchers(dispatcher, dispatcher, dispatcher),
        )
        val fixture = Fixture(this)
        fixture.login(1)
        fixture.complete { fixture.accounts.add() }
        fixture.login(2)
        fixture.clearMail = mail::disconnect
        mail.initialize()
        val generation = mail.connection.value.generation
        assertTrue(mail.connection.value.connected)
        fixture.complete { fixture.accounts.switch(1) }
        assertEquals(1L, fixture.accounts.state.value.activeUid)
        assertFalse(mail.connection.value.connected)
        assertEquals("", saved)
        assertTrue(mail.connection.value.generation > generation)
    }

    // Bug: a platform that leaves the previous login behind opens add, then adopts it as a new login.
    @Test
    fun `adding refuses a cookie replacement that leaves the original login active`() = runTest {
        val fixture = Fixture(this)
        fixture.login(1)
        fixture.draft("original private draft")
        fixture.keepLoginOnClear = true
        fixture.complete { fixture.accounts.add() }
        assertFalse(fixture.accounts.state.value.adding)
        assertEquals(AccountFailure.STORAGE, fixture.accounts.state.value.failure)
        assertEquals(1L, fixture.accounts.state.value.activeUid)
        assertEquals("session=1", fixture.cookies.capture())
        assertEquals("original private draft", fixture.postDrafts.data.first()[draftKey])
        assertNull(fixture.vault.read().pending)
    }

    // Bug: a transient vault read failure is mistaken for an empty account list and overwrites it.
    @Test
    fun `an unreadable vault cannot be overwritten by background profile adoption or account actions`() = runTest {
        val fixture = Fixture(this)
        fixture.login(1)
        fixture.cipher.canDecrypt = false
        val reopened = fixture.repository()
        reopened.awaitReady()
        reopened.remember(UserProfile(2, "other", ""), fixture.session.peek().fingerprint)
        reopened.configureAttendance(1) { it.copy(enabled = true) }
        reopened.add()
        runCurrent()
        assertEquals(AccountFailure.STORAGE, reopened.state.value.failure)
        fixture.cipher.canDecrypt = true
        assertEquals(listOf(1L), fixture.vault.read().accounts.map { it.account.uid })
        assertEquals("session=1", fixture.cookies.capture())
    }

    // Bug: a failed encrypted write crashes the switch handler or displays an unsaved enabled state.
    @Test
    fun `failed attendance preference writes preserve the last saved switch value`() = runTest {
        val fixture = Fixture(this)
        fixture.login(1)
        fixture.cipher.canEncrypt = false
        fixture.accounts.configureAttendance(1) { it.copy(enabled = true) }
        assertEquals(AccountFailure.STORAGE, fixture.accounts.state.value.failure)
        assertFalse(fixture.accounts.state.value.accounts.single().attendance.enabled)
        assertFalse(fixture.vault.read().accounts.single().account.attendance.enabled)
    }

    // Bug: manual sign-in and background sign-in both read an unsigned day and race two POSTs.
    @Test
    fun `manual and automatic attendance serialize the same account and recheck its daily ledger`() = runTest {
        val fixture = Fixture(this)
        fixture.login(1)
        fixture.accounts.configureAttendance(1) { it.copy(enabled = true) }
        val runner = fixture.attendanceRunner()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var creditReads = 0
        fixture.beforeAttendanceStatus = {
            creditReads++
            if (!entered.isCompleted) entered.complete(Unit)
            release.await()
        }
        val automatic = async { runner.run() }
        entered.await()
        val manual = async { fixture.manualAttendance.signInForToday(AttendanceMode.FIXED_FIVE) }
        runCurrent()
        assertEquals(1, creditReads)
        release.complete(Unit)
        assertEquals(AutoAttendanceOutcome.COMPLETED, automatic.await())
        assertEquals(5, manual.await().gain)
        assertEquals(listOf(1L to AttendanceMode.RANDOM.wireValue), fixture.attendancePosts)
    }

    // Bug: the first account's old global preference enables every newly added account.
    @Test
    fun `legacy attendance belongs only to the original account and survives switching back`() = runTest {
        val fixture = Fixture(this)
        fixture.settings.automaticAttendance.setEnabled(true)
        fixture.settings.automaticAttendance.setMode(AttendanceMode.FIXED_FIVE)
        fixture.login(1)
        fixture.complete { fixture.accounts.add() }
        fixture.login(2)
        var saved = fixture.vault.read()
        assertTrue(saved.accounts.single { it.account.uid == 1L }.account.attendance.enabled)
        assertFalse(saved.accounts.single { it.account.uid == 2L }.account.attendance.enabled)
        fixture.complete { fixture.accounts.switch(1) }
        saved = fixture.vault.read()
        assertEquals(AttendanceMode.FIXED_FIVE, saved.accounts.single { it.account.uid == 1L }.account.attendance.mode)
        assertTrue(saved.accounts.single { it.account.uid == 1L }.account.attendance.enabled)
        fixture.accounts.remove(2)
        fixture.accounts.state.first { it.accounts.size == 1 }
        assertTrue(fixture.accounts.state.value.accounts.single().attendance.enabled)
    }

    // Bug: an expired first account aborts the entire batch or its cookies replace the browsing login.
    @Test
    fun `enabled accounts run independently and expiry does not stop the next account`() = runTest {
        val fixture = Fixture(this)
        fixture.login(1)
        fixture.accounts.configureAttendance(1) { it.copy(enabled = true) }
        fixture.complete { fixture.accounts.add() }
        fixture.login(2)
        fixture.accounts.configureAttendance(2) { it.copy(enabled = true, mode = AttendanceMode.FIXED_FIVE) }
        fixture.complete { fixture.accounts.add() }
        fixture.login(3)
        fixture.attendanceFailures[1] = SiteError.LoginRequired
        val runner = fixture.attendanceRunner()
        assertEquals(AutoAttendanceOutcome.COMPLETED, runner.run())
        assertEquals(listOf(2L to AttendanceMode.FIXED_FIVE.wireValue), fixture.attendancePosts)
        assertEquals(setOf(1L, 2L), fixture.attendanceReads.toSet())
        assertEquals("session=3", fixture.cookies.capture())
        assertEquals(3L, fixture.accounts.state.value.activeUid)
        assertTrue(fixture.accounts.state.value.accounts.single { it.uid == 1L }.needsSignIn)
        assertTrue(runner.enabled.first())
        assertFalse(runner.config.first().enabled)
        fixture.attendanceRunner().run()
        assertEquals(1, fixture.attendancePosts.size)
    }

    // Bug: rotating a session resets a day's retry budget or a successful account hides a retry.
    @Test
    fun `network failure keeps retry outcome while the other account completes only once`() = runTest {
        val fixture = Fixture(this)
        fixture.login(1)
        fixture.accounts.configureAttendance(1) { it.copy(enabled = true) }
        fixture.complete { fixture.accounts.add() }
        fixture.login(2)
        fixture.accounts.configureAttendance(2) { it.copy(enabled = true) }
        fixture.attendanceFailures[1] = SiteError.Network
        val runner = fixture.attendanceRunner()
        assertEquals(AutoAttendanceOutcome.RETRY, runner.run())
        assertEquals(AutoAttendanceOutcome.RETRY, runner.run())
        assertEquals(1, fixture.attendanceReads.count { it == 1L })
        assertEquals(listOf(2L to AttendanceMode.RANDOM.wireValue), fixture.attendancePosts)
        fixture.now += AutoAttendance.RETRY_DELAY_MILLIS
        fixture.attendanceFailures.clear()
        assertEquals(AutoAttendanceOutcome.COMPLETED, runner.run())
        assertEquals(listOf(2L, 1L), fixture.attendancePosts.map { it.first })
    }

    // Bug: a wrong saved login spends the daily action on someone else's account.
    @Test
    fun `server identity is checked before any background attendance write`() = runTest {
        val fixture = Fixture(this)
        fixture.login(1)
        fixture.accounts.configureAttendance(1) { it.copy(enabled = true) }
        fixture.serverUid = 99
        assertEquals(AutoAttendanceOutcome.BLOCKED, fixture.attendanceRunner().run())
        assertTrue(fixture.attendancePosts.isEmpty())
        assertTrue(fixture.accounts.state.value.accounts.single().needsSignIn)
        assertEquals("session=1", fixture.cookies.capture())
    }

    // Bug: disabling or deleting an account after reading its profile still submits a daily write.
    @Test
    fun `turning off or removing an account during a read prevents the pending write`() = runTest {
        for (remove in listOf(false, true)) {
            val fixture = Fixture(this)
            fixture.login(1)
            fixture.accounts.configureAttendance(1) { it.copy(enabled = true) }
            fixture.complete { fixture.accounts.add() }
            fixture.login(2)
            fixture.beforeAttendanceStatus = {
                if (remove) {
                    fixture.accounts.remove(1)
                    fixture.accounts.state.first { state -> state.accounts.none { it.uid == 1L } }
                } else {
                    fixture.accounts.configureAttendance(1) { it.copy(enabled = false) }
                }
            }
            assertEquals(AutoAttendanceOutcome.SKIPPED, fixture.attendanceRunner().run())
            assertTrue(fixture.attendancePosts.isEmpty())
            assertEquals("session=2", fixture.cookies.capture())
            assertEquals(!remove, fixture.vault.read().accounts.any { it.account.uid == 1L })
        }
    }

    // Bug: the background runner writes refreshed cookies into the browser or loses them on disk.
    @Test
    fun `background cookie refresh is saved only to its own account`() = runTest {
        val fixture = Fixture(this)
        fixture.login(1)
        fixture.accounts.configureAttendance(1) { it.copy(enabled = true) }
        fixture.complete { fixture.accounts.add() }
        fixture.login(2)
        fixture.refreshClearance = true
        fixture.attendanceRunner().run()
        assertTrue(fixture.vault.read().accounts.single { it.account.uid == 1L }.cookies.contains("cf_clearance=renewed"))
        assertEquals("session=2", fixture.cookies.capture())
        assertEquals("session=2", fixture.vault.read().accounts.single { it.account.uid == 2L }.cookies)
    }

    // Bug: account switching waits forever for a background request or it resurrects the old login.
    @Test
    fun `switching cancels isolated background requests before replacing the foreground account`() = runTest {
        val fixture = Fixture(this)
        fixture.login(1)
        fixture.accounts.configureAttendance(1) { it.copy(enabled = true) }
        fixture.complete { fixture.accounts.add() }
        fixture.login(2)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        fixture.beforeAttendanceStatus = {
            entered.complete(Unit)
            release.await()
        }
        val running = async { fixture.attendanceRunner().run() }
        entered.await()
        fixture.complete { fixture.accounts.switch(1) }
        assertTrue(running.isCancelled)
        assertTrue(fixture.attendancePosts.isEmpty())
        assertEquals("session=1", fixture.cookies.capture())
        assertEquals(1L, fixture.accounts.state.value.activeUid)
    }

    // Bug: restoring only cookies leaves A's draft and unread watermark attached to B's session.
    @Test
    fun `switching restores each verified account's drafts and notification baseline`() = runTest {
        val fixture = Fixture(this)
        fixture.login(1)
        fixture.draft("alpha")
        fixture.settings.setNotificationSeenCounts(NotificationCounts(replies = 37))
        fixture.complete { fixture.accounts.add() }
        assertFalse(fixture.session.state.value.isSignedIn)
        assertTrue(fixture.postDrafts.data.first().asMap().isEmpty())
        fixture.login(2)
        fixture.draft("beta")
        fixture.settings.setNotificationSeenCounts(NotificationCounts(messages = 9))
        fixture.complete { fixture.accounts.switch(1) }
        assertEquals(1L, fixture.accounts.state.value.activeUid)
        assertEquals("session=1", fixture.cookies.capture())
        assertEquals("alpha", fixture.postDrafts.data.first()[draftKey])
        assertEquals(NotificationCounts(replies = 37), fixture.settings.notificationSeenCounts())
        fixture.complete { fixture.accounts.switch(2) }
        assertEquals("beta", fixture.postDrafts.data.first()[draftKey])
        assertEquals(NotificationCounts(messages = 9), fixture.settings.notificationSeenCounts())
    }

    // Bug: an account transition can clear data while a hidden tab still displays it.
    @Test
    fun `cookies stay unchanged until all attached navigation owners release their pages`() = runTest {
        val fixture = Fixture(this)
        fixture.login(1)
        var released = false
        val detach = fixture.accounts.attachUi { released = true }
        fixture.accounts.add()
        fixture.accounts.state.first { it.busy }
        assertTrue(fixture.accounts.state.value.busy)
        assertEquals("session=1", fixture.cookies.capture())
        detach()
        fixture.accounts.state.first { !it.busy && it.revision > 0 }
        assertTrue(released)
        assertEquals("", fixture.cookies.capture())
        assertTrue(fixture.accounts.state.value.adding)
    }

    // Bug: backing out of adding an account silently signs out the previously active one.
    @Test
    fun `canceling an additional login restores the previous account and unfinished draft`() = runTest {
        val fixture = Fixture(this)
        fixture.login(1)
        fixture.draft("unfinished")
        fixture.complete { fixture.accounts.add() }
        fixture.complete { fixture.accounts.cancelAdd() }
        assertEquals(1L, fixture.accounts.state.value.activeUid)
        assertFalse(fixture.accounts.state.value.adding)
        assertEquals("session=1", fixture.cookies.capture())
        assertEquals("unfinished", fixture.postDrafts.data.first()[draftKey])
        assertEquals(1, fixture.accounts.state.value.accounts.size)
    }

    // Bug: an expired or wrong cookie is accepted merely because a cookie named session exists.
    @Test
    fun `a rejected saved login rolls back and marks only that account as needing login`() = runTest {
        val fixture = Fixture(this)
        fixture.login(1)
        fixture.complete { fixture.accounts.add() }
        fixture.login(2)
        fixture.draft("keep beta")
        fixture.offline.hasCopy = true
        fixture.rejectedUid = 1
        fixture.complete { fixture.accounts.switch(1) }
        assertEquals(2L, fixture.accounts.state.value.activeUid)
        assertEquals("session=2", fixture.cookies.capture())
        assertEquals("keep beta", fixture.postDrafts.data.first()[draftKey])
        assertTrue(fixture.offline.hasCopy)
        assertEquals(AccountFailure.EXPIRED, fixture.accounts.state.value.failure)
        assertTrue(fixture.accounts.state.value.accounts.single { it.uid == 1L }.needsSignIn)
        assertFalse(fixture.accounts.state.value.accounts.single { it.uid == 2L }.needsSignIn)
    }

    // Bug: signing out deletes all saved accounts or a saved cookie lets the signed-out one return.
    @Test
    fun `signing out forgets the current saved login while retaining the other account`() = runTest {
        val fixture = Fixture(this)
        fixture.login(1)
        fixture.complete { fixture.accounts.add() }
        fixture.login(2)
        fixture.accounts.signOut()
        advanceUntilIdle()
        assertNull(fixture.accounts.state.value.activeUid)
        assertFalse(fixture.session.state.value.isSignedIn)
        assertEquals(listOf(1L), fixture.vault.read().accounts.map { it.account.uid })
        fixture.complete { fixture.accounts.switch(1) }
        assertEquals(1L, fixture.accounts.state.value.activeUid)
    }

    // Bug: a failing keystore discards the only live session before it has been saved.
    @Test
    fun `failed secret persistence never replaces the active cookies`() = runTest {
        val fixture = Fixture(this)
        fixture.login(1)
        fixture.draft("keep alpha")
        fixture.cipher.canEncrypt = false
        fixture.complete { fixture.accounts.add() }
        assertEquals("session=1", fixture.cookies.capture())
        assertEquals("keep alpha", fixture.postDrafts.data.first()[draftKey])
        assertEquals(AccountFailure.STORAGE, fixture.accounts.state.value.failure)
        assertFalse(fixture.accounts.state.value.busy)
    }

    // Bug: a process death after clearing the active draft file loses it before account commit.
    @Test
    fun `startup recovers an interrupted account change before exposing navigation`() = runTest {
        val fixture = Fixture(this)
        fixture.login(1)
        fixture.vault.write(
            fixture.vault.read().copy(
                recovery = PendingAccount(
                    previousUid = 1,
                    cookies = "session=1",
                    local = AccountLocalSnapshot(postDrafts = mapOf("draft" to "journal draft")),
                ),
            ),
        )
        fixture.jar.removeAll()
        fixture.jar.setCookie(NodeSeekSite.BASE_URL, "session=2")
        fixture.draft("partial second account")
        val restarted = fixture.repository()
        restarted.awaitReady()
        assertTrue(restarted.state.value.ready)
        assertEquals(1L, restarted.state.value.activeUid)
        assertEquals("session=1", fixture.cookies.capture())
        assertEquals("journal draft", fixture.postDrafts.data.first()[draftKey])
        assertNull(fixture.vault.read().recovery)
    }

    // Bug: a manual account change in the site browser attributes the old account's draft to the new UID.
    @Test
    fun `browser identity changes archive the previous draft without handing it to the next user`() = runTest {
        val fixture = Fixture(this)
        fixture.login(1)
        fixture.draft("private alpha")
        fixture.login(2)
        assertNull(fixture.postDrafts.data.first()[draftKey])
        fixture.complete { fixture.accounts.switch(1) }
        assertEquals("private alpha", fixture.postDrafts.data.first()[draftKey])
        assertEquals("session=1", fixture.cookies.capture())
    }

    private companion object {
        val draftKey = stringPreferencesKey("draft")
    }

    // Bug: Foundation drops session-only cookies at process exit even though the account was saved.
    @Test
    fun `startup restores the active saved session without overwriting its newer draft file`() = runTest {
        val fixture = Fixture(this)
        fixture.login(1)
        fixture.draft("newer than the saved account snapshot")
        fixture.jar.removeAll()
        fixture.session.sync()
        val restarted = fixture.repository()
        restarted.awaitReady()
        assertTrue(fixture.session.state.value.isSignedIn)
        assertEquals("session=1", fixture.cookies.capture())
        assertEquals("newer than the saved account snapshot", fixture.postDrafts.data.first()[draftKey])
    }

    // Bug: a malformed legacy recovery cookie keeps vaultLoaded false forever, even after a new login.
    @Test
    fun `invalid interrupted add is quarantined and a new login can actually be saved`() = runTest {
        val fixture = Fixture(this)
        fixture.accounts.awaitReady()
        val recovery = PendingAccount(null, "broken-recovery", AccountLocalSnapshot(postDrafts = mapOf("draft" to "unidentified private draft")))
        val pending = PendingAccount(null, "broken-pending", AccountLocalSnapshot(postDrafts = mapOf("draft" to "earlier private draft")))
        fixture.vault.write(AccountVault(pending = pending, recovery = recovery))
        fixture.invalidSnapshots += recovery.cookies
        fixture.jar.setCookie(NodeSeekSite.BASE_URL, "session=2")
        fixture.session.sync()
        val reopened = fixture.repository()
        reopened.awaitReady()
        assertTrue(reopened.storageAvailable)
        assertTrue(reopened.state.value.adding)
        assertFalse(fixture.session.peek().isSignedIn)
        assertNull(reopened.state.value.activeUid)
        assertEquals(setOf(recovery, pending), fixture.vault.read().quarantined.toSet())
        assertNull(fixture.vault.read().recovery)
        fixture.jar.setCookie(NodeSeekSite.BASE_URL, "session=2")
        fixture.session.sync()
        assertTrue(reopened.saveCurrentLogin())
        assertEquals(2L, fixture.vault.read().activeUid)
        assertTrue(fixture.vault.read().accounts.single().local.postDrafts.isEmpty())
        val restarted = fixture.repository()
        restarted.awaitReady()
        assertTrue(restarted.storageAvailable)
        assertEquals(2L, restarted.state.value.activeUid)
        assertEquals(setOf(recovery, pending), fixture.vault.read().quarantined.toSet())
    }

    // Bug: skipping invalid recovery associates A's journal drafts with the next successfully signed-in UID.
    @Test
    fun `quarantining one account preserves other accounts and restores drafts only to their owner`() = runTest {
        val fixture = Fixture(this)
        fixture.login(1)
        fixture.complete { fixture.accounts.add() }
        fixture.login(2)
        fixture.draft("beta saved draft")
        fixture.accounts.configureAttendance(2) { it.copy(enabled = true) }
        fixture.complete { fixture.accounts.switch(1) }
        val original = fixture.vault.read()
        val beta = original.accounts.single { it.account.uid == 2L }
        val recovery = PendingAccount(1, "broken-alpha", AccountLocalSnapshot(postDrafts = mapOf("draft" to "alpha journal draft")))
        fixture.vault.write(original.copy(recovery = recovery))
        fixture.invalidSnapshots += recovery.cookies
        fixture.offline.hasCopy = true
        val reopened = fixture.repository()
        reopened.awaitReady()
        assertTrue(reopened.storageAvailable)
        assertEquals(beta, fixture.vault.read().accounts.single { it.account.uid == 2L })
        val alpha = fixture.vault.read().accounts.single { it.account.uid == 1L }
        assertTrue(alpha.account.needsSignIn)
        assertEquals("", alpha.cookies)
        assertEquals(recovery.local, alpha.local)
        assertNull(fixture.postDrafts.data.first()[draftKey])
        assertFalse(fixture.offline.hasCopy)
        fixture.complete(reopened) { reopened.switch(2) }
        assertEquals("beta saved draft", fixture.postDrafts.data.first()[draftKey])
        fixture.complete(reopened) { reopened.add() }
        fixture.login(1, reopened)
        assertEquals("alpha journal draft", fixture.postDrafts.data.first()[draftKey])
        assertFalse(fixture.vault.read().accounts.single { it.account.uid == 1L }.account.needsSignIn)
        assertTrue(fixture.vault.read().accounts.single { it.account.uid == 2L }.account.attendance.enabled)
        reopened.remove(1)
        reopened.state.first { state -> state.accounts.none { it.uid == 1L } }
        assertTrue(fixture.vault.read().quarantined.none { it.previousUid == 1L })
    }

    // Bug: treating a platform-store error as corrupt data destroys a valid recovery record and live draft.
    @Test
    fun `ordinary cookie store failure never quarantines or clears the recovery data`() = runTest {
        val fixture = Fixture(this)
        fixture.login(1)
        fixture.draft("live draft")
        val original = fixture.vault.read().copy(recovery = PendingAccount(1, "session=1", AccountLocalSnapshot()))
        fixture.vault.write(original)
        fixture.replaceFailure = AccountNativeFailure("NSPOSIXErrorDomain", 13)
        val reopened = fixture.repository()
        reopened.awaitReady()
        assertFalse(reopened.storageAvailable)
        assertEquals(AccountFailure.STORAGE, reopened.state.value.failure)
        assertEquals(original, fixture.vault.read())
        assertEquals("session=1", fixture.cookies.capture())
        assertEquals("live draft", fixture.postDrafts.data.first()[draftKey])
    }

    // Bug: clearing a malformed recovery before its quarantine write succeeds loses the only private draft.
    @Test
    fun `failed quarantine persistence leaves the original vault and live files untouched`() = runTest {
        val fixture = Fixture(this)
        fixture.login(1)
        fixture.draft("keep live draft")
        val original = fixture.vault.read().copy(recovery = PendingAccount(1, "broken", AccountLocalSnapshot(postDrafts = mapOf("draft" to "keep journal"))))
        fixture.vault.write(original)
        fixture.invalidSnapshots += "broken"
        fixture.cipher.canEncrypt = false
        val reopened = fixture.repository()
        reopened.awaitReady()
        assertFalse(reopened.storageAvailable)
        assertEquals(original, fixture.vault.read())
        assertEquals("session=1", fixture.cookies.capture())
        assertEquals("keep live draft", fixture.postDrafts.data.first()[draftKey])
    }

    // Bug: invalid active cookies either block initialization forever or discard newer on-disk drafts.
    @Test
    fun `invalid active cookies retain newer live drafts for the same account to sign in again`() = runTest {
        val fixture = Fixture(this)
        fixture.login(1)
        fixture.draft("newer live draft")
        val original = fixture.vault.read()
        fixture.vault.write(original.copy(accounts = original.accounts.map { it.copy(cookies = "broken") }))
        fixture.invalidSnapshots += "broken"
        fixture.jar.removeAll()
        fixture.session.sync()
        val reopened = fixture.repository()
        reopened.awaitReady()
        assertTrue(reopened.storageAvailable)
        assertNull(fixture.postDrafts.data.first()[draftKey])
        assertTrue(fixture.vault.read().quarantined.any { it.local.postDrafts["draft"] == "newer live draft" })
        fixture.login(1, reopened)
        assertEquals("newer live draft", fixture.postDrafts.data.first()[draftKey])
        assertFalse(reopened.state.value.adding)
    }

    // Bug: a crash after quarantine but before cleanup commit repeats the bad snapshot or exposes old files.
    @Test
    fun `a failed cleanup commit restarts with an empty journal and never replays the corrupt snapshot`() = runTest {
        val fixture = Fixture(this)
        fixture.accounts.awaitReady()
        val recovery = PendingAccount(null, "broken", AccountLocalSnapshot(postDrafts = mapOf("draft" to "protected draft")))
        fixture.vault.write(AccountVault(recovery = recovery))
        fixture.invalidSnapshots += "broken"
        fixture.afterReplace = { if (it.isEmpty()) fixture.cipher.canEncrypt = false }
        val failed = fixture.repository()
        failed.awaitReady()
        assertFalse(failed.storageAvailable)
        val checkpoint = fixture.vault.read()
        assertEquals(listOf(recovery), checkpoint.quarantined)
        assertEquals("", checkNotNull(checkpoint.recovery).cookies)
        fixture.afterReplace = {}
        fixture.cipher.canEncrypt = true
        fixture.jar.setCookie(NodeSeekSite.BASE_URL, "session=99")
        fixture.draft("partial cleanup must not be adopted")
        val restarted = fixture.repository()
        restarted.awaitReady()
        assertTrue(restarted.storageAvailable)
        assertFalse(fixture.session.peek().isSignedIn)
        assertNull(fixture.postDrafts.data.first()[draftKey])
        assertEquals(listOf(recovery), fixture.vault.read().quarantined)
        assertNull(fixture.vault.read().recovery)
        assertTrue(restarted.state.value.adding)
    }

    // Bug: canceling the automatic recovery login lets a later normal login overwrite its owner's saved drafts.
    @Test
    fun `canceling recovery login still preserves the known account drafts when signing in later`() = runTest {
        val fixture = Fixture(this)
        fixture.login(1)
        val recovery = PendingAccount(1, "broken", AccountLocalSnapshot(postDrafts = mapOf("draft" to "owner draft")))
        fixture.vault.write(fixture.vault.read().copy(recovery = recovery))
        fixture.invalidSnapshots += "broken"
        val reopened = fixture.repository()
        reopened.awaitReady()
        fixture.complete(reopened) { reopened.cancelAdd() }
        assertFalse(reopened.state.value.adding)
        fixture.login(1, reopened)
        assertEquals("owner draft", fixture.postDrafts.data.first()[draftKey])
        assertEquals("owner draft", fixture.vault.read().accounts.single().local.postDrafts["draft"])
    }

    // Bug: a platform clear that silently leaves cookies active makes quarantine adopt an untrusted identity.
    @Test
    fun `incomplete cookie cleanup cannot report successful recovery and remains restartable`() = runTest {
        val fixture = Fixture(this)
        fixture.login(1)
        val recovery = PendingAccount(1, "broken", AccountLocalSnapshot(postDrafts = mapOf("draft" to "owner draft")))
        fixture.vault.write(fixture.vault.read().copy(recovery = recovery))
        fixture.invalidSnapshots += "broken"
        fixture.keepLoginOnClear = true
        val failed = fixture.repository()
        failed.awaitReady()
        assertFalse(failed.storageAvailable)
        assertEquals(AccountFailure.STORAGE, failed.state.value.failure)
        assertEquals("", checkNotNull(fixture.vault.read().recovery).cookies)
        fixture.keepLoginOnClear = false
        val restarted = fixture.repository()
        restarted.awaitReady()
        assertTrue(restarted.storageAvailable)
        assertFalse(fixture.session.peek().isSignedIn)
        assertNull(fixture.postDrafts.data.first()[draftKey])
        assertNull(fixture.vault.read().recovery)
        assertEquals(recovery.local, fixture.vault.read().accounts.single().local)
    }

    private class Fixture(private val test: TestScope) {
        var clearMail: suspend () -> Unit = {}
        var keepLoginOnClear = false
        var captureFailure: Exception? = null
        val invalidSnapshots = mutableSetOf<String>()
        var replaceFailure: Exception? = null
        var afterReplace: suspend (String) -> Unit = {}
        val jar = FakeSessionCookieStore()
        val session = SessionRepository(SessionCookies(NodeSeekSite.CONFIG, jar))
        val cookies = object : AccountCookieStore {
            override suspend fun capture(): String {
                captureFailure?.let { throw it }
                return jar.cookieHeader(NodeSeekSite.BASE_URL).orEmpty()
            }
            override suspend fun replace(snapshot: String) {
                if (snapshot in invalidSnapshots) throw InvalidAccountCookieSnapshot(AccountNativeFailure("NSCocoaErrorDomain", 3840))
                replaceFailure?.let { throw it }
                if (snapshot.isEmpty() && keepLoginOnClear) return
                jar.removeAll()
                if (snapshot.isNotEmpty()) jar.setCookie(NodeSeekSite.BASE_URL, snapshot)
                afterReplace(snapshot)
            }
        }
        val cipher = TestCipher()
        val vault = SavedAccountStore(testPreferenceStore(test.backgroundScope, "accounts"), cipher, "test-site")
        val postDrafts = testPreferenceStore(test.backgroundScope, "posts")
        private val commentDrafts = testPreferenceStore(test.backgroundScope, "comments")
        val settings = SettingsRepository(testPreferenceStore(test.backgroundScope, "settings"))
        var now = 1_783_814_400_000L
        val attendanceFailures = mutableMapOf<Long, SiteError>()
        val attendanceReads = mutableListOf<Long>()
        val attendancePosts = mutableListOf<Pair<Long, String>>()
        var beforeAttendanceStatus: suspend () -> Unit = {}
        var refreshClearance = false
        var serverUid: Long? = null
        val attendanceWrites = AttendanceWriteGate()
        lateinit var manualAttendance: AssetsRepository
        var rejectedUid: Long? = null
        val offline = TestOfflineLibrary()
        private val local = AccountLocalData(
            postDrafts,
            commentDrafts,
            NoOpPostRepository(),
            object : ProfileRepository {
                override suspend fun profile(refresh: Boolean): UserProfile = error("Not the identity source")
                override suspend fun profile(uid: Long): UserProfile = error("Not used")
            },
            offline,
            settings,
            clearMail = { clearMail() },
        )
        val accounts = repository()

        fun repository() = AccountRepository(vault, cookies, session, AccountRequestGate(), local, currentProfile = {
            val uid = cookies.capture().substringAfter("session=").toLong()
            if (uid == rejectedUid) throw SiteException(SiteError.LoginRequired)
            UserProfile(uid, "account-$uid", "")
        }, stopPlatformRequests = {}, scope = test, legacyAttendance = { settings.automaticAttendance.config.first() })

        fun attendanceRunner(): MultiAccountAttendance {
            val dispatcher = StandardTestDispatcher(test.testScheduler)
            val dispatchers = AppDispatchers(io = dispatcher, default = dispatcher, main = dispatcher)
            val clock = AppClock { now }
            fun isolated(snapshot: String): AttendanceSession {
                val privateJar = FakeSessionCookieStore().apply {
                    snapshot.split("; ").filter { it.isNotEmpty() }.forEach { setCookie(NodeSeekSite.BASE_URL, it) }
                }
                val uid = snapshot.substringAfter("session=").substringBefore(';').toLong()
                val transport = object : HttpTransport {
                    override suspend fun execute(request: HttpRequest, onUploadProgress: UploadProgress?): HttpResponse {
                        val path = request.url.removePrefix(NodeSeekSite.BASE_URL)
                        val body = when {
                            path == "/" -> {
                                attendanceReads += uid
                                attendanceFailures[uid]?.let { throw SiteException(it) }
                                if (refreshClearance) privateJar.setCookie(NodeSeekSite.BASE_URL, "cf_clearance=renewed")
                                val profile = """{"user":{"member_id":${serverUid ?: uid},"member_name":"account-$uid"}}"""
                                "<html><script id=\"temp-script\">${Base64.encode(profile.encodeToByteArray())}</script></html>"
                            }

                            path.startsWith("/api/account/getInfo/") -> """{"data":{"member_id":${serverUid ?: uid},"member_name":"account-$uid"}}"""

                            path.startsWith("/api/account/credit/") -> {
                                beforeAttendanceStatus()
                                if (attendancePosts.any { it.first == uid }) """{"data":[[5,100,"签到收益5个鸡腿","${Instant.fromEpochMilliseconds(now)}"]],"total":1}""" else """{"data":[],"total":0}"""
                            }

                            path.startsWith("/api/attendance?") -> {
                                attendancePosts += uid to path.substringAfter("random=")
                                """{"success":true,"gain":5}"""
                            }

                            else -> error("Unexpected attendance request $path")
                        }
                        return HttpResponse(200, request.url, emptyMap(), body)
                    }
                }
                return AttendanceSession(transport, SessionCookies(NodeSeekSite.CONFIG, privateJar), { privateJar.cookieHeader(NodeSeekSite.BASE_URL).orEmpty() }, {})
            }
            val fallback = isolated("session=${accounts.state.value.activeUid ?: 999}")
            val json = NodeSeekJsonClient(fallback.transport, dispatchers)
            val profiles = NetworkProfileRepository(SiteHtmlClient(fallback.transport, dispatchers, NodeSeekSite.CONFIG), json, null, { 0 }, { true }, clock)
            manualAttendance = NetworkAssetsRepository(profiles, NetworkCreditRepository(json, dispatchers), json, dispatchers, clock, attendanceWrites)
            val legacy = AutoAttendance(settings.automaticAttendance, session, profiles, manualAttendance, clock, test.backgroundScope)
            return MultiAccountAttendance(accounts, settings.automaticAttendance, legacy, NodeSeekSite.CONFIG, dispatchers, clock, test.backgroundScope, attendanceWrites, ::isolated)
        }

        suspend fun login(uid: Long, repository: AccountRepository = accounts) {
            repository.awaitReady()
            jar.removeAll()
            jar.setCookie(NodeSeekSite.BASE_URL, "session=$uid")
            session.sync()
            repository.remember(UserProfile(uid, "account-$uid", ""), session.state.value.fingerprint)
        }
        suspend fun complete(repository: AccountRepository = accounts, action: () -> Unit) {
            val revision = repository.state.value.revision
            action()
            repository.state.first { !it.busy && it.revision > revision }
        }

        suspend fun draft(text: String) {
            postDrafts.edit { it[draftKey] = text }
        }
    }

    private class TestCipher : SecretCipher {
        var canEncrypt = true
        var canDecrypt = true
        override fun encrypt(plaintext: String): String? = plaintext.takeIf { canEncrypt }?.reversed()
        override fun decrypt(stored: String): String = if (canDecrypt) stored.reversed() else ""
    }

    private class TestOfflineLibrary : OfflineLibrary by EmptyOfflineLibrary {
        var hasCopy = false
        override suspend fun clearAll() {
            hasCopy = false
        }
    }
}

private object EmptyOfflineLibrary : OfflineLibrary {
    override val isAvailable = true
    override val states = flowOf(emptyMap<Long, OfflineState>())
    override val usage = flowOf(OfflineUsage())
    override val settings = flowOf(OfflineSettings())
    override suspend fun download(postIds: Collection<Long>) = Unit
    override suspend fun noteReplyCounts(counts: Map<Long, Int>) = Unit
    override suspend fun estimateBytes(postIds: Collection<Long>): Long? = null
    override suspend fun cancel(postId: Long) = Unit
    override suspend fun clearAll() = Unit
    override suspend fun updateSettings(settings: OfflineSettings) = Unit
}
