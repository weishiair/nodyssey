package io.github.nodyssey.data.diagnostics

import okhttp3.Dns
import okio.ByteString.Companion.encodeUtf8
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketException

class ProbeTraceRecorderTest {
    private val host = "www.nodeseek.com"
    private val ip = Dns.Record.IpAddress(host, InetAddress.getByName("192.0.2.1"))
    private val metadata = Dns.Record.ServiceMetadata(host, echConfigList = "ech-config".encodeUtf8())

    private fun recorder() = ProbeTraceRecorder(host, "test provider", true, "enabled") { it == metadata.echConfigList }

    @Test
    fun `streamed metadata is accumulated and a different hostname does not advertise usable ECH`() {
        val trace = recorder()
        val dns = StreamingDns()
        val observed = trace.observe(dns).newCall(Dns.Request(host))
        val delivered = mutableListOf<Dns.Record>()
        observed.enqueue(object : Dns.Callback {
            override fun onRecords(call: Dns.Call, last: Boolean, records: List<Dns.Record>) {
                delivered += records
            }

            override fun onFailure(call: Dns.Call, e: IOException) = throw AssertionError(e)
        })
        val other = Dns.Record.ServiceMetadata("other.example", echConfigList = metadata.echConfigList)
        dns.callback.onRecords(dns.call, false, listOf(ip, other))
        assertEquals(0, trace.snapshot().dnsAnswers.single().echConfigBytes)
        dns.callback.onRecords(dns.call, true, listOf(metadata))

        val answer = trace.snapshot().dnsAnswers.single()
        assertEquals(listOf("192.0.2.1"), answer.addresses)
        assertEquals(2, answer.httpsRecords)
        assertEquals(10, answer.echConfigBytes)
        assertEquals(true, answer.echConfigValid)
        assertEquals(listOf(ip, other, metadata), delivered)

        val unmatched = recorder()
        val second = StreamingDns()
        unmatched.observe(second).newCall(Dns.Request(host)).enqueue(NoOpCallback)
        second.callback.onRecords(second.call, true, listOf(ip, other))
        assertEquals(1, unmatched.snapshot().dnsAnswers.single().httpsRecords)
        assertEquals(0, unmatched.snapshot().dnsAnswers.single().echConfigBytes)
    }

    @Test
    fun `a later TCP retry does not erase the TLS boundary already reached`() {
        val trace = recorder()
        val first = InetSocketAddress("192.0.2.1", 443)
        trace.connectStart(first, Proxy.NO_PROXY)
        trace.secureConnectStart()
        trace.connectFailed(first, Proxy.NO_PROXY, SocketException("Connection reset"))
        trace.connectStart(InetSocketAddress("192.0.2.2", 443), Proxy.NO_PROXY)

        val snapshot = trace.snapshot()
        assertEquals(ProbeStage.TLS, snapshot.stage)
        assertTrue(snapshot.connectionEvents.any { "192.0.2.1:443" in it && "CONNECTION_RESET" in it })
        repeat(50) { trace.secureConnectStart() }
        assertEquals(24, trace.snapshot().connectionEvents.size)
        assertEquals(4, snapshot.connectionEvents.size)
    }

    @Test
    fun `ECH control accepts only an unambiguous successful trace and never copies the public IP`() {
        val body = "ip=203.0.113.22\nsni=encrypted\nloc=XX\n"
        assertEquals("HTTP 200; sni=encrypted; route_ech_bytes=71", echControlSummary(200, body, 71))
        assertEquals("HTTP 403; sni=unknown; route_ech_bytes=71", echControlSummary(403, body, 71))
        assertEquals("HTTP 200; sni=unknown; route_ech_bytes=0", echControlSummary(200, "sni=encrypted\nsni=plaintext", 0))
        assertEquals("HTTP 200; sni=unknown; route_ech_bytes=0", echControlSummary(200, "sni=secret.example", 0))
        assertFalse("203.0.113.22" in echControlSummary(200, body, 71))
    }

    @Test
    fun `proxy names addresses and exception messages are absent from the report`() {
        val trace = recorder()
        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("198.51.100.44", 9090))
        val failure = SocketException("Connection reset ECONNRESET at https://user:secret@proxy.private/path")
        failure.initCause(IOException("password=hunter2"))
        failure.addSuppressed(IOException("ETIMEDOUT at private endpoint"))
        trace.connectStart(proxy.address() as InetSocketAddress, proxy)
        trace.connectFailed(proxy.address() as InetSocketAddress, proxy, failure)
        val dns = StreamingDns()
        trace.observe(dns).newCall(Dns.Request("proxy.private")).enqueue(NoOpCallback)
        dns.callback.onRecords(dns.call, true, listOf(Dns.Record.IpAddress("proxy.private", ip.address)))

        val snapshot = trace.snapshot(failure)
        assertTrue(snapshot.dnsAnswers.isEmpty())
        assertEquals(listOf("SocketException", "ECONNRESET", "CONNECTION_RESET", "IOException", "ETIMEDOUT"), snapshot.failureCodes)
        val report = snapshot.toString()
        for (secret in listOf("secret", "hunter2", "proxy.private", "198.51.100.44", "9090")) {
            assertFalse(secret, secret in report)
        }
    }

    private class StreamingDns : Dns {
        lateinit var callback: Dns.Callback
        val call = object : Dns.Call {
            override val request = Dns.Request("unused.example")
            override fun enqueue(callback: Dns.Callback) {
                this@StreamingDns.callback = callback
            }

            override fun cancel() = Unit
            override fun isCanceled() = false
        }

        override fun lookup(hostname: String): List<InetAddress> = throw AssertionError("lookup loses metadata")
        override fun newCall(request: Dns.Request): Dns.Call = call
    }

    private object NoOpCallback : Dns.Callback {
        override fun onRecords(call: Dns.Call, last: Boolean, records: List<Dns.Record>) = Unit
        override fun onFailure(call: Dns.Call, e: IOException) = Unit
    }
}
