@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.plaza.designsys.component

import kotlinx.cinterop.CValue
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGPoint
import platform.CoreGraphics.CGRectZero
import platform.UIKit.UIAction
import platform.UIKit.UIButton
import platform.UIKit.UIControlEventMenuActionTriggered
import platform.UIKit.UIEvent
import platform.UIKit.UIKeyInputProtocol
import platform.UIKit.UINavigationBar
import platform.UIKit.UIResponder
import platform.UIKit.UIView
import kotlin.native.ref.WeakReference

/**
 * Keeps a pop-up menu from bringing up the software keyboard.
 *
 * With a hardware keyboard attached — or one the simulator believes is, as a remote session's does,
 * connecting and disconnecting under every keystroke — a menu turns on type-select
 * (`_UITypeSelectKeyInput`) against whatever holds first responder. Under Compose that is its
 * `OverlayInputView`, held for hardware key events and never released; type-select makes UIKit treat
 * it as needing a keyboard, and when the hardware one reads as disconnected the software one comes up.
 * A plain UIKit page has no first responder there and shows none, so the menu is opened from that same
 * state: the non-text responder is released as the menu is triggered, and handed back when the control
 * owning the menu leaves composition, so Compose keeps its key events. Compose takes the focus back on
 * its own only when its window or its layers change, neither of which a menu does.
 *
 * A `UIBarButtonItem` sends no event as its menu opens, so a native navigation bar releases on the
 * touch that reaches it instead ([FocusReleasingNavigationBar]); nothing in the bar takes text.
 *
 * Remove when Compose Multiplatform stops holding first responder for hardware keys, or lets an
 * interop view opt out of it.
 */
@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
internal class NativeMenuFocus {
    // Weak so a navigation bar or button inside a ComposeUIViewController never forms an
    // ObjC/Kotlin retain cycle back through Compose's OverlayInputView when the page pops.
    private var released: WeakReference<UIResponder>? = null

    fun releaseFor(view: UIView) {
        val window = view.window ?: return
        val current = firstResponder(window) ?: return
        // A text field keeps its keyboard: that one is asked for.
        if (current is UIKeyInputProtocol) return
        if (current.resignFirstResponder()) released = WeakReference(current)
    }

    fun restore() {
        val responder = released?.get()
        released = null
        val window = (responder as? UIView)?.window ?: return
        if (firstResponder(window) == null) responder.becomeFirstResponder()
    }

    private fun firstResponder(view: UIView): UIView? {
        if (view.isFirstResponder()) return view
        return view.subviews.firstNotNullOfOrNull { firstResponder(it as UIView) }
    }
}

internal fun UIButton.releasesFocusForMenu(focus: NativeMenuFocus) {
    // The handler holds the focus object and not the button: UIAction retains what it captures.
    addAction(
        UIAction.actionWithHandler { action -> (action?.sender as? UIView)?.let(focus::releaseFor) },
        forControlEvents = UIControlEventMenuActionTriggered,
    )
}

/** A navigation bar that lets go of Compose's responder before any of its items can open a menu. */
internal class FocusReleasingNavigationBar(
    private val focus: NativeMenuFocus,
) : UINavigationBar(frame = CGRectZero.readValue()) {
    override fun hitTest(point: CValue<CGPoint>, withEvent: UIEvent?): UIView? =
        super.hitTest(point, withEvent)?.also { focus.releaseFor(this) }
}
