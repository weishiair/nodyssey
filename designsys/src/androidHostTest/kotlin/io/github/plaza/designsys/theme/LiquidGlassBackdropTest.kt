package io.github.plaza.designsys.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiquidGlassBackdropTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** A glass preference used to allocate a capture layer even where blur cannot run. */
    @Test
    @Config(sdk = [30])
    fun `Android without blur does not allocate a backdrop`() {
        var backdrop: LayerBackdrop? = null
        composeRule.setContent {
            PlazaTheme(frostedGlass = true) {
                backdrop = rememberLiquidGlassBackdrop()
                Box(Modifier.size(100.dp).liquidGlassBackdropSource(backdrop))
            }
        }
        composeRule.runOnIdle { assertNull(backdrop) }
    }

    /** Stopping capture must release its graphics layer, not only stop exposing the backdrop. */
    @Test
    @Config(sdk = [31, 33])
    fun `a disappearing consumer releases capture and can start it again`() {
        val enabled = mutableStateOf(true)
        var backdrop: LayerBackdrop? = null
        composeRule.setContent {
            PlazaTheme(frostedGlass = true) {
                backdrop = rememberLiquidGlassBackdrop(enabled = enabled.value)
                Box(Modifier.size(100.dp).liquidGlassBackdropSource(backdrop))
            }
        }
        composeRule.waitForIdle()
        val firstLayer = checkNotNull(backdrop).graphicsLayer
        composeRule.runOnIdle {
            assertFalse(firstLayer.isReleased)
            enabled.value = false
        }
        composeRule.runOnIdle {
            assertNull(backdrop)
            assertTrue(firstLayer.isReleased)
            enabled.value = true
        }
        composeRule.runOnIdle {
            assertNotNull(backdrop)
            assertFalse(checkNotNull(backdrop).graphicsLayer.isReleased)
        }
    }
}
