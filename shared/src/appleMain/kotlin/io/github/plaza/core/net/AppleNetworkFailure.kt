package io.github.plaza.core.net

import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSUnderlyingErrorKey

/**
 * NSURLSession's localizedDescription loses the error code and its underlying TLS error. Keep
 * those numeric facts separately so a login failure can be diagnosed without exporting userInfo,
 * which may contain the failing URL, certificate objects and other request context.
 */
internal fun NSError.asNetworkFailure(): SiteException {
    val errors = mutableListOf<NativeNetworkError>()
    var current: NSError? = this
    repeat(TransportFailureDiagnostics.MAX_ERRORS) {
        val error = current ?: return@repeat
        errors += NativeNetworkError(
            domain = NativeNetworkErrorDomain.fromNative(error.domain),
            code = error.code,
            streamDomain = (error.userInfo["_kCFStreamErrorDomainKey"] as? NSNumber)?.longLongValue,
            streamCode = (error.userInfo["_kCFStreamErrorCodeKey"] as? NSNumber)?.longLongValue,
            originalTlsCode = (error.userInfo["_kCFNetworkCFStreamSSLErrorOriginalValue"] as? NSNumber)?.longLongValue,
        )
        current = error.userInfo[NSUnderlyingErrorKey] as? NSError
    }
    return SiteException(
        error = SiteError.Network,
        detail = localizedDescription,
        diagnostics = TransportFailureDiagnostics(
            platformVersion = NSProcessInfo.processInfo.operatingSystemVersionString,
            errors = errors,
        ),
    )
}
