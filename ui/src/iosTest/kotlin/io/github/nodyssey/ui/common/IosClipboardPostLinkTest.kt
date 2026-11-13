package io.github.nodyssey.ui.common

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSDate
import platform.Foundation.NSRunLoop
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import platform.UIKit.UIPasteboard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class IosClipboardPostLinkTest {
    @Test
    fun `a shared message containing a title and a post link reaches the post parser`() {
        val pasteboard = UIPasteboard.pasteboardWithUniqueName()
        val scope = MainScope()
        try {
            pasteboard.string = "A shared post\nhttps://www.nodeseek.com/post-42-1"
            // Model an external copy completed before the app regains focus.
            NSRunLoop.currentRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.01))
            val reader = ClipboardUrlReader(IosClipboardUrlSource(pasteboard))
            val result = scope.async { reader.readNewUrlText() }
            val started = TimeSource.Monotonic.markNow()
            while (!result.isCompleted && started.elapsedNow() < 5.seconds) {
                NSRunLoop.currentRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.01))
            }

            assertTrue(result.isCompleted, "UIKit pattern detection did not complete")
            val text = runBlocking { result.await() }
            assertEquals(ClipboardPost("https://www.nodeseek.com/post-42-1", 42, 1), clipboardPostToOffer(text, null))
        } finally {
            scope.cancel()
            UIPasteboard.removePasteboardWithName(pasteboard.name)
        }
    }
}
