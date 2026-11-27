package io.github.nodyssey.data.mail

import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.HttpBody
import io.github.plaza.core.net.httpResponse
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MailSendingTest {
    @Test
    fun `sent listings do not mark a received message with the same ID as read`() = runTest {
        val transport = MailTestTransport().apply {
            answer = { request ->
                val row = if ("folder=sent" in request.url) {
                    """{"id":21,"mailboxId":7,"toAddress":"outside@example.org","sentAt":"2026-11-24T12:30:00Z","bodyHtml":"<p>Sent text</p>","status":"sent"}"""
                } else {
                    MAIL_JSON
                }
                httpResponse("""{"data":[$row],"pagination":{"total":1,"limit":20,"offset":0}}""")
            }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(MemoryMailCredentials("fixture"), transport, AppDispatchers(dispatcher, dispatcher))
        repository.initialize()
        repository.inbox(1, null)

        val sent = repository.list(1, 7, 0, MailListFilter(MailSystemFolder.SENT)).messages.single()

        assertEquals("Sent text", sent.bodyText)
        assertEquals("2026-11-24T12:30:00Z", sent.receivedAt)
        assertEquals(false, repository.readStates.value.values[21])
        assertEquals("sender@example.com", repository.inboxUpdates.value?.page?.messages?.single()?.fromAddress)
        assertTrue(transport.requests.all { it.method == "GET" })
    }

    @Test
    fun `sending validates recipients and keeps CC and BCC in separate fields`() = runTest {
        val transport = MailTestTransport().apply { answer = { httpResponse("""{"success":true,"error":null}""") } }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(MemoryMailCredentials("fixture"), transport, AppDispatchers(dispatcher, dispatcher))
        repository.initialize()
        val message = OutgoingMail(
            7,
            listOf("to@example.org", "other@example.org"),
            "Subject",
            "Plain text",
            listOf("cc@example.org"),
            listOf("bcc@example.org"),
        )

        val invalid = message.copy(bcc = listOf("address@example.org injected"))
        assertEquals(MailError.INVALID_INPUT, assertFailsWith<MailException> { repository.send(1, invalid) }.error)
        assertTrue(transport.requests.isEmpty())

        repository.send(1, message)

        val request = transport.requests.single()
        assertEquals("POST", request.method)
        assertEquals("https://seek.li/openapi/v1/send", request.url)
        assertEquals(
            Json.parseToJsonElement(
                """{"mailboxId":7,"to":"to@example.org,other@example.org","cc":"cc@example.org","bcc":"bcc@example.org","subject":"Subject","bodyText":"Plain text"}""",
            ),
            Json.parseToJsonElement((request.body as HttpBody.Text).content),
        )
    }

    @Test
    fun `sending through a website session encodes empty CC and BCC as strings`() = runTest {
        val transport = MailTestTransport().apply { answer = { httpResponse("""{"success":true}""") } }
        val message = OutgoingMail(7, listOf("to@example.org"), "Subject", "Plain text")

        SeekMailClient(transport).send("web:fixture", message)

        val request = transport.requests.single()
        assertEquals("https://seek.li/api/send", request.url)
        assertEquals(
            Json.parseToJsonElement("""{"mailboxId":7,"to":"to@example.org","cc":"","bcc":"","subject":"Subject","bodyText":"Plain text"}"""),
            Json.parseToJsonElement((request.body as HttpBody.Text).content),
        )
    }

    @Test
    fun `a rejected send preserves the service reason without retrying`() = runTest {
        val transport = MailTestTransport().apply {
            answer = { httpResponse("""{"error":{"message":"Sending quota reached"}}""", code = 400) }
        }
        val client = SeekMailClient(transport)
        val message = OutgoingMail(7, listOf("to@example.org"), "Subject", "Plain text")

        val error = assertFailsWith<MailException> { client.send("fixture", message) }

        assertEquals(MailError.INVALID_INPUT, error.error)
        assertEquals(400, error.responseStatus)
        assertEquals("Sending quota reached", error.rejection)
        assertEquals(MailError.INVALID_INPUT.name, error.message)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `a server error during send preserves the explicit delivery failure reason`() = runTest {
        val transport = MailTestTransport().apply {
            answer = {
                httpResponse(
                    """{"error":{"code":"SEND_FAILED","message":"Sending is disabled for this domain"}}""",
                    code = 500,
                )
            }
        }
        val message = OutgoingMail(7, listOf("to@example.org"), "Subject", "Plain text")

        val error = assertFailsWith<MailException> { SeekMailClient(transport).send("fixture", message) }

        assertEquals(MailError.SERVICE, error.error)
        assertEquals(500, error.responseStatus)
        assertEquals("Sending is disabled for this domain", error.rejection)
        assertEquals(1, transport.requests.size)
    }
}
