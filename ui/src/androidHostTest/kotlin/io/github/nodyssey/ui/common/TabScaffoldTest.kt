package io.github.nodyssey.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class TabScaffoldTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `content width shrinks monotonically across an iPad landscape squeeze`() {
        val widths = listOf(1194, 1000, 960, 840, 834, 720, 620, 600, 580, 560, 540, 520, 440, 360)
        val contentWidths =
            widths.map { width ->
                tabContentWidth(
                    availableWidth = width.dp,
                    availableHeight = 834.dp,
                    isIosPad = true,
                    windowLandscape = true,
                )
            }

        assertEquals(IosPadTabContentMaxWidth, contentWidths.first())
        assertEquals(360.dp, contentWidths.last())
        contentWidths.zipWithNext().forEach { (wider, narrower) ->
            assertTrue("Expected monotonic shrink: $wider -> $narrower", wider >= narrower)
        }
    }

    @Test
    fun `horizontal padding preserves resting values at wide and split widths`() {
        assertEquals(Spacing.lg, tabHorizontalPadding(WideTabMinWidth))
        assertEquals(Spacing.lg, tabHorizontalPadding(1194.dp))
        assertEquals(0.dp, tabHorizontalPadding(TabSqueezePaddingMinWidth))
        assertEquals(0.dp, tabHorizontalPadding(360.dp))
    }

    @Test
    @Config(qualifiers = "w1200dp-h900dp")
    fun `intermediate squeeze widths do not recompose the scaffold body`() {
        var paneWidth by mutableStateOf(1194.dp)
        var contentRecompositions = 0
        var lastWide: Boolean? = null

        rule.setContent {
            PlazaTheme {
                CompositionLocalProvider(
                    LocalIosPad provides true,
                    LocalIosPadLandscape provides true,
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .layout { measurable, constraints ->
                                val widthPx = paneWidth.roundToPx().coerceIn(0, constraints.maxWidth)
                                val placeable =
                                    measurable.measure(
                                        constraints.copy(minWidth = widthPx, maxWidth = widthPx),
                                    )
                                layout(constraints.maxWidth, constraints.maxHeight) {
                                    placeable.placeRelative(0, 0)
                                }
                            },
                    ) {
                        TabScaffold(
                            modifier = Modifier.fillMaxSize(),
                            topBar = {},
                        ) { _, wide ->
                            contentRecompositions++
                            lastWide = wide
                            Box(Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val initialCount = contentRecompositions
        assertEquals(true, lastWide)

        // Shrinking within the wide band only re-measures; it must not recompose the content tree.
        for (width in listOf(1020.dp, 880.dp, 760.dp, 640.dp)) {
            paneWidth = width
            rule.waitForIdle()
        }
        assertEquals(initialCount, contentRecompositions)

        // Crossing below 600dp flips `wide` once, then further narrowing stays recomposition-free.
        paneWidth = 540.dp
        rule.waitForIdle()
        assertEquals(false, lastWide)
        val narrowCount = contentRecompositions
        for (width in listOf(480.dp, 420.dp, 360.dp)) {
            paneWidth = width
            rule.waitForIdle()
        }
        assertEquals(narrowCount, contentRecompositions)
    }
}
