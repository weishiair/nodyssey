package io.github.nodyssey.data.mail

import io.github.plaza.core.net.SessionCookieStore
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekMailCookieJarTest {
    @Test
    fun `mail redirect cannot request forum cookies from the browser bridge`() {
        val queried = mutableListOf<String>()
        val store = object : SessionCookieStore {
            override fun cookieHeader(url: String): String {
                queried += url
                return "csrf_token=example"
            }
            override fun setCookie(url: String, cookie: String) = Unit
            override suspend fun removeAll() = Unit
            override fun flush() = Unit
        }
        val jar = SeekMailCookieJar(store)
        assertEquals("example", jar.loadForRequest("https://seek.li/api/mails".toHttpUrl()).single().value)
        assertTrue(jar.loadForRequest("https://www.nodeseek.com/".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("http://seek.li/".toHttpUrl()).isEmpty())
        assertEquals(listOf("https://seek.li/api/mails"), queried)
    }
}
