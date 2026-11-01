package io.github.nodyssey.ui.sticker

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import io.github.nodyssey.data.sticker.StickerCdnSettings
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
class StickerManageSwipeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val hidden = mutableStateOf(false)
    private val changes = mutableListOf<Boolean>()

    private fun setRow() {
        composeRule.setContent {
            val item = StickerGroupItem.Mine(emptyList(), hidden = hidden.value)
            PlazaTheme {
                GroupRow(
                    item = item,
                    brokenMine = 0,
                    cdn = StickerCdnSettings(),
                    first = true,
                    last = true,
                    onClick = {},
                    onToggleHidden = {
                        val next = !item.hidden
                        changes += next
                        hidden.value = next
                    },
                    modifier = Modifier.testTag(GROUP),
                )
            }
        }
        composeRule.waitForIdle()
        // Bound the animation time: the regression keeps scheduling toggles and never becomes idle.
        composeRule.mainClock.autoAdvance = false
    }

    @Test
    fun `a swipe toggles once and returns so the next swipe can show the group`() {
        setRow()

        composeRule.onNodeWithTag(GROUP).performTouchInput { swipeLeft() }
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.runOnIdle { assertEquals(listOf(true), changes) }

        // The same row must accept another gesture, using the hidden state from the first one.
        composeRule.onNodeWithTag(GROUP).performTouchInput { swipeLeft() }
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.runOnIdle { assertEquals(listOf(true, false), changes) }
    }

    private companion object {
        const val GROUP = "sticker-group"
    }
}
