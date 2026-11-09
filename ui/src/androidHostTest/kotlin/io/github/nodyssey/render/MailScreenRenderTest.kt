package io.github.nodyssey.render

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.nodyssey.data.mail.MailAuthMode
import io.github.nodyssey.data.mail.MailMessage
import io.github.nodyssey.data.mail.MailPreview
import io.github.nodyssey.data.mail.Mailbox
import io.github.nodyssey.ui.mail.MailDetailScreen
import io.github.nodyssey.ui.mail.MailDetailUiState
import io.github.nodyssey.ui.mail.MailInboxScreen
import io.github.nodyssey.ui.mail.MailInboxUiState
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
            PlazaTheme {
                Inbox(MailInboxUiState(initializing = false))
            }
        }
        composeRule.onRoot().captureRender("mail-connect")
    }

    @Test
    fun `mail inbox`() {
        composeRule.setContent {
            PlazaTheme {
                Inbox(
                    MailInboxUiState(
                        initializing = false,
                        connected = true,
                        authMode = MailAuthMode.API_KEY,
                        mailboxes = listOf(Mailbox(7, "reader@nodeseek.org")),
                        selectedMailboxId = 7,
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
    fun `mail inbox marking all read`() {
        composeRule.setContent {
            PlazaTheme {
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
            PlazaTheme {
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
    fun `mail detail in dark`() {
        composeRule.setContent {
            PlazaTheme(darkTheme = true) {
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
    OTP,
    OTP.copy(id = 23, isRead = true),
    MAIL.copy(id = 24, subject = "[Network Alert] Case #138: network issue", bodyText = "Service incident notification: we are investigating the issue.", isRead = true),
)
