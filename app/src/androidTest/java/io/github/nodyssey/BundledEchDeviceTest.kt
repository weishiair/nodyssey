package io.github.nodyssey

import android.os.Build
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nodyssey.data.diagnostics.ProbeTraceRecorder
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.dnsoverhttps.DnsOverHttps
import org.conscrypt.Conscrypt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocket

/** Opt-in live interoperability check; routine CI journeys never depend on an external server. */
@RunWith(AndroidJUnit4::class)
class BundledEchDeviceTest {
    @Test
    fun cloudflareConfirmsEchOnTheAppsActualTlsClient() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("liveEch") == "true")
        assumeTrue(Build.VERSION.SDK_INT < 37)
        val app = ApplicationProvider.getApplicationContext<NodysseyApp>()
        val appClient = app.container.okHttpClient
        appClient.sslSocketFactory.createSocket().use { socket ->
            assertTrue("App must load standalone Conscrypt on API ${Build.VERSION.SDK_INT}", Conscrypt.isConscrypt(socket as SSLSocket))
        }
        val report = ProbeTraceRecorder.forClient("cloudflare-ech.com", appClient).snapshot()
        assertTrue("Diagnostics must detect the bundled TLS backend", report.nativeEchSupported)
        assertEquals("app-bundled enabled", report.echPolicy)
        val bootstrap = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
        val dns = DnsOverHttps.Builder()
            .client(bootstrap)
            .url((arguments.getString("dohEndpoint") ?: "https://cloudflare-dns.com/dns-query").toHttpUrl())
            .includeIPv6(false)
            .includeServiceMetadata(true)
            .build()
        val client = appClient.newBuilder()
            .apply {
                interceptors().clear()
                networkInterceptors().clear()
            }
            .cookieJar(CookieJar.NO_COOKIES)
            .dns(dns)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
        try {
            client.newCall(Request.Builder().url("https://cloudflare-ech.com/cdn-cgi/trace").build()).execute().use { response ->
                val trace = response.body.string()
                val ech = trace.lineSequence().firstOrNull { it.startsWith("sni=") }
                Log.i("BundledEchTest", "API=${Build.VERSION.SDK_INT} HTTP=${response.code} $ech protocol=${response.protocol}")
                assertEquals(200, response.code)
                assertEquals("sni=encrypted", ech)
            }
        } finally {
            client.connectionPool.evictAll()
            bootstrap.connectionPool.evictAll()
        }
    }
}
