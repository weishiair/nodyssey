@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.UIKit.accessibilityElementsHidden

/**
 * Keeps a native glass view inside the clip Compose would give it.
 *
 * Glass is placed as an overlay so it can sample the content behind it, and an overlay sits above
 * the whole canvas: an ancestor's `clip` — the home header folding away under the status bar — never
 * reaches it, and the control slides up over the clock instead of disappearing with its row. This
 * masks the view to the part of its slot that is still visible, and stops it taking touches or
 * reaching VoiceOver once nothing is left.
 */
@Composable
internal fun Modifier.clipNativeToCompose(view: UIView): Modifier {
    val density = LocalDensity.current.density
    val mask = remember { UIView().apply { backgroundColor = UIColor.blackColor } }
    return onGloballyPositioned { coordinates ->
        val origin = coordinates.positionInWindow()
        val visible = coordinates.boundsInWindow()
        val width = coordinates.size.width
        val height = coordinates.size.height
        val whole = visible.width >= width - 0.5f && visible.height >= height - 0.5f
        val gone = visible.width <= 0f || visible.height <= 0f
        if (whole) {
            view.maskView = null
        } else {
            mask.setFrame(
                if (gone) {
                    CGRectMake(0.0, 0.0, 0.0, 0.0)
                } else {
                    CGRectMake(
                        ((visible.left - origin.x) / density).toDouble(),
                        ((visible.top - origin.y) / density).toDouble(),
                        (visible.width / density).toDouble(),
                        (visible.height / density).toDouble(),
                    )
                },
            )
            view.maskView = mask
        }
        view.userInteractionEnabled = !gone
        view.accessibilityElementsHidden = gone
    }
}
