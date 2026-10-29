package io.github.nodyssey.data.session

import io.github.nodyssey.data.dns.DohConfig
import io.github.nodyssey.data.dns.resolvesOverHttps
import io.github.nodyssey.data.proxy.ProxyConfig
import io.github.plaza.core.AppVersion
import io.github.plaza.core.net.TransportFailureDiagnostics

/** Only the saved routing choices; a custom resolver/proxy address or credential never leaves here. */
data class SignInDiagnosticContext(
    val appVersion: AppVersion? = null,
    val doh: DohConfig? = null,
    val proxy: ProxyConfig? = null,
)

/**
 * A report of the failed attempt, not a second login or a network probe. It takes no account,
 * password, captcha token, OTP/session, cookie, exception message or arbitrary response text.
 */
fun signInDiagnosticReport(
    failure: TransportFailureDiagnostics,
    context: SignInDiagnosticContext,
    twoFactor: Boolean,
    verificationPassed: Boolean,
    timeMillis: Long,
    emailSignIn: Boolean = false,
): String = buildString {
    appendLine("Nodyssey login diagnostics")
    appendLine("App: ${context.appVersion?.let { "${it.name} (${it.code})" } ?: "unknown"}")
    appendLine("Time (Unix ms): $timeMillis")
    appendLine(
        "Step: ${if (emailSignIn) {
            "email"
        } else if (twoFactor) {
            "two-factor"
        } else {
            "credentials"
        }}",
    )
    appendLine("Verification ready: $verificationPassed")
    val doh = context.doh
    appendLine(
        "DoH setting: " + when {
            doh == null -> "unavailable"
            !doh.resolvesOverHttps() -> "off"
            else -> "on; selected=" + doh.chain.joinToString(",") { it.preset?.name ?: "CUSTOM" }
        },
    )
    val proxy = context.proxy
    appendLine(
        "App proxy setting: " + when {
            proxy == null -> "unavailable"
            !proxy.enabled || !proxy.isUsable -> "off"
            else -> "${proxy.type}; scope=${proxy.scope}"
        },
    )
    appendLine("System VPN/proxy and actual resolver selection: not inspected")
    append(failure.asText())
}
