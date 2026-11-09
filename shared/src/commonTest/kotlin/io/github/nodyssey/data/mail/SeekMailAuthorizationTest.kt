package io.github.nodyssey.data.mail

import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.HttpBody
import io.github.plaza.core.net.HttpResponse
import io.github.plaza.core.net.SessionCookieStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SeekMailAuthorizationTest {
    @Test
    fun `official exchange preserves signed JSON types and verifies before saving the session`() = runTest {
        val mail = authorizationMail()
        val forum = authorizationForum()
        val store = MemoryMailCredentials()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(store, mail, AppDispatchers(dispatcher, dispatcher), authorizationHttp = forum)
        val attempt = repository.beginAuthorization()
        repository.authorize(attempt)
        assertEquals(listOf(SIGNATURE_URL), forum.requests.map { it.url })
        assertEquals(listOf(PROVIDERS_URL, CALLBACK_URL, "https://seek.li/api/mailboxes"), mail.requests.map { it.url })
        val callback = mail.requests[1]
        assertEquals("POST", callback.method)
        assertEquals(Json.parseToJsonElement(PAYLOAD), Json.parseToJsonElement((callback.body as HttpBody.Text).content))
        assertFalse("Authorization" in callback.headers)
        assertEquals("Bearer synthetic-session", mail.requests.last().headers["Authorization"])
        assertEquals("web:synthetic-session", store.key)
        assertTrue(repository.connection.value.connected)
    }

    @Test
    fun `changed provider targets are refused before granting access on the forum`() = runTest {
        val mail = authorizationMail().apply {
            answer = { response(it.url, PROVIDERS.replace("NodeMailDev", "UntrustedApp")) }
        }
        val forum = authorizationForum()
        val exchange = SeekMailAuthorization(forum, SeekMailClient(mail))
        assertEquals(MailError.WEB_AUTHORIZATION, assertFailsWith<MailException> { exchange.token {} }.error)
        assertTrue(forum.requests.isEmpty())
    }

    @Test
    fun `a signature for another receiver is never sent to the mail callback`() = runTest {
        val forum = authorizationForum().apply {
            answer = { response(it.url, SIGNATURE.replace("https://seek.li", "https://other.example")) }
        }
        val mail = authorizationMail()
        val exchange = SeekMailAuthorization(forum, SeekMailClient(mail))
        assertEquals(MailError.INVALID_RESPONSE, assertFailsWith<MailException> { exchange.token {} }.error)
        assertEquals(listOf(PROVIDERS_URL), mail.requests.map { it.url })
    }

    @Test
    fun `login expiry and security challenges offer distinct recovery and do not exchange a signature`() = runTest {
        for ((code, body, error) in listOf(
            Triple(200, """{"success":false,"message":"not logged in"}""", MailError.FORUM_SIGN_IN),
            Triple(401, "{}", MailError.FORUM_SIGN_IN),
            Triple(468, "<html>verification</html>", MailError.CHALLENGE),
        )) {
            val mail = authorizationMail()
            val forum = authorizationForum().apply { answer = { response(it.url, body, code) } }
            assertEquals(error, assertFailsWith<MailException> { SeekMailAuthorization(forum, SeekMailClient(mail)).token {} }.error)
            assertEquals(1, mail.requests.size)
        }
    }

    @Test
    fun `two factor onboarding and redirected callbacks cannot be stored as completed authorization`() = runTest {
        for ((url, body, code) in listOf(
            Triple(CALLBACK_URL, """{"requires_2fa":true,"pending_token":"pending","token":"must-not-save"}""", 200),
            Triple(CALLBACK_URL, """{"isNewUser":true,"initialPassword":"synthetic","token":"must-not-save"}""", 200),
            Triple("https://other.example/", """{"token":"must-not-save"}""", 200),
            Triple(CALLBACK_URL, "{}", 307),
        )) {
            val mail = authorizationMail().apply {
                val fallback = answer
                answer = { if (it.url == CALLBACK_URL) response(url, body, code) else fallback(it) }
            }
            val dispatcher = StandardTestDispatcher(testScheduler)
            val store = MemoryMailCredentials()
            val repository = DefaultMailRepository(store, mail, AppDispatchers(dispatcher, dispatcher), authorizationHttp = authorizationForum())
            val attempt = repository.beginAuthorization()
            assertEquals(MailError.WEB_AUTHORIZATION, assertFailsWith<MailException> { repository.authorize(attempt) }.error)
            assertEquals("", store.key)
            assertFalse(repository.connection.value.connected)
            assertEquals(2, mail.requests.size)
        }
    }

    @Test
    fun `callback renews CSRF once without granting another forum signature or forwarding a bearer`() = runTest {
        val cookies = AuthorizationCookies()
        var callbacks = 0
        val mail = authorizationMail().apply {
            val fallback = answer
            answer = { request ->
                when (request.url) {
                    CALLBACK_URL -> {
                        callbacks++
                        if (request.headers["X-CSRF-Token"] == "new+csrf") {
                            response(request.url, """{"token":"synthetic-session"}""")
                        } else {
                            response(request.url, """{"error":"CSRF token 不匹配"}""", 403)
                        }
                    }

                    "https://seek.li/api/auth/csrf" -> {
                        cookies.value = "csrf_token=new%2Bcsrf"
                        response(request.url, "{}")
                    }

                    else -> fallback(request)
                }
            }
        }
        val forum = authorizationForum()
        assertEquals("synthetic-session", SeekMailAuthorization(forum, SeekMailClient(mail, cookies)).token {})
        assertEquals(2, callbacks)
        assertEquals(1, forum.requests.size)
        val writes = mail.requests.filter { it.method == "POST" }
        assertEquals(writes[0].body, writes[1].body)
        assertTrue(writes.all { "Authorization" !in it.headers })
        assertEquals("new+csrf", writes[1].headers["X-CSRF-Token"])
        mail.requests.clear()
        mail.answer = { response(it.url, """{"error":"CSRF token 不匹配"}""", if (it.method == "POST") 403 else 200) }
        assertEquals(
            MailError.PERMISSION,
            assertFailsWith<MailException> {
                SeekMailClient(mail, cookies).authorizationRequest("/auth/provider/nodeseek/callback", "POST", HttpBody.Text(PAYLOAD))
            }.error,
        )
        assertEquals(listOf("POST", "GET", "POST"), mail.requests.map { it.method })
    }

    @Test
    fun `cancel while waiting for the forum prevents exchanging its late signature`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val forum = authorizationForum().apply {
            answer = {
                gate.await()
                response(it.url, SIGNATURE)
            }
        }
        val mail = authorizationMail()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = MemoryMailCredentials()
        val repository = DefaultMailRepository(store, mail, AppDispatchers(dispatcher, dispatcher), authorizationHttp = forum)
        val attempt = repository.beginAuthorization()
        val pending = async { repository.authorize(attempt) }
        runCurrent()
        repository.cancelAuthorization(attempt)
        gate.complete(Unit)
        assertFailsWith<CancellationException> { pending.await() }
        assertEquals(listOf(PROVIDERS_URL), mail.requests.map { it.url })
        assertEquals("", store.key)
    }

    @Test
    fun `disconnect during callback cannot revive or verify the old account`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val mail = authorizationMail().apply {
            val fallback = answer
            answer = {
                if (it.url == CALLBACK_URL) gate.await()
                fallback(it)
            }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = MemoryMailCredentials()
        val repository = DefaultMailRepository(store, mail, AppDispatchers(dispatcher, dispatcher), authorizationHttp = authorizationForum())
        val attempt = repository.beginAuthorization()
        val pending = async { repository.authorize(attempt) }
        runCurrent()
        repository.disconnect()
        val next = repository.beginAuthorization()
        gate.complete(Unit)
        assertFailsWith<CancellationException> { pending.await() }
        assertEquals(next, repository.connection.value.generation)
        assertEquals("", store.key)
        assertFalse(mail.requests.any { it.url.endsWith("/mailboxes") })
    }
}

private fun authorizationMail() = MailTestTransport().apply {
    answer = {
        response(
            it.url,
            when (it.url) {
                PROVIDERS_URL -> PROVIDERS
                CALLBACK_URL -> """{"token":"synthetic-session","isNewUser":false}"""
                else -> """{"owned":[{"id":7,"address":"a@example.org"}]}"""
            },
        )
    }
}

private fun authorizationForum() = MailTestTransport().apply { answer = { response(it.url, SIGNATURE) } }

private fun response(url: String, body: String, code: Int = 200) = HttpResponse(code, url, emptyMap(), body)

private class AuthorizationCookies : SessionCookieStore {
    var value = "csrf_token=old-csrf"
    override fun cookieHeader(url: String) = value
    override fun setCookie(url: String, cookie: String) = Unit
    override suspend fun removeAll() = Unit
    override fun flush() = Unit
}

private const val PROVIDERS_URL = "https://seek.li/api/auth/providers"
private const val SIGNATURE_URL = "https://www.nodeseek.com/api/cAuth?target=NodeMailDev"
private const val CALLBACK_URL = "https://seek.li/api/auth/provider/nodeseek/callback"
private const val PROVIDERS = """{"providers":[{"id":"password","enabled":true,"type":"password"},{"id":"nodeseek","enabled":true,"type":"oauth_popup","providerType":"nodeseek","config":{"origin":"https://www.nodeseek.com","popup_url":"https://www.nodeseek.com/connect?target=NodeMailDev","source":"ns-connect","target":"NodeMailDev"}}]}"""
private const val PAYLOAD = """{"data":{"id":123,"username":"synthetic"},"wtf":456,"sign":"synthetic-signature"}"""
private const val SIGNATURE = """{"success":true,"links":["https://seek.li"],"data":{"id":123,"username":"synthetic"},"wtf":456,"sign":"synthetic-signature"}"""
