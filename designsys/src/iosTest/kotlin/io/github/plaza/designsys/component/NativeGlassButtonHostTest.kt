@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.plaza.designsys.component

import androidx.compose.ui.graphics.Color
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventPrimaryActionTriggered
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
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
    fun `top bar updates title without replacing its navigation item or menu bar item`() {
        val host = NativeTopBarHost()
        val menu = listOf(GlassMenuAction("收藏", {}, symbol = "bookmark"))
        val actions = listOf(
            GlassAction("浏览器", {}, symbol = "safari"),
            GlassAction("更多", {}, symbol = "ellipsis", menu = menu),
        )
        host.update(
            title = "",
            subtitle = null,
            titleAction = null,
            back = GlassAction("返回", {}, symbol = "chevron.backward"),
            actions = actions,
            accent = UIColor.blueColor,
            foreground = UIColor.blackColor,
            mutedForeground = UIColor.grayColor,
            background = Color.White,
            nativeGlass = false,
            dark = false,
            scale = 1f,
        )
        val initialItem = host.bar.topItem
        val initialRightItems = host.item.rightBarButtonItems
        val initialMoreMenu = host.item.rightBarButtonItems?.firstOrNull()

        host.update(
            title = "帖子标题",
            subtitle = null,
            titleAction = GlassAction("回到主帖", {}),
            back = GlassAction("返回", {}, symbol = "chevron.backward"),
            actions = actions,
            accent = UIColor.blueColor,
            foreground = UIColor.blackColor,
            mutedForeground = UIColor.grayColor,
            background = Color.White,
            nativeGlass = false,
            dark = false,
            scale = 1f,
        )

        assertSame(initialItem, host.bar.topItem)
        assertEquals(initialRightItems, host.item.rightBarButtonItems)
        assertSame(initialMoreMenu, host.item.rightBarButtonItems?.firstOrNull())
        assertTrue(host.item.titleView != null)
    }
}
