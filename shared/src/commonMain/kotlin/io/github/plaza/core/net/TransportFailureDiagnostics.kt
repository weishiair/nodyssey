package io.github.plaza.core.net

/**
 * A deliberately small export boundary for native failures. Never carries an NSError description,
 * userInfo dictionary, request URL, headers, body or certificate. Even an unfamiliar error domain
 * is replaced: a third-party domain can be an arbitrary string rather than a platform constant.
 */
data class NativeNetworkError(
    val domain: NativeNetworkErrorDomain,
    val code: Long,
    val streamDomain: Long? = null,
    val streamCode: Long? = null,
    val originalTlsCode: Long? = null,
)

enum class NativeNetworkErrorDomain(val label: String) {
    URL("NSURLErrorDomain"),
    CFNETWORK("kCFErrorDomainCFNetwork"),
    POSIX("NSPOSIXErrorDomain"),
    OSSTATUS("NSOSStatusErrorDomain"),
    STREAM_SSL("kCFStreamErrorDomainSSL"),
    OTHER("Other"),
    ;

    companion object {
        fun fromNative(domain: String): NativeNetworkErrorDomain = entries.firstOrNull { it.label == domain } ?: OTHER
    }
}

/** This is the category reported by the OS, not a claim about the remote cause or furthest stage. */
enum class TransportFailureCategory { DNS, TIMEOUT, CONNECTION, TLS, CERTIFICATE, ATS, OTHER }

data class TransportFailureDiagnostics(
    val platformVersion: String,
    val errors: List<NativeNetworkError>,
) {
    val category: TransportFailureCategory
        get() {
            // A wrapper such as NSCocoaErrorDomain may sit above the actionable URL-loading error.
            val code = errors.firstOrNull {
                it.domain == NativeNetworkErrorDomain.URL || it.domain == NativeNetworkErrorDomain.CFNETWORK
            }?.code
            return when (code) {
                -1003L, -1006L -> TransportFailureCategory.DNS
                -1001L -> TransportFailureCategory.TIMEOUT
                -1004L, -1005L, -1009L -> TransportFailureCategory.CONNECTION
                -1200L -> TransportFailureCategory.TLS
                in -1206L..-1201L -> TransportFailureCategory.CERTIFICATE
                -1022L -> TransportFailureCategory.ATS
                else -> TransportFailureCategory.OTHER
            }
        }

    fun asText(): String = buildString {
        appendLine("OS: $platformVersion")
        appendLine("Transport: Apple URLSession")
        appendLine("OS error category: $category")
        errors.take(MAX_ERRORS).forEachIndexed { index, error ->
            appendLine("Error[$index]: ${error.domain.label} (${error.code})")
            error.streamDomain?.let { appendLine("  CFStream domain: $it") }
            error.streamCode?.let { appendLine("  CFStream code: $it") }
            error.originalTlsCode?.let { appendLine("  Original TLS code: $it") }
        }
        // Enabling DoH or receiving an HTTPS record is not proof that ECH was negotiated.
        append("ECH negotiation: not measured")
    }

    companion object {
        const val MAX_ERRORS = 6
    }
}
