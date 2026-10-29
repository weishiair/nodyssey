package io.github.nodyssey.core.net

import io.github.nodyssey.data.proxy.ProxyConfig
import io.github.nodyssey.data.proxy.ProxyType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.Base64
import java.util.Collections
import java.util.concurrent.Semaphore

/**
 * CONNECT only: Chromium keeps ownership of HTTPS, certificates, cookies and challenges.
 * ProxyController has no credential parameter and Chromium supports no SOCKS5 authentication
 * (net/docs/proxy.md). Remove this adapter if WebView gains authenticated SOCKS5 proxy support.
 * Destination DNS is performed by the upstream proxy, never by the phone.
 */
internal class WebViewProxyTunnel(private val config: ProxyConfig) : Closeable {
    private val listener = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))

    // Each CONNECT needs two blocking copies plus the accept loop. Use IO's elastic view so idle
    // WebViews cannot consume the shared IO pool that the app's own forum requests need.
    private val tunnelDispatcher = Dispatchers.IO.limitedParallelism(65)
    private val scope = CoroutineScope(SupervisorJob() + tunnelDispatcher)
    private val sockets = Collections.synchronizedSet(mutableSetOf<Socket>())
    private val permits = Semaphore(32)
    private var closed = false
    val port: Int get() = listener.localPort

    init {
        scope.launch {
            try {
                while (true) {
                    val client = listener.accept()
                    if (!permits.tryAcquire()) {
                        client.close()
                        continue
                    }
                    if (!track(client)) {
                        permits.release()
                        break
                    }
                    launch {
                        try {
                            serve(client)
                        } catch (_: IOException) {
                            // A failed tunnel stays failed: never retry it as a direct connection.
                        } finally {
                            sockets -= client
                            client.close()
                            permits.release()
                        }
                    }
                }
            } catch (_: IOException) {
                // Closing the listener ends accept, including during a settings change.
            }
        }
    }

    private suspend fun serve(client: Socket) {
        client.soTimeout = TIMEOUT_MILLIS
        val input = client.getInputStream()
        val request = readHeaders(input).lineSequence().first().split(' ')
        val target = request.getOrNull(1)?.let { runCatching { URI("https://$it") }.getOrNull() }
        if (request.firstOrNull() != "CONNECT" || target?.host == null || target.port != 443 ||
            target.rawUserInfo != null || !target.rawPath.isNullOrEmpty() || target.rawQuery != null || target.rawFragment != null
        ) {
            client.getOutputStream().write("HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n".toByteArray())
            return
        }
        val upstream = Socket()
        if (!track(upstream)) return
        try {
            upstream.use {
                upstream.soTimeout = TIMEOUT_MILLIS
                upstream.connect(InetSocketAddress(config.host, config.port), TIMEOUT_MILLIS)
                when (config.type) {
                    ProxyType.SOCKS -> connectSocks(upstream, target.host, target.port)
                    ProxyType.HTTP -> connectHttp(upstream, request[1])
                }
                client.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                // Idle sockets remain bounded; close/cancellation interrupts both blocking copies.
                coroutineScope {
                    launch(tunnelDispatcher) {
                        try {
                            input.copyTo(upstream.getOutputStream())
                        } finally {
                            upstream.close()
                            client.close()
                        }
                    }
                    try {
                        upstream.getInputStream().copyTo(client.getOutputStream())
                    } finally {
                        upstream.close()
                        client.close()
                    }
                }
            }
        } finally {
            sockets -= upstream
        }
    }

    private fun connectSocks(socket: Socket, host: String, port: Int) {
        val input = socket.getInputStream()
        val output = socket.getOutputStream()
        val authenticated = config.username.isNotBlank()
        output.write(byteArrayOf(5, 1, if (authenticated) 2 else 0))
        if (input.byte() != 5 || input.byte() != if (authenticated) 2 else 0) throw IOException("SOCKS method refused")
        if (authenticated) {
            val user = config.username.toByteArray(Charsets.UTF_8)
            val password = config.password.toByteArray(Charsets.UTF_8)
            if (user.size !in 1..255 || password.size !in 1..255) throw IOException("Invalid SOCKS credentials length")
            output.write(byteArrayOf(1, user.size.toByte()) + user + byteArrayOf(password.size.toByte()) + password)
            if (input.byte() != 1 || input.byte() != 0) throw IOException("SOCKS authentication refused")
        }
        val name = host.toByteArray(Charsets.US_ASCII)
        if (name.size !in 1..255) throw IOException("Invalid SOCKS destination")
        output.write(byteArrayOf(5, 1, 0, 3, name.size.toByte()) + name + byteArrayOf((port ushr 8).toByte(), port.toByte()))
        if (input.byte() != 5 || input.byte() != 0 || input.byte() != 0) throw IOException("SOCKS connection refused")
        val addressLength = when (input.byte()) {
            1 -> 4
            3 -> input.byte()
            4 -> 16
            else -> throw IOException("Invalid SOCKS address")
        }
        repeat(addressLength + 2) { input.byte() }
    }

    private fun connectHttp(socket: Socket, authority: String) {
        val auth = if (config.username.isBlank()) {
            ""
        } else {
            val credential = Base64.getEncoder().encodeToString("${config.username}:${config.password}".toByteArray(Charsets.ISO_8859_1))
            "Proxy-Authorization: Basic $credential\r\n"
        }
        socket.getOutputStream().write("CONNECT $authority HTTP/1.1\r\nHost: $authority\r\n${auth}\r\n".toByteArray(Charsets.ISO_8859_1))
        val status = readHeaders(socket.getInputStream()).lineSequence().first().split(' ').getOrNull(1)
        if (status != "200") throw IOException("HTTP proxy refused tunnel")
    }

    override fun close() {
        synchronized(sockets) {
            closed = true
            listener.close()
            scope.cancel()
            sockets.forEach { runCatching { it.close() } }
            sockets.clear()
        }
    }

    private fun track(socket: Socket): Boolean = synchronized(sockets) {
        if (closed) {
            socket.close()
            false
        } else {
            sockets.add(socket)
            true
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 30_000
    }
}

private fun InputStream.byte(): Int = read().also { if (it < 0) throw EOFException() }

/** Do not buffer past the HTTP header: the next byte can already be part of the TLS stream. */
private fun readHeaders(input: InputStream): String {
    val header = StringBuilder()
    repeat(8192) {
        header.append(input.byte().toChar())
        if (header.endsWith("\r\n\r\n")) return header.toString()
    }
    throw IOException("Proxy header too large")
}
