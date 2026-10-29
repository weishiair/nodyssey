package io.github.plaza.core.net

import android.os.Handler
import android.os.HandlerThread
import android.webkit.CookieManager
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.InterruptedIOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * [SessionCookieStore] on Android's own `CookieManager`.
 *
 * The reason this is the app's cookie store and not an OkHttp jar of its own: login and Cloudflare
 * challenges can only be completed in a real WebView, so the WebView's store is where those cookies
 * land. Sharing it rather than copying out of it means there is no moment where one side has a
 * cookie the other does not — and it persists across launches for free.
 */
class WebViewCookieStore(
    private val cookieManager: CookieManager = CookieManager.getInstance(),
    private val writeResponseCookie: (String, String, (Boolean) -> Unit) -> Unit = { url, cookie, callback ->
        cookieHandler.post { cookieManager.setCookie(url, cookie) { callback(it) } }
    },
) : SessionCookieStore {
    private val writes = ConcurrentHashMap<String, String>()

    init {
        cookieManager.setAcceptCookie(true)
    }

    override fun cookieHeader(url: String): String? = cookieManager.getCookie(url)

    override fun setCookie(url: String, cookie: String) = cookieManager.setCookie(url, cookie)

    /** Called only by OkHttp's blocking cookie jar, never from the UI thread. */
    internal fun setResponseCookie(url: String, cookie: String) {
        val name = cookie.substringBefore('=')
        val completed = CountDownLatch(1)
        writes[name] = "pending"
        // The callback needs a Looper. OkHttp's worker has none, and posting it to the UI thread
        // would deadlock a caller that is waiting there. Give cookie callbacks their own Looper.
        writeResponseCookie(url, cookie) { accepted ->
            writes[name] = if (accepted) "accepted" else "rejected"
            completed.countDown()
        }
        try {
            if (!completed.await(5, TimeUnit.SECONDS)) writes.replace(name, "pending", "timed out")
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Cookie write interrupted").apply { initCause(exception) }
        }
    }

    /** Only explicitly requested cookie names are reported, never their values or attributes. */
    fun responseWriteDiagnostics(names: List<String>): String =
        names.joinToString("; ") { name -> "$name=${writes[name] ?: "not observed"}" }

    // `removeAllCookies` completes on a callback; passing null used to mean "and never find out
    // when". Sign-out re-reads the store the moment this returns, so returning before the callback
    // let it read the cookies it had just removed and conclude nothing changed.
    override suspend fun removeAll(): Unit =
        suspendCancellableCoroutine { continuation ->
            cookieManager.removeAllCookies { continuation.resume(Unit) }
        }

    override fun flush() = cookieManager.flush()

    private companion object {
        val cookieHandler by lazy { Handler(HandlerThread("session-cookie-writes").apply { start() }.looper) }
    }
}
