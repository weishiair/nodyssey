@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.plaza.designsys.component

import io.github.plaza.designsys.nativeglass.plaza_segmented_control_set_selected_index
import io.github.plaza.designsys.nativeglass.plaza_slider_set_value
import io.github.plaza.designsys.nativeglass.plaza_slider_value
import io.github.plaza.designsys.nativeglass.plaza_switch_is_on
import io.github.plaza.designsys.nativeglass.plaza_switch_set_on
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventTouchCancel
import platform.UIKit.UIControlEventTouchDown
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.isAccessibilityElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NativeSettingsControlsHostTest {
    @Test
    fun `switch host uses latest callback and ignores programmatic sync or disabled events`() {
        val changes = mutableListOf<String>()
        val host = NativeSettingsSwitchHost()
        host.update(
            checked = false,
            enabled = true,
            label = "Wi-Fi images",
            accessibilityEnabled = false,
            accent = UIColor.blueColor,
            dark = false,
            onCheckedChange = { changes += "first:$it" },
        )
        assertTrue(changes.isEmpty())
        assertFalse(host.view.isAccessibilityElement)

        // Programmatic state sync must not echo back into business callbacks.
        host.update(
            checked = true,
            enabled = true,
            label = "Wi-Fi images",
            accessibilityEnabled = false,
            accent = UIColor.blueColor,
            dark = false,
            onCheckedChange = { changes += "second:$it" },
        )
        assertTrue(plaza_switch_is_on(host.view))
        assertTrue(changes.isEmpty())

        // User toggle dispatches the updated callback once.
        plaza_switch_set_on(host.view, false, animated = false)
        host.view.sendActionsForControlEvents(UIControlEventValueChanged)
        assertEquals(listOf("second:false"), changes)

        // Queued events arriving after disable or dispose must be ignored.
        host.update(
            checked = false,
            enabled = false,
            label = "Wi-Fi images",
            accessibilityEnabled = false,
            accent = UIColor.blueColor,
            dark = false,
            onCheckedChange = { changes += "disabled:$it" },
        )
        plaza_switch_set_on(host.view, true, animated = false)
        host.view.sendActionsForControlEvents(UIControlEventValueChanged)
        assertEquals(listOf("second:false"), changes)

        host.dispose()
        host.view.sendActionsForControlEvents(UIControlEventValueChanged)
        assertEquals(listOf("second:false"), changes)
    }

    @Test
    fun `slider host previews continuously while dragging and commits only once on release`() {
        val previews = mutableListOf<Float>()
        var committedCount = 0
        var lastPreview = 16f
        val host = NativeSliderHost()
        fun syncHost(value: Float, enabled: Boolean = true) {
            host.update(
                value = value,
                min = 14f,
                max = 24f,
                steps = 9,
                enabled = enabled,
                label = "Body font size",
                valueDescription = "${value.toInt()} sp",
                accent = UIColor.blueColor,
                dark = false,
                onValueChange = {
                    lastPreview = it
                    previews += it
                    syncHost(it, enabled = enabled)
                },
                onValueChangeFinished = { committedCount++ },
            )
        }
        syncHost(16f)
        assertTrue(previews.isEmpty())
        assertEquals(0, committedCount)

        host.view.sendActionsForControlEvents(UIControlEventTouchDown)
        plaza_slider_set_value(host.view, 18.4f)
        host.view.sendActionsForControlEvents(UIControlEventValueChanged)
        plaza_slider_set_value(host.view, 20.6f)
        host.view.sendActionsForControlEvents(UIControlEventValueChanged)

        assertEquals(listOf(18f, 21f), previews)
        assertEquals(0, committedCount)

        host.view.sendActionsForControlEvents(UIControlEventTouchUpInside)
        assertEquals(1, committedCount)
        assertEquals(21f, plaza_slider_value(host.view))
        assertEquals(21f, lastPreview)

        // Duplicate up events without a new touch down must not re-commit.
        host.view.sendActionsForControlEvents(UIControlEventTouchUpInside)
        assertEquals(1, committedCount)
    }

    @Test
    fun `slider host reverts preview on cancel and commits VoiceOver step without TouchDown`() {
        val previews = mutableListOf<Float>()
        var committedCount = 0
        var currentPreview = 30f
        val host = NativeSliderHost()
        fun syncHost(value: Float, enabled: Boolean = true) {
            host.update(
                value = value,
                min = 20f,
                max = 90f,
                steps = 13,
                enabled = enabled,
                label = "Sticker size",
                valueDescription = "${value.toInt()} sp",
                accent = UIColor.blueColor,
                dark = false,
                onValueChange = {
                    currentPreview = it
                    previews += it
                    syncHost(it, enabled = enabled)
                },
                onValueChangeFinished = { committedCount++ },
            )
        }
        syncHost(30f)

        // Cancelled drag restores baseline without committing.
        host.view.sendActionsForControlEvents(UIControlEventTouchDown)
        plaza_slider_set_value(host.view, 54f)
        host.view.sendActionsForControlEvents(UIControlEventValueChanged)
        assertEquals(55f, currentPreview)
        assertEquals(0, committedCount)

        host.view.sendActionsForControlEvents(UIControlEventTouchCancel)
        assertEquals(30f, currentPreview)
        assertEquals(30f, plaza_slider_value(host.view))
        assertEquals(0, committedCount)

        // VoiceOver increment emits ValueChanged without TouchDown and must commit.
        plaza_slider_set_value(host.view, 43f)
        host.view.sendActionsForControlEvents(UIControlEventValueChanged)
        assertEquals(45f, currentPreview)
        assertEquals(45f, plaza_slider_value(host.view))
        assertEquals(1, committedCount)

        // Disabled or disposed hosts reject queued VoiceOver/touch notifications.
        syncHost(45f, enabled = false)
        plaza_slider_set_value(host.view, 60f)
        host.view.sendActionsForControlEvents(UIControlEventValueChanged)
        assertEquals(45f, currentPreview)
        assertEquals(1, committedCount)

        host.dispose()
        plaza_slider_set_value(host.view, 70f)
        host.view.sendActionsForControlEvents(UIControlEventTouchUpInside)
        assertEquals(1, committedCount)
    }

    @Test
    fun `segmented host skips spurious update events and blocks disabled segment selection`() {
        val selected = mutableListOf<Int>()
        val host = NativeSegmentedControlHost()
        host.update(
            labels = listOf("Auto", "Light", "Dark"),
            selectedIndex = 0,
            enabled = true,
            segmentEnabled = listOf(true, true, false),
            label = "Theme mode",
            dark = false,
            scale = 1f,
            onSelectIndex = { selected += it },
        )
        assertTrue(selected.isEmpty())

        // Selecting an enabled segment fires once.
        plaza_segmented_control_set_selected_index(host.view, 1)
        host.view.sendActionsForControlEvents(UIControlEventValueChanged)
        assertEquals(listOf(1), selected)

        // Queued selection on a disabled segment reverts to the prior valid selection.
        plaza_segmented_control_set_selected_index(host.view, 2)
        host.view.sendActionsForControlEvents(UIControlEventValueChanged)
        assertEquals(listOf(1), selected)
    }

    @Test
    fun `menu action resolution matches by index and blocks disabled actions`() {
        val calls = mutableListOf<String>()
        val items =
            listOf(
                GlassMenuAction("Option A", { calls += "first" }, enabled = true),
                GlassMenuAction("Option A", { calls += "second" }, enabled = true),
                GlassMenuAction("Wallpaper", { calls += "wallpaper" }, enabled = false),
            )
        resolveNativeMenuAction(items, index = 1, label = "Option A")?.onClick?.invoke()
        assertEquals(listOf("second"), calls)
        assertNull(resolveNativeMenuAction(items, index = 2, label = "Wallpaper"))

        val overlayHost = NativeMenuOverlayHost()
        overlayHost.update("Theme", items, dark = false, enabled = false)
        assertFalse(overlayHost.view.enabled)
    }
}
