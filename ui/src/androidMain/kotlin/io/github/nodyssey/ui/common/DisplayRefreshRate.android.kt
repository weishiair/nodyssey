package io.github.nodyssey.ui.common

import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.View
import android.view.Window
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import io.github.nodyssey.data.settings.SettingsRepository
import kotlin.math.abs

@Composable
internal actual fun rememberDisplayRefreshRates(): Set<Int>? {
    val view = LocalView.current
    var rates by remember(view) { mutableStateOf(supportedRefreshRates(view.display)) }
    DisposableEffect(view) {
        val stop = observeDisplayChanges(view) { rates = supportedRefreshRates(view.display) }
        onDispose { stop() }
    }
    return rates
}

@Composable
internal actual fun ApplyDisplayRefreshRate(hertz: Int) {
    if (hertz == 0) return
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view, hertz) {
        val preference = WindowRefreshRateOverride(window)
        val stop = observeDisplayChanges(view) {
            val display = view.display
            val current = display?.mode
            preference.apply(
                if (display != null && current != null) {
                    requestedRefreshRate(hertz, current.physicalWidth, current.physicalHeight, display.refreshModes())
                } else {
                    0f
                },
            )
        }
        onDispose {
            stop()
            preference.restore()
        }
    }
}

private fun supportedRefreshRates(display: Display?): Set<Int> {
    val current = display?.mode ?: return emptySet()
    val modes = display.refreshModes()
    return SettingsRepository.DISPLAY_REFRESH_RATE_CHOICES
        .filter { it > 0 && requestedRefreshRate(it, current.physicalWidth, current.physicalHeight, modes) > 0f }
        .toSet()
}

private fun Display.refreshModes(): List<RefreshRateMode> =
    supportedModes.map { RefreshRateMode(it.physicalWidth, it.physicalHeight, it.refreshRate) }

internal data class RefreshRateMode(val width: Int, val height: Int, val hertz: Float)

/** Match reported modes without changing resolution; 59.94/119.88 are 60/120 Hz choices. */
internal fun requestedRefreshRate(hertz: Int, width: Int, height: Int, modes: List<RefreshRateMode>): Float =
    if (hertz <= 0) {
        0f
    } else {
        modes.filter { it.width == width && it.height == height && it.hertz.isFinite() && it.hertz > 0f }
            .minByOrNull { abs(it.hertz - hertz) }
            ?.hertz
            ?.takeIf { abs(it - hertz) < 1f }
            ?: 0f
    }

/** Restore just our two fields, preserving unrelated window changes made while the preference lived. */
internal class WindowRefreshRateOverride(private val window: Window) {
    private val originalRate = window.attributes.preferredRefreshRate
    private val originalMode = window.attributes.preferredDisplayModeId

    fun apply(hertz: Float) {
        val attributes = window.attributes
        val rate = if (hertz > 0f) hertz else originalRate
        // A nonzero mode id overrides preferredRefreshRate, and could also change resolution.
        val mode = if (hertz > 0f) 0 else originalMode
        if (attributes.preferredRefreshRate != rate || attributes.preferredDisplayModeId != mode) {
            attributes.preferredRefreshRate = rate
            attributes.preferredDisplayModeId = mode
            window.attributes = attributes
        }
    }

    fun restore() = apply(0f)
}

/** Re-evaluate when the view attaches or its display changes; no per-frame polling or invalidation. */
private fun observeDisplayChanges(view: View, update: () -> Unit): () -> Unit {
    val manager = view.context.getSystemService(DisplayManager::class.java)
    var active = true
    val refresh = { if (active) update() }
    val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refresh()
        override fun onDisplayRemoved(displayId: Int) = refresh()
        override fun onDisplayChanged(displayId: Int) = refresh()
    }
    val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = refresh()
        override fun onViewDetachedFromWindow(v: View) = Unit
    }
    manager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
    view.addOnAttachStateChangeListener(attachListener)
    refresh()
    return {
        active = false
        view.removeOnAttachStateChangeListener(attachListener)
        manager.unregisterDisplayListener(listener)
    }
}
