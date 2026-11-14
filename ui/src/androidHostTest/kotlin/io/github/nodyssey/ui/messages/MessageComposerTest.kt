package io.github.nodyssey.ui.messages

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.WindowInsets
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import io.github.nodyssey.data.composer.ImageAttachment
import io.github.nodyssey.data.composer.UploadStatus
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/** The message bar's half of the shared editor (7f, redrawn as 3d/3e with the keys in a grid behind +). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class MessageComposerTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var draftState: TextFieldState

    private fun setScreen(
        draft: String = "",
        frostedGlass: () -> Boolean = { false },
        bottomInset: Dp = 0.dp,
        imeInset: Dp = 0.dp,
    ) {
        composeRule.setContent {
            draftState = remember { TextFieldState(draft) }
            val density = LocalDensity.current
            val insets = with(density) {
                WindowInsetsCompat.Builder()
                    .setInsets(
                        WindowInsetsCompat.Type.navigationBars(),
                        Insets.of(0, 0, 0, bottomInset.toPx().roundToInt()),
                    )
                    .setInsets(
                        WindowInsetsCompat.Type.ime(),
                        Insets.of(0, 0, 0, imeInset.toPx().roundToInt()),
                    )
                    .build()
            }
            DeviceConfigurationOverride(DeviceConfigurationOverride.WindowInsets(insets)) {
                PlazaTheme(frostedGlass = frostedGlass()) {
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

    @Test
    fun `the composer surface covers the bottom inset in both standard and glass modes`() {
        var frostedGlass by mutableStateOf(false)
        setScreen(frostedGlass = { frostedGlass }, bottomInset = 34.dp)

        listOf(false, true).forEach { glass ->
            composeRule.runOnIdle { frostedGlass = glass }
            composeRule.waitForIdle()

            val rootBottom = composeRule.onAllNodes(isRoot()).onFirst().getUnclippedBoundsInRoot().bottom
            val surfaceBottom = composeRule.onNodeWithTag(MESSAGE_COMPOSER_SURFACE_TAG).getUnclippedBoundsInRoot().bottom
            val sendBottom = composeRule.onNodeWithContentDescription("发送").getUnclippedBoundsInRoot().bottom

            assertEquals("frostedGlass=$glass", rootBottom.value, surfaceBottom.value, 1f)
            assertTrue(
                "Send button should clear 34dp inset (frostedGlass=$glass): sendBottom=$sendBottom rootBottom=$rootBottom",
                sendBottom <= rootBottom - 34.dp,
            )
        }
    }

    @Test
    fun `the input bar rests directly above the keyboard without double-counting the bottom inset`() {
        setScreen(bottomInset = 34.dp, imeInset = 300.dp)
        composeRule.waitForIdle()

        val rootBottom = composeRule.onAllNodes(isRoot()).onFirst().getUnclippedBoundsInRoot().bottom
        val surfaceBottom = composeRule.onNodeWithTag(MESSAGE_COMPOSER_SURFACE_TAG).getUnclippedBoundsInRoot().bottom
        val sendBottom = composeRule.onNodeWithContentDescription("发送").getUnclippedBoundsInRoot().bottom

        assertEquals(rootBottom.value, surfaceBottom.value, 1f)
        // Input bar has 8dp bottom padding inside the row above the 300dp keyboard.
        assertEquals((rootBottom - 308.dp).value, sendBottom.value, 1.5f)
    }

    @Test
    fun `the composer surface stays flush when the emoji panel is open over the bottom inset`() {
        setScreen(bottomInset = 34.dp)
        composeRule.onNodeWithContentDescription("表情").performTouchInput { click() }
        composeRule.waitForIdle()

        val rootBottom = composeRule.onAllNodes(isRoot()).onFirst().getUnclippedBoundsInRoot().bottom
        val surfaceBottom = composeRule.onNodeWithTag(MESSAGE_COMPOSER_SURFACE_TAG).getUnclippedBoundsInRoot().bottom
        val backspaceBottom = composeRule.onNodeWithContentDescription("删除").getUnclippedBoundsInRoot().bottom

        assertEquals(rootBottom.value, surfaceBottom.value, 1f)
        assertTrue(
            "Emoji panel controls should clear 34dp inset: backspaceBottom=$backspaceBottom rootBottom=$rootBottom",
            backspaceBottom <= rootBottom - 34.dp,
        )
    }
}
