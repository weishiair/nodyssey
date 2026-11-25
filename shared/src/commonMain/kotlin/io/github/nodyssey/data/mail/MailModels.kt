package io.github.nodyssey.data.mail

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

fun isMailAddress(value: String): Boolean {
    val separator = value.indexOf('@')
    return separator in 1 until value.lastIndex && separator == value.lastIndexOf('@') &&
        value.length <= 254 && value.none { it.isWhitespace() || it.isISOControl() || it in "<>,;" }
}

@Serializable
data class Mailbox(
    val id: Long,
    val address: String,
    val alias: String? = null,
    val sendDisabled: Boolean = false,
)

@Serializable
data class MailMessage(
    val id: Long,
    val mailboxId: Long,
    val fromAddress: String = "",
    val fromName: String? = null,
    val toAddress: String = "",
    val subject: String? = null,
    val bodyText: String? = null,
    val bodyHtml: String? = null,
    val isRead: Boolean = false,
    val hasAttachments: Boolean = false,
    val receivedAt: String? = null,
    val isFlagged: Boolean = false,
    val folderId: Long? = null,
    val tags: List<MailTag>? = null,
    val sentAt: String? = null,
    val createdAt: String? = null,
    val status: String? = null,
    @Transient val formattedBody: MailHtmlBody? = null,
) {
    val sender: String get() = fromName?.takeIf(String::isNotBlank) ?: fromAddress
}

sealed interface MailFolderScope

enum class MailSystemFolder(internal val queryName: String) : MailFolderScope {
    INBOX("inbox"),
    SPAM("spam"),
    TRASH("trash"),
    SENT("sent"),
}

@Serializable
data class MailFolder(val id: Long, val name: String) : MailFolderScope

@Serializable
data class MailTag(val id: Long, val name: String)

data class MailListFilter(
    val folder: MailFolderScope = MailSystemFolder.INBOX,
    val search: String = "",
    val tagId: Long? = null,
) {
    val isInbox: Boolean get() = folder == MailSystemFolder.INBOX && search.isBlank() && tagId == null
    val isSent: Boolean get() = folder == MailSystemFolder.SENT
}

@Serializable
data class OutgoingMail(
    val mailboxId: Long,
    val to: List<String>,
    val subject: String,
    val bodyText: String,
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
) {
    val isValid: Boolean get() = mailboxId > 0 && to.isNotEmpty() && (to + cc + bcc).all(::isMailAddress) &&
        subject.none(Char::isISOControl) && (subject.isNotBlank() || bodyText.isNotBlank())
}

@Serializable
data class MailDraft(
    val mailboxId: Long? = null,
    val to: String = "",
    val cc: String = "",
    val bcc: String = "",
    val subject: String = "",
    val body: String = "",
) {
    val hasText: Boolean get() = listOf(to, cc, bcc, subject, body).any(String::isNotBlank)
}

@Serializable
data class SavedMailDraft(val id: String, val draft: MailDraft, val savedAt: Long)

@Serializable
data class MailAttachment(
    val index: Int,
    val filename: String,
    val contentType: String? = null,
    val size: Long = 0,
) {
    val suggestedName: String
        get() = filename.substringAfterLast('/').substringAfterLast('\\')
            .filterNot { it.isISOControl() }.trim().take(180).takeUnless { it.isEmpty() || it == "." || it == ".." }
            ?: "attachment"
}

data class MailPage(val messages: List<MailMessage>, val nextOffset: Int?, val total: Int)

/** A new generation invalidates every request and screen belonging to the previous credential. */
data class MailConnection(
    val initialized: Boolean = false,
    val generation: Long = 0,
    val connected: Boolean = false,
    val error: MailError? = null,
    val authMode: MailAuthMode? = null,
)

enum class MailAuthMode { WEB_SESSION, API_KEY }

data class MailArrival(val mailboxId: Long?, val count: Int, val generation: Long = 0)

/** Latest first page of the selected inbox scope; a null mailbox ID includes all mailboxes. */
data class MailInboxSnapshot(val generation: Long, val mailboxId: Long?, val page: MailPage)

data class MailReadStates(
    val generation: Long = 0,
    val values: Map<Long, Boolean> = emptyMap(),
    val revision: Long = 0,
)

enum class MailError {
    INVALID_KEY,
    INVALID_INPUT,
    ATTACHMENT_TOO_LARGE,
    UNAUTHORIZED,
    PERMISSION,
    NOT_FOUND,
    RATE_LIMITED,
    NETWORK,
    SERVICE,
    INVALID_RESPONSE,
    STORAGE,
    CHALLENGE,
    FORUM_SIGN_IN,
    WEB_AUTHORIZATION,
}

/** Never includes response bodies, mail content or credentials in an exception message. */
class MailException(
    val error: MailError,
    val responseStatus: Int? = null,
    val rejection: String? = null,
) : Exception(error.name)
