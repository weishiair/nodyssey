package io.github.nodyssey.data.mail

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.SeekMailSite
import io.github.nodyssey.core.urlEncode
import io.github.plaza.core.net.BoundedDownload
import io.github.plaza.core.net.HttpBody
import io.github.plaza.core.net.HttpRequest
import io.github.plaza.core.net.HttpResponse
import io.github.plaza.core.net.HttpTransport
import io.github.plaza.core.net.SessionCookieStore
import io.github.plaza.core.net.WebUrl
import io.github.plaza.core.net.isChallengeAnswer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

    suspend fun list(key: String, mailboxId: Long?, offset: Int, filter: MailListFilter = MailListFilter()): MailPage {
        require((mailboxId == null || mailboxId > 0) && offset >= 0)
        // The deprecated /mailboxes/{id}/mails ignores its path id. Only mailbox_id isolates inboxes.
        val query = buildList {
            mailboxId?.let { add("mailbox_id=$it") }
            when (val folder = filter.folder) {
                is MailSystemFolder -> add("folder=${folder.queryName}")
                is MailFolder -> add("folder_id=${folder.id}")
            }
            filter.tagId?.let {
                require(it > 0)
                add("tag_id=$it")
            }
            filter.search.trim().takeIf(String::isNotEmpty)?.let { add("search=${it.urlEncode()}") }
            add("limit=$PAGE_SIZE")
            add("offset=$offset")
        }.joinToString("&")
        val body = request(key, "/mails?$query")
        val result = if (key.isWebSession()) {
            val web = decode<WebMails>(body)
            MailsEnvelope(web.mails, MailPagination(web.total, web.limit, web.offset))
        } else {
            decode<MailsEnvelope>(body)
        }
        val paging = result.pagination
        // OpenAPI returns data: null for an empty inbox. A nonzero total must still have rows.
        val messages = result.data ?: if (paging.total == 0) emptyList() else throw MailException(MailError.INVALID_RESPONSE)
        if (paging.offset != offset || paging.total < 0 || messages.any { it.mailboxId <= 0 || (mailboxId != null && it.mailboxId != mailboxId) }) {
            throw MailException(MailError.INVALID_RESPONSE)
        }
        val consumed = offset + messages.size
        return MailPage(
            messages = if (filter.isSent) {
                messages.map { message ->
                    message.copy(
                        isRead = true,
                        receivedAt = message.sentAt ?: message.createdAt ?: message.receivedAt,
                        bodyText = MailBody.text(message),
                        bodyHtml = null,
                    )
                }
            } else {
                messages
            },
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

    suspend fun checkAddress(key: String, address: String): Boolean {
        val path = "/mailboxes/check?address=${address.urlEncode()}"
        val body = request(key, path, "POST", if (key.isWebSession()) HttpBody.Text(json.encodeToString(MailAddress(address))) else HttpBody.Empty)
        val result = if (key.isWebSession()) decode<AddressAvailability>(body) else decode<ApiData<AddressAvailability>>(body).data
        return result?.available ?: throw MailException(MailError.INVALID_RESPONSE)
    }

    suspend fun createMailbox(key: String, address: String): Mailbox {
        val body = request(key, "/mailboxes", "POST", HttpBody.Text(json.encodeToString(MailAddress(address))))
        val result = if (key.isWebSession()) decode<WebCreatedMailbox>(body).mailbox else decode<ApiData<Mailbox>>(body).data
        return result?.takeIf { it.id > 0 && it.address.equals(address, ignoreCase = true) }
            ?: throw MailException(MailError.INVALID_RESPONSE)
    }

    suspend fun attachments(key: String, id: Long): List<MailAttachment> {
        require(id > 0)
        val body = request(key, "/mails/$id/attachments")
        val attachments = if (key.isWebSession()) {
            decode<WebAttachments>(body).attachments
        } else {
            decode<ApiData<List<MailAttachment>>>(body).data
        }
        return attachments.orEmpty().filter { it.index >= 0 && it.size >= 0 }.distinctBy(MailAttachment::index)
    }

    suspend fun downloadAttachment(key: String, id: Long, attachment: MailAttachment): ByteArray {
        require(id > 0 && attachment.index >= 0)
        if (attachment.size > MAX_ATTACHMENT_BYTES) throw MailException(MailError.ATTACHMENT_TOO_LARGE)
        val transport = (if (key.isWebSession()) sessionHttp else http) as? BoundedDownload
            ?: throw MailException(MailError.SERVICE)
        val request = requestFor(key, "/mails/$id/attachments/${attachment.index}", "GET", null, "application/octet-stream")
        val result = transport.download(request, MAX_ATTACHMENT_BYTES)
        if (result.url != request.url) throw MailException(MailError.INVALID_RESPONSE)
        val contentType = result.headers["content-type"].orEmpty()
        val html = contentType.substringBefore(';').trim().equals("text/html", ignoreCase = true)
        val attachmentHeader = result.headers["content-disposition"]?.substringBefore(';')?.trim().equals("attachment", ignoreCase = true)
        val sample = if (result.code !in 200..299 || (html && !attachmentHeader)) {
            result.bytes?.let { it.decodeToString(endIndex = minOf(it.size, 8192)) }.orEmpty()
        } else {
            ""
        }
        checkResponse(HttpResponse(result.code, result.url, result.headers, sample))
        if (html && !attachmentHeader) throw MailException(MailError.INVALID_RESPONSE)
        return result.bytes ?: throw MailException(MailError.ATTACHMENT_TOO_LARGE)
    }

    suspend fun send(key: String, mail: OutgoingMail) {
        // The website and the live OpenAPI expect comma-separated strings for recipient fields.
        val payload = SendMailRequest(
            mailboxId = mail.mailboxId,
            to = mail.to.joinToString(","),
            cc = mail.cc.joinToString(","),
            bcc = mail.bcc.joinToString(","),
            subject = mail.subject,
            bodyText = mail.bodyText,
        )
        val response = request(key, "/send", "POST", HttpBody.Text(json.encodeToString(payload)))
        if (response.isNotBlank()) {
            val confirmation = decode<JsonObject>(response)
            val error = confirmation["error"]
            if ((error != null && error != JsonNull) || confirmation["success"]?.toString() == "false") {
                throw MailException(MailError.SERVICE, rejection = confirmation.rejectionMessage())
            }
        }
    }

    suspend fun tags(key: String): List<MailTag> {
        val body = request(key, "/tags")
        val tags = if (key.isWebSession()) decode<WebTags>(body).tags else decode<ApiData<List<MailTag>>>(body).data
        return tags.orEmpty().filter { it.id > 0 && it.name.isNotBlank() }.distinctBy(MailTag::id)
    }

    suspend fun createTag(key: String, name: String): MailTag {
        val body = request(key, "/tags", "POST", HttpBody.Text(json.encodeToString(MailTagName(name))))
        val tag = if (key.isWebSession()) decode<WebCreatedTag>(body).tag else decode<ApiData<MailTag>>(body).data
        return tag?.takeIf { it.id > 0 && it.name.isNotBlank() } ?: throw MailException(MailError.INVALID_RESPONSE)
    }

    suspend fun setTag(key: String, id: Long, tagId: Long, attach: Boolean) {
        require(id > 0 && tagId > 0)
        if (attach) {
            request(key, "/mails/$id/tags", "POST", HttpBody.Text("{\"tagId\":$tagId}"))
        } else {
            request(key, "/mails/$id/tags/$tagId", "DELETE")
        }
    }

    suspend fun folders(key: String): List<MailFolder> {
        val body = request(key, "/folders")
        val folders = if (key.isWebSession()) {
            decode<WebFolders>(body).folders
        } else {
            decode<ApiData<List<MailFolder>>>(body).data
        }
        return folders.orEmpty().filter { it.id > 0 && it.name.isNotBlank() }.distinctBy(MailFolder::id)
    }

    suspend fun flag(key: String, id: Long, flagged: Boolean) {
        require(id > 0)
        request(key, "/mails/$id/flag", "PUT", HttpBody.Text("{\"flagged\":$flagged}"))
    }

    suspend fun move(key: String, id: Long, folderId: Long?) {
        require(id > 0 && (folderId == null || folderId > 0))
        request(key, "/mails/$id/folder", "PUT", HttpBody.Text("{\"folderId\":$folderId}"))
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
        checkResponse(response, includeSendRejection = path == "/send")
        return response.body
    }

    private suspend fun execute(key: String, path: String, method: String, body: HttpBody?): HttpResponse =
        (if (key.isWebSession()) sessionHttp else http).execute(requestFor(key, path, method, body))

    private fun requestFor(
        key: String,
        path: String,
        method: String,
        body: HttpBody?,
        accept: String = "application/json",
    ): HttpRequest = HttpRequest(
        url = (if (key.isWebSession()) "${SeekMailSite.ORIGIN}/api" else BASE_URL) + path,
        method = method,
        headers = buildMap {
            key.removePrefix("web:").takeIf(String::isNotEmpty)?.let { put("Authorization", "Bearer $it") }
            put("Accept", accept)
            put("Cache-Control", "no-cache, no-store")
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
    )

    private fun checkResponse(response: HttpResponse, includeSendRejection: Boolean = false) {
        if (response.code == 468 || isChallengeAnswer(response.code, response.body, response.header("cf-mitigated"), NodeSeekSite.CONFIG.markers)) {
            throw MailException(MailError.CHALLENGE)
        }
        if (!response.isSuccessful) {
            val reportSendError = includeSendRejection && (response.code in SEND_REJECTION_STATUS || response.code in 500..599)
            throw MailException(
                when (response.code) {
                    400, 409, 422 -> MailError.INVALID_INPUT
                    401 -> MailError.UNAUTHORIZED
                    403 -> MailError.PERMISSION
                    404 -> MailError.NOT_FOUND
                    429 -> MailError.RATE_LIMITED
                    468 -> MailError.CHALLENGE
                    else -> MailError.SERVICE
                },
                responseStatus = response.code.takeIf { reportSendError },
                rejection = if (reportSendError && response.body.length <= 8192) {
                    runCatching { json.parseToJsonElement(response.body) as? JsonObject }.getOrNull()?.rejectionMessage()
                } else {
                    null
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
        const val MAX_ATTACHMENT_BYTES = 32 * 1024 * 1024
        val SEND_REJECTION_STATUS = setOf(400, 403, 409, 422)
        val CSRF_ERRORS = setOf("缺少 CSRF cookie，请重新登录", "缺少 CSRF 请求头", "CSRF token 不匹配")
    }
}

private fun JsonObject.rejectionMessage(): String? = sequenceOf(this["message"], (this["error"] as? JsonObject)?.get("message"), this["error"])
    .filterIsInstance<JsonPrimitive>()
    .filter { it.isString }
    .map { value -> value.content.map { if (it.isISOControl()) ' ' else it }.joinToString("").trim().take(240) }
    .firstOrNull(String::isNotBlank)

@Serializable
private data class MailboxesEnvelope(val data: List<Mailbox>)

@Serializable
private data class ApiData<T>(val data: T?)

@Serializable
private data class MailAddress(val address: String)

@Serializable
private data class SendMailRequest(
    val mailboxId: Long,
    val to: String,
    val cc: String,
    val bcc: String,
    val subject: String,
    val bodyText: String,
)

@Serializable
private data class AddressAvailability(val available: Boolean)

@Serializable
private data class WebCreatedMailbox(val mailbox: Mailbox)

@Serializable
private data class WebAttachments(val attachments: List<MailAttachment>?)

@Serializable
private data class MailTagName(val name: String)

@Serializable
private data class WebTags(val tags: List<MailTag>?)

@Serializable
private data class WebCreatedTag(val tag: MailTag)

@Serializable
private data class WebFolders(val folders: List<MailFolder>?)

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
    val isFlagged: Boolean = false,
    val folderId: Long? = null,
    val tags: List<MailTag>? = null,
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
        isFlagged = payload.isFlagged,
        folderId = payload.folderId,
        tags = payload.tags,
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
