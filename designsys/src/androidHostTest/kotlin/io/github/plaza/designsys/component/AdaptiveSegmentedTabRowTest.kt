package io.github.plaza.designsys.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.plaza.designsys.theme.LocalPlazaLayers
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
class AdaptiveSegmentedTabRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class RecordingHaptics : PlatformHaptics {
        var toggles = 0
        var refreshes = 0

        override fun toggle() {
            toggles++
        }

        override fun refreshTriggered() {
            refreshes++
        }
    }

    @Test
    fun `public entry invokes fallback on Android even when frostedGlass is enabled`() {
        var fallbackCalls = 0
        composeRule.setContent {
            PlazaTheme(frostedGlass = true) {
                val frostedLayers = LocalPlazaLayers.current.copy(frostedGlass = true)
                CompositionLocalProvider(LocalPlazaLayers provides frostedLayers) {
                    AdaptiveSegmentedTabRow(
                        tabs = listOf(TabLabel("Interactions", "4"), TabLabel("Messages")),
                        selectedIndex = 0,
                        onSelect = {},
                    ) {
                        fallbackCalls++
                        Text("Fallback Tab Row")
                    }
                }
            }
        }

        composeRule.onNodeWithText("Fallback Tab Row").assertExists()
        composeRule.onNodeWithText("Interactions").assertDoesNotExist()
        assertEquals(1, fallbackCalls)
    }

    @Test
    fun `capsule style bypasses fallback, renders badges and selection state, and toggles haptics only on tab change`() {
        val haptics = RecordingHaptics()
        var fallbackCalls = 0
        var selected by mutableIntStateOf(0)
        val selectedCallbacks = mutableListOf<Int>()

        composeRule.setContent {
            PlazaTheme(darkTheme = true) {
                AdaptiveSegmentedTabRowContent(
                    tabs = listOf(TabLabel("Interactions", "12"), TabLabel("Messages", "99+")),
                    selectedIndex = selected,
                    onSelect = { index ->
                        selectedCallbacks += index
                        selected = index
                    },
                    useCapsuleStyle = true,
                    haptics = haptics,
                ) {
                    fallbackCalls++
                    Text("Fallback Tab Row")
                }
            }
        }

        composeRule.onNodeWithText("Fallback Tab Row").assertDoesNotExist()
        assertEquals(0, fallbackCalls)

        composeRule.onNodeWithText("12").assertExists()
        composeRule.onNodeWithText("99+").assertExists()
        composeRule.onNodeWithText("Interactions").assertIsSelected()
        composeRule.onNodeWithText("Messages").assertIsNotSelected()

        // Re-tapping already selected segment invokes onSelect (scroll-to-top) without a toggle tick.
        composeRule.onNodeWithText("Interactions").performClick()
        assertEquals(listOf(0), selectedCallbacks)
        assertEquals(0, haptics.toggles)

        // Switching to the other segment fires both toggle haptic and onSelect(1).
        composeRule.onNodeWithText("Messages").performClick()
        assertEquals(listOf(0, 1), selectedCallbacks)
        assertEquals(1, haptics.toggles)
        composeRule.onNodeWithText("Messages").assertIsSelected()
        composeRule.onNodeWithText("Interactions").assertIsNotSelected()
    }

    @Test
    fun `empty tab list and out of range selected index do not crash`() {
        composeRule.setContent {
            PlazaTheme {
                AdaptiveSegmentedTabRowContent(
                    tabs = emptyList(),
                    selectedIndex = 0,
                    onSelect = {},
                    useCapsuleStyle = true,
                    haptics = RecordingHaptics(),
                ) {}
                AdaptiveSegmentedTabRowContent(
                    tabs = listOf(TabLabel("Alpha"), TabLabel("Beta")),
                    selectedIndex = 99,
                    onSelect = {},
                    useCapsuleStyle = true,
                    haptics = RecordingHaptics(),
                ) {}
            }
        }

        composeRule.onNodeWithText("Alpha").assertIsNotSelected()
        composeRule.onNodeWithText("Beta").assertIsSelected()
    }

    @Test
    fun `capsule style survives intrinsic height measurement with fractional pager offset`() {
        composeRule.setContent {
            PlazaTheme {
                Box(Modifier.height(IntrinsicSize.Min)) {
                    AdaptiveSegmentedTabRowContent(
                        tabs = listOf(TabLabel("Interactions", "3"), TabLabel("Messages")),
                        selectedIndex = 0,
                        onSelect = {},
                        selectionFraction = { 0.5f },
                        useCapsuleStyle = true,
                        haptics = RecordingHaptics(),
                    ) {}
                }
            }
        }

        composeRule.onNodeWithText("Interactions").assertIsSelected()
        composeRule.onNodeWithText("Messages").assertIsNotSelected()
    }
}
