package io.github.nodyssey.core.net

import android.os.Build
import okhttp3.internal.OkHttpInternalApi
import okhttp3.internal.platform.Platform
import org.conscrypt.Conscrypt
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/** App-owned TLS for Android 8–16; Android 17 keeps its native ECH and retry support. */
@OptIn(OkHttpInternalApi::class)
internal object BundledEch {
    @Synchronized
    fun install(sdk: Int = Build.VERSION.SDK_INT) {
        if (sdk >= 37 || Platform.get() is BundledEchPlatform) return
        // Robolectric may supply an older desktop Conscrypt. Do not load Android JNI there.
        val version = Conscrypt.version() ?: return
        if (version.major() < 2 || (version.major() == 2 && version.minor() < 7) || !Conscrypt.isAvailable()) return

        // OkHttp 5.5 exposes no production registration API for a SocketAdapter. Its bundled
        // Conscrypt adapter accepts but ignores the route's ECH config. Isolate this internal
        // platform hook here and remove it when OkHttp configures Conscrypt 2.7 ECH itself.
        Platform.resetForTests(BundledEchPlatform(Platform.get()))
    }

    fun socketFactory(trustManager: X509TrustManager): SSLSocketFactory =
        SSLContext.getInstance("TLS", Conscrypt.newProvider()).apply {
            init(null, arrayOf(EchTrustManager(trustManager)), null)
        }.socketFactory
}
