@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.plaza.designsys.component

import kotlinx.cinterop.CValue
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGPoint
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIColor
import platform.UIKit.UIDeferredMenuElement
import platform.UIKit.UIEvent
import platform.UIKit.UIMenuElement
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIWindow

/**
 * Keeps the tap that closes a native menu from also landing on the Compose page underneath.
 *
 * In UIKit that tap only closes the menu: the menu's dismissal gesture wins over every gesture
 * recognizer below it, which is how a text field or a table row there stays untouched. Compose reads
 * raw touches rather than gesture recognizers, so on a Compose page the same tap closes the menu and
 * then opens whatever post card it fell on.
 *
 * While a menu is up, a clear view sits directly above the window's root view — below the menu
 * itself, which UIKit adds later and higher — and takes the next touch for itself, which is that
 * closing tap, then steps aside. Choosing an entry takes it down instead ([lower] from the entry's handler), so the
 * tap after a choice reaches the page as usual.
 *
 * Remove when Compose Multiplatform lets a UIKit gesture cancel its pointer input.
 */
internal object NativeMenuShield {
    private var shield: UIView? = null

    /** A menu child that raises the shield each time the menu around it is shown, and draws nothing. */
    fun element(anchor: () -> UIView?): UIMenuElement =
        UIDeferredMenuElement.elementWithUncachedProvider { completion ->
            anchor()?.window?.let(::raise)
            completion?.invoke(emptyList<UIMenuElement>())
        }

    fun lower() {
        shield?.removeFromSuperview()
        shield = null
    }

    private fun raise(window: UIWindow) {
        if (shield?.window === window) return
        lower()
        val root = window.rootViewController?.view ?: return
        val view = ShieldView().apply {
            backgroundColor = UIColor.clearColor
            autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
            setFrame(window.bounds)
        }
        window.insertSubview(view, aboveSubview = root)
        shield = view
    }

    private class ShieldView : UIView(frame = CGRectZero.readValue()) {
        // The closing tap reaches this view as a touch the menu then cancels, so neither began nor
        // ended can be relied on to arrive. Being hit is enough: this touch stays swallowed, and the
        // shield is gone before the next one is dispatched.
        override fun hitTest(point: CValue<CGPoint>, withEvent: UIEvent?): UIView? =
            super.hitTest(point, withEvent)?.also { NSOperationQueue.mainQueue.addOperationWithBlock { lower() } }
    }
}
