package io.github.nodyssey.ui.messages

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import io.github.nodyssey.data.MessageConversation
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class MessageThreadScrollTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var state by mutableStateOf(
        MessageThreadUiState(
            uid = 4471,
            userName = MessageConversation.SYSTEM_NAME,
            messages = (1..60).map(::message),
        ),
    )

    @Test
    fun `incoming messages preserve the older bubble until the reader asks for the latest`() {
        setContent()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(35)
        composeRule.onNodeWithText("message-25").assertIsDisplayed()

        composeRule.runOnIdle { state = state.copy(messages = state.messages + message(61)) }

        composeRule.onNodeWithText("message-25").assertIsDisplayed()
        composeRule.onNodeWithText("有新消息").performClick()
        composeRule.onNodeWithText("message-61").assertIsDisplayed()
    }

    @Test
    fun `sending explicitly follows the new bubble while reading older messages`() {
        setContent()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(35)
        composeRule.onNodeWithText("message-25").assertIsDisplayed()

        composeRule.runOnIdle {
            state = state.copy(
                messages = state.messages + message(61).copy(isMine = true),
                scrollToLatestRequest = 1,
            )
        }

        composeRule.onNodeWithText("message-61").assertIsDisplayed()
    }

    private fun setContent() {
        composeRule.setContent {
            PlazaTheme {
                MessageThreadScreen(
                    state = state,
                    draftState = TextFieldState(),
                    onBack = {},
                    onSignIn = {},
                    onVerify = {},
                    onOpenBrowser = {},
                    onOpenSpace = {},
                    onRetryLoad = {},
                    onSend = {},
                    onRetrySend = {},
                    onQuote = {},
                    onRemoveQuote = {},
                    onPickImages = {},
                    onRemoveAttachment = {},
                    onRetryAttachment = {},
                    onToolbarChange = {},
                    onToolbarReset = {},
                )
            }
        }
    }

    private fun message(number: Int) = MessageBubble(
        id = number.toString(),
        isMine = false,
        content = "message-$number",
        isMarkdown = false,
        sentAtMillis = null,
        sentAtText = null,
        status = SendStatus.SENT,
    )
}
