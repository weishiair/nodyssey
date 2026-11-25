package io.github.nodyssey.data.mail

import io.github.plaza.core.net.BoundedDownload
import io.github.plaza.core.net.DownloadResponse
import io.github.plaza.core.net.HttpRequest
import io.github.plaza.core.net.HttpTransport
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MailAttachmentTest {
    @Test
    fun `binary bytes are preserved and a challenge page is never saved as an attachment`() = runTest {
        val transport = AttachmentTransport()
        val client = SeekMailClient(transport)
        val attachment = MailAttachment(0, "example.bin")
        val bytes = byteArrayOf(0, -1, -128, 1)
        transport.answer = { DownloadResponse(200, it.url, mapOf("content-type" to "application/octet-stream"), bytes) }

        assertContentEquals(bytes, client.downloadAttachment("synthetic", 21, attachment))
        assertEquals("https://seek.li/openapi/v1/mails/21/attachments/0", transport.requests.single().url)
        assertEquals("application/octet-stream", transport.requests.single().headers["Accept"])

        transport.answer = { DownloadResponse(200, it.url, mapOf("content-type" to "text/html"), "<html>blocked</html>".encodeToByteArray()) }
        assertEquals(
            MailError.INVALID_RESPONSE,
            assertFailsWith<MailException> { client.downloadAttachment("synthetic", 21, attachment) }.error,
        )
        transport.answer = { DownloadResponse(200, it.url, emptyMap(), null) }
        assertEquals(
            MailError.ATTACHMENT_TOO_LARGE,
            assertFailsWith<MailException> { client.downloadAttachment("synthetic", 21, attachment) }.error,
        )
    }

    @Test
    fun `address checks encode the full address as one public API query parameter`() = runTest {
        val transport = MailTestTransport().apply {
            answer = { io.github.plaza.core.net.httpResponse("""{"data":{"available":false}}""") }
        }
        val available = SeekMailClient(transport).checkAddress("synthetic", "a+b@example.org")

        assertEquals(false, available)
        val request = transport.requests.single()
        assertEquals("POST", request.method)
        assertEquals("https://seek.li/openapi/v1/mailboxes/check?address=a%2Bb%40example.org", request.url)
        assertEquals(io.github.plaza.core.net.HttpBody.Empty, request.body)
    }

    private class AttachmentTransport :
        HttpTransport by MailTestTransport(),
        BoundedDownload {
        val requests = mutableListOf<HttpRequest>()
        lateinit var answer: (HttpRequest) -> DownloadResponse

        override suspend fun download(request: HttpRequest, maxBytes: Int): DownloadResponse {
            requests += request
            return answer(request)
        }
    }
}
