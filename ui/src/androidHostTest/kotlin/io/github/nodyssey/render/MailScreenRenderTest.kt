package io.github.nodyssey.render

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.nodyssey.data.mail.MailAttachment
import io.github.nodyssey.data.mail.MailAuthMode
import io.github.nodyssey.data.mail.MailMessage
import io.github.nodyssey.data.mail.MailPreview
import io.github.nodyssey.data.mail.MailTag
import io.github.nodyssey.data.mail.Mailbox
import io.github.nodyssey.ui.mail.MailDetailScreen
import io.github.nodyssey.ui.mail.MailDetailUiState
import io.github.nodyssey.ui.mail.MailInboxControls
import io.github.nodyssey.ui.mail.MailInboxScreen
import io.github.nodyssey.ui.mail.MailInboxUiState
import io.github.nodyssey.ui.mail.MailTagControls
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class MailScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @Test
    fun `mail connection`() {
        composeRule.setContent {
            PlazaTheme(pureWhite = true, frostedGlass = true, oneHandMode = false) {
                Inbox(MailInboxUiState(initializing = false))
            }
        }
        composeRule.onRoot().captureRender("mail-connect")
    }

    @Test
    fun `mail inbox`() {
        composeRule.setContent {
            PlazaTheme(pureWhite = true, frostedGlass = true, oneHandMode = false) {
                Inbox(
                    MailInboxUiState(
                        initializing = false,
                        connected = true,
                        authMode = MailAuthMode.API_KEY,
                        mailboxes = listOf(Mailbox(7, "reader@nodeseek.org"), Mailbox(9, "alerts@nodeseek.org")),
                        messages = MAILS,
                        nowMillis = 1794182400000,
                        previews = MAILS.associate { it.id to MailPreview.from(it) },
                    ),
                )
            }
        }
        composeRule.onRoot().captureRender("mail-inbox")
    }

    @Test
    fun `mail light list in pure white`() {
        val examples = listOf(
            OTP.copy(receivedAt = "2026-11-25T14:48:00Z"),
            MAIL.copy(receivedAt = "2026-11-25T10:20:00Z"),
            MAILS.first().copy(receivedAt = "2026-11-24T03:30:00Z", isFlagged = true),
            MAILS[1].copy(receivedAt = "2026-11-23T09:35:00Z"),
            MAILS.last().copy(receivedAt = "2026-11-22T03:30:00Z"),
        )
        composeRule.setContent {
            PlazaTheme(darkTheme = false, pureWhite = true, frostedGlass = true, oneHandMode = false) {
                Inbox(
                    MailInboxUiState(
                        initializing = false,
                        connected = true,
                        authMode = MailAuthMode.API_KEY,
                        mailboxes = listOf(Mailbox(7, "reader@nodeseek.org"), Mailbox(9, "alerts@nodeseek.org")),
                        messages = examples,
                        nowMillis = 1795618800000,
                        previews = examples.associate { it.id to MailPreview.from(it) },
                    ),
                )
            }
        }
        composeRule.onRoot().captureRender("mail-inbox-light-list-pure-white")
    }

    @Test
    fun `mail inbox marking all read`() {
        composeRule.setContent {
            PlazaTheme(pureWhite = true, frostedGlass = true, oneHandMode = false) {
                Inbox(
                    MailInboxUiState(
                        initializing = false,
                        connected = true,
                        authMode = MailAuthMode.API_KEY,
                        mailboxes = listOf(Mailbox(7, "reader@nodeseek.org")),
                        selectedMailboxId = 7,
                        messages = MAILS,
                        markingAllRead = true,
                        nowMillis = 1794182400000,
                        previews = MAILS.associate { it.id to MailPreview.from(it) },
                    ),
                )
            }
        }
        composeRule.onRoot().captureRender("mail-inbox-marking-all-read")
    }

    @Test
    fun `mail empty inbox`() {
        composeRule.setContent {
            PlazaTheme(pureWhite = true, frostedGlass = true, oneHandMode = false) {
                Inbox(
                    MailInboxUiState(
                        initializing = false,
                        connected = true,
                        authMode = MailAuthMode.API_KEY,
                        mailboxes = listOf(Mailbox(7, "reader@nodeseek.org")),
                        selectedMailboxId = 7,
                    ),
                )
            }
        }
        composeRule.onRoot().captureRender("mail-inbox-empty")
    }

    @Test
    fun `disconnecting mail`() {
        composeRule.setContent {
            PlazaTheme(pureWhite = true, frostedGlass = true, oneHandMode = false) {
                Inbox(
                    MailInboxUiState(
                        initializing = false,
                        connected = true,
                        authMode = MailAuthMode.API_KEY,
                        confirmingDisconnect = true,
                    ),
                )
            }
        }
        composeRule.onNode(isDialog()).captureRender("mail-disconnect-confirmation")
    }

    @Test
    fun `mail detail in dark`() {
        composeRule.setContent {
            PlazaTheme(darkTheme = true, pureWhite = true, frostedGlass = true, oneHandMode = false) {
                MailDetailScreen(
                    state = MailDetailUiState(
                        loading = false,
                        message = OTP.copy(isRead = true),
                        body = OTP.bodyText.orEmpty(),
                        verificationCode = MailPreview.from(OTP).verificationCode,
                    ),
                    onBack = {},
                    onRetry = {},
                    onToggleRead = {},
                    onOpenWebsite = {},
                )
            }
        }
        composeRule.onRoot().captureRender("mail-detail-dark")
    }

    @Test
    fun `mail inbox in dark`() {
        val examples = listOf(
            OTP.copy(receivedAt = "2026-11-25T14:48:00Z"),
            MAIL.copy(receivedAt = "2026-11-25T10:20:00Z", tags = listOf(MailTag(1, "账单"), MailTag(2, "重要"))),
            MAILS.first().copy(receivedAt = "2026-11-24T03:30:00Z", isFlagged = true),
            MAILS[1].copy(receivedAt = "2026-11-23T09:35:00Z"),
            MAILS.last().copy(receivedAt = "2026-11-22T03:30:00Z"),
        )
        composeRule.setContent {
            PlazaTheme(darkTheme = true, pureWhite = true, frostedGlass = true, oneHandMode = false) {
                Inbox(
                    MailInboxUiState(
                        initializing = false,
                        connected = true,
                        authMode = MailAuthMode.API_KEY,
                        mailboxes = listOf(Mailbox(7, "reader@nodeseek.org"), Mailbox(9, "alerts@nodeseek.org")),
                        messages = examples,
                        nowMillis = 1795618800000,
                        previews = examples.associate { it.id to MailPreview.from(it) },
                    ),
                )
            }
        }
        composeRule.onRoot().captureRender("mail-inbox-dark")
    }

    @Test
    fun `mail inbox selection mode`() {
        composeRule.setContent {
            PlazaTheme(darkTheme = false, pureWhite = true, frostedGlass = true, oneHandMode = false) {
                Inbox(
                    MailInboxUiState(
                        initializing = false,
                        connected = true,
                        authMode = MailAuthMode.API_KEY,
                        mailboxes = listOf(Mailbox(7, "reader@nodeseek.org"), Mailbox(9, "alerts@nodeseek.org")),
                        messages = MAILS,
                        selectionMode = true,
                        selectedIds = setOf(20, 22),
                        nowMillis = 1794182400000,
                        previews = MAILS.associate { it.id to MailPreview.from(it) },
                    ),
                )
            }
        }
        composeRule.onRoot().captureRender("mail-inbox-selection")
    }

    @Test
    fun `mail detail light with attachments`() {
        val mail = OTP.copy(
            isRead = true,
            hasAttachments = true,
            tags = listOf(MailTag(1, "安全验证")),
        )
        composeRule.setContent {
            PlazaTheme(darkTheme = false, pureWhite = true, frostedGlass = true, oneHandMode = false) {
                MailDetailScreen(
                    state = MailDetailUiState(
                        loading = false,
                        message = mail,
                        body = mail.bodyText.orEmpty(),
                        verificationCode = MailPreview.from(mail).verificationCode,
                        attachments = listOf(
                            MailAttachment(0, "invoice-2026-11.pdf", "application/pdf", 248_320),
                            MailAttachment(1, "network-topology.png", "image/png", 1_420_800),
                        ),
                    ),
                    onBack = {},
                    onRetry = {},
                    onToggleRead = {},
                    onOpenWebsite = {},
                    onSaveAttachment = {},
                )
            }
        }
        composeRule.onRoot().captureRender("mail-detail-attachments-light")
    }

    @androidx.compose.runtime.Composable
    private fun Inbox(state: MailInboxUiState) {
        MailInboxScreen(
            state = state,
            apiKey = TextFieldState(),
            onBack = {},
            onConnect = {},
            onDisconnect = {},
            onConfirmDisconnect = {},
            onDismissDisconnect = {},
            onRefresh = {},
            onSelectMailbox = {},
            onLoadMore = {},
            onOpenMail = {},
            onOpenWebsite = {},
            controls = MailInboxControls(
                changeFilter = {},
                toggleUnread = {},
                loadFolders = {},
                toggleSelection = {},
                toggleSelected = {},
                selectVisible = {},
                markSelectedRead = {},
                moveSelected = {},
                toggleFlag = {},
                checkAlias = {},
                createAlias = {},
                resetAlias = {},
                sendMail = {},
                resetCompose = {},
                tags = MailTagControls(load = {}, create = {}, clearCreated = {}, setSelected = { _, _ -> }),
            ),
        )
    }
}

private val MAIL = MailMessage(
    id = 21,
    mailboxId = 7,
    fromAddress = "team@example.org",
    fromName = "NodeSeek Mail",
    toAddress = "reader@nodeseek.org",
    subject = "Welcome to your inbox",
    bodyText = "Your mailbox is ready. Read messages and switch between mailboxes here.",
    receivedAt = "2026-11-08T10:30:00Z",
)

private val OTP = MAIL.copy(
    id = 22,
    fromName = "Example Security",
    subject = "Your Example Verification Code",
    bodyText = "Hello,\n\nYour Example verification code is:\n\n016845\n\nThis code expires in 5 minutes. If you did not request it, please ignore this email.",
)

private val MAILS = listOf(
    MAIL.copy(id = 20, subject = "What's new: backbone, looking glass, status page and more", isRead = true),
    MAIL.copy(id = 19, subject = "Black Friday countdown: this month's offers", bodyText = "A few updates and offers from our team. See what's new this month.", isRead = true),
    OTP.copy(mailboxId = 9, toAddress = "alerts@nodeseek.org"),
    OTP.copy(id = 23, isRead = true),
    MAIL.copy(id = 24, subject = "[Network Alert] Case #138: network issue", bodyText = "Service incident notification: we are investigating the issue.", isRead = true),
)
