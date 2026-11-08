package io.github.nodyssey.render

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.nodyssey.data.mail.MailMessage
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
                        mailboxes = listOf(Mailbox(7, "reader@nodeseek.org")),
                        selectedMailboxId = 7,
                        messages = listOf(MAIL, MAIL.copy(id = 22, subject = "Your weekly update", isRead = true)),
                    ),
                )
            }
        }
        composeRule.onRoot().captureRender("mail-inbox")
    }

    @Test
    fun `mail detail in dark`() {
        composeRule.setContent {
            PlazaTheme(darkTheme = true) {
                MailDetailScreen(
                    state = MailDetailUiState(
                        loading = false,
                        message = MAIL.copy(isRead = true),
                        body = "Hello,\n\nYour mailbox is ready. Read messages and switch between mailboxes here.\n\nhttps://example.org/welcome\n\nHave a great day!",
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
    receivedAt = "2026-11-08T10:30:00Z",
)
