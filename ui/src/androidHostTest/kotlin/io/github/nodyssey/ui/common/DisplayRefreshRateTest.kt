package io.github.nodyssey.ui.common

import android.app.Activity
import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DisplayRefreshRateTest {
    @Test
    fun `a higher rate at another resolution cannot be selected`() {
        val modes = listOf(
            RefreshRateMode(1080, 2400, 60f),
            RefreshRateMode(1440, 3200, 120f),
            RefreshRateMode(1080, 2400, 90f),
        )
        assertEquals(0f, requestedRefreshRate(120, 1080, 2400, modes), 0f)
        assertEquals(90f, requestedRefreshRate(90, 1080, 2400, modes), 0f)
    }

    @Test
    fun `fractional screen rates use the supported value rather than an invented rate`() {
        val modes = listOf(RefreshRateMode(1080, 2400, 59.94f), RefreshRateMode(1080, 2400, 119.88f))
        assertEquals(119.88f, requestedRefreshRate(120, 1080, 2400, modes), 0f)
        assertEquals(0f, requestedRefreshRate(90, 1080, 2400, modes), 0f)
        assertEquals(0f, requestedRefreshRate(0, 1080, 2400, modes), 0f)
    }

    @Test
    fun `an unavailable display or invalid reported rates do not request a mode`() {
        assertEquals(0f, requestedRefreshRate(165, 1080, 2400, emptyList()), 0f)
        assertEquals(
            0f,
            requestedRefreshRate(120, 1080, 2400, listOf(RefreshRateMode(1080, 2400, Float.NaN))),
            0f,
        )
    }

    @Test
    fun `changing rate clears an overriding mode and restores it without clobbering other window fields`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val window = activity.get().window
            window.attributes = window.attributes.apply {
                preferredRefreshRate = 90f
                preferredDisplayModeId = 7
            }
            val preference = WindowRefreshRateOverride(window)
            preference.apply(120f)
            assertEquals(120f, window.attributes.preferredRefreshRate, 0f)
            assertEquals(0, window.attributes.preferredDisplayModeId)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            preference.restore()
            assertEquals(90f, window.attributes.preferredRefreshRate, 0f)
            assertEquals(7, window.attributes.preferredDisplayModeId)
            assertEquals(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
        } finally {
            activity.pause().stop().destroy()
        }
    }
}
