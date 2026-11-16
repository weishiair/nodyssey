package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect

/**
 * No share sheet on a desktop, and the honest fallback would be a clipboard copy — which is a
 * different action with a different confirmation, so the menu item does nothing here rather than
 * quietly doing something else.
 */
@Composable
actual fun rememberShareText(
    anchorInWindow: () -> Rect?,
): (text: String, chooserTitle: String?) -> Unit = { _, _ -> }
