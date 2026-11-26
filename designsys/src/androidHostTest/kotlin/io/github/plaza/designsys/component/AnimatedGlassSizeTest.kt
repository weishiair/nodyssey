package io.github.plaza.designsys.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class AnimatedGlassSizeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `first frame settles directly on initial size and subsequent changes animate smoothly`() {
        var expanded by mutableStateOf(true)
        val observedChildHeights = mutableListOf<Int>()
        var outerSize = IntSize.Zero
        var expandedPx = 0
        var collapsedPx = 0

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            val density = LocalDensity.current
            expandedPx = with(density) { 144.dp.roundToPx() }
            collapsedPx = with(density) { 56.dp.roundToPx() }
            PlazaTheme {
                AnimatedGlassSize(
                    modifier = Modifier.onSizeChanged { outerSize = it },
                ) { sizeModifier ->
                    Box(
                        modifier = sizeModifier
                            .size(width = 56.dp, height = if (expanded) 144.dp else 56.dp)
                            .onSizeChanged { observedChildHeights += it.height },
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()

        // First frame must never animate from zero.
        assertEquals(listOf(expandedPx), observedChildHeights.distinct())
        assertEquals(expandedPx, outerSize.height)

        observedChildHeights.clear()
        expanded = false
        Snapshot.sendApplyNotifications()
        // Advance a few frames mid-flight: both child and outer container should step through intermediate heights.
        repeat(4) { composeRule.mainClock.advanceTimeBy(16L) }
        val midFlightHeight = outerSize.height
        assertTrue(
            "Expected mid-flight height strictly between $collapsedPx and $expandedPx, got $midFlightHeight",
            midFlightHeight in (collapsedPx + 1) until expandedPx,
        )
        assertTrue(
            "Expected child itself to be measured at intermediate height so native glass morphs continuously",
            observedChildHeights.any { it in (collapsedPx + 1) until expandedPx },
        )

        composeRule.mainClock.advanceTimeBy(1_000L)
        assertEquals(collapsedPx, outerSize.height)
        assertEquals(collapsedPx, observedChildHeights.last())
    }

    @Test
    fun `eink mode snaps size changes without intermediate frames`() {
        var expanded by mutableStateOf(true)
        var outerSize = IntSize.Zero
        var collapsedPx = 0

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            collapsedPx = with(LocalDensity.current) { 56.dp.roundToPx() }
            PlazaTheme(einkMode = true) {
                AnimatedGlassSize(
                    modifier = Modifier.onSizeChanged { outerSize = it },
                ) { sizeModifier ->
                    Box(modifier = sizeModifier.size(width = 56.dp, height = if (expanded) 144.dp else 56.dp))
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()

        expanded = false
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(collapsedPx, outerSize.height)
    }

    @Test
    fun `preserves caller alignment and fixed size modifiers on outer container`() {
        var outerSize = IntSize.Zero
        var expectedPx = 0

        composeRule.setContent {
            expectedPx = with(LocalDensity.current) { 48.dp.roundToPx() }
            PlazaTheme {
                Box(Modifier.size(200.dp)) {
                    AnimatedGlassSize(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(48.dp)
                            .onSizeChanged { outerSize = it },
                    ) { sizeModifier ->
                        Box(modifier = sizeModifier.size(96.dp))
                    }
                }
            }
        }

        assertEquals(IntSize(expectedPx, expectedPx), outerSize)
    }

    @Test
    fun `propagates minimum size constraints to the child so collapsed pills match full slot`() {
        var childSize = IntSize.Zero
        var expectedPx = 0

        composeRule.setContent {
            expectedPx = with(LocalDensity.current) { 56.dp.roundToPx() }
            PlazaTheme {
                AnimatedGlassSize(
                    modifier = Modifier.widthIn(min = 56.dp).heightIn(min = 56.dp),
                ) { sizeModifier ->
                    Box(modifier = sizeModifier.size(44.dp).onSizeChanged { childSize = it })
                }
            }
        }

        assertEquals(IntSize(expectedPx, expectedPx), childSize)
    }
}
