@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.plaza.core.net

import io.github.nodyssey.data.dns.DohConfig
import io.github.nodyssey.data.dns.resolvesOverHttps
import io.github.plaza.core.net.ech.nodyssey_network_cancel
import io.github.plaza.core.net.ech.nodyssey_network_free
import io.github.plaza.core.net.ech.nodyssey_network_start
import io.github.plaza.core.net.ech.nodyssey_network_take
import io.github.plaza.core.toByteArray
import kotlinx.cinterop.toKString
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSHTTPCookieStorage
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import kotlin.io.encoding.Base64

/**
 * A request selects a route after reading saved settings, including the first request after launch.
 * DoH-on uses app-owned DNS, TLS/ECH and connection pools. Proxy/off uses the existing URLSession.
 * Redirects stay here so each hop sees the correct native cookie jar; Rust never owns a second jar.
 */
class AppleEchTransport(
    private val fallback: HttpTransport,
    private val config: suspend () -> DohConfig,
    private val usesProxy: suspend () -> Boolean,
    private val userAgent: String,
    private val acceptLanguage: String,
    private val referer: String,
    private val sessionCookies: SessionCookies,
    private val byteSession: () -> NSURLSession,
    private val storage: NSHTTPCookieStorage = NSHTTPCookieStorage.sharedHTTPCookieStorage,
) : HttpTransport {
    private val cookies = AppleCookieStore(storage)

    override suspend fun execute(request: HttpRequest, onUploadProgress: UploadProgress?): HttpResponse {
        val settings = config()
        if (!settings.resolvesOverHttps() || usesProxy()) return fallback.execute(request, onUploadProgress)
        val bytes = executeEncrypted(request, settings, onUploadProgress, 32 * 1024 * 1024)
        return HttpResponse(bytes.code, bytes.url, bytes.headers, bytes.body.decodeToString())
    }

    /** False means URLSession is selected; an ECH failure never silently changes the route. */
    suspend fun isEnabled(): Boolean = config().resolvesOverHttps() && !usesProxy()

    suspend fun executeBytes(request: HttpRequest, onUploadProgress: UploadProgress? = null, maxBytes: Int = 32 * 1024 * 1024): AppleEchResponse {
        val settings = config()
        if (!settings.resolvesOverHttps() || usesProxy()) {
            require(request.method == "GET" && request.body == null)
            val response = byteSession().getBytes(request.url, maxBytes.toLong()) ?: throw SiteException(SiteError.Network)
            return AppleEchResponse(200, request.url, response.headers.toMap(), response.data.toByteArray())
        }
        return executeEncrypted(request, settings, onUploadProgress, maxBytes)
    }

    private suspend fun executeEncrypted(request: HttpRequest, settings: DohConfig, onUploadProgress: UploadProgress?, maxBytes: Int): AppleEchResponse {
        val nativeSettings = NativeEchSettings(settings.chain.map { NativeEchResolver(it.url, it.bootstrap) }, settings.includeIPv6)
        var current = request
        repeat(MAX_REDIRECTS + 1) { hop ->
            sessionCookies.dropUnreplayable()
            val url = NSURL.URLWithString(current.url) ?: throw SiteException(SiteError.Network)
            val parsed = WebUrl.parse(current.url)
            if (parsed?.scheme != "https" || parsed.userInfo != null) throw SiteException(SiteError.Network)
            val headers = buildMap {
                put("User-Agent", userAgent)
                put("Accept-Language", acceptLanguage)
                if (hop == 0) put("Referer", referer)
                putAll(current.headers)
                // Read after every redirect, including cookies set by a successful credentials POST.
                if (keys.none { it.equals("Cookie", true) }) cookies.cookieHeader(current.url)?.let { put("Cookie", it) }
            }.toMutableMap()
            val body = current.body?.toNSData()?.toByteArray() ?: ByteArray(0)
            when (val payload = current.body) {
                is HttpBody.Text -> headers["Content-Type"] = payload.contentType
                is HttpBody.Multipart -> headers["Content-Type"] = "multipart/form-data; boundary=----NodysseyFormBoundary7MA4YWxkTrZu0gW"
                else -> Unit
            }
            val input = NativeEchInput(current.url, current.method, headers.map { listOf(it.key, it.value) }, Base64.encode(body), nativeSettings, maxBytes)
            val result = callNative(input)
            if (current.body != null) onUploadProgress?.invoke(1f)
            result.headers.filter { it[0].equals("set-cookie", true) }.forEach { cookies.setCookie(current.url, it[1]) }
            sessionCookies.dropUnreplayable()
            val responseHeaders = result.headers.groupBy({ it[0].lowercase() }, { it[1] }).mapValues { it.value.joinToString(",") }
            val location = responseHeaders["location"]
            if (result.code !in REDIRECTS || location == null) {
                return AppleEchResponse(result.code, current.url, responseHeaders, Base64.decode(result.body), result.facts.ip)
            }
            if (hop == MAX_REDIRECTS) throw SiteException(SiteError.Network)
            val target = NSURL.URLWithString(location, relativeToURL = url)?.absoluteURL?.absoluteString
                ?: throw SiteException(SiteError.Network)
            current = redirectRequest(current, target, result.code) ?: throw SiteException(SiteError.Network)
        }
        error("unreachable")
    }

    private suspend fun callNative(input: NativeEchInput): NativeEchOutput {
        val id = nodyssey_network_start(Json.encodeToString(input))
        if (id == 0uL) throw nativeFailure("CONFIG", "native_capacity_or_input", "NotEstablished")
        try {
            return withTimeout(40_000) {
                while (true) {
                    val pointer = nodyssey_network_take(id)
                    if (pointer != null) {
                        val text = try {
                            pointer.toKString()
                        } finally {
                            nodyssey_network_free(pointer)
                        }
                        val result = Json.decodeFromString<NativeEchOutput>(text)
                        if (result.error != null) throw nativeFailure(result.facts.stage, result.error, result.facts.ech)
                        return@withTimeout result
                    }
                    delay(25)
                }
                @Suppress("UNREACHABLE_CODE")
                error("unreachable")
            }
        } finally {
            // Safe after completion too. Cancellation drops an in-flight request and its sensitive body.
            nodyssey_network_cancel(id)
        }
    }

    private fun nativeFailure(stage: String, reason: String, ech: String): SiteException = SiteException(
        SiteError.Network,
        diagnostics = TransportFailureDiagnostics(
            platformVersion = NSProcessInfo.processInfo.operatingSystemVersionString,
            errors = emptyList(),
            engine = NativeTransportEngine.RUST_ECH,
            connection = EncryptedConnectionDiagnostics.fromNative(stage, reason, ech),
        ),
    )

    private companion object {
        const val MAX_REDIRECTS = 10
        val REDIRECTS = setOf(301, 302, 303, 307, 308)
    }
}

data class AppleEchResponse(val code: Int, val url: String, val headers: Map<String, String>, val body: ByteArray, val remoteAddress: String? = null)

@Serializable private data class NativeEchResolver(val url: String, val bootstrap: List<String>)

@Serializable private data class NativeEchSettings(val resolvers: List<NativeEchResolver>, val ipv6: Boolean)

@Serializable private data class NativeEchInput(val url: String, val method: String, val headers: List<List<String>>, val body: String, val settings: NativeEchSettings, @kotlinx.serialization.SerialName("max_body") val maxBody: Int)

@Serializable private data class NativeEchFacts(val stage: String, val ech: String, val ip: String? = null)

@Serializable private data class NativeEchOutput(val code: Int, val headers: List<List<String>>, val body: String, val error: String? = null, val facts: NativeEchFacts)
