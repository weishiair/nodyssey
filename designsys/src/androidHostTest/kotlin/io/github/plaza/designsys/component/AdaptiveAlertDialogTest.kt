package io.github.plaza.designsys.component

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The callbacks the dialog routes its two buttons to, and the single-button shape a null
 * `dismissLabel` leaves: a confirmation that cannot be cancelled must not still draw a cancel
 * button wired to `onDismiss`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class AdaptiveAlertDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var confirms = 0
    private var dismisses = 0

    private fun setDialog(dismissLabel: String? = "Keep") {
        confirms = 0
        dismisses = 0
        composeRule.setContent {
            PlazaTheme {
                AdaptiveAlertDialog(
                    title = "Reset the counter",
                    message = "The current value is lost.",
                    confirmLabel = "Reset",
                    onConfirm = { confirms++ },
                    onDismiss = { dismisses++ },
                    dismissLabel = dismissLabel,
                )
            }
        }
    }

    @Test
    fun `the confirm button reports a confirm and nothing else`() {
        setDialog()

        composeRule.onNodeWithText("Reset").performClick()

        assertEquals(1, confirms)
        assertEquals(0, dismisses)
    }

    @Test
    fun `the dismiss button reports a dismiss and nothing else`() {
        setDialog()

        composeRule.onNodeWithText("Keep").performClick()

        assertEquals(0, confirms)
        assertEquals(1, dismisses)
    }

    @Test
    fun `a missing dismiss label leaves the confirm as the only button`() {
        setDialog(dismissLabel = null)

        composeRule
            .onAllNodes(hasClickAction() and hasAnyAncestor(isDialog()))
            .assertCountEquals(1)
    }
}
