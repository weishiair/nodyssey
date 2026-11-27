package io.github.plaza.designsys.theme

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadableWidthSpecTest {
    @Test
    fun `default spec caps wide windows at readable content width with no gutter`() {
        assertEquals(360.dp, ReadableWidthSpec.Default.resolveWidth(360.dp, 800.dp))
        assertEquals(640.dp, ReadableWidthSpec.Default.resolveWidth(640.dp, 800.dp))
        assertEquals(640.dp, ReadableWidthSpec.Default.resolveWidth(1194.dp, 834.dp))
    }

    @Test
    fun `wide spec applies gutter only at or above threshold and switches cap by orientation`() {
        val spec =
            ReadableWidthSpec(
                maxWidth = 720.dp,
                landscapeMaxWidth = 960.dp,
                horizontalGutter = 16.dp,
                gutterMinWidth = 600.dp,
            )

        assertEquals(560.dp, spec.resolveWidth(560.dp, 834.dp))
        assertEquals(568.dp, spec.resolveWidth(600.dp, 834.dp))
        assertEquals(712.dp, spec.resolveWidth(744.dp, 1133.dp))
        assertEquals(720.dp, spec.resolveWidth(834.dp, 1194.dp))
        assertEquals(868.dp, spec.resolveWidth(900.dp, 700.dp))
        assertEquals(960.dp, spec.resolveWidth(1194.dp, 834.dp))
        assertEquals(960.dp, spec.resolveWidth(1366.dp, 1024.dp))
    }

    @Test
    fun `explicit window orientation overrides container aspect ratio`() {
        val landscapeSpec =
            ReadableWidthSpec(
                maxWidth = 720.dp,
                landscapeMaxWidth = 960.dp,
                horizontalGutter = 16.dp,
                gutterMinWidth = 600.dp,
                windowLandscape = true,
            )
        val portraitSpec = landscapeSpec.copy(windowLandscape = false)

        // Even when the container height exceeds its width (e.g. a top bar or tall pane), windowLandscape wins.
        assertEquals(802.dp, landscapeSpec.resolveWidth(834.dp, 1000.dp))
        assertEquals(720.dp, portraitSpec.resolveWidth(1000.dp, 64.dp))
    }
}
