package io.github.nodyssey.core.net

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.internal.OkHttpInternalApi
import okhttp3.internal.platform.Platform
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.ByteString.Companion.decodeBase64
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.DataInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

@OptIn(OkHttpInternalApi::class)
class BundledEchTest {
    private val original = Platform.get()

    @Before
    fun install() {
        BundledEch.install(sdk = 26)
        check(Platform.get() is BundledEchPlatform)
    }

    @After
    fun restore() {
        Platform.resetForTests(original)
    }

    @Test
    fun `the actual ClientHello hides the target hostname using DNS ECH metadata`() {
        val plain = captureHello(ech = false)
        val encrypted = captureHello(ech = true)
        assertEquals("hidden.example", plain.serverName)
        assertEquals(encrypted.toString(), "example.com", encrypted.serverName)
        assertTrue(encrypted.extensions.contains(0xfe0d))
    }

    @Test
    fun `bundled TLS still rejects an untrusted server certificate`() {
        tlsServer("localhost") { port ->
            val client = client(ech = false)
            assertThrows(SSLHandshakeException::class.java) {
                client.newCall(request("localhost", port)).execute().close()
            }
        }
    }

    @Test
    fun `trusting a certificate still requires its hostname to match`() {
        tlsServer("localhost") { port, certificate ->
            val trust = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
            val client = client(ech = false).newBuilder()
                .sslSocketFactory(BundledEch.socketFactory(trust.trustManager), trust.trustManager)
                .build()
            assertThrows(SSLPeerUnverifiedException::class.java) {
                client.newCall(request("hidden.example", port)).execute().close()
            }
        }
    }

    @Test
    fun `a trusted matching server completes a normal HTTPS request`() {
        tlsServer("localhost") { port, certificate ->
            val trust = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
            val client = client(ech = false).newBuilder()
                .sslSocketFactory(BundledEch.socketFactory(trust.trustManager), trust.trustManager)
                .build()
            client.newCall(request("localhost", port)).execute().use { assertEquals(200, it.code) }
        }
    }

    private fun captureHello(ech: Boolean): Hello {
        val executor = Executors.newSingleThreadExecutor()
        ServerSocket(0, 1, LOOPBACK).use { server ->
            server.soTimeout = 5_000
            val hello = executor.submit<Hello> {
                server.accept().use { socket ->
                    socket.soTimeout = 5_000
                    val input = DataInputStream(socket.getInputStream())
                    assertEquals(22, input.readUnsignedByte())
                    input.readUnsignedShort()
                    parseHello(ByteArray(input.readUnsignedShort()).also(input::readFully))
                }
            }
            try {
                try {
                    client(ech).newCall(request("hidden.example", server.localPort)).execute().close()
                } catch (_: IOException) {
                    // The capture server closes after ClientHello, without doing a TLS handshake.
                }
                return hello.get(10, TimeUnit.SECONDS)
            } finally {
                executor.shutdownNow()
            }
        }
    }

    private fun client(ech: Boolean): OkHttpClient = OkHttpClient.Builder()
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = listOf(LOOPBACK)

            override fun newCall(request: Dns.Request): Dns.Call = object : Dns.Call {
                override val request: Dns.Request = request

                override fun enqueue(callback: Dns.Callback) {
                    val records = buildList {
                        add(Dns.Record.IpAddress(request.hostname, LOOPBACK))
                        if (ech) add(Dns.Record.ServiceMetadata(request.hostname, port = request.port, echConfigList = CONFIG))
                    }
                    callback.onRecords(this, true, records)
                }
                override fun cancel() = Unit
                override fun isCanceled(): Boolean = false
            }
        })
        .retryOnConnectionFailure(false)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    private fun request(host: String, port: Int): Request = Request.Builder().url("https://$host:$port/").build()

    private fun tlsServer(host: String, block: (Int) -> Unit) = tlsServer(host) { port, _ -> block(port) }

    private fun tlsServer(host: String, block: (Int, HeldCertificate) -> Unit) {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName(host).build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val executor = Executors.newSingleThreadExecutor()
        (serverTls.sslContext().serverSocketFactory.createServerSocket(0, 1, LOOPBACK) as SSLServerSocket).use { server ->
            server.soTimeout = 5_000
            val serving = executor.submit {
                try {
                    (server.accept() as SSLSocket).use { socket ->
                        socket.soTimeout = 5_000
                        val reader = socket.inputStream.bufferedReader()
                        while (!reader.readLine().isNullOrEmpty()) {
                            // Consume the request headers before returning the local response.
                        }
                        socket.outputStream.write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        socket.outputStream.flush()
                    }
                } catch (_: IOException) {
                    // Certificate and hostname rejection deliberately close the server's socket.
                }
            }
            try {
                block(server.localPort, certificate)
                serving.get(10, TimeUnit.SECONDS)
            } finally {
                executor.shutdownNow()
            }
        }
    }

    private data class Hello(val serverName: String?, val extensions: Set<Int>)

    private fun parseHello(bytes: ByteArray): Hello {
        val data = ByteBuffer.wrap(bytes)
        assertEquals(1, data.get().toInt())
        data.position(4 + 2 + 32)
        data.position(data.position() + 1 + (data.get(data.position()).toInt() and 0xff))
        val cipherBytes = data.short.toInt() and 0xffff
        data.position(data.position() + cipherBytes)
        data.position(data.position() + 1 + (data.get(data.position()).toInt() and 0xff))
        val end = data.position() + 2 + (data.short.toInt() and 0xffff)
        val extensions = mutableSetOf<Int>()
        var serverName: String? = null
        while (data.position() < end) {
            val type = data.short.toInt() and 0xffff
            val length = data.short.toInt() and 0xffff
            val next = data.position() + length
            extensions += type
            if (type == 0) {
                data.short
                assertEquals(0, data.get().toInt())
                val name = ByteArray(data.short.toInt() and 0xffff).also(data::get)
                serverName = name.toString(Charsets.US_ASCII)
            }
            data.position(next)
        }
        assertNotNull(serverName)
        return Hello(serverName, extensions)
    }

    companion object {
        private val LOOPBACK = InetAddress.getByName("127.0.0.1")

        // Public test configuration from google/conscrypt 2.7.0, Apache-2.0:
        // openjdk/src/test/resources/boringssl-ech-config-list.bin (public name example.com).
        private val CONFIG = checkNotNull(
            "AEL+DQA+3gAgACDDPiXRO6S2zMRHYJKWYmIIfNrHyFX49B24FpKZkMqXfgAIAAEAAQABAAMAC2V4YW1wbGUuY29tAAA=".decodeBase64(),
        )
    }
}
