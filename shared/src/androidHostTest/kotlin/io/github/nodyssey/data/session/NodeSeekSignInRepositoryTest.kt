package io.github.nodyssey.data.session

import io.github.nodyssey.core.net.JsonPostResponse
import io.github.nodyssey.core.net.JsonWriteSource
import io.github.nodyssey.core.net.NodeSeekJsonClient
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wire half of 登录 (h1), read off the site's own sign-in page rather than from documentation.
 *
 * These tests are the record of what that reading found: one endpoint for both legs, the credentials
 * carrying Cloudflare's token in a header, and the second leg authenticated by nothing but the
 * `otpSession` the first one handed back. If the site changes any of it, this is what says so.
 */
class NodeSeekSignInRepositoryTest {

    @Test
    fun `a successful password response that redirects to email is not a completed login`() = runTest {
        val repository = NodeSeekSignInRepository(RecordingWrites("""{"success":true,"redirect":"/emailSignIn.html?email=reader%2Btest%40example.com"}"""))
        assertEquals(
            SignInOutcome.EmailRequired(EmailSignInChallenge("reader+test@example.com")),
            repository.signIn(SignInCredentials("reader", "password", "proof")),
        )
    }

    @Test
    fun `a successful second factor can still require email verification`() = runTest {
        val repository = NodeSeekSignInRepository(RecordingWrites("""{"success":true,"redirect":"https://www.nodeseek.com/emailSignIn.html"}"""))
        assertEquals(SignInOutcome.EmailRequired(EmailSignInChallenge()), repository.verifyTwoFactor(TwoFactorChallenge("reader", "session"), "123456"))
    }

    @Test
    fun `a redirect on an unrelated host cannot populate the native email form`() = runTest {
        val repository = NodeSeekSignInRepository(RecordingWrites("""{"success":true,"redirect":"https://evil.test/emailSignIn.html?email=attacker%40example.com"}"""))
        assertEquals(SignInOutcome.Signed, repository.signIn(SignInCredentials("reader", "password", "proof")))
    }

    @Test
    fun `email login requests a totp email code with proof in the body and no password`() = runTest {
        val source = RecordingWrites("""{"success":true}""")
        assertEquals(EmailCodeOutcome.Sent, NodeSeekSignInRepository(source).requestEmailCode("reader@example.com", "proof"))
        val call = source.calls.single()
        assertEquals(NodeSeekJsonClient.PATH_EMAIL_CODE, call.path)
        assertEquals("${io.github.nodyssey.core.NodeSeekSite.BASE_URL}/emailSignIn.html", call.referer)
        val body = Json.parseToJsonElement(call.body).jsonObject
        assertEquals("totp", body.getValue("mode").jsonPrimitive.content)
        assertEquals("reader@example.com", body.getValue("email").jsonPrimitive.content)
        assertEquals("proof", body.getValue("token").jsonPrimitive.content)
        assertEquals("turnstile", body.getValue("source").jsonPrimitive.content)
        assertEquals("v3", body.getValue("version").jsonPrimitive.content)
        assertTrue(call.headers.isEmpty())
        assertTrue("password" !in body)
    }

    @Test
    fun `email verification sends the address and code to the email login endpoint`() = runTest {
        val source = RecordingWrites("""{"success":false,"message":"expired code"}""")
        assertEquals(SignInOutcome.Refused(SignInRefusal.EmailCode, "expired code"), NodeSeekSignInRepository(source).verifyEmail("reader@example.com", "123456"))
        val call = source.calls.single()
        assertEquals(NodeSeekJsonClient.PATH_ACCOUNT_EMAIL_SIGN_IN, call.path)
        val body = Json.parseToJsonElement(call.body).jsonObject
        assertEquals(setOf("email", "code"), body.keys)
        assertEquals("123456", body.getValue("code").jsonPrimitive.content)
    }

    @Test
    fun `the OTP error code returns to credentials even when the human message has no code`() = runTest {
        val repository = NodeSeekSignInRepository(RecordingWrites("""{"success":false,"error":"OTP_EXPIRED_OR_NOT_EXIST","message":"session expired"}"""))
        assertEquals(SignInOutcome.Refused(SignInRefusal.TwoFactorSessionExpired, "session expired"), repository.verifyTwoFactor(TwoFactorChallenge("reader", "session"), "123456"))
    }

    @Test
    fun `an unavailable diagnostic provider does not turn an accepted sign-in into a failure`() = runTest {
        val repository = NodeSeekSignInRepository(
            RecordingWrites("""{"success":true}"""),
            cookieWriteDiagnostics = { error("Platform provider unavailable") },
        )
        assertEquals(SignInOutcome.Signed, repository.signIn(SignInCredentials("reader", "password", null)))
    }

    @Test
    fun `the credentials leg posts the username, the password and the captcha headers`() =
        runTest {
            val source = RecordingWrites("""{"success":true,"redirect":"/"}""")
            val outcome =
                NodeSeekSignInRepository(source).signIn(
                    SignInCredentials(account = "nssk", password = "hunter2", verificationToken = "tk"),
                )

            assertEquals(SignInOutcome.Signed, outcome)
            val call = source.calls.single()
            assertEquals(NodeSeekJsonClient.PATH_ACCOUNT_SIGN_IN, call.path)
            assertEquals("POST", call.method)
            val body = Json.parseToJsonElement(call.body).jsonObject
            assertEquals("nssk", body.getValue("username").jsonPrimitive.content)
            assertEquals("hunter2", body.getValue("password").jsonPrimitive.content)
            assertEquals("tk", call.headers[NodeSeekSignInRepository.HEADER_CAPTCHA_TOKEN])
            assertEquals(
                NodeSeekSignInRepository.TURNSTILE,
                call.headers[NodeSeekSignInRepository.HEADER_CAPTCHA_SOURCE],
            )
        }

    @Test
    fun `no token means no captcha headers rather than an empty one`() =
        runTest {
            val source = RecordingWrites("""{"success":true}""")
            NodeSeekSignInRepository(source).signIn(
                SignInCredentials(account = "nssk", password = "hunter2", verificationToken = null),
            )

            assertTrue(source.calls.single().headers.isEmpty())
        }

    @Test
    fun `a second factor rides on a successful answer and carries the session`() =
        runTest {
            val source = RecordingWrites("""{"success":true,"need2FA":true,"otpSession":"sess-9"}""")
            val outcome =
                NodeSeekSignInRepository(source).signIn(
                    SignInCredentials(account = "nssk", password = "hunter2", verificationToken = "tk"),
                )

            assertEquals(
                SignInOutcome.TwoFactorRequired(TwoFactorChallenge(account = "nssk", otpSession = "sess-9")),
                outcome,
            )
        }

    @Test
    fun `a refusal carries the site's own sentence`() =
        runTest {
            val source = RecordingWrites("""{"success":false,"message":"用户名或密码错误"}""")
            val outcome =
                NodeSeekSignInRepository(source).signIn(
                    SignInCredentials(account = "nssk", password = "wrong", verificationToken = "tk"),
                )

            assertEquals(
                SignInOutcome.Refused(SignInRefusal.Credentials, "用户名或密码错误"),
                outcome,
            )
        }

    @Test
    fun `the second leg posts the code and the session, and nothing else`() =
        runTest {
            val source = RecordingWrites("""{"success":true,"redirect":"/"}""")
            val outcome =
                NodeSeekSignInRepository(source).verifyTwoFactor(
                    TwoFactorChallenge(account = "nssk", otpSession = "sess-9"),
                    "491723",
                )

            assertEquals(SignInOutcome.Signed, outcome)
            val call = source.calls.single()
            val body = Json.parseToJsonElement(call.body).jsonObject
            assertEquals("491723", body.getValue("otp_code").jsonPrimitive.content)
            assertEquals("sess-9", body.getValue("otp_session").jsonPrimitive.content)
            assertNull("the credentials are not sent again", body["password"])
            assertTrue("the token was spent on the first leg", call.headers.isEmpty())
        }

    @Test
    fun `a dead otp session is told apart from a wrong code`() =
        runTest {
            val dead = RecordingWrites("""{"success":false,"message":"OTP_EXPIRED_OR_NOT_EXIST"}""")
            val wrong = RecordingWrites("""{"success":false,"message":"验证码错误"}""")
            val challenge = TwoFactorChallenge(account = "nssk", otpSession = "sess-9")

            assertEquals(
                SignInRefusal.TwoFactorSessionExpired,
                (NodeSeekSignInRepository(dead).verifyTwoFactor(challenge, "000000") as SignInOutcome.Refused).reason,
            )
            assertEquals(
                SignInRefusal.TwoFactorCode,
                (NodeSeekSignInRepository(wrong).verifyTwoFactor(challenge, "000000") as SignInOutcome.Refused).reason,
            )
        }

    @Test
    fun `an answer that is not JSON is not the forum talking`() =
        runTest {
            val source = RecordingWrites("<!doctype html><title>Just a moment…</title>")
            val thrown =
                runCatching {
                    NodeSeekSignInRepository(source).signIn(
                        SignInCredentials(account = "nssk", password = "hunter2", verificationToken = "tk"),
                    )
                }.exceptionOrNull()

            assertEquals(SiteError.Unparsable, (thrown as? SiteException)?.error)
        }
}

private class RecordingWrites(private val answer: String, private val code: Int = 200) : JsonWriteSource {
    data class Call(
        val method: String,
        val path: String,
        val body: String,
        val referer: String,
        val headers: Map<String, String>,
    )

    val calls = mutableListOf<Call>()

    override suspend fun postJson(path: String, body: String, referer: String): String =
        error("登录 must go through sendJson so the status stays readable; got POST $path")

    override suspend fun sendJson(
        method: String,
        path: String,
        body: String,
        referer: String,
        extraHeaders: Map<String, String>,
    ): JsonPostResponse {
        calls += Call(method, path, body, referer, extraHeaders)
        return JsonPostResponse(code = code, body = answer)
    }
}
