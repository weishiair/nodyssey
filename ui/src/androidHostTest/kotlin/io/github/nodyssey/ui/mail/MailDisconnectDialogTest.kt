package io.github.nodyssey.ui.mail

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.nodyssey.data.mail.MailAuthMode
import io.github.nodyssey.data.mail.Mailbox
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class MailDisconnectDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `only confirmation disconnects the inbox`() {
        var disconnects = 0
        composeRule.setContent {
            var confirming by remember { mutableStateOf(false) }
            PlazaTheme {
                MailInboxScreen(
                    state = MailInboxUiState(
                        initializing = false,
                        connected = true,
                        authMode = MailAuthMode.API_KEY,
                        mailboxes = listOf(Mailbox(7, "reader@example.org")),
                        confirmingDisconnect = confirming,
                    ),
                    apiKey = rememberTextFieldState(),
                    onBack = {},
                    onConnect = {},
                    onDisconnect = { confirming = true },
                    onConfirmDisconnect = {
                        confirming = false
                        disconnects++
                    },
                    onDismissDisconnect = { confirming = false },
                    onRefresh = {},
                    onSelectMailbox = {},
                    onLoadMore = {},
                    onOpenMail = {},
                    onOpenWebsite = {},
                )
            }
        }

        fun openConfirmation() {
            composeRule.onNodeWithContentDescription("邮箱选项").performClick()
            composeRule.onNodeWithText("断开连接").performClick()
        }

        openConfirmation()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNode(isDialog()).assertDoesNotExist()
        assertEquals(0, disconnects)

        openConfirmation()
        composeRule.onNode(hasText("断开连接") and hasClickAction()).performClick()
        assertEquals(1, disconnects)
    }
}
