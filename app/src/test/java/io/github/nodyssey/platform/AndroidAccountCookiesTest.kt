package io.github.nodyssey.platform

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.nodyssey.core.NodeSeekSite
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AndroidAccountCookiesTest {
    @Before
    fun initializeCookieParser() {
        OkHttp.initialize(ApplicationProvider.getApplicationContext())
    }

    // Bug: WebView exports Domain for host-only cookies too; treating it as a Set-Cookie
    // Domain attribute prevents deletion of the original login and broadens it on replay.
    @Test
    fun `WebView exported host cookies retain their host-only scope`() {
        val saved = checkNotNull(normalizeAccountCookie(URL, "session=original; domain=www.nodeseek.com; path=/; secure; httponly; samesite=lax"))
        val cookie = checkNotNull(Cookie.parse(URL.toHttpUrl(), saved.header))
        assertTrue(cookie.hostOnly)
        assertTrue(cookie.matches(URL.toHttpUrl()))
        assertFalse(cookie.matches("https://child.www.nodeseek.com/".toHttpUrl()))
        assertTrue(cookie.httpOnly)
        assertEquals("lax", cookie.sameSite)
    }

    // Bug: removing every exported Domain also loses a real domain cookie's sibling-host scope.
    @Test
    fun `WebView exported domain cookies keep their leading-dot scope`() {
        val saved = checkNotNull(normalizeAccountCookie(URL, "session=original; domain=.nodeseek.com; path=/; secure; httponly"))
        val cookie = checkNotNull(Cookie.parse(URL.toHttpUrl(), saved.header))
        assertFalse(cookie.hostOnly)
        assertTrue(cookie.matches("https://nodeseek.com/".toHttpUrl()))
        assertTrue(cookie.matches("https://sibling.nodeseek.com/".toHttpUrl()))
        assertFalse(cookie.matches("https://unrelated.test/".toHttpUrl()))
    }

    // Bug: old WebView snapshots widen a host-only login in an isolated attendance jar, while
    // confusing new OkHttp response headers with those exports can narrow a real domain cookie.
    @Test
    fun `legacy exports and background response cookies retain their distinct scopes after restoring`() {
        val legacy = Json.encodeToString(listOf(SavedCookie(URL, "session=old; domain=www.nodeseek.com; path=/; secure; httponly", null)))
        val jar = AndroidAttendanceCookies(legacy, NodeSeekSite.CONFIG)
        assertEquals("session=old", jar.cookieHeader(URL))
        assertNull(jar.cookieHeader("https://child.www.nodeseek.com/"))
        jar.setCookie(URL, "token=renewed; Domain=www.nodeseek.com; Path=/; Secure; HttpOnly")
        val restored = AndroidAttendanceCookies(jar.snapshot(), NodeSeekSite.CONFIG)
        assertEquals("token=renewed", restored.cookieHeader("https://child.www.nodeseek.com/"))
        assertEquals(setOf("session=old", "token=renewed"), restored.cookieHeader(URL)?.split("; ")?.toSet())
    }

    // Bug: an isolated runner replays expired cookies, ignores paths, or sends another account's login.
    @Test
    fun `background jars preserve cookie scope and remain independent`() {
        fun jar(value: String) = AndroidAttendanceCookies(
            Json.encodeToString(
                listOf(
                    SavedCookie("https://www.nodeseek.com", "session=$value; Path=/; Secure; HttpOnly", null),
                    SavedCookie("https://www.nodeseek.com", "api=only-api; Path=/api/; Secure", null),
                    SavedCookie("https://www.nodeseek.com", "old=expired; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT", 0),
                ),
            ),
            NodeSeekSite.CONFIG,
        )
        val alpha = jar("alpha")
        val beta = jar("beta")
        assertEquals("api=only-api; session=alpha", alpha.cookieHeader("https://www.nodeseek.com/api/account"))
        assertEquals("session=alpha", alpha.cookieHeader("https://www.nodeseek.com/"))
        assertNull(alpha.cookieHeader("https://nodeseek.com/"))
        alpha.setCookie("https://www.nodeseek.com/api/account", "session=renewed; Path=/; Secure; HttpOnly")
        val restored = AndroidAttendanceCookies(alpha.snapshot(), NodeSeekSite.CONFIG)
        assertEquals("session=renewed", restored.cookieHeader("https://www.nodeseek.com/"))
        assertEquals("session=beta", beta.cookieHeader("https://www.nodeseek.com/"))
        assertNull(restored.cookieHeader("https://unrelated.test/"))
        alpha.setCookie("https://www.nodeseek.com/api/account", "session=api-alpha; Path=/api/; Secure; HttpOnly; SameSite=Strict")
        assertEquals(listOf("api-alpha", "renewed"), alpha.loadForRequest(URL.toHttpUrl()).filter { it.name == "session" }.map { it.value })
        assertEquals("Strict", AndroidAttendanceCookies(alpha.snapshot(), NodeSeekSite.CONFIG).loadForRequest(URL.toHttpUrl()).first { it.name == "session" }.sameSite)
    }

    // Bug: replaying Max-Age during a switch gives an old session a fresh client-side lifetime.
    @Test
    fun `relative expiry becomes absolute while cookie scope and browser attributes survive`() {
        val now = System.currentTimeMillis()
        val cookie = checkNotNull(
            normalizeAccountCookie(
                URL,
                "session=test-session; Path=/api/; Domain=.nodeseek.com; Max-Age=120; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Secure; HttpOnly; SameSite=Strict",
            ),
        )
        val replayed = checkNotNull(Cookie.parse(URL.toHttpUrl(), cookie.header))
        assertTrue(replayed.expiresAt in (now + 119_000)..(System.currentTimeMillis() + 120_000))
        assertTrue(kotlin.math.abs(checkNotNull(cookie.expiresAtMillis) - replayed.expiresAt) < 1_000)
        assertFalse(cookie.header.contains("Max-Age", ignoreCase = true))
        assertEquals("/api/", replayed.path)
        assertEquals("nodeseek.com", replayed.domain)
        assertFalse(replayed.hostOnly)
        assertTrue(replayed.secure)
        assertTrue(replayed.httpOnly)
        assertTrue(cookie.header.contains("SameSite=Strict"))
    }

    // Bug: inventing a Domain or Expires attribute broadens a host-only session cookie.
    @Test
    fun `session cookies remain host-only and do not acquire an invented expiry`() {
        val cookie = checkNotNull(normalizeAccountCookie(URL, "session=test-session; Path=/; Secure; HttpOnly"))
        val replayed = checkNotNull(Cookie.parse(URL.toHttpUrl(), cookie.header))
        assertNull(cookie.expiresAtMillis)
        assertFalse(replayed.persistent)
        assertTrue(replayed.hostOnly)
    }

    private companion object {
        const val URL = "https://www.nodeseek.com/api/"
    }
}
