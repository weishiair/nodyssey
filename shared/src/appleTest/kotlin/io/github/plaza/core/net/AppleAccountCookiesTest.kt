package io.github.plaza.core.net

import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSHTTPCookieDomain
import platform.Foundation.NSHTTPCookieMaximumAge
import platform.Foundation.NSHTTPCookieName
import platform.Foundation.NSHTTPCookiePath
import platform.Foundation.NSHTTPCookieValue
import platform.Foundation.NSHTTPCookieVersion
import platform.Foundation.timeIntervalSince1970
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AppleAccountCookiesTest {
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
