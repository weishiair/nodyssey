package io.github.nodyssey.data.mail

import com.fleeksoft.ksoup.Ksoup
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.BoundedDownload
import io.github.plaza.core.net.DownloadResponse
import io.github.plaza.core.net.HttpRequest
import io.github.plaza.core.net.HttpTransport
import io.github.plaza.core.net.httpResponse
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MailHtmlBodyTest {
    @Test
    fun `detail keeps mail layout and only renders network images after an anonymous fetch`() = runTest {
        val transport = ImageTransport()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(
            credentials = MemoryMailCredentials("web:synthetic"),
            http = transport,
            sessionHttp = MailTestTransport().apply {
                answer = {
                    httpResponse(
                        """
                        {"id":21,"mailboxId":7,"isRead":true,"bodyText":"Plain fallback",
                        "bodyHtml":"<html><head><style>.content { color: red }</style></head><body style='background:#fff'><table class='content'><tr><td><strong>Welcome</strong><img src='//images.example.org/logo.png' srcset='https://tracker.example.org/large.png 2x' onerror='alert(1)' alt='Logo'></td></tr></table><script>alert(1)</script><iframe src='https://tracker.example.org/'></iframe><a href='javascript:alert(1)'>bad</a><a href='https://example.org/help'>help</a></body></html>"}
                        """.trimIndent(),
                    )
                }
            },
            dispatchers = AppDispatchers(dispatcher, dispatcher),
        )
        repository.initialize()
        val generation = repository.connection.value.generation
        val message = repository.detail(generation, 7, 21)
        val body = assertNotNull(message.formattedBody)
        assertEquals("Plain fallback", message.bodyText)
        assertEquals(listOf(IMAGE_URL), body.remoteImages)
        assertTrue(transport.requests.isEmpty())

        val document = body.document(emptyMap(), dark = false)
        val parsed = Ksoup.parse(document)
        assertEquals("Welcome", parsed.select("table.content td strong").text())
        assertEquals("background:#fff", parsed.body().attr("style"))
        assertTrue(parsed.select("style").any { it.data().contains(".content { color: red }") })
        assertTrue(parsed.select("meta[http-equiv=Content-Security-Policy]").attr("content").contains("img-src data:"))
        assertFalse(parsed.selectFirst("img")!!.hasAttr("src"))
        assertTrue(parsed.select("[srcset],[onerror],script,iframe").isEmpty())
        assertEquals("https://example.org/help", parsed.select("a[href]").single().attr("href"))

        val images = repository.loadRemoteImages(generation, body.remoteImages)
        val rendered = Ksoup.parse(body.document(images, dark = false))
        assertEquals("data:image/png;base64,$PNG", rendered.select("img").attr("src"))
        assertEquals(listOf(IMAGE_URL, "https://images.example.org/cdn/logo.png"), transport.requests.map(HttpRequest::url))
        assertTrue(transport.requests.all { it.headers.keys == setOf("Accept", "Cache-Control") })
        assertFalse(body.document(emptyMap(), dark = false).contains("data:image/png"))
    }

    @Test
    fun `redirects to a non web URL and non image responses remain blocked`() = runTest {
        val transport = ImageTransport().apply {
            answer = { url ->
                if (url.endsWith("logo.png")) {
                    DownloadResponse(302, url, mapOf("location" to "file:///etc/passwd"), null)
                } else {
                    DownloadResponse(200, url, mapOf("content-type" to "text/html"), "error page".encodeToByteArray())
                }
            }
        }

        val urls = listOf(IMAGE_URL, "https://images.example.org/invalid.png")
        assertTrue(transport.loadMailImages(urls).isEmpty())
        assertEquals(urls, transport.requests.map(HttpRequest::url))
    }

    private class ImageTransport :
        HttpTransport by MailTestTransport(),
        BoundedDownload {
        val requests = mutableListOf<HttpRequest>()
        var answer: (String) -> DownloadResponse = { url ->
            if (url == IMAGE_URL) {
                DownloadResponse(302, url, mapOf("location" to "/cdn/logo.png"), null)
            } else {
                DownloadResponse(200, url, mapOf("content-type" to "image/png"), Base64.decode(PNG))
            }
        }

        override suspend fun download(request: HttpRequest, maxBytes: Int): DownloadResponse {
            requests += request
            return answer(request.url)
        }
    }

    private companion object {
        const val IMAGE_URL = "https://images.example.org/logo.png"
        const val PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jL0cAAAAASUVORK5CYII="
    }
}
