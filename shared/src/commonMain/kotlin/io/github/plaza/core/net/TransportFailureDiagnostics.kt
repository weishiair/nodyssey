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

enum class NativeTransportEngine(val label: String) {
    URL_SESSION("Apple URLSession"),
    RUST_ECH("Nodyssey rustls DoH/ECH"),
}

/** Only allowlisted native facts cross the diagnostic export boundary. */
class EncryptedConnectionDiagnostics private constructor(val stage: String, val reason: String, val ech: String) {
    companion object {
        fun fromNative(stage: String, reason: String, ech: String) = EncryptedConnectionDiagnostics(
            stage.takeIf { it in setOf("CONFIG", "DNS", "TCP", "TLS", "ECH_CONFIG", "HTTP", "HTTP_BODY", "TIMEOUT", "COMPLETE") } ?: "UNKNOWN",
            reason.takeIf { it in setOf("resolver_connection", "resolver_http_status", "resolver_body", "resolver_body_too_large", "no_address", "alias_limit", "timeout", "connection_failed", "connection_task", "invalid_server_name", "certificate_rejected", "ech_rejected", "handshake_failed", "unsupported_or_invalid", "configuration", "body_read_or_size_limit", "gzip_decode", "body_too_large", "request_timeout", "invalid_resolvers", "invalid_resolver_url", "invalid_bootstrap", "resolver_client", "https_required", "invalid_url", "invalid_body", "request_too_large", "invalid_headers", "native_capacity_or_input") } ?: "other",
            ech.takeIf { it in setOf("Accepted", "Rejected", "NotOffered", "Grease", "NotEstablished", "Unknown") } ?: "Unknown",
        )
    }
}

data class TransportFailureDiagnostics(
    val platformVersion: String,
    val errors: List<NativeNetworkError>,
    val engine: NativeTransportEngine = NativeTransportEngine.URL_SESSION,
    val connection: EncryptedConnectionDiagnostics? = null,
) {
    val category: TransportFailureCategory
        get() {
            connection?.let {
                return when {
                    it.reason == "certificate_rejected" -> TransportFailureCategory.CERTIFICATE
                    it.reason == "timeout" || it.stage == "TIMEOUT" -> TransportFailureCategory.TIMEOUT
                    it.stage == "DNS" -> TransportFailureCategory.DNS
                    it.stage == "TLS" || it.stage == "ECH_CONFIG" -> TransportFailureCategory.TLS
                    it.stage == "TCP" -> TransportFailureCategory.CONNECTION
                    else -> TransportFailureCategory.OTHER
                }
            }
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
        appendLine("Transport: ${engine.label}")
        appendLine("OS error category: $category")
        errors.take(MAX_ERRORS).forEachIndexed { index, error ->
            appendLine("Error[$index]: ${error.domain.label} (${error.code})")
            error.streamDomain?.let { appendLine("  CFStream domain: $it") }
            error.streamCode?.let { appendLine("  CFStream code: $it") }
            error.originalTlsCode?.let { appendLine("  Original TLS code: $it") }
        }
        // Enabling DoH or receiving an HTTPS record is not proof that ECH was negotiated.
        if (connection == null) {
            append("ECH negotiation: not measured")
        } else {
            appendLine("Connection stage: ${connection.stage}")
            appendLine("Connection result: ${connection.reason}")
            append("ECH negotiation: ${connection.ech}")
        }
    }

    companion object {
        const val MAX_ERRORS = 6
    }
}
