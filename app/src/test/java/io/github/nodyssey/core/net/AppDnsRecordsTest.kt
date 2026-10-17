package io.github.nodyssey.core.net

import io.github.nodyssey.data.dns.DohConfig
import io.github.nodyssey.data.dns.DohProvider
import io.github.nodyssey.data.dns.DohServer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Dns
import okio.ByteString.Companion.encodeUtf8
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetAddress

@OptIn(ExperimentalCoroutinesApi::class)
class AppDnsRecordsTest {
    private val request = Dns.Request("www.nodeseek.com", 8443)
    private val ip = Dns.Record.IpAddress(request.hostname, InetAddress.getByName("192.0.2.1"))
    private val metadata = Dns.Record.ServiceMetadata(
        request.hostname,
        port = request.port,
        echConfigList = "test ECH configuration".encodeUtf8(),
    )
    private val config = DohConfig(
        enabled = true,
        servers = listOf(
            DohServer.preset(DohProvider.ALIDNS, checked = true),
            DohServer.preset(DohProvider.DNSPOD, checked = true),
        ),
    )

    @Test
    fun `the connection receives ECH metadata and addresses from every batch`() = runTest {
        val resolver = RecordDns()
        val dns = AppDns(backgroundScope, MutableStateFlow(config), { resolver })
        runCurrent()
        val result = Result()
        val call = dns.newCall(request)
        call.enqueue(result)
        runCurrent()

        val delegate = resolver.calls.single()
        assertEquals(request, delegate.request)
        delegate.records(false, listOf(ip))
        assertTrue(result.records.isEmpty())
        delegate.records(true, listOf(metadata))
        runCurrent()

        assertEquals(listOf(ip, metadata), result.records)
        assertEquals(1, result.terminals)
        assertTrue(result.failures.isEmpty())
        call.cancel()
        assertFalse(call.isCanceled())
    }

    @Test
    fun `failed partial answers are discarded and the working server is preferred next time`() = runTest {
        val first = RecordDns()
        val second = RecordDns()
        val dns = AppDns(backgroundScope, MutableStateFlow(config), {
            if (it.url == DohProvider.ALIDNS.url) first else second
        })
        runCurrent()
        val result = Result()
        dns.newCall(request).enqueue(result)
        runCurrent()
        first.calls.single().records(false, listOf(metadata))
        first.calls.single().fail(IOException("unreachable"))
        runCurrent()
        second.calls.single().records(true, listOf(ip))
        runCurrent()

        assertEquals(listOf(ip), result.records)
        assertEquals(1, result.terminals)
        dns.newCall(request).enqueue(Result())
        runCurrent()
        assertEquals(1, first.calls.size)
        assertEquals(2, second.calls.size)
    }

    @Test
    fun `a failed optional query keeps addresses and ECH from the same server`() = runTest {
        val resolver = RecordDns()
        val dns = AppDns(backgroundScope, MutableStateFlow(config), { resolver })
        runCurrent()
        val result = Result()
        dns.newCall(request).enqueue(result)
        runCurrent()
        resolver.calls.single().records(false, listOf(ip, metadata))
        resolver.calls.single().fail(IOException("AAAA query failed"))
        runCurrent()

        assertEquals(listOf(ip, metadata), result.records)
        assertTrue(result.failures.isEmpty())
        assertEquals(1, resolver.calls.size)
        assertEquals(1, result.terminals)
    }

    @Test
    fun `an answer without addresses moves on to the next resolver`() = runTest {
        val first = RecordDns()
        val second = RecordDns()
        val dns = AppDns(backgroundScope, MutableStateFlow(config), {
            if (it.url == DohProvider.ALIDNS.url) first else second
        })
        runCurrent()
        val result = Result()
        dns.newCall(request).enqueue(result)
        runCurrent()
        first.calls.single().records(true, listOf(metadata))
        runCurrent()
        second.calls.single().records(true, listOf(ip))
        runCurrent()

        assertEquals(listOf(ip), result.records)
        assertTrue(result.failures.isEmpty())
        assertEquals(1, result.terminals)
    }

    @Test
    fun `strict mode reports the first failure with the later failures attached`() = runTest {
        val resolver = RecordDns()
        val system = RecordDns()
        val dns = AppDns(backgroundScope, MutableStateFlow(config), { resolver }, system)
        runCurrent()
        val result = Result()
        dns.newCall(request).enqueue(result)
        runCurrent()
        val first = IOException("first")
        resolver.calls[0].fail(first)
        runCurrent()
        val second = IOException("second")
        resolver.calls[1].fail(second)
        runCurrent()

        val reported = result.failures.single()
        assertEquals("first", reported.message)
        // Coroutine stack recovery may copy exceptions and retain the original as their cause.
        val suppressed = generateSequence<Throwable>(reported) { it.cause }
            .flatMap { it.suppressed.asSequence() }
            .map { it.message }
            .toList()
        assertEquals(listOf("second"), suppressed)
        assertTrue(system.calls.isEmpty())
        assertEquals(1, result.terminals)
    }

    @Test
    fun `enabled fallback preserves system service metadata too`() = runTest {
        val resolver = RecordDns()
        val system = RecordDns()
        val dns = AppDns(backgroundScope, MutableStateFlow(config.copy(fallbackToSystem = true)), { resolver }, system)
        runCurrent()
        val result = Result()
        dns.newCall(request).enqueue(result)
        runCurrent()
        resolver.calls[0].fail(IOException("first"))
        runCurrent()
        resolver.calls[1].fail(IOException("second"))
        runCurrent()
        system.calls.single().records(true, listOf(ip, metadata))
        runCurrent()

        assertEquals(listOf(ip, metadata), result.records)
        assertTrue(result.failures.isEmpty())
    }

    @Test
    fun `canceling a lookup cancels its DoH request without trying the next server`() = runTest {
        val resolver = RecordDns()
        val dns = AppDns(backgroundScope, MutableStateFlow(config), { resolver })
        runCurrent()
        val result = Result()
        val call = dns.newCall(request)
        call.enqueue(result)
        runCurrent()
        call.cancel()
        runCurrent()
        val delegate = resolver.calls.single()
        assertTrue(delegate.isCanceled())
        assertTrue(call.isCanceled())
        assertEquals(1, result.failures.size)

        delegate.records(true, listOf(ip, metadata))
        runCurrent()
        assertEquals(1, resolver.calls.size)
        assertTrue(result.records.isEmpty())
        assertEquals(1, result.terminals)
    }

    @Test
    fun `canceling before enqueue never starts a resolver`() = runTest {
        val resolver = RecordDns()
        val dns = AppDns(backgroundScope, MutableStateFlow(config), { resolver })
        runCurrent()
        val call = dns.newCall(request)
        call.cancel()
        val result = Result()
        call.enqueue(result)
        runCurrent()

        assertTrue(resolver.calls.isEmpty())
        assertEquals(1, result.failures.size)
        assertEquals(1, result.terminals)
    }

    private class Result : Dns.Callback {
        val records = mutableListOf<Dns.Record>()
        val failures = mutableListOf<IOException>()
        var terminals = 0

        override fun onRecords(call: Dns.Call, last: Boolean, records: List<Dns.Record>) {
            this.records += records
            if (last) terminals++
        }

        override fun onFailure(call: Dns.Call, e: IOException) {
            failures += e
            terminals++
        }
    }

    private class RecordDns : Dns {
        val calls = mutableListOf<RecordCall>()

        override fun lookup(hostname: String): List<InetAddress> = error("lookup would discard service metadata")

        override fun newCall(request: Dns.Request): Dns.Call = RecordCall(request).also { calls += it }
    }

    private class RecordCall(override val request: Dns.Request) : Dns.Call {
        private lateinit var callback: Dns.Callback
        private var canceled = false

        override fun enqueue(callback: Dns.Callback) {
            this.callback = callback
        }

        override fun cancel() {
            canceled = true
        }

        override fun isCanceled(): Boolean = canceled

        fun records(last: Boolean, records: List<Dns.Record>) = callback.onRecords(this, last, records)

        fun fail(failure: IOException) = callback.onFailure(this, failure)
    }
}
