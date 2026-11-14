package io.github.nodyssey.data.diagnostics

import android.content.SharedPreferences
import io.github.plaza.core.AppClock
import io.github.plaza.core.net.SiteConfig
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.net.Proxy

/** Retains one failure across successful requests and restarts, until cleared or seven days old. */
internal class PostResponseDiagnosticStore(
    private val preferences: SharedPreferences,
    private val clock: AppClock,
) {
    fun save(record: PostResponseDiagnostic) {
        preferences.edit().putString(KEY, Json.encodeToString(record)).apply()
    }

    fun read(): PostResponseDiagnostic? {
        val stored = preferences.getString(KEY, null) ?: return null
        val record = runCatchingExceptCancellation { Json.decodeFromString<PostResponseDiagnostic>(stored) }.getOrNull()
        if (record == null || clock.nowMillis() - record.recordedAtMillis > MAX_AGE_MILLIS) {
            clear()
            return null
        }
        return record
    }

    fun clear() = preferences.edit().remove(KEY).apply()

    private companion object {
        const val KEY = "last_failure"
        const val MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1_000
    }
}

/**
 * Observes, without retrying or changing error classification. The application interceptor sees
 * decompressed content; its paired network interceptor remembers the final hop's actual cookies
 * and route. Neither raw bytes nor arbitrary strings escape into the saved record.
 */
internal class PostResponseDiagnosticsInterceptor(
    private val config: SiteConfig,
    private val clock: AppClock,
    private val save: (PostResponseDiagnostic) -> Unit,
) : Interceptor {
    private val hosts = (config.sessionUrls + config.baseUrl).map { it.toHttpUrl().host }.toSet()

    val connectionInterceptor = Interceptor { chain ->
        chain.request().tag(WireTrace::class.java)?.let { trace ->
            val connection = chain.connection()
            trace.cookies = cookieNames(chain.request().header("Cookie").orEmpty().split(';').map { it.substringBefore('=').trim() })
            trace.remoteAddress = connection?.route()?.takeIf { it.proxy.type() == Proxy.Type.DIRECT }?.socketAddress?.address?.hostAddress
            trace.protocol = connection?.protocol()?.toString()
            trace.tlsVersion = connection?.handshake()?.tlsVersion?.javaName
            trace.routeEchBytes = connection?.route()?.echConfigList?.size ?: 0
        }
        chain.proceed(chain.request())
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET" || request.url.host !in hosts || !POST_PATH.matches(request.url.encodedPath)) {
            return chain.proceed(request)
        }
        val trace = WireTrace()
        val response = chain.proceed(request.newBuilder().tag(WireTrace::class.java, trace).build())
        if (response.code in 400..599) {
            // Observability must not turn an otherwise usable HTTP response into a new failure.
            runCatchingExceptCancellation {
                val sample = response.peekBody(MAX_SAMPLE_BYTES)
                val sampledBytes = sample.contentLength().toInt()
                val body = sample.string()
                save(
                    PostResponseDiagnostic(
                        recordedAtMillis = clock.nowMillis(),
                        requestedUrl = safeAddress(request.url),
                        finalUrl = safeAddress(response.request.url),
                        statusCode = response.code,
                        server = when (response.header("Server")?.lowercase()) {
                            "cloudflare" -> "cloudflare"
                            "nginx" -> "nginx"
                            null -> "missing"
                            else -> "other"
                        },
                        cfRay = response.header("CF-Ray")?.takeIf(CF_RAY::matches),
                        cfMitigated = response.header("CF-Mitigated").equals("challenge", ignoreCase = true),
                        contentType = when (response.body.contentType()?.subtype?.lowercase()) {
                            "html" -> "html"
                            "json" -> "json"
                            "plain" -> "text"
                            else -> "other"
                        },
                        sampledBytes = sampledBytes,
                        bodyFeatures = buildList {
                            if (config.markers.challenge.any(body::contains)) add("cf-challenge")
                            if (body.contains("cf-error-details") || body.contains("Sorry, you have been blocked")) add("cf-block-page")
                            if (body.contains("403 Forbidden") && body.contains("nginx")) add("nginx-forbidden")
                            if (config.markers.usablePage.any(body::contains)) add("site-page")
                        },
                        sentCookieNames = trace.cookies,
                        receivedCookieNames = cookieNames(response.headers.values("Set-Cookie").map { it.substringBefore('=').trim() }),
                        remoteAddress = trace.remoteAddress,
                        protocol = trace.protocol,
                        tlsVersion = trace.tlsVersion,
                        routeEchBytes = trace.routeEchBytes,
                    ),
                )
            }
        }
        return response
    }

    private fun safeAddress(url: HttpUrl): String {
        // Query, fragment, user info and unrecognised redirect paths can carry login tokens.
        if (url.host !in hosts) return "[external-host]/[redacted]"
        val origin = "${url.scheme}://${url.host}" + if (url.port == 80 || url.port == 443) "" else ":${url.port}"
        val path = if (url.host in hosts && POST_PATH.matches(url.encodedPath)) url.encodedPath else "/[redacted]"
        return origin + path
    }

    private fun cookieNames(names: List<String>): List<String> = names.filter { it in COOKIE_NAMES }.distinct().sorted()

    private class WireTrace {
        var cookies: List<String> = emptyList()
        var remoteAddress: String? = null
        var protocol: String? = null
        var tlsVersion: String? = null
        var routeEchBytes: Int? = null
    }

    private companion object {
        val POST_PATH = Regex("/post-[0-9]+-[0-9]+")
        val CF_RAY = Regex("[a-fA-F0-9]{16}-[A-Z]{3}")
        val COOKIE_NAMES = setOf("session", "token", "cf_clearance", "pjwt", "smac", "colorscheme")
        const val MAX_SAMPLE_BYTES = 32_768L
    }
}
