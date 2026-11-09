package io.github.nodyssey.data.mail

import kotlinx.serialization.Serializable

@Serializable
data class Mailbox(
    val id: Long,
    val address: String,
    val alias: String? = null,
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
) {
    val sender: String get() = fromName?.takeIf(String::isNotBlank) ?: fromAddress
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

data class MailArrival(val mailboxId: Long, val count: Int, val generation: Long = 0)

/** Latest first page of the default inbox, shared by foreground polling and the inbox screen. */
data class MailInboxSnapshot(val generation: Long, val mailboxId: Long, val page: MailPage)

data class MailReadStates(
    val generation: Long = 0,
    val values: Map<Long, Boolean> = emptyMap(),
    val revision: Long = 0,
)

enum class MailError {
    INVALID_KEY,
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
class MailException(val error: MailError) : Exception(error.name)
