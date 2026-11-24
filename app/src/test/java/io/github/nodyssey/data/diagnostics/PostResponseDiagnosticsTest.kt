package io.github.nodyssey.data.diagnostics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sun.net.httpserver.HttpServer
import io.github.nodyssey.core.NodeSeekSite
import io.github.plaza.core.AppClock
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream

/** Catch loss of the original error body, leaked credentials, and evidence erased by recovery. */
@RunWith(RobolectricTestRunner::class)
class PostResponseDiagnosticsTest {
    @Test
    fun `a gzip challenge is recorded without consuming the body or leaking tokens`() = withServer { server, origin ->
        val body = "<html><script>_cf_chl_opt = 'body-secret'</script>Just a moment...</html>"
        val compressed = ByteArrayOutputStream().also { output -> GZIPOutputStream(output).use { it.write(body.toByteArray()) } }.toByteArray()
        server.createContext("/post-1053419-1") { exchange ->
            exchange.responseHeaders.add("Content-Encoding", "gzip")
            exchange.responseHeaders.add("Content-Type", "text/html")
            exchange.responseHeaders.add("Server", "cloudflare")
            exchange.responseHeaders.add("Cf-Mitigated", "challenge")
            exchange.responseHeaders.add("CF-Ray", "a5a486a72c00ddc2-HKG")
            exchange.responseHeaders.add("Set-Cookie", "cf_clearance=response-secret; Path=/")
            exchange.responseHeaders.add("Set-Cookie", "secret-cookie-name=hidden; Path=/")
            exchange.sendResponseHeaders(403, compressed.size.toLong())
            exchange.responseBody.use { it.write(compressed) }
        }
        val records = mutableListOf<PostResponseDiagnostic>()
        val observer = observer(origin, records::add)
        val client = client(observer)
        try {
            val request = Request.Builder().url("$origin/post-1053419-1?token=query-secret#fragment-secret").build()
            client.newCall(request).execute().use { response ->
                assertEquals(403, response.code)
                assertEquals(body, response.body.string())
            }
            val record = records.single()
            assertEquals("$origin/post-1053419-1", record.requestedUrl)
            assertEquals(record.requestedUrl, record.finalUrl)
            assertTrue(record.cfMitigated)
            assertEquals(listOf("cf-challenge"), record.bodyFeatures)
            assertEquals(listOf("fog", "pjwt", "session"), record.sentCookieNames)
            assertEquals(listOf("cf_clearance"), record.receivedCookieNames)
            assertEquals("127.0.0.1", record.remoteAddress)
            assertEquals(body.toByteArray().size, record.sampledBytes)
            val saved = Json.encodeToString(record)
            listOf("request-secret", "response-secret", "body-secret", "query-secret", "fragment-secret", "secret-cookie-name").forEach {
                assertFalse("Leaked $it", it in saved)
            }
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    @Test
    fun `successes and unrelated failures preserve the last post evidence and samples are bounded`() = withServer { server, origin ->
        val body = "403 Forbidden nginx" + "x".repeat(40_000)
        server.createContext("/") { exchange ->
            val success = exchange.requestURI.path == "/post-2-1"
            val bytes = if (success) "success".toByteArray() else body.toByteArray()
            exchange.sendResponseHeaders(if (success) 200 else 403, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        val records = mutableListOf<PostResponseDiagnostic>()
        val observer = observer(origin, records::add)
        val client = client(observer)
        try {
            listOf("/post-1-1", "/post-2-1", "/api/account/signIn").forEach { path ->
                client.newCall(Request.Builder().url(origin + path).build()).execute().use { response ->
                    assertEquals(if (path == "/post-2-1") "success" else body, response.body.string())
                }
            }
            val record = records.single()
            assertEquals(32_768, record.sampledBytes)
            assertEquals(listOf("nginx-forbidden"), record.bodyFeatures)
            assertFalse(record.cfMitigated)
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    @Test
    fun `redirect targets redact non post paths query and credentials`() = withServer { server, origin ->
        server.createContext("/post-1-1") { exchange ->
            exchange.responseHeaders.add("Location", "$origin/login-path-secret?token=query-secret")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/login-path-secret") { exchange ->
            exchange.responseHeaders.add("CF-Ray", "header-secret")
            exchange.sendResponseHeaders(403, -1)
            exchange.close()
        }
        val records = mutableListOf<PostResponseDiagnostic>()
        val observer = observer(origin, records::add)
        val client = client(observer)
        try {
            client.newCall(Request.Builder().url("$origin/post-1-1").build()).execute().close()
            assertEquals("$origin/[redacted]", records.single().finalUrl)
            assertNull(records.single().cfRay)
            assertFalse("secret" in Json.encodeToString(records.single()))
            server.createContext("/post-2-1") { exchange ->
                exchange.responseHeaders.add("Location", origin.replace("127.0.0.1", "localhost") + "/login-path-secret?token=query-secret")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
            client.newCall(Request.Builder().url("$origin/post-2-1").build()).execute().close()
            assertEquals("[external-host]/[redacted]", records.last().finalUrl)
            assertEquals(2, records.size)
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    /**
     * The record has to say who refused: an edge rule (no origin time, Cloudflare's own page and
     * code) needs the web view, an origin 403 does not. Losing either turns the record back into the
     * bare status the screen already showed.
     */
    @Test
    fun `an edge block records its code and that the origin was never asked`() = withServer { server, origin ->
        server.createContext("/post-1-1") { exchange ->
            val body =
                """<html><head><title>Access denied | www.nodeseek.com used Cloudflare to restrict access</title></head>
                <body><div id="cf-error-details"><h1><span data-translate="error">Error</span><span>1020</span></h1></div></body></html>"""
                    .toByteArray()
            exchange.responseHeaders.add("Server", "cloudflare")
            exchange.responseHeaders.add("Server-Timing", "cfEdge;dur=4,cfOrigin;dur=0")
            exchange.sendResponseHeaders(403, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        val records = mutableListOf<PostResponseDiagnostic>()
        val observer = observer(origin, records::add)
        val client = client(observer)
        try {
            client.newCall(Request.Builder().url("$origin/post-1-1").build()).execute().close()
            val record = records.single()
            assertEquals(listOf("cf-block-page", "cf-error-page", "cf-error-1020"), record.bodyFeatures)
            assertEquals(0, record.cfOriginMillis)
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    @Test
    fun `a failed diagnostic write does not replace the server response`() = withServer { server, origin ->
        server.createContext("/") { exchange ->
            val body = "forbidden".toByteArray()
            exchange.sendResponseHeaders(403, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        val observer = observer(origin) { throw IllegalStateException("disk write failed") }
        val client = client(observer)
        try {
            client.newCall(Request.Builder().url("$origin/post-1-1").build()).execute().use {
                assertEquals(403, it.code)
                assertEquals("forbidden", it.body.string())
            }
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    @Test
    fun `saved evidence survives a new store expires and can be cleared`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("post-response-test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        var now = 1_000L
        val clock = AppClock { now }
        val original = PostResponseDiagnostic(
            now, "https://www.nodeseek.com/post-1-1", "https://www.nodeseek.com/post-1-1", 403,
            "cloudflare", null, false, "html", 5, emptyList(), emptyList(), emptyList(), null, null, null, null,
        )
        PostResponseDiagnosticStore(preferences, clock).save(original)
        val reopened = PostResponseDiagnosticStore(preferences, clock)
        assertEquals(original, reopened.read())
        now += TimeUnit.DAYS.toMillis(7) + 1
        assertNull(reopened.read())
        assertTrue(preferences.all.isEmpty())
        reopened.save(original.copy(recordedAtMillis = now))
        reopened.clear()
        assertNull(reopened.read())
        preferences.edit().putString("last_failure", "broken-json").commit()
        assertNull(reopened.read())
        assertTrue(preferences.all.isEmpty())
    }

    private fun observer(origin: String, save: (PostResponseDiagnostic) -> Unit) =
        PostResponseDiagnosticsInterceptor(NodeSeekSite.CONFIG.copy(baseUrl = origin, sessionUrls = listOf(origin)), AppClock { 1_000L }, save)

    private fun client(observer: PostResponseDiagnosticsInterceptor) = OkHttpClient.Builder()
        .addInterceptor(observer)
        .addNetworkInterceptor(observer.connectionInterceptor)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit

            override fun loadForRequest(url: HttpUrl): List<Cookie> = listOf(
                Cookie.Builder().name("session").value("request-secret").hostOnlyDomain(url.host).build(),
                Cookie.Builder().name("pjwt").value("request-secret").hostOnlyDomain(url.host).build(),
                Cookie.Builder().name("fog").value("request-secret").hostOnlyDomain(url.host).build(),
            )
        })
        .build()

    private fun withServer(block: (HttpServer, String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        try {
            block(server, "http://127.0.0.1:${server.address.port}")
        } finally {
            server.stop(0)
        }
    }
}
