@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.plaza.designsys.component

import androidx.compose.ui.graphics.Color
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventPrimaryActionTriggered
import platform.UIKit.UIView
import platform.UIKit.labelColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun `grouped prominent action keeps plain toolbar background while reporting visual swaps`() {
        val page = GlassAction("Page", {}, title = "1", subtitle = "/ 10")
        val refresh = GlassAction("Refresh", {}, symbol = "arrow.clockwise", prominent = true)
        val host = NativeGlassButtonHost(page, grouped = true)
        host.update(page, UIColor.grayColor, dark = false, scale = 1f)

        assertTrue(host.hasVisualChange(refresh))
        host.update(refresh, UIColor.blueColor, dark = false, scale = 1f)

        assertFalse(host.hasVisualChange(refresh))
        assertNull(host.view.configuration?.baseBackgroundColor)
    }

    @Test
    fun `horizontal toolbar keeps trailing button inside narrow bounds without clipping`() {
        val host = NativeGlassToolbarHost(vertical = false)
        val actions = listOf(
            GlassAction("Mark selected as read", {}, title = "Mark all selected as read", symbol = "checkmark.circle"),
            GlassAction("Move selected", {}, title = "Move to another folder", symbol = "folder"),
            GlassAction("Tags", {}, title = "Tags", symbol = "tag"),
        )
        host.update(actions, UIColor.labelColor, UIColor.blueColor, Color.White, dark = false, scale = 1f)
        val toolbarWidth = 240.0
        host.view.setFrame(CGRectMake(0.0, 0.0, toolbarWidth, 56.0))
        host.view.layoutIfNeeded()

        val arranged = host.stack.arrangedSubviews
        assertEquals(3, arranged.size)
        val trailingButton = arranged.last() as UIView
        val frameInContent = host.view.contentView.convertRect(trailingButton.frame, fromView = host.stack)
        val (rightEdge, width) = frameInContent.useContents { (origin.x + size.width) to size.width }
        assertTrue(rightEdge <= toolbarWidth + 0.5, "Trailing button overflowed toolbar: rightEdge=$rightEdge")
        assertTrue(width >= 44.0, "Trailing button was squashed below tappable width: width=$width")
    }
}
