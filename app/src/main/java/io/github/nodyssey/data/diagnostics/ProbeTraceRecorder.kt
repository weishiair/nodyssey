package io.github.nodyssey.data.diagnostics

import android.net.ssl.EchConfigList
import android.net.ssl.InvalidEchDataException
import android.net.ssl.SSLSockets
import android.os.Build
import android.security.NetworkSecurityPolicy
import okhttp3.Dns
import okhttp3.OkHttpClient
import okio.ByteString
import org.conscrypt.Conscrypt
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import javax.net.ssl.SSLSocket

/** Observes the probe's real DNS calls without replacing them with address-only lookup(). */
internal class ProbeTraceRecorder(
    private val hostname: String,
    private val tlsImplementation: String,
    private val nativeEchSupported: Boolean,
    private val echPolicy: String,
    private val validateEch: (ByteString) -> Boolean?,
) {
    private var stage = ProbeStage.START
    private val answers = linkedMapOf<String, ProbeDnsAnswer>()
    private val events = ArrayDeque<String>()

    fun observe(delegate: Dns): Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> = delegate.lookup(hostname)

        override fun newCall(request: Dns.Request): Dns.Call {
            val original = delegate.newCall(request)
            // Proxy names can belong to the reader's own infrastructure. Only report the public
            // hostname this probe was asked to contact, not every DNS request made along the way.
            if (request.hostname != hostname) return original
            return object : Dns.Call by original {
                override fun enqueue(callback: Dns.Callback) {
                    val observed = this
                    val allRecords = mutableListOf<Dns.Record>()
                    original.enqueue(object : Dns.Callback {
                        override fun onRecords(call: Dns.Call, last: Boolean, records: List<Dns.Record>) {
                            allRecords += records
                            recordAnswers(request.hostname, allRecords)
                            callback.onRecords(observed, last, records)
                        }

                        override fun onFailure(call: Dns.Call, e: IOException) {
                            event("DNS_FAIL ${safeFailureCodes(e).joinToString(" / ")}")
                            callback.onFailure(observed, e)
                        }
                    })
                }
            }
        }
    }

    @Synchronized
    private fun recordAnswers(hostname: String, records: List<Dns.Record>) {
        val metadata = records.filterIsInstance<Dns.Record.ServiceMetadata>()
        val ech = metadata.lastOrNull { it.hostname == hostname }?.echConfigList
        answers[hostname] = ProbeDnsAnswer(
            hostname = hostname,
            addresses = records.filterIsInstance<Dns.Record.IpAddress>()
                .filter { it.hostname == hostname }.mapNotNull { it.address.hostAddress }.distinct().take(MAX_ADDRESSES),
            httpsRecords = metadata.size,
            echConfigBytes = ech?.size ?: 0,
            echConfigValid = ech?.let(validateEch),
        )
    }

    @Synchronized
    fun reached(boundary: ProbeStage) {
        if (boundary.ordinal > stage.ordinal) stage = boundary
    }

    fun connectStart(address: InetSocketAddress, proxy: Proxy) {
        reached(ProbeStage.CONNECT)
        event("TCP_START ${address.publicDescription(proxy)}")
    }

    fun secureConnectStart() {
        reached(ProbeStage.TLS)
        event("TLS_START")
    }

    fun secureConnectEnd() {
        event("TLS_READY")
    }

    fun connectFailed(address: InetSocketAddress, proxy: Proxy, failure: IOException) {
        event("CONNECT_FAIL ${address.publicDescription(proxy)} ${safeFailureCodes(failure).joinToString(" / ")}")
    }

    fun connectionAcquired(echConfigBytes: Int) {
        event("CONNECTED route_ech_bytes=$echConfigBytes")
    }

    fun echControl(result: String) {
        event("ECH_CONTROL cloudflare-ech.com $result")
    }

    @Synchronized
    private fun event(value: String) {
        if (events.size == MAX_EVENTS) events.removeFirst()
        events.addLast(value)
    }

    @Synchronized
    fun snapshot(failure: Throwable? = null): ProbeTrace = ProbeTrace(
        stage = stage,
        dnsAnswers = answers.values.toList(),
        connectionEvents = events.toList(),
        tlsImplementation = tlsImplementation,
        nativeEchSupported = nativeEchSupported,
        echPolicy = echPolicy,
        failureCodes = failure?.let(::safeFailureCodes).orEmpty(),
    )

    private fun InetSocketAddress.publicDescription(proxy: Proxy): String =
        if (proxy.type() == Proxy.Type.DIRECT) {
            "${address?.hostAddress ?: "unresolved"}:$port"
        } else {
            "${proxy.type()} proxy"
        }

    companion object {
        private const val MAX_EVENTS = 24
        private const val MAX_ADDRESSES = 16

        fun forClient(hostname: String, client: OkHttpClient): ProbeTraceRecorder {
            val socket = client.sslSocketFactory.createSocket()
            val bundled = socket is SSLSocket && Conscrypt.isConscrypt(socket)
            val policy = if (bundled) {
                "app-bundled enabled"
            } else if (Build.VERSION.SDK_INT >= 37) {
                when (NetworkSecurityPolicy.getInstance().getDomainEncryptionMode(hostname)) {
                    NetworkSecurityPolicy.DOMAIN_ENCRYPTION_MODE_ENABLED -> "enabled"
                    NetworkSecurityPolicy.DOMAIN_ENCRYPTION_MODE_OPPORTUNISTIC -> "opportunistic"
                    NetworkSecurityPolicy.DOMAIN_ENCRYPTION_MODE_DISABLED -> "disabled"
                    else -> "unknown"
                }
            } else {
                "unavailable (API ${Build.VERSION.SDK_INT})"
            }
            val implementation = socket.javaClass.name
            val supported = socket.use {
                bundled || (Build.VERSION.SDK_INT >= 37 && it is SSLSocket && SSLSockets.isSupportedSocket(it))
            }
            return ProbeTraceRecorder(hostname, implementation, supported, policy) { config ->
                if (!bundled && Build.VERSION.SDK_INT >= 37) {
                    try {
                        EchConfigList.fromBytes(config.toByteArray())
                        true
                    } catch (_: InvalidEchDataException) {
                        false
                    }
                } else {
                    null
                }
            }
        }
    }
}

/** Exception messages may contain proxy passwords or URLs. Emit only fixed transport tokens. */
internal fun safeFailureCodes(failure: Throwable): List<String> {
    val pending = ArrayDeque<Throwable>()
    val seen = mutableSetOf<Throwable>()
    val codes = linkedSetOf<String>()
    pending.add(failure)
    while (pending.isNotEmpty() && seen.size < 16) {
        val current = pending.removeFirst()
        if (!seen.add(current)) continue
        val name = current.javaClass.simpleName
        if (name.matches(Regex("[A-Za-z0-9_]{1,80}"))) codes += name
        val message = current.message.orEmpty()
        ERROR_TOKENS.filter { Regex("\\b$it\\b").containsMatchIn(message) }.forEach { codes += it }
        if (message.contains("connection reset", ignoreCase = true)) codes += "CONNECTION_RESET"
        if (message.contains("connection closed", ignoreCase = true)) codes += "CONNECTION_CLOSED"
        current.cause?.let(pending::addLast)
        current.suppressed.take(8).forEach(pending::addLast)
    }
    return codes.take(16)
}

private val ERROR_TOKENS = listOf(
    "ECONNRESET", "ECONNREFUSED", "ETIMEDOUT", "ENETUNREACH", "EHOSTUNREACH", "EPIPE",
    "ECONNABORTED", "EPERM", "EACCES",
)
