package io.github.plaza.designsys.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosRefreshSpinnerMathTest {
    @Test
    fun `pull before appear threshold leaves every tick hidden`() {
        assertEquals(List(IOS_REFRESH_TICK_COUNT) { 0f }, iosRefreshTickAlphas(0f, isRefreshing = false, activeStep = 0))
        assertEquals(List(IOS_REFRESH_TICK_COUNT) { 0f }, iosRefreshTickAlphas(0.10f, isRefreshing = false, activeStep = 0))
        assertEquals(List(IOS_REFRESH_TICK_COUNT) { 0f }, iosRefreshTickAlphas(0.15f, isRefreshing = false, activeStep = 0))
    }

    @Test
    fun `partial pull lights leading ticks clockwise and leaves trailing ticks dark`() {
        // Halfway between 0.15f and 1.0f -> normalized = 0.5f -> first 6 ticks lit, last 6 ticks 0.
        val alphas = iosRefreshTickAlphas(0.575f, isRefreshing = false, activeStep = 0)
        assertEquals(IOS_REFRESH_TICK_COUNT, alphas.size)
        for (i in 0 until 6) {
            assertTrue(alphas[i] > 0f, "tick $i should be lit at mid pull")
        }
        for (i in 6 until IOS_REFRESH_TICK_COUNT) {
            assertEquals(0f, alphas[i], 0.0001f, "tick $i should stay hidden at mid pull")
        }
    }

    @Test
    fun `full pull and overscroll saturate all ticks within expected alpha bounds`() {
        val atFull = iosRefreshTickAlphas(1f, isRefreshing = false, activeStep = 0)
        val atOverscroll = iosRefreshTickAlphas(1.6f, isRefreshing = false, activeStep = 0)

        assertTrue(atFull.all { it > 0.7f && it <= 0.8f })
        assertEquals(atFull, atOverscroll)
    }

    @Test
    fun `refreshing spinner places brightest tick at activeStep and decays behind clockwise`() {
        val step3 = iosRefreshTickAlphas(progress = 1f, isRefreshing = true, activeStep = 3)
        val step4 = iosRefreshTickAlphas(progress = 1f, isRefreshing = true, activeStep = 4)

        val brightestIndexStep3 = step3.indices.maxBy { step3[it] }
        val dimmestIndexStep3 = step3.indices.minBy { step3[it] }
        assertEquals(3, brightestIndexStep3)
        assertEquals(4, dimmestIndexStep3)

        // Stepping from 3 to 4 advances the brightest tick clockwise by 1 slot.
        assertEquals(4, step4.indices.maxBy { step4[it] })
        // Trailing decay around the clock strictly descends from distanceBehind = 0..11.
        for (behind in 0 until IOS_REFRESH_TICK_COUNT - 1) {
            val currentIdx = (3 - behind).mod(IOS_REFRESH_TICK_COUNT)
            val olderIdx = (3 - behind - 1).mod(IOS_REFRESH_TICK_COUNT)
            assertTrue(step3[currentIdx] > step3[olderIdx])
        }
    }
}
