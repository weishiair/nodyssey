package io.github.nodyssey.ui.composer

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_retry
import io.github.plaza.core.net.SiteError
import io.github.plaza.designsys.theme.PlazaTheme
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class ReplyComposerHostTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `scrim tap and swipe down cannot hide sheet while publishing and keep retry visible on failure`() {
        var state by mutableStateOf(
            ReplyComposerUiState(
                postId = 1L,
                visible = true,
                body = "发布中的回复内容",
                isPublishing = true,
            ),
        )
        var dismissed = false
        var retryLabel = ""

        composeRule.setContent {
            PlazaTheme {
                retryLabel = stringResource(Res.string.action_retry)
                ReplyComposerHost(
                    state = state,
                    covered = false,
                    onDismiss = { dismissed = true },
                    bodyState = rememberTextFieldState("发布中的回复内容"),
                    onClearReplyTo = {},
                    onPreviewChange = {},
                    onPickImages = {},
                    onRemoveAttachment = {},
                    onRetryAttachment = {},
                    onRetryFailedUploads = {},
                    onPublish = {},
                    onClearError = {},
                    onSignIn = {},
                    onVerify = {},
                    onToolbarChange = {},
                    onToolbarReset = {},
                    onCreateVote = { _, _, _, _, _ -> },
                    onDismissVoteCreation = {},
                    payeeUid = { null },
                    onInsertReceiveCode = { _, _, _, _ -> },
                )
            }
        }

        // Tapping outside on the scrim and swiping down while publishing must leave the sheet open.
        composeRule.onNode(isDialog()).performTouchInput {
            click(Offset(5f, 5f))
        }
        composeRule.waitForIdle()
        composeRule.onNode(isDialog()).performTouchInput {
            swipeDown(startY = centerY, endY = bottom - 20f)
        }
        composeRule.waitForIdle()

        assertFalse(dismissed)
        composeRule.onNodeWithText("发布中的回复内容").assertIsDisplayed()

        // If publishing fails afterwards, the error banner and retry entry stay visible in the sheet.
        state = state.copy(isPublishing = false, publishError = SiteError.Network)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(retryLabel).assertIsDisplayed()

        // Once settled, tapping the scrim dismisses the sheet normally.
        composeRule.onNode(isDialog()).performTouchInput {
            click(Offset(5f, 5f))
        }
        composeRule.waitForIdle()
        assertTrue(dismissed)
    }
}
