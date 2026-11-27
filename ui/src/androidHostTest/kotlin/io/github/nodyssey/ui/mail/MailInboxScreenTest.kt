package io.github.nodyssey.ui.mail

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.nodyssey.data.mail.MailAuthMode
import io.github.nodyssey.data.mail.MailMessage
import io.github.nodyssey.data.mail.Mailbox
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MailInboxScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `an unread filter automatically continues past a fully read first page`() {
        val unread = MailMessage(id = 21, mailboxId = 7, subject = "Unread on page two")
        compose.setContent {
            var state by remember {
                mutableStateOf(
                    MailInboxUiState(
                        initializing = false,
                        connected = true,
                        authMode = MailAuthMode.API_KEY,
                        mailboxes = listOf(Mailbox(7, "reader@example.org")),
                        messages = (1L..20L).map { MailMessage(it, 7, isRead = true) },
                        nextOffset = 20,
                        unreadOnly = true,
                    ),
                )
            }
            PlazaTheme {
                MailInboxScreen(
                    state = state,
                    apiKey = rememberTextFieldState(),
                    onBack = {},
                    onConnect = {},
                    onDisconnect = {},
                    onConfirmDisconnect = {},
                    onDismissDisconnect = {},
                    onRefresh = {},
                    onSelectMailbox = {},
                    onLoadMore = { state = state.copy(messages = state.messages + unread, nextOffset = null) },
                    onOpenMail = {},
                    onOpenWebsite = {},
                )
            }
        }

        compose.onNodeWithText(unread.subject.orEmpty()).assertIsDisplayed()
    }
}
