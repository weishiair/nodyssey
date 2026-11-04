package io.github.nodyssey.data.attendance

import io.github.nodyssey.core.net.NodeSeekJsonClient
import io.github.nodyssey.data.AttendanceMode
import io.github.nodyssey.data.NetworkAssetsRepository
import io.github.nodyssey.data.NetworkCreditRepository
import io.github.nodyssey.data.NetworkProfileRepository
import io.github.nodyssey.data.session.AccountRepository
import io.github.nodyssey.data.session.SessionRepository
import io.github.plaza.core.AppClock
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.SiteConfig
import io.github.plaza.core.net.SiteHtmlClient
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serial daily work across saved accounts. All network repositories and cookies are private. */
class MultiAccountAttendance(
    private val accounts: AccountRepository,
    private val store: AutoAttendanceStore,
    private val legacy: AutoAttendance,
    private val site: SiteConfig,
    private val dispatchers: AppDispatchers,
    private val clock: AppClock,
    private val scope: CoroutineScope,
    private val writes: AttendanceWriteGate = AttendanceWriteGate(),
    private val openSession: suspend (String) -> AttendanceSession,
) : AutomaticAttendance {
    private val mutex = Mutex()
    private val results = MutableStateFlow<Map<Long, AutoAttendanceState>>(emptyMap())
    override val accountStates = results.asStateFlow()
    override val config = combine(accounts.state, store.config) { saved, original ->
        saved.accounts.find { it.uid == saved.activeUid }?.attendance
            ?: if (saved.accounts.isEmpty() && !saved.adding && saved.navigationKey == "legacy") original else AutoAttendanceConfig()
    }
    override val enabled = combine(accounts.state, store.config) { saved, original ->
        saved.accounts.any { it.attendance.enabled } || (saved.accounts.isEmpty() && !saved.adding && saved.navigationKey == "legacy" && original.enabled)
    }
    override val state = combine(accounts.state, results, legacy.state) { saved, values, original ->
        if (saved.accounts.isEmpty()) original else values[saved.activeUid] ?: AutoAttendanceState()
    }.stateIn(scope, SharingStarted.Eagerly, AutoAttendanceState())

    override fun request() {
        scope.launch { runCatchingExceptCancellation { run() } }
    }

    override suspend fun setEnabled(enabled: Boolean) {
        accounts.awaitReady()
        val uid = accounts.state.value.activeUid
        if (uid == null) store.setEnabled(enabled) else accounts.configureAttendance(uid) { it.copy(enabled = enabled) }
    }

    override suspend fun setMode(mode: AttendanceMode) {
        accounts.awaitReady()
        val uid = accounts.state.value.activeUid
        if (uid == null) store.setMode(mode) else accounts.configureAttendance(uid) { it.copy(mode = mode) }
    }

    override suspend fun run(): AutoAttendanceOutcome = mutex.withLock {
        accounts.runWork {
            if (!accounts.storageAvailable) return@runWork AutoAttendanceOutcome.BLOCKED
            val saved = accounts.state.value
            if (saved.adding) return@runWork AutoAttendanceOutcome.SKIPPED
            if (saved.accounts.isEmpty()) return@runWork if (saved.navigationKey == "legacy") legacy.run() else AutoAttendanceOutcome.SKIPPED
            var outcome = AutoAttendanceOutcome.SKIPPED
            for (account in saved.accounts.filter { it.attendance.enabled }) {
                if (account.needsSignIn) {
                    results.value += account.uid to AutoAttendanceState(failure = AutoAttendanceFailure.SIGN_IN)
                    if (outcome == AutoAttendanceOutcome.SKIPPED) outcome = AutoAttendanceOutcome.BLOCKED
                    continue
                }
                val attempt = runCatchingExceptCancellation {
                    val lease = accounts.attendanceAccount(account.uid) ?: return@runCatchingExceptCancellation AutoAttendanceOutcome.SKIPPED
                    val isolated = openSession(lease.cookies)
                    try {
                        val session = SessionRepository(isolated.cookies)
                        val json = NodeSeekJsonClient(isolated.transport, dispatchers)
                        val profiles = NetworkProfileRepository(
                            SiteHtmlClient(isolated.transport, dispatchers, site),
                            json,
                            null,
                            { session.peek().fingerprint },
                            { session.peek().isSignedIn },
                            clock,
                        )
                        val runner = AutoAttendance(
                            store,
                            session,
                            profiles,
                            NetworkAssetsRepository(profiles, NetworkCreditRepository(json, dispatchers), json, dispatchers, clock),
                            clock,
                            scope,
                            config = accounts.state.map { current ->
                                current.accounts.find { it.uid == lease.uid && !it.needsSignIn }?.attendance ?: AutoAttendanceConfig()
                            },
                            expectedUid = lease.uid,
                        )
                        results.value += account.uid to AutoAttendanceState(running = true)
                        val result = try {
                            writes.run(account.uid) { runner.run() }
                        } finally {
                            results.value += account.uid to runner.state.value
                        }
                        accounts.saveAttendanceCookies(lease, isolated.snapshot(), session.peek().fingerprint, runner.state.value.failure == AutoAttendanceFailure.SIGN_IN)
                        result
                    } finally {
                        isolated.close()
                    }
                }
                val result = attempt.getOrElse {
                    results.value += account.uid to AutoAttendanceState(failure = AutoAttendanceFailure.OTHER)
                    AutoAttendanceOutcome.BLOCKED
                }
                // A retriable account must not be hidden by another account's success or expiry.
                outcome = when {
                    result == AutoAttendanceOutcome.RETRY || outcome == AutoAttendanceOutcome.RETRY -> AutoAttendanceOutcome.RETRY
                    result == AutoAttendanceOutcome.COMPLETED || outcome == AutoAttendanceOutcome.COMPLETED -> AutoAttendanceOutcome.COMPLETED
                    else -> result
                }
            }
            outcome
        }
    }
}
