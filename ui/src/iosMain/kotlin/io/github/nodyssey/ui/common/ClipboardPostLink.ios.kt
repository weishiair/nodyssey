package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable
import io.github.plaza.designsys.component.isOwnIosClipboardChange
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UIKit.UIPasteboard
import platform.UIKit.UIPasteboardDetectionPatternProbableWebURL
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
 * `detectPatternsForPatterns` for [UIPasteboardDetectionPatternProbableWebURL] only inspects
 * metadata and does not show iOS 16's paste permission dialog. Only when a new external clip is
 * confirmed to contain a probable web URL is the pasteboard string or URL actually read.
 */
internal object IosClipboardPostReader {
    private var examinedChangeCount: Long? = null

    suspend fun readNewUrlText(): String? {
        val pasteboard = UIPasteboard.generalPasteboard
        val changeCount = pasteboard.changeCount
        if (changeCount == examinedChangeCount) return null
        if (isOwnIosClipboardChange(changeCount)) {
            examinedChangeCount = changeCount
            return null
        }
        if (!pasteboard.hasURLs && !pasteboard.hasStrings) {
            examinedChangeCount = changeCount
            return null
        }
        val hasWebUrl = pasteboard.hasProbableWebUrl()
        if (pasteboard.changeCount == changeCount) {
            examinedChangeCount = changeCount
        }
        if (!hasWebUrl) return null
        return when {
            pasteboard.hasURLs -> pasteboard.URL?.absoluteString
            pasteboard.hasStrings -> pasteboard.string
            else -> null
        }
    }
}

private suspend fun UIPasteboard.hasProbableWebUrl(): Boolean =
    suspendCancellableCoroutine { continuation ->
        detectPatternsForPatterns(setOf(UIPasteboardDetectionPatternProbableWebURL)) { matchedPatterns, error ->
            if (!continuation.isActive) return@detectPatternsForPatterns
            if (error != null || matchedPatterns == null) {
                continuation.resume(false)
            } else {
                continuation.resume(matchedPatterns.contains(UIPasteboardDetectionPatternProbableWebURL))
            }
        }
    }
