@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.plaza.designsys.component

import kotlinx.cinterop.CValue
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPoint
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGRectZero
import platform.UIKit.UIBlurEffect
import platform.UIKit.UIBlurEffectStyle
import platform.UIKit.UIEvent
import platform.UIKit.UIView
import platform.UIKit.UIVisualEffectView

/**
 * A standalone navigation bar over one background that may reach past it.
 *
 * The bar sits inside [top], [left] and [right]; [background] fills the whole host. The post's
 * top bar passes the status bar as [top], so a single system blur covers both instead of a solid
 * strip meeting the bar's own blur at a seam. Touches outside the bar fall through to Compose.
 */
internal class NativeTopBarHost(focus: NativeMenuFocus) : UIView(frame = CGRectZero.readValue()) {
    val bar = FocusReleasingNavigationBar(focus)
    private val background = UIVisualEffectView(effect = null)
    var top = 0.0
    var left = 0.0
    var right = 0.0

    init {
        addSubview(background)
        addSubview(bar)
    }

    var blurred: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            background.effect = if (value) UIBlurEffect.effectWithStyle(UIBlurEffectStyle.UIBlurEffectStyleSystemMaterial) else null
        }

    override fun layoutSubviews() {
        super.layoutSubviews()
        bounds.useContents {
            background.setFrame(CGRectMake(0.0, 0.0, size.width, size.height))
            bar.setFrame(
                CGRectMake(left, top, (size.width - left - right).coerceAtLeast(0.0), (size.height - top).coerceAtLeast(0.0)),
            )
        }
    }

    override fun hitTest(point: CValue<CGPoint>, withEvent: UIEvent?): UIView? =
        super.hitTest(point, withEvent)?.takeUnless { it == this || it == background }
}
