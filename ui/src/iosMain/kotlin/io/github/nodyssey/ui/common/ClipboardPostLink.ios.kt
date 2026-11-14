package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable
import io.github.plaza.designsys.component.isOwnIosClipboard
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UIKit.UIPasteboard
import platform.UIKit.UIPasteboardDetectionPatternLink
import kotlin.coroutines.resume

internal actual val clipboardPostPromptSupported: Boolean = true

/**
 * Kept as a no-op so synchronous callers such as `StickerAddScreen` do not trigger iOS 16's
 * system paste prompt on screen open; foreground post-link detection goes through
 * [IosClipboardPostReader.readNewUrlText] instead.
 */
@Composable
internal actual fun rememberNewClipboardText(): () -> String? = { null }

/**
 * Two-step `UIPasteboard` reader for foreground post-link detection on iOS 16+.
 *
 * Checking [UIPasteboard.changeCount], [UIPasteboard.hasURLs], [UIPasteboard.hasStrings], and
 * `detectPatternsForPatterns` for [UIPasteboardDetectionPatternLink] only inspects
 * metadata and does not show iOS 16's paste permission dialog. Only when a new external clip is
 * confirmed to contain a web link is the pasteboard string or URL actually read. That final read
 * remains subject to the system's paste permission.
 */
internal object IosClipboardPostReader {
    private val reader = ClipboardUrlReader(IosClipboardUrlSource(UIPasteboard.generalPasteboard))

    suspend fun readNewUrlText(): String? = reader.readNewUrlText()
}

internal class IosClipboardUrlSource(private val pasteboard: UIPasteboard) : ClipboardUrlSource {
    override val changeCount: Long get() = pasteboard.changeCount
    override val isOwnCopy: Boolean get() = isOwnIosClipboard(pasteboard)
    override val hasText: Boolean get() = pasteboard.hasURLs || pasteboard.hasStrings

    override suspend fun containsWebLink(): Boolean = pasteboard.containsWebLink()

    override fun readText(): String? = when {
        pasteboard.hasURLs -> pasteboard.URL?.absoluteString
        pasteboard.hasStrings -> pasteboard.string
        else -> null
    }
}

private suspend fun UIPasteboard.containsWebLink(): Boolean =
    suspendCancellableCoroutine { continuation ->
        detectPatternsForPatterns(setOf(UIPasteboardDetectionPatternLink)) { matchedPatterns, error ->
            if (!continuation.isActive) return@detectPatternsForPatterns
            if (error != null || matchedPatterns == null) {
                continuation.resume(false)
            } else {
                continuation.resume(matchedPatterns.contains(UIPasteboardDetectionPatternLink))
            }
        }
    }
