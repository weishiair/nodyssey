package io.github.nodyssey.data.session

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.JsonWriteSource
import io.github.nodyssey.core.net.NodeSeekJsonClient
import io.github.nodyssey.data.bool
import io.github.nodyssey.data.text
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import io.github.plaza.core.net.WebUrl
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 登录 against NodeSeek's own endpoint, `POST /api/account/signIn`.
 *
 * Both legs are that one path. The first sends `{"username","password"}` with Cloudflare's Turnstile
 * token in `x-captcha-token` (and `x-captcha-source: turnstile` beside it); when the account carries
 * a second factor the answer is `success` *with* `need2FA` and an `otpSession`, and the second leg
 * sends `{"otp_code","otp_session"}` — no credentials, and no verification headers, because the
 * session already stands in for both.
 *
 * There is nothing to store on the way out. A successful call answers `Set-Cookie`, the transport's
 * jar is the same one the sign-in web view and every other request read, and the caller's job is to
 * tell [SessionRepository] to look. A `redirect` to email verification instead continues that
 * challenge, because `success` there confirms only the completed password/OTP leg.
 *
 * @param verificationSource the value of `x-captcha-source`. A parameter with the site's own default
 *   rather than a constant, because it names which vendor issued the token in [SignInCredentials] —
 *   if the widget behind the screen ever stops being Turnstile, this is the half that has to change
 *   with it.
 */
class NodeSeekSignInRepository(
    private val writes: JsonWriteSource,
    private val verificationSource: String = TURNSTILE,
    private val cookieWriteDiagnostics: () -> String? = { null },
) : SignInRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private var lastCookieReport: String? = null

    override fun sessionDiagnostics(): String? = lastCookieReport

    override suspend fun signIn(credentials: SignInCredentials): SignInOutcome {
        val body =
            JsonObject(
                mapOf(
                    "username" to JsonPrimitive(credentials.account),
                    "password" to JsonPrimitive(credentials.password),
                ),
            )
        val root =
            post(
                body = body,
                extraHeaders =
                credentials.verificationToken
                    ?.let { mapOf(HEADER_CAPTCHA_TOKEN to it, HEADER_CAPTCHA_SOURCE to verificationSource) }
                    .orEmpty(),
            )

        if (root.bool("success") != true) {
            return SignInOutcome.Refused(SignInRefusal.Credentials, root.text("message"))
        }
        // `need2FA` rides on a *successful* answer: the credentials were right and the sign-in is
        // half done, which is why the second leg needs nothing but the session it hands back.
        if (root.bool("need2FA") == true) {
            val session = root.text("otpSession") ?: throw SiteException(SiteError.Unparsable)
            return SignInOutcome.TwoFactorRequired(
                TwoFactorChallenge(account = credentials.account, otpSession = session),
            )
        }
        emailChallenge(root.text("redirect"))?.let { return SignInOutcome.EmailRequired(it) }
        return SignInOutcome.Signed
    }

    override suspend fun verifyTwoFactor(challenge: TwoFactorChallenge, code: String): SignInOutcome {
        val root =
            post(
                body =
                JsonObject(
                    mapOf(
                        "otp_code" to JsonPrimitive(code),
                        "otp_session" to JsonPrimitive(challenge.otpSession),
                    ),
                ),
                extraHeaders = emptyMap(),
            )

        if (root.bool("success") == true) {
            return emailChallenge(root.text("redirect"))?.let { SignInOutcome.EmailRequired(it) } ?: SignInOutcome.Signed
        }

        val message = root.text("message")
        // The one machine-readable code this endpoint has. It means the half-finished sign-in aged
        // out, so there is nothing left for card 3 to post and the user starts at the password again
        // — a distinction worth carrying, unlike "wrong code" versus "locked", which the endpoint
        // only ever expresses as a sentence.
        val reason =
            if (root.text("error") == OTP_SESSION_DEAD || message?.contains(OTP_SESSION_DEAD) == true) {
                SignInRefusal.TwoFactorSessionExpired
            } else {
                SignInRefusal.TwoFactorCode
            }
        return SignInOutcome.Refused(reason, message)
    }

    override suspend fun requestEmailCode(email: String, verificationToken: String): EmailCodeOutcome {
        // The site's emailSignIn component uses mode=totp (not register/password), and sends the
        // Turnstile proof in the JSON body. These are different from the password form's headers.
        val root = post(
            body = JsonObject(
                mapOf(
                    "email" to JsonPrimitive(email),
                    "mode" to JsonPrimitive("totp"),
                    "token" to JsonPrimitive(verificationToken),
                    "source" to JsonPrimitive(verificationSource),
                    "version" to JsonPrimitive("v3"),
                ),
            ),
            extraHeaders = emptyMap(),
            path = NodeSeekJsonClient.PATH_EMAIL_CODE,
        )
        return if (root.bool("success") == true) EmailCodeOutcome.Sent else EmailCodeOutcome.Refused(root.text("message"))
    }

    override suspend fun verifyEmail(email: String, code: String): SignInOutcome {
        val root = post(
            body = JsonObject(mapOf("email" to JsonPrimitive(email), "code" to JsonPrimitive(code))),
            extraHeaders = emptyMap(),
            path = NodeSeekJsonClient.PATH_ACCOUNT_EMAIL_SIGN_IN,
        )
        if (root.bool("success") != true) return SignInOutcome.Refused(SignInRefusal.EmailCode, root.text("message"))
        return emailChallenge(root.text("redirect"))?.let { SignInOutcome.EmailRequired(it) } ?: SignInOutcome.Signed
    }

    private fun emailChallenge(redirect: String?): EmailSignInChallenge? {
        if (redirect.isNullOrBlank()) return null
        val absolute = NodeSeekSite.absoluteUrl(redirect) ?: return null
        val url = WebUrl.parse(absolute) ?: return null
        val siteHosts = NodeSeekSite.CONFIG.sessionUrls.mapNotNull { WebUrl.parse(it)?.host }.toSet()
        if (url.scheme != "https" || url.userInfo != null || url.host !in siteHosts || url.port !in listOf(-1, 443) ||
            url.path != NodeSeekSite.EMAIL_SIGN_IN_PATH
        ) {
            return null
        }
        return EmailSignInChallenge(url.queryParameter("email")?.takeIf { it.isNotBlank() })
    }

    /**
     * One call to the endpoint, with the failures that are not refusals turned into [SiteException].
     *
     * The status is read rather than trusted: this endpoint puts its meaning in the body on every
     * status it uses, which is why it goes through `sendJson` — but a body that is not JSON at all is
     * not the site answering, and a caller that let that through would show Cloudflare's HTML in the
     * banner as if the forum had said it.
     */
    private suspend fun post(
        body: JsonObject,
        extraHeaders: Map<String, String>,
        path: String = NodeSeekJsonClient.PATH_ACCOUNT_SIGN_IN,
    ): JsonObject {
        lastCookieReport = null
        val response =
            writes.sendJson(
                method = "POST",
                path = path,
                body = json.encodeToString(JsonObject.serializer(), body),
                referer = NodeSeekSite.BASE_URL + if (path == NodeSeekJsonClient.PATH_ACCOUNT_SIGN_IN) NodeSeekSite.SIGN_IN_PATH else NodeSeekSite.EMAIL_SIGN_IN_PATH,
                extraHeaders = extraHeaders,
            )
        lastCookieReport = buildString {
            appendLine("Login response: HTTP ${response.code}")
            appendLine("Session Set-Cookie: ${response.sessionCookieNames?.joinToString(",")?.ifEmpty { "none" } ?: "not inspected"}")
            runCatchingExceptCancellation { cookieWriteDiagnostics() }.getOrNull()?.let { appendLine("Platform cookie writes: $it") }
        }
        return runCatching { json.parseToJsonElement(response.body) as? JsonObject }
            .getOrNull()
            ?: throw SiteException(SiteError.Unparsable)
    }

    companion object {
        const val HEADER_CAPTCHA_TOKEN = "x-captcha-token"
        const val HEADER_CAPTCHA_SOURCE = "x-captcha-source"

        /** What the site's own form sends in [HEADER_CAPTCHA_SOURCE]. */
        const val TURNSTILE = "turnstile"

        /** The endpoint's word for "that `otpSession` is gone". */
        const val OTP_SESSION_DEAD = "OTP_EXPIRED_OR_NOT_EXIST"
    }
}
