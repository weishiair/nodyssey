package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalClipboard
import platform.Foundation.NSData
import platform.Foundation.NSUUID
import platform.UIKit.UIPasteboard
import platform.UniformTypeIdentifiers.UTTypeUTF8PlainText

/**
 * The iOS answer to [rememberPlainTextClipboardWrite]: one item carrying text and its origin.
 *
 * Compose 1.13's `ClipEntry.withPlainText` only writes `UIPasteboard.string`; it cannot attach a
 * private pasteboard type. Use the composition's `nativeClipboard.items` so the marker and text
 * are written atomically. Remove this native write when Compose can carry custom pasteboard types.
 * The label is dropped because `UIPasteboard` has no per-item label.
 */
@Composable
internal actual fun rememberPlainTextClipboardWrite(): suspend (label: String, text: String) -> Unit {
    // 1.13.0-alpha01 deprecates this member but ships no replacement iOS accessor yet.
    // Remove the suppression when its platform-specific replacement is available.
    @Suppress("DEPRECATION")
    val pasteboard = LocalClipboard.current.nativeClipboard
    return remember(pasteboard) { { _, text -> pasteboard.setOwnPlainText(text) } }
}

/**
 * Nothing, for the same reason as desktop and a different mechanism.
 *
 * iOS says nothing about a copy either — there is no system confirmation the way Android 13 has one
 * — but it also gives the app nothing shaped like a toast to say it with. A surface that wants the
 * reader told still has the confirmation string and its own snackbar; this is only the answer to
 * "does the OS say so already", and on iOS it does not.
 */
@Composable
internal actual fun rememberCopyConfirmation(): (confirmation: String) -> Unit = remember { {} }

// Process-specific, so a copy from another app using this library is still an external copy.
private val ownPasteboardType = "io.github.plaza.designsys.own-clip.${NSUUID().UUIDString().lowercase()}"

internal fun UIPasteboard.setOwnPlainText(text: String) {
    items = listOf(mapOf(UTTypeUTF8PlainText.identifier to text, ownPasteboardType to NSData()))
}

/**
 * Checks the current item's types, not its contents or a cached change count. UIKit can delay
 * updating `changeCount` until the end of an event loop; an item marker is already valid then.
 */
fun isOwnIosClipboard(pasteboard: UIPasteboard): Boolean =
    pasteboard.containsPasteboardTypes(listOf(ownPasteboardType))
