package io.github.nodyssey.data.attendance

import io.github.nodyssey.data.AssetsRepository
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.session.SessionRepository
import io.github.plaza.core.AppClock
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okio.IOException
import kotlin.time.Instant

/** One runner for foreground catch-up and both platforms' background jobs. */
class AutoAttendance(
    private val store: AutoAttendanceStore,
    private val session: SessionRepository,
    private val profiles: ProfileRepository,
    private val assets: AssetsRepository,
    private val clock: AppClock,
    private val scope: CoroutineScope,
    override val config: Flow<AutoAttendanceConfig> = store.config,
    private val expectedUid: Long? = null,
) : AutomaticAttendance {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(AutoAttendanceState())
    override val state = mutableState.asStateFlow()
    override val enabled = config.map { it.enabled }
    override suspend fun setEnabled(enabled: Boolean) = store.setEnabled(enabled)
    override suspend fun setMode(mode: io.github.nodyssey.data.AttendanceMode) = store.setMode(mode)

    // Native iOS screens have separate compositions. Navigating between them must not cancel
    // an in-flight daily request; this job belongs to the graph, which is retired on site switches.
    override fun request() {
        scope.launch { run() }
    }

    override suspend fun run(): AutoAttendanceOutcome = try {
        runLocked()
    } catch (failure: IOException) {
        // An unreadable receipt store is no permission to submit untracked daily writes.
        mutableState.value = AutoAttendanceState(failure = AutoAttendanceFailure.OTHER)
        AutoAttendanceOutcome.BLOCKED
    }

    private suspend fun runLocked(): AutoAttendanceOutcome = mutex.withLock {
        val config = this.config.first()
        if (!config.enabled) {
            mutableState.value = AutoAttendanceState()
            return@withLock AutoAttendanceOutcome.SKIPPED
        }
        val snapshot = session.peek()
        if (!snapshot.isSignedIn) {
            mutableState.value = AutoAttendanceState(failure = AutoAttendanceFailure.SIGN_IN)
            return@withLock AutoAttendanceOutcome.BLOCKED
        }
        val now = clock.nowMillis()
        val today = Instant.fromEpochMilliseconds(now).toLocalDateTime(SITE_ZONE).date.toString()
        val records = store.records()
        val previous = records.lastOrNull { it.fingerprint == snapshot.fingerprint || (expectedUid != null && it.uid == expectedUid) }
        if (previous?.completed == true && previous.date == today) {
            mutableState.value = AutoAttendanceState(completed = true, gain = previous.gain)
            return@withLock AutoAttendanceOutcome.COMPLETED
        }
        // Browser intervention must change the session before another request, even on a new day.
        if (previous?.fingerprint == snapshot.fingerprint &&
            (previous.failure == AutoAttendanceFailure.VERIFICATION || previous.failure == AutoAttendanceFailure.SIGN_IN)
        ) {
            mutableState.value = AutoAttendanceState(failure = previous.failure)
            return@withLock AutoAttendanceOutcome.BLOCKED
        }
        val attempts = previous?.takeIf { it.date == today }?.attempts ?: 0
        if (attempts >= MAX_ATTEMPTS) {
            mutableState.value = AutoAttendanceState(failure = previous?.failure, exhausted = true)
            return@withLock AutoAttendanceOutcome.BLOCKED
        }
        if (attempts > 0 && previous != null && now - previous.attemptedAtMillis in 0 until RETRY_DELAY_MILLIS) {
            mutableState.value = AutoAttendanceState(failure = previous.failure)
            return@withLock AutoAttendanceOutcome.RETRY
        }
        // Persist before any network request: expiration, process death and profile failures also
        // spend an attempt, so relaunching the app cannot create an unbounded retry loop.
        var record = AutoAttendanceRecord(snapshot.fingerprint, today, attempts + 1, now, uid = expectedUid)
        store.save(record)
        mutableState.value = AutoAttendanceState(running = true)
        try {
            val uid = profiles.profile().uid
            if (expectedUid != null && uid != expectedUid) throw SiteException(SiteError.LoginRequired)
            if (!sameSession(snapshot.fingerprint) || !this.config.first().enabled) {
                mutableState.value = AutoAttendanceState()
                return@withLock AutoAttendanceOutcome.SKIPPED
            }
            record = record.copy(uid = uid)
            val completed = records.lastOrNull { it.uid == uid && it.date == today && it.completed }
            val gain = if (completed != null) {
                completed.gain
            } else {
                val status = assets.refreshAttendanceStatus(uid)
                if (status.hasSignedIn) {
                    status.gain
                } else {
                    if (!sameSession(snapshot.fingerprint) || !this.config.first().enabled) {
                        mutableState.value = AutoAttendanceState()
                        return@withLock AutoAttendanceOutcome.SKIPPED
                    }
                    assets.signInForToday(this.config.first().mode).gain
                }
            }
            if (!sameSession(snapshot.fingerprint)) {
                mutableState.value = AutoAttendanceState()
                return@withLock AutoAttendanceOutcome.SKIPPED
            }
            store.save(record.copy(completed = true, gain = gain))
            mutableState.value = AutoAttendanceState(completed = true, gain = gain)
            if (completed == null) {
                runCatchingExceptCancellation { profiles.refreshProfile(snapshot.fingerprint) }
            }
            AutoAttendanceOutcome.COMPLETED
        } catch (cancelled: CancellationException) {
            mutableState.value = AutoAttendanceState()
            throw cancelled
        } catch (failure: Exception) {
            val reason = when ((failure as? SiteException)?.error) {
                is SiteError.Cloudflare -> AutoAttendanceFailure.VERIFICATION
                SiteError.LoginRequired, SiteError.SessionUnrecognised -> AutoAttendanceFailure.SIGN_IN
                SiteError.Network -> AutoAttendanceFailure.NETWORK
                else -> AutoAttendanceFailure.OTHER
            }
            store.save(record.copy(failure = reason))
            mutableState.value = AutoAttendanceState(failure = reason, exhausted = record.attempts >= MAX_ATTEMPTS)
            if (reason == AutoAttendanceFailure.NETWORK && record.attempts < MAX_ATTEMPTS) {
                AutoAttendanceOutcome.RETRY
            } else {
                AutoAttendanceOutcome.BLOCKED
            }
        }
    }

    private fun sameSession(fingerprint: Int): Boolean =
        session.peek().let { it.isSignedIn && it.fingerprint == fingerprint }

    companion object {
        const val RETRY_DELAY_MILLIS = 30 * 60 * 1000L
        private const val MAX_ATTEMPTS = 3
        private val SITE_ZONE = TimeZone.of("Asia/Shanghai")
    }
}

enum class AutoAttendanceOutcome { SKIPPED, COMPLETED, RETRY, BLOCKED }

data class AutoAttendanceState(
    val running: Boolean = false,
    val completed: Boolean = false,
    val gain: Int? = null,
    val failure: AutoAttendanceFailure? = null,
    val exhausted: Boolean = false,
)
