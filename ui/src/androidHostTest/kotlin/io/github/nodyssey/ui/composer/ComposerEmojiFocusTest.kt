package io.github.nodyssey.ui.composer

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Focusing an editor field after using the emoji panel closes the panel so the field keeps room for
 * the keyboard rather than squeezing between the panel and the IME.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class ComposerEmojiFocusTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `focusing post body closes the open emoji panel`() {
        composeRule.setContent {
            PlazaTheme {
                PostComposerScreen(
                    state = PostComposerUiState(
                        boardSlug = "tech",
                        boardTitle = "技术",
                        draftDecisionMade = true,
                        isSignedIn = true,
                    ),
                    titleState = rememberTextFieldState(),
                    bodyState = rememberTextFieldState(),
                    snackbarHostState = SnackbarHostState(),
                    onClose = {},
                    onBoardSelect = {},
                    onPermissionSelect = {},
                    onViewModeChange = {},
                    onPickImages = {},
                    onRemoveAttachment = {},
                    onRetryAttachment = {},
                    onPublish = {},
                    onVerify = {},
                    onContinueDraft = {},
                    onDiscardDraft = {},
                    onToolbarChange = {},
                    onToolbarReset = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("表情").performTouchInput { click() }
        composeRule.onNodeWithText("Fluent").performClick()
        composeRule.onNodeWithContentDescription("😀").assertExists()

        val body = composeRule.onNode(hasSetTextAction() and hasText("写下正文", substring = true))
        body.performTouchInput { click() }

        body.assertIsFocused()
        composeRule.onAllNodesWithContentDescription("😀").fetchSemanticsNodes().let { nodes ->
            check(nodes.isEmpty()) { "Expected emoji panel to close once body gained focus" }
        }
    }

    @Test
    fun `focusing reply field closes the open emoji panel`() {
        composeRule.setContent {
            PlazaTheme {
                ReplyComposerHost(
                    state = ReplyComposerUiState(visible = true),
                    covered = false,
                    onDismiss = {},
                    bodyState = rememberTextFieldState(),
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

        composeRule.onNodeWithContentDescription("表情").performTouchInput { click() }
        composeRule.onNodeWithText("Fluent").performClick()
        composeRule.onNodeWithContentDescription("😀").assertExists()

        val field = composeRule.onNode(hasSetTextAction())
        field.performTouchInput { click() }

        field.assertIsFocused()
        composeRule.onAllNodesWithContentDescription("😀").fetchSemanticsNodes().let { nodes ->
            check(nodes.isEmpty()) { "Expected reply emoji panel to close once field gained focus" }
        }
    }
}
