package io.github.plaza.designsys.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The link key's two templates: a tap is still the plain link, and the long press is the only route
 * to the image one — it has no key of its own, because 图片 is the upload flow.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class ComposerEditorBarLinkMenuTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val bodyState = TextFieldState()

    private fun setBar() {
        composeRule.setContent {
            PlazaTheme {
                ComposerEditorBar(
                    actions = listOf(EditorAction.LINK),
                    bodyState = bodyState,
                    editorState = rememberMarkdownEditorState(),
                    onPickHostedImages = {},
                    inlinePictures = null,
                )
            }
        }
    }

    @Test
    fun `a tap still inserts the link template`() {
        setBar()

        composeRule.onNodeWithContentDescription("链接").performClick()

        assertEquals("[链接文字](https://)", bodyState.text.toString())
    }

    @Test
    fun `a long press offers the image template`() {
        setBar()

        composeRule.onNodeWithContentDescription("链接").performTouchInput { longClick() }
        composeRule.onNodeWithText("图片链接").performClick()

        assertEquals("![image](https://)", bodyState.text.toString())
    }
}
