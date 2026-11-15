package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import java.awt.datatransfer.StringSelection

/** The desktop answer to [rememberPlainTextClipboardWrite]: an AWT [StringSelection]. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun rememberPlainTextClipboardWrite(): suspend (label: String, text: String) -> Unit {
    val clipboard = LocalClipboard.current
    return remember(clipboard) { { _, text -> clipboard.setClipEntry(ClipEntry(StringSelection(text))) } }
}

/**
 * Nothing, on purpose.
 *
 * A desktop copy is a keyboard shortcut's worth of interaction and no desktop platform announces
 * one; a toast-shaped popup appearing over a window would be this app inventing a convention rather
 * than following one. The confirmation text still exists — a surface that wants to show it has the
 * string — this is only the answer to "does the OS say so already".
 */
@Composable
internal actual fun rememberCopyConfirmation(): (confirmation: String) -> Unit = remember { {} }
