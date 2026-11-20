package io.github.nodyssey.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
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
class ClearFocusOnBlankTapTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var buttonTaps = 0

    private fun show() {
        buttonTaps = 0
        composeRule.setContent {
            PlazaTheme {
                val focusManager = LocalFocusManager.current
                Column(Modifier.fillMaxSize().clearFocusOnBlankTap(focusManager)) {
                    BasicTextField(
                        value = "draft",
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth().testTag("field"),
                    )
                    Button(onClick = { buttonTaps++ }, modifier = Modifier.testTag("button")) {
                        Text("Tap me")
                    }
                    Box(Modifier.fillMaxWidth().height(200.dp).testTag("blank"))
                }
            }
        }
        composeRule.onNodeWithTag("field").performClick().assertIsFocused()
    }

    @Test
    fun `a tap on blank space lets the focused field go`() {
        show()

        composeRule.onNodeWithTag("blank").performClick()

        composeRule.onNodeWithTag("field").assertIsNotFocused()
    }

    @Test
    fun `a tap on a button keeps the field's focus`() {
        show()

        composeRule.onNodeWithTag("button").performClick()

        assertEquals(1, buttonTaps)
        composeRule.onNodeWithTag("field").assertIsFocused()
    }

    @Test
    fun `a drag across blank space is a scroll, not a dismissal`() {
        show()

        composeRule.onNodeWithTag("blank").performTouchInput { swipeUp() }

        composeRule.onNodeWithTag("field").assertIsFocused()
    }

    @Test
    fun `a long press on blank space is somebody else's gesture, not a dismissal`() {
        show()

        composeRule.onNodeWithTag("blank").performTouchInput { longClick() }

        composeRule.onNodeWithTag("field").assertIsFocused()
    }
}
