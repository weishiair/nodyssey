@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.plaza.designsys.component

import kotlinx.cinterop.useContents
import platform.Foundation.NSClassFromString
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSSelectorFromString
import platform.Foundation.setValue
import platform.UIKit.UIAction
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIImage
import platform.UIKit.UIMenu
import platform.UIKit.UIMenuElement
import platform.UIKit.UIMenuElementAttributesDestructive
import platform.UIKit.UIMenuElementAttributesDisabled
import platform.UIKit.UIMenuElementState
import platform.UIKit.accessibilityLabel
import platform.darwin.NSObject

actual val usesNativeChrome: Boolean = true

private val supportsLiquidGlass: Boolean
    get() = NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion >= 26 }

fun List<PlatformBarAction>.toUIKitMenu(): UIMenu = UIMenu.menuWithTitle("", map { it.toUIKitMenuElement() })

@OptIn(kotlinx.cinterop.BetaInteropApi::class)
fun PlatformBarAction.toUIKitBarButton(): UIBarButtonItem = (
    if (menu.isNotEmpty()) {
        UIBarButtonItem(label, systemImage?.let { UIImage.systemImageNamed(it) }, null, null, menu.toUIKitMenu()).apply { enabled = this@toUIKitBarButton.enabled }
    } else {
        UIBarButtonItem(primaryAction = toUIKitMenuElement() as UIAction).apply { enabled = this@toUIKitBarButton.enabled }
    }
    ).apply {
    accessibilityLabel = this@toUIKitBarButton.accessibilityLabel ?: this@toUIKitBarButton.label
    if (this@toUIKitBarButton.badge) {
        val indicator = if (supportsLiquidGlass) {
            // Kotlin/Native strongly links referenced Objective-C classes even behind a version guard.
            // Resolve the iOS 26 badge class at runtime until Kotlin supports weak class references.
            (NSClassFromString("UIBarButtonItemBadge") as? NSObject)?.performSelector(NSSelectorFromString("indicatorBadge"))
        } else {
            null
        }
        if (indicator != null) {
            setValue(indicator, forKey = "badge")
        } else {
            // Older UIKit has no bar-button badge API. Keep the update indicator visible as text.
            image = null
            title = "${this@toUIKitBarButton.label} •"
        }
    }
}

private fun PlatformBarAction.toUIKitMenuElement(): UIMenuElement {
    if (menu.isNotEmpty()) return UIMenu.menuWithTitle(label, menu.map { it.toUIKitMenuElement() })
    val operation = this
    return UIAction.actionWithTitle(label, systemImage?.let { UIImage.systemImageNamed(it) }, null) {
        if (operation.enabled) operation.onClick()
    }.apply {
        attributes = (if (operation.enabled) 0uL else UIMenuElementAttributesDisabled) or
            (if (operation.destructive) UIMenuElementAttributesDestructive else 0uL)
        state = if (operation.selected) UIMenuElementState.UIMenuElementStateOn else UIMenuElementState.UIMenuElementStateOff
    }
}
