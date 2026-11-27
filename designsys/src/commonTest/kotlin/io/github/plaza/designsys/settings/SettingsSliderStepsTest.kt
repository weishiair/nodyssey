package io.github.plaza.designsys.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [snapToSteps] is what the iOS slider reports in place of the raw thumb position. Off by one stop and
 * a reader who drags to 18 stores 17; snapped wrongly at the ends and a value outside the range the
 * store accepts reaches the repository.
 */
class SettingsSliderStepsTest {
    @Test
    fun `a drag lands on the nearest stop Compose would offer`() {
        // 14..24 with nine interior stops is ten 1sp intervals, as 阅读字号 has.
        assertEquals(18f, snapToSteps(17.6f, 14f..24f, steps = 9))
        assertEquals(17f, snapToSteps(17.4f, 14f..24f, steps = 9))
    }

    @Test
    fun `stops follow the interval size and not whole numbers`() {
        // 20..90 with thirteen interior stops is 5sp apart — 表情大小.
        assertEquals(55f, snapToSteps(56.9f, 20f..90f, steps = 13))
    }

    @Test
    fun `a thumb past either end is held at that end`() {
        assertEquals(24f, snapToSteps(30f, 14f..24f, steps = 9))
        assertEquals(14f, snapToSteps(-3f, 14f..24f, steps = 9))
    }

    @Test
    fun `a slider without stops keeps the exact position`() {
        assertEquals(0.37f, snapToSteps(0.37f, 0f..1f, steps = 0))
    }
}
