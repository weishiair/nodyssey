@file:OptIn(ExperimentalForeignApi::class)

package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIActivityViewController
import platform.UIKit.popoverPresentationController

/**
 * `UIActivityViewController`, which is what 分享 means on iOS.
 *
 * [chooserTitle] is dropped: the share sheet is titled by the system and labelling it is not
 * something the API offers. It stays in the signature because Android's chooser does take one.
 *
 * The presenting controller comes from [topmostViewController], which every modal here shares.
 */
@Composable
actual fun rememberShareText(
    anchorInWindow: () -> Rect?,
): (text: String, chooserTitle: String?) -> Unit {
    val density = LocalDensity.current.density
    val currentAnchor by rememberUpdatedState(anchorInWindow)
    return remember(density) {
        { text, _ ->
            topmostViewController()?.let { host ->
                val sheet =
                    UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
                sheet.popoverPresentationController?.let { popover ->
                    popover.sourceView = host.view
                    val anchor = currentAnchor()
                    popover.sourceRect = host.view.bounds.useContents {
                        if (anchor != null && density > 0f) {
                            val x = (anchor.left / density).toDouble().coerceIn(0.0, size.width)
                            val y = (anchor.top / density).toDouble().coerceIn(0.0, size.height)
                            val w = (anchor.width / density).toDouble().coerceIn(1.0, (size.width - x).coerceAtLeast(1.0))
                            val h = (anchor.height / density).toDouble().coerceIn(1.0, (size.height - y).coerceAtLeast(1.0))
                            CGRectMake(x, y, w, h)
                        } else {
                            CGRectMake(size.width / 2.0, size.height / 2.0, 1.0, 1.0)
                        }
                    }
                }
                host.presentViewController(sheet, animated = true, completion = null)
            }
        }
    }
}
