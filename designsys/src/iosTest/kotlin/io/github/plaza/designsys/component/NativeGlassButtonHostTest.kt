@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.plaza.designsys.component

import platform.UIKit.UIColor
import platform.UIKit.UIControlEventPrimaryActionTriggered
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Reusing UIKit controls must not retain the factory's account or publish an operation twice.
 * The toolbar's plain-button configuration exercises the same dispatch on every supported iOS.
 */
class NativeGlassButtonHostTest {
    @Test
    fun `a reused button uses the current action rather than the factory action`() {
        val opened = mutableListOf<String>()
        val initial = GlassAction("Open account", { opened += "previous account" }, title = "Open")
        val host = NativeGlassButtonHost(initial, grouped = true)
        host.update(initial, UIColor.blueColor, dark = false, scale = 1f)
        host.update(initial.copy(onClick = { opened += "current account" }), UIColor.blueColor, dark = false, scale = 1f)

        host.view.sendActionsForControlEvents(UIControlEventPrimaryActionTriggered)

        assertEquals(listOf("current account"), opened)
    }

    @Test
    fun `a queued native action cannot publish again after entering the loading state`() {
        var published = 0
        val ready = GlassAction("Publish", { published++ }, title = "Publish")
        val host = NativeGlassButtonHost(ready, grouped = true)
        host.update(ready, UIColor.blueColor, dark = false, scale = 1f)
        host.view.sendActionsForControlEvents(UIControlEventPrimaryActionTriggered)
        host.update(ready.copy(loading = true), UIColor.blueColor, dark = false, scale = 1f)

        // Direct dispatch models an action already queued before the control was disabled.
        host.view.sendActionsForControlEvents(UIControlEventPrimaryActionTriggered)

        assertFalse(host.view.enabled)
        assertEquals(1, published)
        host.update(ready, UIColor.blueColor, dark = false, scale = 1f)
        host.view.sendActionsForControlEvents(UIControlEventPrimaryActionTriggered)
        assertEquals(2, published)
    }
}
