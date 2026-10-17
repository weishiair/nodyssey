package io.github.nodyssey.core.net

import android.annotation.SuppressLint
import androidx.annotation.Keep
import org.conscrypt.DomainEncryptionMode
import org.conscrypt.NetworkSecurityPolicy
import org.conscrypt.metrics.CertificateTransparencyVerificationReason
import java.net.Socket
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

/**
 * Keeps Android's certificate validation while opting the bundled TLS engine into ECH.
 * Conscrypt 2.7's Android default policy is UNKNOWN, which ignores setEchConfigList(). It asks
 * trust managers for this policy by reflection; keep this class and method through R8. Remove
 * the policy bridge when standalone Conscrypt exposes an explicit, public ECH policy API.
 *
 * Implement X509TrustManager rather than subclassing X509ExtendedTrustManager: Conscrypt's engine
 * socket wraps extended managers in an anonymous delegate that drops the policy method. Android's
 * Conscrypt Platform also finds the socket/engine validation overloads below by reflection.
 */
@Keep
// This is a policy adapter, not a new certificate validator: every check delegates to the
// original manager. Real TLS regression tests reject untrusted and mismatched certificates.
@SuppressLint("CustomX509TrustManager")
internal class EchTrustManager(private val delegate: X509TrustManager) : X509TrustManager {
    fun getNetworkSecurityPolicy(): NetworkSecurityPolicy = EchPolicy

    override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
        delegate.checkClientTrusted(chain, authType)

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) =
        delegate.checkServerTrusted(chain, authType)

    fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket) {
        if (delegate is X509ExtendedTrustManager) {
            delegate.checkClientTrusted(chain, authType, socket)
        } else {
            delegate.checkClientTrusted(chain, authType)
        }
    }

    fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket) {
        if (delegate is X509ExtendedTrustManager) {
            delegate.checkServerTrusted(chain, authType, socket)
        } else {
            delegate.checkServerTrusted(chain, authType)
        }
    }

    fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine) {
        if (delegate is X509ExtendedTrustManager) {
            delegate.checkClientTrusted(chain, authType, engine)
        } else {
            delegate.checkClientTrusted(chain, authType)
        }
    }

    fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine) {
        if (delegate is X509ExtendedTrustManager) {
            delegate.checkServerTrusted(chain, authType, engine)
        } else {
            delegate.checkServerTrusted(chain, authType)
        }
    }

    private object EchPolicy : NetworkSecurityPolicy {
        override fun getDomainEncryptionMode(hostname: String?): DomainEncryptionMode = DomainEncryptionMode.ENABLED

        // Certificate validation is performed by the original Android trust manager, including
        // whatever CT policy it applies. This wrapper does not introduce a second CT validator.
        override fun isCertificateTransparencyVerificationRequired(hostname: String?): Boolean = false

        override fun getCertificateTransparencyVerificationReason(hostname: String?): CertificateTransparencyVerificationReason =
            CertificateTransparencyVerificationReason.UNKNOWN
    }
}
