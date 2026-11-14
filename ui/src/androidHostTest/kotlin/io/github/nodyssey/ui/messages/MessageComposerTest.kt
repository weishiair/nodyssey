package io.github.nodyssey.ui.messages

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import io.github.nodyssey.data.composer.ImageAttachment
import io.github.nodyssey.data.composer.UploadStatus
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The message bar's half of the shared editor (7f, redrawn as 3d/3e with the keys in a grid behind +). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class MessageComposerTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var draftState: TextFieldState

    private fun setScreen(draft: String = "") {
        composeRule.setContent {
            draftState = remember { TextFieldState(draft) }
            PlazaTheme {
                MessageThreadScreen(
                    state =
                    MessageThreadUiState(
                        uid = 4471,
                        userName = "iwil",
                        hasDraftText = draft.isNotBlank(),
                        messages =
                        listOf(
                            MessageBubble(
                                id = "1",
                                isMine = false,
                                content = "在的",
                                isMarkdown = true,
                                sentAtMillis = 1_785_000_000_000L,
                                sentAtText = null,
                                status = SendStatus.SENT,
                            ),
                        ),
                    ),
                    draftState = draftState,
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

    /** Clearing focus for the panel must preserve the draft and allow returning to typing. */
    @Test
    fun `the inline emoji picker inserts into the draft and gives way to typing`() {
        setScreen()
        val field = composeRule.onNode(hasSetTextAction())
        field.performTextInput("hello")
        composeRule.onNodeWithContentDescription("表情").performTouchInput { click() }
        composeRule.onNodeWithText("Fluent").performClick()
        composeRule.onNodeWithContentDescription("😀").performClick()

        composeRule.runOnIdle { assertEquals("hello😀", draftState.text.toString()) }

        field.performTouchInput { click() }
        composeRule.onNodeWithContentDescription("😀").assertDoesNotExist()
        field.performTextInput(" again")

        composeRule.runOnIdle { assertEquals("hello😀 again", draftState.text.toString()) }
    }

    @Test
    fun `an upload still in flight holds the send key`() {
        setScreen(draft = "看这个")
        composeRule.waitForIdle()

        // Sending now would send `![](…)` with a URL the upload has not produced yet.
        assertFalse(
            MessageThreadUiState(
                uid = 1,
                userName = "iwil",
                hasDraftText = true,
                attachments = listOf(
                    ImageAttachment("1", "content://a", "a.png", UploadStatus.UPLOADING),
                ),
            ).canSend,
        )
    }
}
