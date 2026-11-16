package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect

/**
 * Hands a piece of text to whatever this platform means by 分享.
 *
 * Text and not bytes on purpose at both call sites: a thread is a link, and an attachment is a hosted
 * URL that stays a link for the person receiving it.
 *
 * [chooserTitle] is what Android's chooser is labelled with, and null where the caller has nothing
 * better to say than the platform's own default.
 *
 * [anchorInWindow] supplies an optional window-relative trigger rectangle in px for iPad popovers.
 */
@Composable
expect fun rememberShareText(
    anchorInWindow: () -> Rect? = { null },
): (text: String, chooserTitle: String?) -> Unit
