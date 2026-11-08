package io.github.nodyssey.data.mail

import io.github.nodyssey.core.html.Fixtures
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.HttpBody
import io.github.plaza.core.net.httpResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MailRepositoryTest {
    @Test
    fun `inbox explicitly filters mailbox and advances by consumed rows on a short page`() = runTest {
        val transport = MailTestTransport().apply {
            answer = { httpResponse("""{"data":[$MAIL_JSON],"pagination":{"total":43,"limit":20,"offset":40}}""") }
        }
        val client = SeekMailClient(transport)
        val page = client.inbox("sk_live_example", mailboxId = 7, offset = 40)
        assertEquals("https://seek.li/openapi/v1/mails?mailbox_id=7&folder=inbox&limit=20&offset=40", transport.requests.single().url)
        assertEquals("Bearer sk_live_example", transport.requests.single().headers["Authorization"])
        assertEquals(41, page.nextOffset)
        assertEquals(21L, page.messages.single().id)
    }

    @Test
    fun `an empty last page stops pagination and a foreign mailbox response is rejected`() = runTest {
        val transport = MailTestTransport()
        val client = SeekMailClient(transport)
        transport.answer = { httpResponse("""{"data":[],"pagination":{"total":43,"limit":20,"offset":40}}""") }
        assertEquals(null, client.inbox("key", 7, 40).nextOffset)
        transport.answer = { httpResponse("""{"data":[$MAIL_JSON],"pagination":{"total":1,"limit":20,"offset":0}}""") }
        assertEquals(MailError.INVALID_RESPONSE, assertFailsWith<MailException> { client.inbox("key", 9, 0) }.error)
        transport.answer = { httpResponse("""{"data":$MAIL_JSON}""") }
        assertEquals(MailError.NOT_FOUND, assertFailsWith<MailException> { client.detail("key", 9, 21) }.error)
    }

    @Test
    fun `refused keys and storage failures never publish a connected account`() = runTest {
        val transport = MailTestTransport()
        val store = MemoryMailCredentials()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(store, transport, AppDispatchers(dispatcher, dispatcher))
        transport.answer = { httpResponse("""{"error":{"message":"private server text"}}""", code = 401) }
        assertEquals(MailError.UNAUTHORIZED, assertFailsWith<MailException> { repository.connect("sk_live_example") }.error)
        assertEquals("", store.key)
        assertFalse(repository.connection.value.connected)
        transport.answer = { httpResponse(MAILBOXES_JSON) }
        store.refuseSave = true
        assertEquals(MailError.STORAGE, assertFailsWith<MailException> { repository.connect("sk_live_example") }.error)
        assertFalse(repository.connection.value.connected)
    }

    @Test
    fun `disconnect invalidates an in flight connection before it can persist the key`() = runTest {
        val response = CompletableDeferred<Unit>()
        val transport = MailTestTransport().apply {
            answer = {
                response.await()
                httpResponse(MAILBOXES_JSON)
            }
        }
        val store = MemoryMailCredentials()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(store, transport, AppDispatchers(dispatcher, dispatcher))
        val connect = async { repository.connect("sk_live_example") }
        runCurrent()
        repository.disconnect()
        response.complete(Unit)
        assertFailsWith<CancellationException> { connect.await() }
        assertEquals("", store.key)
        assertFalse(repository.connection.value.connected)
    }

    @Test
    fun `a late inbox cannot return data after disconnect`() = runTest {
        val response = CompletableDeferred<Unit>()
        val transport = MailTestTransport().apply {
            answer = {
                response.await()
                httpResponse("""{"data":[$MAIL_JSON],"pagination":{"total":1,"limit":20,"offset":0}}""")
            }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(MemoryMailCredentials("sk_live_example"), transport, AppDispatchers(dispatcher, dispatcher))
        repository.initialize()
        val pending = async { repository.inbox(repository.connection.value.generation, 7) }
        runCurrent()
        repository.disconnect()
        response.complete(Unit)
        assertFailsWith<CancellationException> { pending.await() }
    }

    @Test
    fun `read state is optimistic and rolls back when permission is refused`() = runTest {
        val response = CompletableDeferred<Unit>()
        val transport = MailTestTransport().apply {
            answer = {
                response.await()
                httpResponse("{}", code = 403)
            }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(MemoryMailCredentials("sk_live_example"), transport, AppDispatchers(dispatcher, dispatcher))
        repository.initialize()
        val mark = async {
            runCatching { repository.markRead(repository.connection.value.generation, MailMessage(21, 7), true) }
        }
        runCurrent()
        assertEquals(true, repository.readStates.value.values[21])
        assertEquals("PUT", transport.requests.single().method)
        assertEquals(HttpBody.Text("{\"read\":true}"), transport.requests.single().body)
        response.complete(Unit)
        assertEquals(MailError.PERMISSION, (mark.await().exceptionOrNull() as MailException).error)
        assertEquals(emptyMap(), repository.readStates.value.values)
    }

    @Test
    fun `a previous connection's pending read cannot block or roll back the next connection`() = runTest {
        val response = CompletableDeferred<Unit>()
        val transport = MailTestTransport().apply {
            answer = { request ->
                if (request.method == "PUT" && request.headers["Authorization"] == "Bearer sk_live_previous") {
                    response.await()
                    httpResponse("{}", code = 403)
                } else {
                    httpResponse(MAILBOXES_JSON)
                }
            }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(MemoryMailCredentials("sk_live_previous"), transport, AppDispatchers(dispatcher, dispatcher))
        repository.initialize()
        val previousGeneration = repository.connection.value.generation
        val previousWrite = async {
            runCatching { repository.markRead(previousGeneration, MailMessage(21, 7), true) }
        }
        runCurrent()
        repository.disconnect()
        repository.connect("sk_live_next")
        val nextGeneration = repository.connection.value.generation
        repository.markRead(nextGeneration, MailMessage(21, 7), false)
        response.complete(Unit)
        previousWrite.await()
        assertEquals(nextGeneration, repository.readStates.value.generation)
        assertEquals(mapOf(21L to false), repository.readStates.value.values)
    }

    @Test
    fun `refresh observes website read changes but a page already in flight cannot undo a newer write`() = runTest {
        val transport = MailTestTransport()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(MemoryMailCredentials("sk_live_example"), transport, AppDispatchers(dispatcher, dispatcher))
        repository.initialize()
        val generation = repository.connection.value.generation
        repository.markRead(generation, MailMessage(21, 7), true)
        transport.answer = { httpResponse("""{"data":[$MAIL_JSON],"pagination":{"total":1,"limit":20,"offset":0}}""") }
        repository.inbox(generation, 7)
        // A fresh request sees that the website marked it unread.
        assertEquals(false, repository.readStates.value.values[21])
        val pageGate = CompletableDeferred<Unit>()
        transport.answer = { request ->
            if (request.method == "GET") {
                pageGate.await()
                httpResponse("""{"data":[$MAIL_JSON],"pagination":{"total":1,"limit":20,"offset":0}}""")
            } else {
                httpResponse("{}")
            }
        }
        val stalePage = async { repository.inbox(generation, 7) }
        runCurrent()
        repository.markRead(generation, MailMessage(21, 7), true)
        pageGate.complete(Unit)
        stalePage.await()
        assertEquals(true, repository.readStates.value.values[21])
    }

    @Test
    fun `HTML only mail keeps readable paragraphs and links while dropping active and tracking content`() {
        val text = MailBody.text(MailMessage(1, 7, bodyHtml = Fixtures.load("mail/html-only.html")))
        assertTrue(text.contains("Welcome & hello\n"))
        assertTrue(text.contains("First line\nSecond line"))
        assertTrue(text.contains("your account (https://example.com/verify?code=123&source=mail)"))
        assertTrue(text.contains("Code\t123456"))
        assertFalse(text.contains("stealSession"))
        assertFalse(text.contains("tracker.invalid"))
        assertFalse(text.contains("sendToken"))
        assertEquals("Text\nwith lines", MailBody.text(MailMessage(1, 7, bodyText = "Text\nwith lines", bodyHtml = "<p>different</p>")))
    }
}
