package io.github.nodyssey.data.mail

import io.github.plaza.core.net.HttpRequest
import io.github.plaza.core.net.HttpResponse
import io.github.plaza.core.net.HttpTransport
import io.github.plaza.core.net.UploadProgress
import io.github.plaza.core.net.httpResponse

/** Synthetic service data: no authenticated captures or working credentials. */
class MemoryMailCredentials(var key: String = "") : MailCredentialStore {
    var refuseSave = false
    var defaultMailboxId: Long? = null
    val notificationCursors = mutableMapOf<Long, Long>()

    override suspend fun read() = key

    override suspend fun save(key: String) {
        if (refuseSave) throw MailException(MailError.STORAGE)
        this.key = key
    }

    override suspend fun readDefaultMailboxId() = defaultMailboxId

    override suspend fun saveDefaultMailboxId(id: Long?) {
        defaultMailboxId = id
    }

    override suspend fun clearDefaultMailboxId() {
        defaultMailboxId = null
    }

    override suspend fun readNotificationCursor(mailboxId: Long) = notificationCursors[mailboxId]

    override suspend fun saveNotificationCursor(mailboxId: Long, messageId: Long) {
        notificationCursors[mailboxId] = messageId
    }

    override suspend fun clearNotificationCursors() {
        notificationCursors.clear()
    }

    override suspend fun clear() {
        key = ""
    }
}

class MailTestTransport : HttpTransport {
    val requests = mutableListOf<HttpRequest>()
    var answer: suspend (HttpRequest) -> HttpResponse = { httpResponse(MAILBOXES_JSON) }

    override suspend fun execute(request: HttpRequest, onUploadProgress: UploadProgress?): HttpResponse {
        requests += request
        return answer(request)
    }
}

const val MAILBOXES_JSON = """{"data":[{"id":7,"address":"a@example.org","unknown":"ignored"},{"id":9,"address":"b@example.org"}]}"""
const val MAIL_JSON = """{"id":21,"mailboxId":7,"fromAddress":"sender@example.com","subject":"Hello","isRead":false,"bodyText":"Private words"}"""
