package io.github.plaza.core.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HttpRedirectsTest {
    private val post = HttpRequest(
        url = "https://forum.example/login",
        method = "POST",
        headers = mapOf("Cookie" to "stale", "Authorization" to "private", "Content-Type" to "application/json", "Referer" to "https://forum.example/"),
        body = HttpBody.Text("private", "application/json"),
    )

    @Test fun passwordPostIsNeverReplayedToAnotherOrigin() {
        for (status in listOf(307, 308)) assertNull(redirectRequest(post, "https://other.example/", status))
        assertNull(redirectRequest(post, "http://forum.example/", 302))
        assertNull(redirectRequest(post, "https://user:pass@forum.example/", 302))
    }

    @Test fun browserGetRedirectDropsBodyAndCrossOriginSecrets() {
        for (status in listOf(301, 302, 303)) {
            val next = assertNotNull(redirectRequest(post, "https://other.example/", status))
            assertEquals("GET", next.method)
            assertNull(next.body)
            assertEquals(emptyMap(), next.headers)
        }
    }

    @Test fun sameOriginPreservesPostAndRefreshesCookiesFromJar() {
        val next = assertNotNull(redirectRequest(post, "https://forum.example:443/next", 307))
        assertEquals(post.body, next.body)
        assertEquals("POST", next.method)
        assertEquals("private", next.headers["Authorization"])
        assertFalse(next.headers.keys.any { it.equals("cookie", true) })
    }
}
