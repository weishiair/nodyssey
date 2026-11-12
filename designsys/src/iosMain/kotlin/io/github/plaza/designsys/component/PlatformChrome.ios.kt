@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package io.github.plaza.designsys.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSClassFromString
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSSelectorFromString
import platform.Foundation.setValue
import platform.UIKit.UIAction
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIButton
import platform.UIKit.UIButtonConfiguration
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIImage
import platform.UIKit.UIMenu
import platform.UIKit.UIMenuElement
import platform.UIKit.UIMenuElementAttributesDestructive
import platform.UIKit.UIMenuElementAttributesDisabled
import platform.UIKit.UIMenuElementState
import platform.UIKit.UIScrollView
import platform.UIKit.UIToolbar
import platform.UIKit.accessibilityLabel
import platform.darwin.NSObject

actual val usesNativeChrome: Boolean = true

private val supportsLiquidGlass: Boolean
    get() = NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion >= 26 }

@Composable
actual fun platformActionButton(action: PlatformBarAction, modifier: Modifier, showLabel: Boolean, prominent: Boolean): Boolean {
    val current = rememberUpdatedState(action)
    val tint = MaterialTheme.colorScheme.primary
    UIKitView(
        factory = {
            UIButton.buttonWithConfiguration(buttonConfiguration(prominent), primaryAction = null).apply {
                addAction(UIAction.actionWithTitle("", null, null) { current.value.takeIf { it.enabled }?.onClick?.invoke() }, UIControlEventTouchUpInside)
            }
        },
        modifier = modifier,
        // The native glass must sit over the Metal canvas to sample the content below it.
        properties = UIKitInteropProperties(isNativeAccessibilityEnabled = true, placedAsOverlay = true),
        update = { button ->
            button.configuration = buttonConfiguration(prominent).apply {
                title = action.label.takeIf { showLabel }
                image = action.systemImage?.let { UIImage.systemImageNamed(it) }
            }
            button.tintColor = UIColor(red = tint.red.toDouble(), green = tint.green.toDouble(), blue = tint.blue.toDouble(), alpha = tint.alpha.toDouble())
            button.accessibilityLabel = action.accessibilityLabel ?: action.label
            button.enabled = action.enabled
            button.selected = action.selected
            button.menu = action.menu.takeIf { it.isNotEmpty() }?.toUIKitMenu()
            button.showsMenuAsPrimaryAction = action.menu.isNotEmpty()
        },
    )
    return true
}

@Composable
actual fun platformActionToolbar(actions: List<PlatformBarAction>, modifier: Modifier): Boolean {
    UIKitView(factory = { ScrollingToolbar() }, modifier = modifier, properties = UIKitInteropProperties(isNativeAccessibilityEnabled = true, placedAsOverlay = true), update = { it.update(actions) })
    return true
}

private class ScrollingToolbar : UIScrollView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    private val toolbar = UIToolbar()
    private var itemCount = 0

    init {
        showsHorizontalScrollIndicator = false
        addSubview(toolbar)
    }

    fun update(actions: List<PlatformBarAction>) {
        itemCount = actions.size
        toolbar.setItems(actions.map { it.toUIKitBarButton() }, animated = false)
        setNeedsLayout()
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        bounds.useContents {
            val width = maxOf(size.width, itemCount * 48.0)
            toolbar.setFrame(CGRectMake(0.0, 0.0, width, size.height))
            setContentSize(CGSizeMake(width, size.height))
        }
    }
}

private fun buttonConfiguration(prominent: Boolean): UIButtonConfiguration =
    if (supportsLiquidGlass) {
        if (prominent) UIButtonConfiguration.prominentGlassButtonConfiguration() else UIButtonConfiguration.glassButtonConfiguration()
    } else {
        if (prominent) UIButtonConfiguration.filledButtonConfiguration() else UIButtonConfiguration.grayButtonConfiguration()
    }

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
