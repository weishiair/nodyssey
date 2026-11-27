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
import io.github.plaza.designsys.theme.ReadableWidthSpec
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
                    squeezing = true,
                )
            }

        assertEquals(IosPadTabContentMaxWidth, contentWidths.first())
        assertEquals(360.dp, contentWidths.last())
        contentWidths.zipWithNext().forEach { (wider, narrower) ->
            assertTrue("Expected monotonic shrink: $wider -> $narrower", wider >= narrower)
        }
    }

    @Test
    fun `horizontal padding preserves static layouts and only ramps while squeezing`() {
        // Static layouts (including 560dp on Android and resting iPad panes) have no gutter below 600dp.
        assertEquals(Spacing.lg, tabHorizontalPadding(WideTabMinWidth))
        assertEquals(Spacing.lg, tabHorizontalPadding(1194.dp))
        assertEquals(0.dp, tabHorizontalPadding(560.dp))
        assertEquals(0.dp, tabHorizontalPadding(TabSqueezePaddingMinWidth))
        assertEquals(0.dp, tabHorizontalPadding(360.dp))
        assertEquals(
            560.dp,
            tabContentWidth(availableWidth = 560.dp, availableHeight = 800.dp, isIosPad = false),
        )
        assertEquals(
            560.dp,
            tabContentWidth(availableWidth = 560.dp, availableHeight = 800.dp, isIosPad = true),
        )

        // During an iPad split-pane squeeze, the gutter interpolates smoothly across 520..600dp.
        assertEquals(Spacing.lg, tabHorizontalPadding(WideTabMinWidth, squeezing = true))
        assertEquals(Spacing.lg * 0.5f, tabHorizontalPadding(560.dp, squeezing = true))
        assertEquals(0.dp, tabHorizontalPadding(TabSqueezePaddingMinWidth, squeezing = true))
        assertEquals(0.dp, tabHorizontalPadding(360.dp, squeezing = true))
    }

    @Test
    @Config(qualifiers = "w1200dp-h900dp")
    fun `intermediate squeeze widths do not recompose the scaffold body`() {
        var paneWidth by mutableStateOf(1194.dp)
        var squeezing by mutableStateOf(false)
        var contentRecompositions = 0
        var lastWide: Boolean? = null

        rule.setContent {
            PlazaTheme {
                CompositionLocalProvider(
                    LocalIosPad provides true,
                    LocalIosPadLandscape provides true,
                    LocalIosPadSqueezing provides { squeezing },
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

        // Toggling squeeze state and shrinking within the wide band only re-measures.
        squeezing = true
        for (width in listOf(1020.dp, 880.dp, 760.dp, 640.dp)) {
            paneWidth = width
            rule.waitForIdle()
        }
        assertEquals(initialCount, contentRecompositions)

        // Crossing below 600dp flips `wide` once, then further narrowing and ending squeeze stay recomposition-free.
        paneWidth = 540.dp
        rule.waitForIdle()
        assertEquals(false, lastWide)
        val narrowCount = contentRecompositions
        for (width in listOf(480.dp, 420.dp, 360.dp)) {
            paneWidth = width
            rule.waitForIdle()
        }
        squeezing = false
        rule.waitForIdle()
        assertEquals(narrowCount, contentRecompositions)
    }

    @Test
    fun `full-page iPad readable spec matches resting tab content width and falls back in split or non-iPad`() {
        assertEquals(
            ReadableWidthSpec.Default,
            iosPadReadableWidthSpec(isIosPad = false, inSplit = false, windowLandscape = true),
        )
        assertEquals(
            ReadableWidthSpec.Default,
            iosPadReadableWidthSpec(isIosPad = true, inSplit = true, windowLandscape = true),
        )

        val landscapeSpec = iosPadReadableWidthSpec(isIosPad = true, inSplit = false, windowLandscape = true)
        val portraitSpec = iosPadReadableWidthSpec(isIosPad = true, inSplit = false, windowLandscape = false)
        for (width in listOf(360, 560, 600, 744, 834, 900, 960, 1080, 1133, 1194, 1366)) {
            assertEquals(
                tabContentWidth(width.dp, 834.dp, isIosPad = true, windowLandscape = true),
                landscapeSpec.resolveWidth(width.dp, 834.dp),
            )
            assertEquals(
                tabContentWidth(width.dp, 1194.dp, isIosPad = true, windowLandscape = false),
                portraitSpec.resolveWidth(width.dp, 1194.dp),
            )
        }
    }
}
