package io.github.plaza.designsys.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdaptiveSettingsControlsLogicTest {
    @Test
    fun `snapSliderValue aligns to discrete intervals matching all settings sliders`() {
        // Font size 14..24 with 9 intermediate steps (1sp step)
        assertEquals(14f, snapSliderValue(10f, 14f, 24f, 9))
        assertEquals(16f, snapSliderValue(16.4f, 14f, 24f, 9))
        assertEquals(17f, snapSliderValue(16.6f, 14f, 24f, 9))
        assertEquals(24f, snapSliderValue(30f, 14f, 24f, 9))

        // Sticker size 20..90 with 13 intermediate steps (5sp step)
        assertEquals(20f, snapSliderValue(22f, 20f, 90f, 13))
        assertEquals(25f, snapSliderValue(23f, 20f, 90f, 13))
        assertEquals(50f, snapSliderValue(49.1f, 20f, 90f, 13))
        assertEquals(90f, snapSliderValue(89.5f, 20f, 90f, 13))

        // Search avatar 20..44 with 23 intermediate steps (1dp step)
        assertEquals(28f, snapSliderValue(28.2f, 20f, 44f, 23))
        assertEquals(44f, snapSliderValue(44f, 20f, 44f, 23))

        // Defensive guards for degenerate bounds or NaN
        assertEquals(14f, snapSliderValue(Float.NaN, 14f, 24f, 9))
        assertEquals(20f, snapSliderValue(25f, 20f, 20f, 0))
    }

    @Test
    fun `shouldUseSegmentedControl drops to menu when translated or scaled labels overflow`() {
        assertTrue(
            shouldUseSegmentedControl(
                labelWidthsPx = listOf(48f, 44f, 44f),
                availableWidthPx = 300f,
                segmentPaddingPx = 30f,
            ),
        )
        assertFalse(
            shouldUseSegmentedControl(
                labelWidthsPx = listOf(128f, 48f, 48f),
                availableWidthPx = 320f,
                segmentPaddingPx = 30f,
            ),
        )
        assertFalse(
            shouldUseSegmentedControl(
                labelWidthsPx = listOf(40f),
                availableWidthPx = 300f,
                segmentPaddingPx = 30f,
            ),
        )
        assertFalse(
            shouldUseSegmentedControl(
                labelWidthsPx = listOf(20f, 20f, 20f, 20f, 20f),
                availableWidthPx = 500f,
                segmentPaddingPx = 30f,
            ),
        )
    }
}
