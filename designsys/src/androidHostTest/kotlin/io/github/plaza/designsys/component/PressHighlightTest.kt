package io.github.plaza.designsys.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PressHighlightTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `a quick press inside a scrolling list highlights immediately and fades on release`() {
        var inkRed = 0f
        rule.setContent {
            PlazaTheme(darkTheme = false) {
                inkRed = MaterialTheme.colorScheme.onSurface.red
                LazyColumn {
                    item {
                        PressHighlight(RectangleShape, native = true) { source, highlight ->
                            Box(
                                Modifier.testTag("row").size(80.dp).then(highlight).background(Color.White)
                                    .clickable(interactionSource = source, indication = null) {},
                            )
                        }
                    }
                }
            }
        }
        val row = rule.onNodeWithTag("row")
        fun red() = row.captureToImage().toPixelMap()[2, 2].red
        rule.mainClock.autoAdvance = false
        row.performTouchInput { down(center) }
        rule.mainClock.advanceTimeByFrame()
        val peakRed = 0.9f + 0.1f * inkRed
        assertEquals(peakRed, red(), 0.008f)
        row.performTouchInput {
            advanceEventTime(40)
            up()
        }
        rule.mainClock.advanceTimeBy(48)
        assertTrue(red() in peakRed - 0.008f..0.999f)
        rule.mainClock.advanceTimeBy(1000)
        assertEquals(1f, red(), 0f)
    }

    @Test
    fun `disabled and non-native controls keep Material interaction and drawing defaults`() {
        var native by mutableStateOf(false)
        var enabled by mutableStateOf(true)
        rule.setContent {
            PressHighlight(RectangleShape, enabled, native) { source, modifier ->
                assertEquals(null, source)
                assertEquals(Modifier, modifier)
            }
        }
        rule.runOnIdle {
            enabled = false
            native = true
        }
        rule.waitForIdle()
    }
}
