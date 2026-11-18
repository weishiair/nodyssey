package io.github.plaza.designsys.component

import platform.Foundation.NSDate
import platform.Foundation.NSRunLoop
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import platform.UIKit.UIPasteboard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class IosClipboardTest {
    @Test
    fun `an own copy stays marked when UIKit advances its change count`() {
        val pasteboard = UIPasteboard.pasteboardWithUniqueName()
        try {
            val text = "https://example.com/thread/42"
            val previousChangeCount = pasteboard.changeCount
            pasteboard.setOwnPlainText(text)
            assertTrue(isOwnIosClipboard(pasteboard))

            val started = TimeSource.Monotonic.markNow()
            while (pasteboard.changeCount == previousChangeCount && started.elapsedNow() < 1.seconds) {
                NSRunLoop.currentRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.01))
            }

            assertNotEquals(previousChangeCount, pasteboard.changeCount)
            assertTrue(isOwnIosClipboard(pasteboard))
            assertEquals(text, pasteboard.string)
        } finally {
            UIPasteboard.removePasteboardWithName(pasteboard.name)
        }
    }

    @Test
    fun `a fresh external copy of identical text is not mistaken for an own copy`() {
        val pasteboard = UIPasteboard.pasteboardWithUniqueName()
        try {
            val text = "https://example.com/thread/42"
            pasteboard.setOwnPlainText(text)
            assertTrue(isOwnIosClipboard(pasteboard))

            // An external producer supplies text without this process's private pasteboard type.
            pasteboard.string = text

            assertFalse(isOwnIosClipboard(pasteboard))
            assertEquals(text, pasteboard.string)
        } finally {
            UIPasteboard.removePasteboardWithName(pasteboard.name)
        }
    }
}
