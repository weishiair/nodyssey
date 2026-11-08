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
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MailWebSessionTest {
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
