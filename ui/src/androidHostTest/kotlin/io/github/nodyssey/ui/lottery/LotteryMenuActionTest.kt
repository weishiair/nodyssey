package io.github.nodyssey.ui.lottery

import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.github.plaza.designsys.component.GlassAction
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
class LotteryMenuActionTest {
    @get:Rule
    val composeRule = createComposeRule()

    /**
     * iOS draws the overflow from its native description, which no Android path runs: a swapped
     * callback would ship without a screen showing it.
     */
    @Test
    fun `the native lottery menu creates a draw and opens the help, each from its own entry`() {
        var created = 0
        var helped = 0
        lateinit var menu: GlassAction
        composeRule.setContent { menu = lotteryMenuAction(onCreate = { created++ }, onHelp = { helped++ }) }
        composeRule.waitForIdle()

        val (create, help) = menu.menu
        create.onClick()
        assertEquals(1 to 0, created to helped)
        help.onClick()
        assertEquals(1 to 1, created to helped)
    }
}
