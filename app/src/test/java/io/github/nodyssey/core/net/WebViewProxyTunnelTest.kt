package io.github.nodyssey.core.net

import io.github.nodyssey.data.proxy.ProxyConfig
import io.github.nodyssey.data.proxy.ProxyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class WebViewProxyTunnelTest {
    @Test
    fun `SOCKS credentials and unresolved destination reach the proxy before any page bytes`() {
        val received = CompletableFuture<Unit>()
        withProxy(ProxyType.SOCKS, upstream = { socket ->
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            assertEquals(listOf(5, 1, 2), input.bytes(3))
            output.write(byteArrayOf(5, 2))
            assertEquals(1, input.read())
            assertEquals("proxy-user", input.text(input.read()))
            assertEquals("proxy-password", input.text(input.read()))
            output.write(byteArrayOf(1, 0))
            assertEquals(listOf(5, 1, 0, 3), input.bytes(4))
            assertEquals("forum.invalid", input.text(input.read()))
            assertEquals(listOf(1, 187), input.bytes(2))
            // A domain-form bound address must be consumed without eating the tunneled data.
            output.write(byteArrayOf(5, 0, 0, 3, 1, 120, 0, 0))
            output.write("server-data".toByteArray())
            assertEquals("client-data", input.text(11))
            received.complete(Unit)
            assertEquals(-1, input.read())
        }) { tunnel ->
            Socket("127.0.0.1", tunnel.port).use { client ->
                client.soTimeout = 3000
                client.getOutputStream().write(connectRequest.toByteArray())
                val input = client.getInputStream()
                assertTrue(input.headers().startsWith("HTTP/1.1 200"))
                assertEquals("server-data", input.text(11))
                client.getOutputStream().write("client-data".toByteArray())
                received.get(2, TimeUnit.SECONDS)
                // Reconfiguring/disposing the proxy must also terminate existing connections.
                tunnel.close()
                assertEquals(-1, input.read())
            }
        }
    }

    @Test
    fun `SOCKS authentication refusal never acknowledges a tunnel or sends a destination`() {
        withProxy(ProxyType.SOCKS, upstream = { socket ->
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            input.bytes(3)
            output.write(byteArrayOf(5, 2))
            input.read()
            input.bytes(input.read())
            input.bytes(input.read())
            output.write(byteArrayOf(1, 1))
            assertEquals(-1, input.read())
        }) { tunnel ->
            Socket("127.0.0.1", tunnel.port).use { client ->
                client.soTimeout = 3000
                client.getOutputStream().write(connectRequest.toByteArray())
                assertEquals(-1, client.getInputStream().read())
            }
        }
    }

    @Test
    fun `HTTP credentials go only to the upstream CONNECT request`() {
        withProxy(ProxyType.HTTP, upstream = { socket ->
            val input = socket.getInputStream()
            val header = input.headers()
            assertTrue(header.startsWith("CONNECT forum.invalid:443 HTTP/1.1\r\n"))
            val encoded = Base64.getEncoder().encodeToString("proxy-user:proxy-password".toByteArray())
            assertTrue(header.contains("Proxy-Authorization: Basic $encoded\r\n"))
            socket.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\nreply".toByteArray())
            assertEquals("hello", input.text(5))
            socket.getOutputStream().write("ack".toByteArray())
        }) { tunnel ->
            Socket("127.0.0.1", tunnel.port).use { client ->
                client.soTimeout = 3000
                client.getOutputStream().write(connectRequest.toByteArray())
                val input = client.getInputStream()
                val response = input.headers()
                assertFalse(response.contains("Authorization"))
                assertEquals("reply", input.text(5))
                client.getOutputStream().write("hello".toByteArray())
                assertEquals("ack", input.text(3))
            }
        }
    }

    @Test
    fun `plain HTTP requests are rejected before opening an upstream socket`() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            server.soTimeout = 200
            WebViewProxyTunnel(config(ProxyType.SOCKS, server.localPort)).use { tunnel ->
                Socket("127.0.0.1", tunnel.port).use { client ->
                    client.soTimeout = 3000
                    client.getOutputStream().write("GET http://forum.invalid/ HTTP/1.1\r\n\r\n".toByteArray())
                    assertTrue(client.getInputStream().headers().startsWith("HTTP/1.1 403"))
                }
            }
            try {
                server.accept().use { error("Rejected request opened an upstream connection") }
            } catch (_: SocketTimeoutException) {
                // No connection is the expected outcome.
            }
        }
    }

    private fun withProxy(type: ProxyType, upstream: (Socket) -> Unit, client: (WebViewProxyTunnel) -> Unit) {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val executor = Executors.newSingleThreadExecutor()
            try {
                val result = executor.submit {
                    server.accept().use { socket ->
                        socket.soTimeout = 3000
                        upstream(socket)
                    }
                }
                WebViewProxyTunnel(config(type, server.localPort)).use(client)
                result.get(5, TimeUnit.SECONDS)
            } finally {
                executor.shutdownNow()
            }
        }
    }

    private fun config(type: ProxyType, port: Int) = ProxyConfig(
        enabled = true,
        type = type,
        host = "127.0.0.1",
        port = port,
        username = "proxy-user",
        password = "proxy-password",
    )

    private val connectRequest = "CONNECT forum.invalid:443 HTTP/1.1\r\nHost: forum.invalid:443\r\n\r\n"
}

private fun InputStream.bytes(count: Int): List<Int> = List(count) { read().also { value -> check(value >= 0) } }
private fun InputStream.text(count: Int): String = bytes(count).map { it.toChar() }.joinToString("")
private fun InputStream.headers(): String {
    val result = StringBuilder()
    while (!result.endsWith("\r\n\r\n")) result.append(read().also { check(it >= 0) }.toChar())
    return result.toString()
}
