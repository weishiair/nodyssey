package io.github.plaza.core.net

import io.github.nodyssey.data.session.AccountNativeFailure
import io.github.nodyssey.data.session.InvalidAccountCookieSnapshot
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSHTTPCookieDomain
import platform.Foundation.NSHTTPCookieMaximumAge
import platform.Foundation.NSHTTPCookieName
import platform.Foundation.NSHTTPCookieOriginURL
import platform.Foundation.NSHTTPCookiePath
import platform.Foundation.NSHTTPCookieValue
import platform.Foundation.NSHTTPCookieVersion
import platform.Foundation.NSURL
import platform.Foundation.timeIntervalSince1970
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

class AppleAccountCookiesTest {
    // Bug: a legacy self-referencing binary plist is mistaken for an inaccessible account vault.
    @Test
    fun `corrupt binary cookies are classified without losing the native diagnostic cause`() {
        // A synthetic self-referencing array encoded independently with Python plistlib.
        val snapshot = "YnBsaXN0MDChAAgAAAAAAAABAQAAAAAAAAABAAAAAAAAAAAAAAAAAAAACg=="
        val failure = assertFailsWith<InvalidAccountCookieSnapshot> { decodeAccountCookies(snapshot) }
        val native = assertIs<AccountNativeFailure>(failure.cause)
        assertEquals("NSCocoaErrorDomain", native.safeDomain)
        assertEquals(3840L, native.code)
    }

    // Bug: malformed base64 or valid plist values of the wrong shape bypass safe startup recovery.
    @Test
    fun `invalid cookie encoding and structure use the same recoverable classification`() {
        for (snapshot in listOf("not base64!", "bm90IGEgcGxpc3Q=", "YnBsaXN0MDBYbm90LWxpc3QIAAAAAAAAAQEAAAAAAAAAAQAAAAAAAAAAAAAAAAAAABE=")) {
            assertFailsWith<InvalidAccountCookieSnapshot> { decodeAccountCookies(snapshot) }
        }
    }

    // Bug: testing cookie reconstruction alone misses Foundation property-list serialization.
    @Test
    fun `account cookies survive the actual property list round trip`() {
        val cookie = checkNotNull(
            NSHTTPCookie.cookieWithProperties(
                mapOf<Any?, Any?>(
                    NSHTTPCookieName to "session",
                    NSHTTPCookieValue to "test-only-not-a-real-session",
                    NSHTTPCookieDomain to "example.test",
                    NSHTTPCookiePath to "/",
                    NSHTTPCookieVersion to "1",
                    NSHTTPCookieMaximumAge to "120",
                    NSHTTPCookieOriginURL to checkNotNull(NSURL.URLWithString("https://example.test")),
                ),
            ),
        )
        val restored = decodeAccountCookies(encodeAccountCookies(listOf(cookie))).single()
        assertEquals(cookie.name, restored.name)
        assertEquals(cookie.value, restored.value)
        assertEquals(cookie.domain, restored.domain)
        assertEquals(cookie.path, restored.path)
        assertEquals(checkNotNull(cookie.expiresDate).timeIntervalSince1970, checkNotNull(restored.expiresDate).timeIntervalSince1970)
        assertEquals(emptyList(), decodeAccountCookies(encodeAccountCookies(emptyList())))
    }

    // Bug: changing the writer format must not make valid earlier binary snapshots unreadable.
    @Test
    fun `valid binary cookie snapshots remain readable`() {
        // A synthetic fixture encoded independently with Python plistlib, not the Kotlin bridge.
        val snapshot = "YnBsaXN0MDChAdQCAwQFBgcICVZEb21haW5UTmFtZVRQYXRoVVZhbHVlXGV4YW1wbGUudGVzdFdzZXNzaW9uUS9ZdGVzdC1vbmx5CAoTGh8kKjc/QQAAAAAAAAEBAAAAAAAAAAoAAAAAAAAAAAAAAAAAAABL"
        val cookie = decodeAccountCookies(snapshot).single()
        assertEquals("session", cookie.name)
        assertEquals("test-only", cookie.value)
        assertEquals("example.test", cookie.domain)
        assertEquals("/", cookie.path)
    }

    // Bug: restoring Foundation's original Max-Age renews an old session's client-side expiry.
    @Test
    fun `restoring a cookie retains its original absolute expiry`() {
        val cookie = checkNotNull(
            NSHTTPCookie.cookieWithProperties(
                mapOf<Any?, Any?>(
                    NSHTTPCookieName to "session",
                    NSHTTPCookieValue to "test-only",
                    NSHTTPCookieDomain to "example.test",
                    NSHTTPCookiePath to "/",
                    NSHTTPCookieVersion to "1",
                    NSHTTPCookieMaximumAge to "120",
                ),
            ),
        )
        val expiry = checkNotNull(cookie.expiresDate).timeIntervalSince1970
        val properties = checkNotNull(savedCookieProperties(cookie))
        val restored = checkNotNull(NSHTTPCookie.cookieWithProperties(properties))
        assertFalse(properties.containsKey(NSHTTPCookieMaximumAge))
        assertEquals(expiry, checkNotNull(restored.expiresDate).timeIntervalSince1970)
    }
}
