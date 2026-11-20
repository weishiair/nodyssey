package io.github.nodyssey.ui.vote

import androidx.compose.runtime.mutableStateOf
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class VoteComposeDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `only the confirmation button creates a poll`() {
        var creates = 0
        val confirming = mutableStateOf(true)
        composeRule.setContent {
            PlazaTheme {
                if (confirming.value) {
                    VoteCreationConfirmation(
                        onDismiss = { confirming.value = false },
                        onConfirm = {
                            confirming.value = false
                            creates++
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNode(isDialog()).assertDoesNotExist()
        assertEquals(0, creates)

        composeRule.runOnIdle { confirming.value = true }
        composeRule.onNodeWithText("创建").performClick()

        composeRule.onNode(isDialog()).assertDoesNotExist()
        assertEquals(1, creates)
    }
}
