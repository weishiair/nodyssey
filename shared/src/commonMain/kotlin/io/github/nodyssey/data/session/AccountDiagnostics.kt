package io.github.nodyssey.data.session

import io.github.plaza.core.AppClock
import io.github.plaza.core.AppVersion
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class AccountStep { INITIALIZE, READ_VAULT, FETCH_PROFILE, CAPTURE_COOKIES, CAPTURE_LOCAL, CLEAR_LOCAL, RESTORE_LOCAL, EVICT_DOWNLOADS, REPLACE_COOKIES, SYNC_SESSION, STOP_REQUESTS, WRITE_VAULT, ADD, SAVE_LOGIN, ACTION }

/** Native errors cross the export boundary as a fixed domain and number, never userInfo. */
class AccountNativeFailure(domainName: String, val code: Long?) :
    IllegalStateException(
        "Account storage native operation failed",
    ) {
    val safeDomain: String = domainName.takeIf { it in setOf("NSCocoaErrorDomain", "NSPOSIXErrorDomain", "NSOSStatusErrorDomain") } ?: "Other"
}

/** A bounded log for this launch. No credentials, cookie values, UIDs, paths or exception messages. */
class AccountDiagnostics(
    private val clock: AppClock = AppClock.System,
    private val appVersion: AppVersion? = null,
    private val platform: String = "unspecified",
) {
    private val events = MutableStateFlow(emptyList<String>())
    private val mutableReport = MutableStateFlow(format(emptyList()))
    val report = mutableReport.asStateFlow()

    suspend fun <T> step(step: AccountStep, block: suspend () -> T): T {
        record(step, "begin")
        try {
            return block().also { record(step, "ok") }
        } catch (failure: Exception) {
            if (failure is CancellationException) {
                record(step, "cancelled")
            } else {
                val types = generateSequence<Throwable>(failure) { it.cause }.take(6).map {
                    it::class.simpleName ?: "Throwable"
                }.joinToString(" -> ")
                val native = generateSequence<Throwable>(failure) { it.cause }.take(6).filterIsInstance<AccountNativeFailure>().firstOrNull()
                record(step, "failed; exception=$types" + (native?.let { "; native=${it.safeDomain}/${it.code}" } ?: ""))
                (failure as? SiteException)?.diagnostics?.let { append(it.asText()) }
            }
            throw failure
        }
    }

    fun state(signedIn: Boolean, adding: Boolean, savedCount: Int, storageAvailable: Boolean) {
        append("${clock.nowMillis()} state signedIn=$signedIn adding=$adding saved=$savedCount storageAvailable=$storageAvailable")
    }

    private fun record(step: AccountStep, result: String) = append("${clock.nowMillis()} $step $result")

    private fun append(event: String) {
        events.update { (it + event).takeLast(128) }
        mutableReport.value = format(events.value)
    }

    private fun format(lines: List<String>): String = buildString {
        appendLine("Nodyssey account diagnostics v1")
        appendLine("App: ${appVersion?.let { "${it.name} (${it.code})" } ?: "unknown"}")
        appendLine("Platform: $platform")
        appendLine("Scope: current launch; timestamps are Unix milliseconds")
        appendLine("No passwords, cookies, account identifiers, response bodies or exception messages included.")
        lines.forEach(::appendLine)
    }
}
