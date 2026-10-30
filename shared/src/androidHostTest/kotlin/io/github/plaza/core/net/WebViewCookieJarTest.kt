package io.github.plaza.core.net

import android.app.Application
import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WebViewCookieJarTest {
    @Test
    fun `a response is not returned to sign-in until CookieManager finishes writing the session`() {
        val completion = CompletableFuture<(Boolean) -> Unit>()
        val store = WebViewCookieStore(CookieManager.getInstance()) { url, cookie, callback ->
            assertEquals("https://forum.test/api/account/signIn", url)
            assertEquals("session=private-value; path=/; secure; httponly", cookie)
            completion.complete(callback)
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val response = executor.submit {
                WebViewCookieJar(store).saveFromResponse(url, listOf(cookie))
            }
            val callback = completion.get(2, TimeUnit.SECONDS)
            assertFalse("Sign-in must not read the jar while the write is pending", response.isDone)
            assertEquals("session=pending", store.responseWriteDiagnostics(listOf("session")))
            callback(true)
            response.get(2, TimeUnit.SECONDS)
            assertEquals("session=accepted", store.responseWriteDiagnostics(listOf("session")))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `a rejected write is distinguished from a missing response cookie without reporting its value`() {
        val store = WebViewCookieStore(CookieManager.getInstance()) { _, _, callback -> callback(false) }
        WebViewCookieJar(store).saveFromResponse(url, listOf(cookie))
        assertEquals("session=rejected; token=not observed", store.responseWriteDiagnostics(listOf("session", "token")))
    }

    private val url = "https://forum.test/api/account/signIn".toHttpUrl()
    private val cookie = Cookie.Builder().name("session").value("private-value").hostOnlyDomain("forum.test").path("/").secure().httpOnly().build()
}
