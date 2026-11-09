package io.github.nodyssey.data.session

import io.github.nodyssey.data.UserProfile
import io.github.nodyssey.data.attendance.AutoAttendanceConfig
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

enum class AccountFailure { EXPIRED, VERIFY, NETWORK, STORAGE, UNSUPPORTED, UNKNOWN }

data class AccountsState(
    val ready: Boolean = false,
    val accounts: List<SavedAccount> = emptyList(),
    val activeUid: Long? = null,
    val adding: Boolean = false,
    val busy: Boolean = false,
    /** Recreates navigation and its ViewModels only when the account changes. */
    val revision: Long = 0,
    val failure: AccountFailure? = null,
    val navigationKey: String = "legacy",
)

/** One active account, with recoverable cookie replacement and no password storage. */
class AccountRepository(
    private val store: SavedAccountStore,
    private val cookies: AccountCookieStore,
    private val session: SessionRepository,
    private val requests: AccountRequestGate,
    private val local: AccountLocalData,
    private val currentProfile: suspend (Boolean) -> UserProfile,
    private val stopPlatformRequests: suspend () -> Unit,
    private val scope: CoroutineScope,
    private val legacyAttendance: suspend () -> AutoAttendanceConfig = { AutoAttendanceConfig() },
) {
    private val mutableState = MutableStateFlow(AccountsState())
    val state = mutableState.asStateFlow()
    private val ready = CompletableDeferred<Unit>()
    private val mutex = Mutex()
    private var vault = AccountVault()
    private var vaultLoaded = false
    internal val storageAvailable: Boolean get() = vaultLoaded
    private val attached = mutableMapOf<Any, () -> Unit>()
    private var detached: CompletableDeferred<Unit>? = null

    init {
        scope.launch {
            try {
                vault = store.read()
                recoverInterruptedChange()
                restoreActiveCookies()
                vaultLoaded = true
                publish()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                requests.resume()
                publish(failure = AccountFailure.STORAGE)
            } finally {
                ready.complete(Unit)
            }
        }
    }

    private suspend fun recoverInterruptedChange() {
        var previous = vault.recovery ?: return
        requests.pause()
        stopPlatformRequests()
        try {
            // Decoding must finish before touching drafts. Platform stores also validate before
            // replacing live cookies, so a failed quarantine write leaves both originals intact.
            cookies.replace(previous.cookies)
        } catch (_: InvalidAccountCookieSnapshot) {
            quarantineCookies(previous)
            previous = checkNotNull(vault.recovery)
            cookies.replace(previous.cookies)
        }
        val clearingQuarantine = previous.previousUid == null && previous.cookies.isEmpty() && vault.quarantined.isNotEmpty()
        local.clear(evictDownloads = clearingQuarantine)
        session.sync()
        if (clearingQuarantine) check(!session.state.value.isSignedIn) { "Could not clear the invalid login" }
        local.restore(previous.local)
        val next = vault.copy(activeUid = previous.previousUid, recovery = null)
        store.write(next)
        vault = next
        requests.resume()
    }

    private suspend fun restoreActiveCookies() {
        // Session-only platform cookies may disappear when the process exits. A saved account
        // still owns the active draft files, so restore its cookies before drawing.
        if (vault.pending != null || session.peek().isSignedIn) return
        val active = vault.accounts.find { it.account.uid == vault.activeUid } ?: return
        requests.pause()
        stopPlatformRequests()
        try {
            cookies.replace(active.cookies)
        } catch (_: InvalidAccountCookieSnapshot) {
            // The files may be newer than the last saved snapshot. Preserve them for this UID,
            // but never attribute them to whoever signs in after this recovery.
            quarantineCookies(PendingAccount(active.account.uid, active.cookies, local.capture()))
            recoverInterruptedChange()
            return
        }
        session.sync()
        if (!session.state.value.isSignedIn) {
            val parked = active.copy(account = active.account.copy(needsSignIn = true))
            val next = vault.copy(
                accounts = vault.accounts.map { if (it.account.uid == active.account.uid) parked else it },
                navigationKey = Uuid.random().toString(),
            )
            store.write(next)
            vault = next
        }
        requests.resume()
    }

    private suspend fun quarantineCookies(previous: PendingAccount) {
        val saved = vault.accounts.find { it.account.uid == previous.previousUid }
        val empty = PendingAccount(null, "", AccountLocalSnapshot())
        val next = vault.copy(
            accounts = vault.accounts.map { entry ->
                if (entry.account.uid == previous.previousUid) {
                    entry.copy(account = entry.account.copy(needsSignIn = true), cookies = "", local = previous.local)
                } else {
                    entry
                }
            },
            activeUid = null,
            // Keep an empty pending add so signing back into a saved UID restores its own drafts.
            // The empty recovery journal makes interrupted cleanup safe to repeat on next launch.
            pending = empty,
            recovery = empty,
            quarantined = (vault.quarantined + listOfNotNull(vault.recovery, vault.pending, saved?.let { PendingAccount(it.account.uid, it.cookies, it.local) }, previous)).distinct(),
            navigationKey = Uuid.random().toString(),
        )
        store.write(next)
        vault = next
    }

    suspend fun awaitReady() = ready.await()
    suspend fun <T> runWork(block: suspend () -> T): T {
        awaitReady()
        return requests.run(block)
    }

    suspend fun configureAttendance(uid: Long, change: (AutoAttendanceConfig) -> AutoAttendanceConfig) =
        withContext(scope.coroutineContext.minusKey(Job)) {
            awaitReady()
            mutex.withLock {
                if (!vaultLoaded) {
                    publish(failure = AccountFailure.STORAGE)
                    return@withLock
                }
                val next = vault.copy(
                    accounts = vault.accounts.map { entry ->
                        if (entry.account.uid == uid) entry.copy(account = entry.account.copy(attendance = change(entry.account.attendance))) else entry
                    },
                )
                try {
                    store.write(next)
                    vault = next
                    publish()
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    publish(failure = AccountFailure.STORAGE)
                }
            }
        }

    /** A short lease on one saved login. Capturing it never replaces the browsing cookies. */
    internal suspend fun attendanceAccount(uid: Long): AttendanceAccount? = withContext(scope.coroutineContext.minusKey(Job)) {
        awaitReady()
        mutex.withLock {
            val entry = vault.accounts.find { it.account.uid == uid } ?: return@withLock null
            if (!entry.account.attendance.enabled || entry.account.needsSignIn || vault.pending != null) return@withLock null
            val active = uid == vault.activeUid
            AttendanceAccount(uid, if (active) cookies.capture() else entry.cookies, entry.cookies, if (active) session.peek().fingerprint else entry.fingerprint)
        }
    }

    internal suspend fun saveAttendanceCookies(lease: AttendanceAccount, updated: String, fingerprint: Int, expired: Boolean) =
        withContext(scope.coroutineContext.minusKey(Job)) {
            mutex.withLock {
                val current = vault.accounts.find { it.account.uid == lease.uid } ?: return@withLock
                // A removed/re-added account or a browser login owns newer credentials.
                if (current.cookies != lease.storedCookies || current.fingerprint != lease.fingerprint ||
                    (vault.activeUid == lease.uid && session.peek().fingerprint != lease.fingerprint)
                ) {
                    return@withLock
                }
                val next = vault.copy(
                    accounts = vault.accounts.map { entry ->
                        if (entry.account.uid == lease.uid) entry.copy(cookies = updated, fingerprint = fingerprint, account = entry.account.copy(needsSignIn = expired)) else entry
                    },
                )
                store.write(next)
                vault = next
                publish()
            }
        }

    /** The shell releases all page-owned ViewModels before acknowledging a transition. */
    fun attachUi(release: () -> Unit): () -> Unit {
        val token = Any()
        attached[token] = release
        return {
            if (mutableState.value.busy) release()
            attached.remove(token)
            if (attached.isEmpty()) detached?.complete(Unit)
        }
    }

    fun dismissFailure() {
        mutableState.value = mutableState.value.copy(failure = null)
    }

    fun refresh() = launchAction {
        if (session.peek().isSignedIn) {
            val fingerprint = session.state.value.fingerprint
            rememberLocked(currentProfile(false), fingerprint)
        }
    }

    /** Explicit completion waits for concurrent adoption and never mistakes a cookie for a save. */
    suspend fun saveCurrentLogin(): Boolean = withContext(scope.coroutineContext.minusKey(Job)) {
        awaitReady()
        mutex.withLock {
            try {
                check(vaultLoaded) { "Saved accounts are unavailable" }
                val fingerprint = session.peek().fingerprint
                if (!session.peek().isSignedIn) return@withLock false
                val profile = currentProfile(false)
                rememberLocked(profile, fingerprint)
                vault.pending == null && vault.activeUid == profile.uid &&
                    session.peek().isSignedIn && session.peek().fingerprint == fingerprint
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                publish(failure = classify(failure))
                false
            }
        }
    }

    /** Called only with a profile obtained under this fingerprint, never a username typed at login. */
    suspend fun remember(profile: UserProfile, fingerprint: Int) {
        // A browser may have changed identity directly. The work must outlive the old navigation
        // effect, since adopting that identity disposes the effect that called this method.
        scope.async { rememberInScope(profile, fingerprint) }.await()
    }

    private suspend fun rememberInScope(profile: UserProfile, fingerprint: Int) {
        awaitReady()
        if (!vaultLoaded) return
        if (!mutex.tryLock()) return
        try {
            rememberLocked(profile, fingerprint)
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            publish(failure = classify(failure))
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun rememberLocked(profile: UserProfile, fingerprint: Int) {
        if (profile.uid <= 0 || !session.state.value.isSignedIn || session.state.value.fingerprint != fingerprint) return
        if (vault.activeUid != null && vault.activeUid != profile.uid && vault.pending == null) {
            adoptBrowserAccount(profile, fingerprint)
            return
        }
        val saved = vault.accounts.find { it.account.uid == profile.uid }
        // Logging into an already saved account resumes its drafts rather than overwriting them
        // with the empty files used while the additional account was being signed in.
        if (saved != null && (vault.pending != null || (vault.activeUid == null && saved.account.needsSignIn))) local.restore(saved.local)
        val entry = StoredAccount(
            SavedAccount(profile.uid, profile.name, profile.avatarUrl, attendance = saved?.account?.attendance ?: if (vault.navigationKey == "legacy") legacyAttendance() else AutoAttendanceConfig()),
            cookies.capture(),
            fingerprint,
            local.capture(),
        )
        val next = vault.copy(
            accounts = vault.accounts.filterNot { it.account.uid == profile.uid } + entry,
            activeUid = profile.uid,
            pending = null,
            navigationKey = if (vault.pending != null) Uuid.random().toString() else vault.navigationKey,
        )
        store.write(next)
        vault = next
        publish()
    }

    private suspend fun adoptBrowserAccount(profile: UserProfile, fingerprint: Int) {
        val original = vault
        val old = checkNotNull(original.accounts.find { it.account.uid == original.activeUid })
        val disposed = CompletableDeferred<Unit>()
        detached = disposed
        session.beginAccountChange()
        mutableState.value = state.value.copy(busy = true, failure = null)
        var previous: PendingAccount? = null
        var failureResult: AccountFailure? = null
        try {
            if (attached.isNotEmpty()) disposed.await()
            requests.pause()
            stopPlatformRequests()
            previous = PendingAccount(old.account.uid, old.cookies, local.capture())
            vault = original.copy(
                accounts = original.accounts.map { if (it.account.uid == old.account.uid) it.copy(local = previous.local) else it },
                recovery = previous,
            )
            store.write(vault)
            local.clear(evictDownloads = false)
            local.restore(original.accounts.find { it.account.uid == profile.uid }?.local ?: AccountLocalSnapshot())
            val entry = StoredAccount(SavedAccount(profile.uid, profile.name, profile.avatarUrl, attendance = original.accounts.find { it.account.uid == profile.uid }?.account?.attendance ?: AutoAttendanceConfig()), cookies.capture(), fingerprint, local.capture())
            val next = vault.copy(accounts = vault.accounts.filterNot { it.account.uid == profile.uid } + entry, activeUid = profile.uid, recovery = null, navigationKey = Uuid.random().toString())
            local.evictDownloads()
            store.write(next)
            vault = next
        } catch (failure: Exception) {
            failureResult = classify(failure)
            withContext(NonCancellable) {
                previous?.let { restore ->
                    local.clear(evictDownloads = false)
                    cookies.replace(restore.cookies)
                    session.sync()
                    local.restore(restore.local)
                }
                vault = original
                store.write(vault)
            }
            if (failure is CancellationException) throw failure
        } finally {
            withContext(NonCancellable) { requests.resume() }
            detached = null
            publish(revision = state.value.revision + 1, failure = failureResult)
        }
    }

    fun add() = launchAction {
        // Preserve a readable login after a failed save instead of clearing it for another form.
        if (vault.pending != null && session.peek().isSignedIn) {
            rememberLocked(currentProfile(false), session.peek().fingerprint)
        } else {
            transition(targetUid = null, adding = true)
        }
    }
    fun switch(uid: Long) = launchAction {
        if (uid != vault.activeUid) transition(targetUid = uid, adding = false)
    }
    fun cancelAdd() = launchAction {
        val previous = vault.pending ?: return@launchAction
        transition(targetUid = previous.previousUid, adding = false, cancel = previous)
    }
    fun cancelIfAbandoned() {
        if (state.value.adding && !session.peek().isSignedIn) cancelAdd()
    }
    fun remove(uid: Long) = launchAction {
        if (uid == vault.activeUid) {
            logout()
        } else {
            val account = vault.accounts.find { it.account.uid == uid } ?: return@launchAction
            val next = vault.copy(
                accounts = vault.accounts.filterNot { it.account.uid == uid },
                quarantined = vault.quarantined.filterNot { it.previousUid == uid },
                pending = vault.pending?.let { previous ->
                    if (previous.previousUid == uid) PendingAccount(null, "", AccountLocalSnapshot()) else previous
                },
            )
            store.write(next)
            vault = next
            local.forgetMessages(account.fingerprint, uid)
            publish()
        }
    }

    /** Runs in the repository's lifetime, since signing out disposes the calling ViewModel. */
    suspend fun signOut() {
        val operation = scope.async {
            awaitReady()
            check(vaultLoaded) { "Saved accounts are unavailable" }
            mutex.lock()
            try {
                logout()
            } finally {
                mutex.unlock()
            }
        }
        operation.await()
    }

    private suspend fun logout() {
        val disposed = CompletableDeferred<Unit>()
        detached = disposed
        val fingerprint = session.state.value.fingerprint
        val uid = vault.activeUid
        session.beginAccountChange()
        mutableState.value = state.value.copy(busy = true, failure = null)
        var failureResult: AccountFailure? = null
        try {
            if (attached.isNotEmpty()) disposed.await()
            requests.pause()
            stopPlatformRequests()
            val next = vault.copy(accounts = vault.accounts.filterNot { it.account.uid == uid }, activeUid = null, pending = null, recovery = null, quarantined = vault.quarantined.filterNot { uid != null && it.previousUid == uid }, navigationKey = Uuid.random().toString())
            store.write(next)
            vault = next
            local.clear()
            local.forgetMessages(fingerprint, uid)
            cookies.replace("")
            session.sync()
        } catch (failure: Exception) {
            failureResult = classify(failure)
            throw failure
        } finally {
            withContext(NonCancellable) { requests.resume() }
            detached = null
            publish(revision = state.value.revision + 1, failure = failureResult)
        }
    }

    private fun launchAction(block: suspend () -> Unit) {
        scope.launch {
            awaitReady()
            if (!mutex.tryLock()) return@launch
            try {
                check(vaultLoaded) { "Saved accounts are unavailable" }
                block()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                var result = classify(failure)
                if (classify(failure) == AccountFailure.EXPIRED && vault.activeUid != null) {
                    val next = vault.copy(
                        accounts = vault.accounts.map { entry ->
                            if (entry.account.uid == vault.activeUid) entry.copy(account = entry.account.copy(needsSignIn = true)) else entry
                        },
                    )
                    try {
                        store.write(next)
                        vault = next
                    } catch (storage: Exception) {
                        if (storage is CancellationException) throw storage
                        result = AccountFailure.STORAGE
                    }
                }
                publish(failure = result)
            } finally {
                mutex.unlock()
            }
        }
    }

    private suspend fun transition(targetUid: Long?, adding: Boolean, cancel: PendingAccount? = null) {
        // Identify and durably save an existing login before offering a blank sign-in screen.
        if (session.peek().isSignedIn && vault.activeUid == null && vault.pending == null) {
            rememberLocked(currentProfile(false), session.state.value.fingerprint)
        }
        val target = targetUid?.let { uid -> checkNotNull(vault.accounts.find { it.account.uid == uid }) }
        val originalVault = vault
        var previous: PendingAccount? = null
        var changed = false
        var failureResult: AccountFailure? = null
        val disposed = CompletableDeferred<Unit>()
        detached = disposed
        session.beginAccountChange()
        mutableState.value = mutableState.value.copy(busy = true, failure = null)
        try {
            if (attached.isNotEmpty()) disposed.await()
            requests.pause()
            stopPlatformRequests()
            previous = PendingAccount(vault.activeUid, cookies.capture(), local.capture())
            val currentUid = vault.activeUid
            val parked = vault.accounts.map { entry ->
                if (entry.account.uid == currentUid) {
                    entry.copy(
                        cookies = previous.cookies,
                        fingerprint = session.state.value.fingerprint,
                        local = previous.local,
                    )
                } else {
                    entry
                }
            }
            // A restart between clearing the active files and committing the new identity restores
            // this journal before either shell renders a page.
            vault = vault.copy(accounts = parked, recovery = previous)
            store.write(vault)
            local.clear(evictDownloads = false)
            changed = true
            cookies.replace(cancel?.cookies ?: target?.cookies.orEmpty())
            session.sync()
            // An untouched form must not adopt the old account as a completed additional login.
            // The platform replacement must have removed its session before any page can load.
            if (adding) check(!session.state.value.isSignedIn) { "Could not clear the previous login" }
            local.restore(cancel?.local ?: target?.local ?: AccountLocalSnapshot())
            var verified: StoredAccount? = null
            if (target != null && cancel == null) {
                val profile = requests.verify { currentProfile(true) }
                if (profile.uid != target.account.uid) throw SiteException(SiteError.LoginRequired)
                session.sync()
                verified = target.copy(account = target.account.copy(name = profile.name, avatarUrl = profile.avatarUrl, needsSignIn = false), cookies = cookies.capture(), fingerprint = session.state.value.fingerprint)
            }
            val next = vault.copy(
                accounts = vault.accounts.map { if (it.account.uid == verified?.account?.uid) checkNotNull(verified) else it },
                activeUid = targetUid,
                pending = if (adding) previous else null,
                recovery = null,
                navigationKey = Uuid.random().toString(),
            )
            local.evictDownloads()
            store.write(next)
            vault = next
        } catch (failure: Exception) {
            failureResult = classify(failure)
            withContext(NonCancellable) {
                val restore = previous
                if (changed && restore != null) {
                    local.clear(evictDownloads = false)
                    cookies.replace(restore.cookies)
                    session.sync()
                    local.restore(restore.local)
                }
                vault = originalVault.copy(
                    accounts = originalVault.accounts.map { entry ->
                        if (failureResult == AccountFailure.EXPIRED && entry.account.uid == targetUid) {
                            entry.copy(account = entry.account.copy(needsSignIn = true))
                        } else {
                            entry
                        }
                    },
                )
                store.write(vault)
            }
            if (failure is CancellationException) throw failure
        } finally {
            withContext(NonCancellable) { requests.resume() }
            detached = null
            publish(revision = mutableState.value.revision + 1, failure = failureResult)
        }
    }

    private fun publish(revision: Long = mutableState.value.revision, failure: AccountFailure? = null) {
        mutableState.value = AccountsState(
            ready = true,
            accounts = vault.accounts.map { it.account },
            activeUid = vault.activeUid,
            adding = vault.pending != null,
            revision = revision,
            failure = failure,
            navigationKey = vault.navigationKey,
        )
    }

    private fun classify(failure: Exception): AccountFailure = when ((failure as? SiteException)?.error) {
        SiteError.LoginRequired, SiteError.SessionUnrecognised -> AccountFailure.EXPIRED

        SiteError.Network -> AccountFailure.NETWORK

        is SiteError.Cloudflare -> AccountFailure.VERIFY

        else -> when (failure) {
            is UnsupportedOperationException -> AccountFailure.UNSUPPORTED
            is IllegalStateException -> AccountFailure.STORAGE
            else -> AccountFailure.UNKNOWN
        }
    }
}
