package io.github.nodyssey.core.net

import android.content.Context
import okhttp3.Dns
import okhttp3.Protocol
import okhttp3.internal.OkHttpInternalApi
import okhttp3.internal.ech.EchRetryPlan
import okhttp3.internal.platform.ContextAwarePlatform
import okhttp3.internal.platform.Platform
import okhttp3.internal.tls.CertificateChainCleaner
import okhttp3.internal.tls.TrustRootIndex
import okio.ByteString
import org.conscrypt.Conscrypt
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ServerSocketFactory
import javax.net.SocketFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/** Forwards Android behavior intact and adds ECH only for app-owned Conscrypt sockets. */
@OptIn(OkHttpInternalApi::class)
internal class BundledEchPlatform(private val delegate: Platform) :
    Platform(),
    ContextAwarePlatform {
    override var applicationContext: Context?
        get() = (delegate as? ContextAwarePlatform)?.applicationContext
        set(value) {
            (delegate as? ContextAwarePlatform)?.applicationContext = value
        }

    override val socketFactory: SocketFactory get() = delegate.socketFactory
    override val serverSocketFactory: ServerSocketFactory get() = delegate.serverSocketFactory
    override val systemDns: Dns get() = delegate.systemDns

    override fun newSSLContext(): SSLContext = delegate.newSSLContext()
    override fun platformTrustManager(): X509TrustManager = delegate.platformTrustManager()
    override fun trustManager(sslSocketFactory: SSLSocketFactory): X509TrustManager? = delegate.trustManager(sslSocketFactory)
    override fun newSslSocketFactory(trustManager: X509TrustManager): SSLSocketFactory = BundledEch.socketFactory(trustManager)

    override fun configureTlsExtensions(sslSocket: SSLSocket, hostname: String?, protocols: List<Protocol>, echConfigList: ByteString?) {
        if (Conscrypt.isConscrypt(sslSocket)) {
            Conscrypt.setUseSessionTickets(sslSocket, true)
            Conscrypt.setApplicationProtocols(sslSocket, alpnProtocolNames(protocols).toTypedArray())
            Conscrypt.setEchConfigList(sslSocket, echConfigList?.toByteArray())
        } else {
            delegate.configureTlsExtensions(sslSocket, hostname, protocols, echConfigList)
        }
    }

    override fun getSelectedProtocol(sslSocket: SSLSocket): String? =
        if (Conscrypt.isConscrypt(sslSocket)) Conscrypt.getApplicationProtocol(sslSocket) else delegate.getSelectedProtocol(sslSocket)

    override fun afterHandshake(sslSocket: SSLSocket) = delegate.afterHandshake(sslSocket)
    override fun echRetryPlan(exception: SSLException): EchRetryPlan? = delegate.echRetryPlan(exception)
    override fun getHandshakeServerNames(sslSocket: SSLSocket): List<String> = delegate.getHandshakeServerNames(sslSocket)
    override fun connectSocket(socket: Socket, address: InetSocketAddress, connectTimeout: Int) =
        delegate.connectSocket(socket, address, connectTimeout)

    override fun isCleartextTrafficPermitted(hostname: String): Boolean = delegate.isCleartextTrafficPermitted(hostname)
    override fun getStackTraceForCloseable(closer: String): Any? = delegate.getStackTraceForCloseable(closer)
    override fun logCloseableLeak(message: String, stackTrace: Any?) = delegate.logCloseableLeak(message, stackTrace)
    override fun log(message: String, level: Int, t: Throwable?) = delegate.log(message, level, t)
    override fun buildCertificateChainCleaner(trustManager: X509TrustManager): CertificateChainCleaner =
        delegate.buildCertificateChainCleaner(trustManager)
    override fun buildTrustRootIndex(trustManager: X509TrustManager): TrustRootIndex = delegate.buildTrustRootIndex(trustManager)
}
