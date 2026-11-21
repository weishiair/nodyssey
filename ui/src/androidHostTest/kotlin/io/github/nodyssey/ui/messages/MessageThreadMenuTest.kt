package io.github.nodyssey.ui.messages

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
class MessageThreadMenuTest {
    @get:Rule
    val composeRule = createComposeRule()

    /**
     * iOS builds the ⋮ menu natively from this description, a path no Android pixel test runs: a
     * wrong callback would ship without a screen showing it.
     */
    @Test
    fun `the native thread menu opens this thread in the browser`() {
        var opened = 0
        lateinit var menu: GlassAction
        composeRule.setContent { menu = threadMenuAction { opened++ } }
        composeRule.waitForIdle()

        val only = menu.menu.single()
        only.onClick()
        assertEquals(1, opened)
    }
}
