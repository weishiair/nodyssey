package io.github.nodyssey.data.mail

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.SeekMailSite
import io.github.plaza.core.net.HttpBody
import io.github.plaza.core.net.HttpRequest
import io.github.plaza.core.net.HttpResponse
import io.github.plaza.core.net.HttpTransport
import io.github.plaza.core.net.SessionCookieStore
import io.github.plaza.core.net.WebUrl
import io.github.plaza.core.net.isChallengeAnswer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/** Website session API, with the published OpenAPI retained for manually supplied API keys. */
internal class SeekMailClient(
    private val http: HttpTransport,
    private val cookies: SessionCookieStore? = null,
    private val sessionHttp: HttpTransport = http,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun mailboxes(key: String): List<Mailbox> {
        val body = request(key, "/mailboxes")
        return if (key.isWebSession()) decode<WebMailboxes>(body).owned else decode<MailboxesEnvelope>(body).data
    }

    suspend fun inbox(key: String, mailboxId: Long, offset: Int): MailPage {
        require(mailboxId > 0 && offset >= 0)
        // The deprecated /mailboxes/{id}/mails ignores its path id. Only mailbox_id isolates inboxes.
        val body = request(key, "/mails?mailbox_id=$mailboxId&folder=inbox&limit=$PAGE_SIZE&offset=$offset")
        val result = if (key.isWebSession()) {
            val web = decode<WebMails>(body)
            MailsEnvelope(web.mails, MailPagination(web.total, web.limit, web.offset))
        } else {
            decode<MailsEnvelope>(body)
        }
        val paging = result.pagination
        // OpenAPI returns data: null for an empty inbox. A nonzero total must still have rows.
        val messages = result.data ?: if (paging.total == 0) emptyList() else throw MailException(MailError.INVALID_RESPONSE)
        if (paging.offset != offset || paging.total < 0 || messages.any { it.mailboxId != mailboxId }) {
            throw MailException(MailError.INVALID_RESPONSE)
        }
        val consumed = offset + messages.size
        return MailPage(
            messages = messages,
            nextOffset = consumed.takeIf { messages.isNotEmpty() && it < paging.total },
            total = paging.total,
        )
    }

    suspend fun detail(key: String, mailboxId: Long, id: Long): MailMessage {
        require(mailboxId > 0 && id > 0)
        val body = request(key, "/mails/$id")
        val mail = if (key.isWebSession()) {
            decode<MailMessage>(body)
        } else {
            decode<ApiMailEnvelope>(body).data?.toMailMessage(json)
                ?: throw MailException(MailError.INVALID_RESPONSE)
        }
        if (mail.id != id || mail.mailboxId != mailboxId) throw MailException(MailError.NOT_FOUND)
        return mail
    }

    suspend fun markRead(key: String, id: Long, read: Boolean) {
        request(key, "/mails/$id/read", "PUT", HttpBody.Text("{\"read\":$read}"))
    }

    internal suspend fun authorizationRequest(path: String, method: String = "GET", body: HttpBody? = null): String =
        request("web:", path, method, body, checkUrl = true)

    private suspend fun request(key: String, path: String, method: String = "GET", body: HttpBody? = null, checkUrl: Boolean = false): String {
        var response = execute(key, path, method, body)
        if (checkUrl) checkAuthorizationUrl(response, path)
        // The website renews CSRF once for precisely these three errors. Retrying other permission
        // failures would repeat a write the user is not entitled to make.
        if (key.isWebSession() && method != "GET" && response.code == 403 && isCsrfFailure(response.body)) {
            val csrf = execute(key, "/auth/csrf", "GET", null)
            if (checkUrl) checkAuthorizationUrl(csrf, "/auth/csrf")
            checkResponse(csrf)
            response = execute(key, path, method, body)
            if (checkUrl) checkAuthorizationUrl(response, path)
        }
        checkResponse(response)
        return response.body
    }

    private suspend fun execute(key: String, path: String, method: String, body: HttpBody?): HttpResponse =
        (if (key.isWebSession()) sessionHttp else http).execute(
            HttpRequest(
                url = (if (key.isWebSession()) "${SeekMailSite.ORIGIN}/api" else BASE_URL) + path,
                method = method,
                headers = buildMap {
                    key.removePrefix("web:").takeIf(String::isNotEmpty)?.let { put("Authorization", "Bearer $it") }
                    put("Accept", "application/json")
                    if (key.isWebSession()) {
                        put("Referer", "${SeekMailSite.ORIGIN}/")
                        put("Origin", SeekMailSite.ORIGIN)
                        val raw = cookies?.cookieHeader("${SeekMailSite.ORIGIN}/")
                            ?.split(';')?.map(String::trim)?.firstOrNull { it.startsWith("csrf_token=") }
                            ?.substringAfter('=')
                        raw?.let { WebUrl.parse("${SeekMailSite.ORIGIN}/?csrf=$it")?.queryParameter("csrf") }
                            ?.let { put("X-CSRF-Token", it) }
                    }
                },
                body = body,
            ),
        )

    private fun checkResponse(response: HttpResponse) {
        if (response.code == 468 || isChallengeAnswer(response.body, response.header("cf-mitigated"), NodeSeekSite.CONFIG.markers)) {
            throw MailException(MailError.CHALLENGE)
        }
        if (!response.isSuccessful) {
            throw MailException(
                when (response.code) {
                    401 -> MailError.UNAUTHORIZED
                    403 -> MailError.PERMISSION
                    404 -> MailError.NOT_FOUND
                    429 -> MailError.RATE_LIMITED
                    468 -> MailError.CHALLENGE
                    else -> MailError.SERVICE
                },
            )
        }
    }

    private fun checkAuthorizationUrl(response: HttpResponse, path: String) {
        // The authorization transport must disable redirects before sending signed provider data.
        if (response.url != "${SeekMailSite.ORIGIN}/api$path" || response.code in 300..399) {
            throw MailException(MailError.WEB_AUTHORIZATION)
        }
    }

    private fun isCsrfFailure(body: String): Boolean =
        runCatching { json.decodeFromString<WebError>(body).error in CSRF_ERRORS }.getOrDefault(false)

    private inline fun <reified T> decode(body: String): T =
        try {
            json.decodeFromString<T>(body)
        } catch (_: SerializationException) {
            throw MailException(MailError.INVALID_RESPONSE)
        } catch (_: IllegalArgumentException) {
            throw MailException(MailError.INVALID_RESPONSE)
        }

    private companion object {
        const val BASE_URL = "https://seek.li/openapi/v1"
        const val PAGE_SIZE = 20
        val CSRF_ERRORS = setOf("缺少 CSRF cookie，请重新登录", "缺少 CSRF 请求头", "CSRF token 不匹配")
    }
}

@Serializable
private data class MailboxesEnvelope(val data: List<Mailbox>)

@Serializable
private data class MailsEnvelope(val data: List<MailMessage>?, val pagination: MailPagination)

/** The public API does not mark mail fields as required, and real messages can contain nulls. */
@Serializable
private data class ApiMailEnvelope(val data: JsonObject? = null)

@Serializable
private data class ApiMailPayload(
    val id: Long? = null,
    val mailboxId: Long? = null,
    val fromAddress: String? = null,
    val fromName: String? = null,
    val toAddress: String? = null,
    val subject: String? = null,
    val bodyText: String? = null,
    val bodyHtml: String? = null,
    val isRead: Boolean = false,
    val hasAttachments: Boolean = false,
    val receivedAt: String? = null,
)

private fun JsonObject.toMailMessage(json: Json): MailMessage? {
    val payload = this["mail"]?.let { mail ->
        try {
            json.decodeFromJsonElement<ApiMailPayload>(mail)
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
    } ?: try {
        json.decodeFromJsonElement<ApiMailPayload>(this)
    } catch (_: SerializationException) {
        return null
    } catch (_: IllegalArgumentException) {
        return null
    }
    val id = payload.id ?: return null
    val mailboxId = payload.mailboxId ?: return null
    return MailMessage(
        id = id,
        mailboxId = mailboxId,
        fromAddress = payload.fromAddress.orEmpty(),
        fromName = payload.fromName,
        toAddress = payload.toAddress.orEmpty(),
        subject = payload.subject,
        bodyText = payload.bodyText,
        bodyHtml = payload.bodyHtml,
        isRead = payload.isRead,
        hasAttachments = payload.hasAttachments,
        receivedAt = payload.receivedAt,
    )
}

@Serializable
private data class MailPagination(val total: Int, val limit: Int, val offset: Int)

@Serializable
private data class WebMailboxes(val owned: List<Mailbox>)

@Serializable
private data class WebMails(val mails: List<MailMessage>, val total: Int, val limit: Int, val offset: Int)

@Serializable
private data class WebError(val error: String)

private fun String.isWebSession(): Boolean = startsWith("web:")
