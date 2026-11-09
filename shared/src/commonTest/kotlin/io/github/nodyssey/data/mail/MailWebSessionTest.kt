package io.github.nodyssey.data.mail

import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.SessionCookieStore
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MailWebSessionTest {
    @Test
    fun `switching from api to web preserves the key and read state until a verified token replaces it`() = runTest {
        val store = MemoryMailCredentials("sk_live_example").apply { defaultMailboxId = 7 }
        val transport = MailTestTransport()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(store, transport, AppDispatchers(dispatcher, dispatcher))
        repository.initialize()
        val before = repository.connection.value
        repository.markRead(before.generation, MailMessage(21, 7), true)
        val readsBefore = repository.readStates.value
        val attempt = repository.beginAuthorization()
        assertEquals(before, repository.connection.value)
        assertEquals(readsBefore, repository.readStates.value)
        assertEquals("sk_live_example", store.key)
        assertEquals(listOf(7L, 9L), repository.mailboxes(before.generation).map { it.id })
        transport.answer = { httpResponse(WEB_MAILBOXES) }
        repository.completeAuthorization(attempt, "example-session")
        assertEquals("web:example-session", store.key)
        assertEquals(MailAuthMode.WEB_SESSION, repository.connection.value.authMode)
        assertTrue(repository.connection.value.generation > before.generation)
        assertNull(repository.defaultMailboxId(repository.connection.value.generation))
        assertFailsWith<CancellationException> { repository.mailboxes(before.generation) }
    }

    @Test
    fun `refused and cancelled web switches keep the current api connection and reject late tokens`() = runTest {
        val store = MemoryMailCredentials("sk_live_example").apply {
            defaultMailboxId = 9
            notificationCursors[9] = 21
        }
        val transport = MailTestTransport().apply { answer = { httpResponse("{}", code = 401) } }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(store, transport, AppDispatchers(dispatcher, dispatcher))
        repository.initialize()
        val before = repository.connection.value
        val attempt = repository.beginAuthorization()
        assertEquals(MailError.UNAUTHORIZED, assertFailsWith<MailException> { repository.completeAuthorization(attempt, "refused-session") }.error)
        assertEquals(before, repository.connection.value)
        assertEquals("sk_live_example", store.key)
        assertEquals(9L, store.defaultMailboxId)
        assertEquals(21L, store.notificationCursors[9])
        val gate = CompletableDeferred<Unit>()
        transport.answer = {
            gate.await()
            httpResponse(WEB_MAILBOXES)
        }
        val pending = async { repository.completeAuthorization(attempt, "late-session") }
        runCurrent()
        repository.cancelAuthorization(attempt)
        gate.complete(Unit)
        assertFailsWith<CancellationException> { pending.await() }
        assertEquals(before, repository.connection.value)
        assertEquals("sk_live_example", store.key)
    }

    @Test
    fun `replacing an api key invalidates an earlier pending web switch`() = runTest {
        val store = MemoryMailCredentials("sk_live_original")
        val transport = MailTestTransport()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(store, transport, AppDispatchers(dispatcher, dispatcher))
        val attempt = repository.beginAuthorization()
        repository.connect("sk_live_replacement")
        assertFailsWith<CancellationException> { repository.completeAuthorization(attempt, "late-session") }
        assertEquals("sk_live_replacement", store.key)
        assertEquals(MailAuthMode.API_KEY, repository.connection.value.authMode)
    }

    @Test
    fun `web session reads website envelopes and only owned mailboxes`() = runTest {
        val transport = MailTestTransport().apply {
            answer = { request ->
                when {
                    request.url.endsWith("/mailboxes") -> httpResponse(WEB_MAILBOXES)
                    request.url.endsWith("/mails/21") -> httpResponse(MAIL_JSON)
                    else -> httpResponse("""{"mails":[$MAIL_JSON],"total":2,"limit":20,"offset":0}""")
                }
            }
        }
        val client = SeekMailClient(transport)
        assertEquals(listOf(7L), client.mailboxes("web:example-session").map { it.id })
        assertEquals(1, client.inbox("web:example-session", 7, 0).nextOffset)
        assertEquals("Hello", client.detail("web:example-session", 7, 21).subject)
        assertTrue(transport.requests.all { it.url.startsWith("https://seek.li/api/") })
        assertEquals("Bearer example-session", transport.requests.first().headers["Authorization"])
    }

    @Test
    fun `web writes renew a refused CSRF cookie once but never retry other permission failures`() = runTest {
        val cookies = MailSessionCookies()
        val transport = MailTestTransport().apply {
            answer = { request ->
                when {
                    request.url.endsWith("/auth/csrf") -> {
                        cookies.header = "csrf_token=fresh%2Btoken"
                        httpResponse("{}")
                    }

                    request.headers["X-CSRF-Token"] == "fresh+token" -> httpResponse("{}")

                    else -> httpResponse("""{"error":"CSRF token 不匹配"}""", code = 403)
                }
            }
        }
        SeekMailClient(transport, cookies).markRead("web:example-session", 21, true)
        assertEquals(listOf("PUT", "GET", "PUT"), transport.requests.map { it.method })
        assertEquals("fresh+token", transport.requests.last().headers["X-CSRF-Token"])
        transport.requests.clear()
        transport.answer = { httpResponse("""{"error":"permission denied"}""", code = 403) }
        assertEquals(
            MailError.PERMISSION,
            assertFailsWith<MailException> {
                SeekMailClient(transport, cookies).markRead("web:example-session", 21, false)
            }.error,
        )
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `cancelled and signed out authorizations cannot save a late website token`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val transport = MailTestTransport().apply {
            answer = {
                gate.await()
                httpResponse(WEB_MAILBOXES)
            }
        }
        val store = MemoryMailCredentials()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(store, transport, AppDispatchers(dispatcher, dispatcher))
        val attempt = repository.beginAuthorization()
        val pending = async { repository.completeAuthorization(attempt, "example-session") }
        runCurrent()
        repository.cancelAuthorization(attempt)
        val next = repository.beginAuthorization()
        gate.complete(Unit)
        assertFailsWith<CancellationException> { pending.await() }
        assertEquals("", store.key)
        repository.disconnect()
        assertFailsWith<CancellationException> { repository.completeAuthorization(next, "example-session") }
        assertFalse(repository.connection.value.connected)
    }

    @Test
    fun `expired website sessions erase credentials and publish a reconnectable signed out state`() = runTest {
        val store = MemoryMailCredentials("web:expired-session")
        val transport = MailTestTransport().apply { answer = { httpResponse("{}", code = 401) } }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(store, transport, AppDispatchers(dispatcher, dispatcher))
        repository.initialize()
        val generation = repository.connection.value.generation
        assertFailsWith<MailException> { repository.inbox(generation, 7) }
        assertEquals("", store.key)
        assertFalse(repository.connection.value.connected)
        assertEquals(MailError.UNAUTHORIZED, repository.connection.value.error)
        assertTrue(repository.connection.value.generation > generation)
    }
}

private const val WEB_MAILBOXES = """{"owned":[{"id":7,"address":"owned@example.org"}],"shared":[{"id":9,"address":"shared@example.org"}]}"""

private class MailSessionCookies : SessionCookieStore {
    var header = "csrf_token=stale-token"
    override fun cookieHeader(url: String) = header
    override fun setCookie(url: String, cookie: String) = Unit
    override suspend fun removeAll() = Unit
    override fun flush() = Unit
}
