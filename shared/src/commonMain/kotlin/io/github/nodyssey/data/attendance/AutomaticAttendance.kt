package io.github.nodyssey.data.attendance

import io.github.nodyssey.data.AttendanceMode
import io.github.plaza.core.net.HttpTransport
import io.github.plaza.core.net.SessionCookies
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface AutomaticAttendance {
    val config: Flow<AutoAttendanceConfig>

    /** True while at least one saved account is enabled, independent of the browsing account. */
    val enabled: Flow<Boolean>
    val state: StateFlow<AutoAttendanceState>
    val accountStates: StateFlow<Map<Long, AutoAttendanceState>> get() = MutableStateFlow(emptyMap())
    fun request()
    suspend fun run(): AutoAttendanceOutcome
    suspend fun setEnabled(enabled: Boolean)
    suspend fun setMode(mode: AttendanceMode)
}

/** A platform-owned private cookie jar; it never mirrors into the sign-in browser. */
class AttendanceSession(
    val transport: HttpTransport,
    val cookies: SessionCookies,
    val snapshot: suspend () -> String,
    val close: () -> Unit,
)
