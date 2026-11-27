package io.github.nodyssey.ui.tools

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.nodyssey.data.InviteCurrency
import io.github.plaza.core.net.SiteError
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
class InviteOutcomeDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val unknown = InviteOutcome.Unknown(InviteCurrency.COIN)
    private val outcome = mutableStateOf<InviteOutcome?>(null)
    private var retries = 0
    private var buys = 0
    private var dismissals = 0

    private fun show(initialOutcome: InviteOutcome) {
        outcome.value = initialOutcome
        composeRule.setContent {
            PlazaTheme {
                InviteScreen(
                    state = InviteUiState(outcome = outcome.value),
                    onBack = {},
                    onBuy = { buys++ },
                    onRetry = { retries++ },
                    onOpenWeb = {},
                    onDismissOutcome = {
                        dismissals++
                        outcome.value = null
                    },
                )
            }
        }
    }

    @Test
    fun `acknowledging a refused purchase only dismisses it`() {
        show(InviteOutcome.Refused(SiteError.Unknown, "Purchase refused"))

        composeRule.onNodeWithText("知道了").performClick()

        composeRule.onNode(isDialog()).assertDoesNotExist()
        assertEquals(1, dismissals)
        assertEquals(0, retries)
        assertEquals(0, buys)
    }

    @Test
    fun `an uncertain outcome only refreshes when checking the result`() {
        show(unknown)

        composeRule.onNodeWithText("知道了").performClick()
        composeRule.onNode(isDialog()).assertDoesNotExist()
        assertEquals(1, dismissals)
        assertEquals(0, retries)

        composeRule.runOnIdle { outcome.value = unknown }
        composeRule.onNodeWithText("核对购买结果").performClick()

        composeRule.onNode(isDialog()).assertDoesNotExist()
        assertEquals(2, dismissals)
        assertEquals(1, retries)
        assertEquals(0, buys)
    }
}
