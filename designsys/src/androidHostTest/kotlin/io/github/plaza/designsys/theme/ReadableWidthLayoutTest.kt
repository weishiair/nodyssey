package io.github.plaza.designsys.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.component.ReadableTopBar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
class ReadableWidthLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    @Config(qualifiers = "w1200dp-h900dp")
    fun `readableWidth and ReadableTopBar re-measure dynamically when LocalReadableWidthSpec changes`() {
        var spec by mutableStateOf(ReadableWidthSpec.Default)
        var bodyLeftDp = -1f
        var bodyWidthDp = -1f
        var barLeftInsetDp = -1f
        var barRightInsetDp = -1f

        rule.setContent {
            PlazaTheme {
                CompositionLocalProvider(LocalReadableWidthSpec provides spec) {
                    val density = LocalDensity.current
                    val direction = LocalLayoutDirection.current
                    Box(Modifier.fillMaxSize()) {
                        ReadableTopBar { insets: WindowInsets ->
                            barLeftInsetDp = with(density) { insets.getLeft(this, direction).toDp().value }
                            barRightInsetDp = with(density) { insets.getRight(this, direction).toDp().value }
                        }
                        Box(
                            Modifier
                                .fillMaxSize()
                                .readableWidth()
                                .onPlaced { coords ->
                                    bodyLeftDp = with(density) { coords.positionInRoot().x.toDp().value }
                                    bodyWidthDp = with(density) { coords.size.width.toDp().value }
                                },
                        ) {
                            Box(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        assertEquals(640, bodyWidthDp.roundToInt())
        assertEquals(280, bodyLeftDp.roundToInt())
        assertEquals(280, barLeftInsetDp.roundToInt())
        assertEquals(280, barRightInsetDp.roundToInt())

        // Switch to iPad landscape full-page spec (960dp centered in 1200dp -> 120dp side margins).
        spec =
            ReadableWidthSpec(
                maxWidth = 720.dp,
                landscapeMaxWidth = 960.dp,
                horizontalGutter = 16.dp,
                gutterMinWidth = 600.dp,
                windowLandscape = true,
            )
        rule.waitForIdle()
        assertEquals(960, bodyWidthDp.roundToInt())
        assertEquals(120, bodyLeftDp.roundToInt())
        assertEquals(120, barLeftInsetDp.roundToInt())
        assertEquals(120, barRightInsetDp.roundToInt())

        // Switch to iPad portrait full-page spec (720dp centered in 1200dp -> 240dp side margins).
        spec = spec.copy(windowLandscape = false)
        rule.waitForIdle()
        assertEquals(720, bodyWidthDp.roundToInt())
        assertEquals(240, bodyLeftDp.roundToInt())
        assertEquals(240, barLeftInsetDp.roundToInt())
        assertEquals(240, barRightInsetDp.roundToInt())
    }
}
