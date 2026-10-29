package io.github.plaza.core.net

import io.github.plaza.core.AppDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * What the HTML client makes of an answer that is not this site's.
 *
 * The bug these hold: signed out, NodeSeek's `/search` answers **302** to a Google `site:` search
 * instead of a page (measured on the live site 2026-10-29, with the app's own browser headers).
 * The transport follows it, the redirect target does not answer from a network where Google is
 * unreachable, and what the reader got was 连不上 NodeSeek for a failure the site had already
 * explained — 请先登录.
 */
class SiteHtmlClientTest {
    private val dispatchers =
        AppDispatchers(io = Dispatchers.Unconfined, default = Dispatchers.Unconfined)

    /** Two hosts, the way a site that answers on both its apex and `www` is configured. */
    private val config =
        SiteConfig(
            baseUrl = "https://www.forum.example",
            fallbackUserAgent = "test-agent",
            htmlAccept = "text/html",
            sessionCookieNames = listOf("session"),
            sessionUrls = listOf("https://www.forum.example", "https://forum.example"),
            markers =
            PageMarkers(
                usablePage = listOf("id=\"nsk-frame\""),
                loginRequired = listOf("请先登录"),
                rateLimit = listOf("每隔2秒可以操作一次"),
            ),
        )

    private fun client(vararg answers: HttpResponse) = SiteHtmlClient(RecordingTransport(*answers), dispatchers, config)

    @Test
    fun `an answer from another host is the site refusing, not a page`() = runTest {
        val site =
            client(
                httpResponse(
                    body = "<!doctype html><title>site:www.forum.example</title>",
                    url = "https://www.google.com/search?q=site%3Awww.forum.example&q=android",
                ),
            )

        val error = assertFailsWith<SiteException> { site.getHtml("/search?q=android") }.error

        assertEquals(SiteError.LoginRequired, error)
    }

    /** A redirect between the site's own two hosts is still the site answering. */
    @Test
    fun `an answer from the site's other host is still a page`() = runTest {
        val page = "<html><body><div id=\"nsk-frame\"></div></body></html>"
        val site = client(httpResponse(body = page, url = "https://forum.example/post-1"))

        assertEquals(page, site.getHtml("/post-1"))
    }
}
