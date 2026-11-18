package io.github.nodyssey.ui.common

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClipboardUrlReaderTest {
    @Test
    fun `a replacement cannot inherit an earlier clipboard item's pattern match`() = runTest {
        val source = TestClipboardSource("https://www.nodeseek.com/post-41-1")
        val reader = ClipboardUrlReader(source)
        val detection = CompletableDeferred<Boolean>()
        source.detect = { detection.await() }
        val first = async { reader.readNewUrlText() }
        runCurrent()

        source.text = "https://www.nodeseek.com/post-42-1"
        source.changeCount++
        detection.complete(true)

        assertNull(first.await())
        assertEquals(0, source.reads)

        source.detect = { true }
        assertEquals("https://www.nodeseek.com/post-42-1", reader.readNewUrlText())
        assertEquals(2, source.detections)
        assertEquals(1, source.reads)
    }

    @Test
    fun `an own copy made during detection is skipped before its count advances`() = runTest {
        val source = TestClipboardSource("https://www.nodeseek.com/post-41-1")
        val reader = ClipboardUrlReader(source)
        source.detect = {
            source.text = "https://www.nodeseek.com/post-42-1"
            source.isOwnCopy = true
            true
        }

        assertNull(reader.readNewUrlText())
        assertEquals(0, source.reads)
    }

    @Test
    fun `returning to the app does not read the same external item again`() = runTest {
        val source = TestClipboardSource("https://www.nodeseek.com/post-42-1")
        val reader = ClipboardUrlReader(source)

        assertEquals("https://www.nodeseek.com/post-42-1", reader.readNewUrlText())
        assertNull(reader.readNewUrlText())
        assertEquals(1, source.detections)
        assertEquals(1, source.reads)
    }

    @Test
    fun `a cancelled detection does not consume the clipboard item`() = runTest {
        val source = TestClipboardSource("https://www.nodeseek.com/post-42-1")
        val reader = ClipboardUrlReader(source)
        val detection = CompletableDeferred<Boolean>()
        source.detect = { detection.await() }
        val interrupted = async { reader.readNewUrlText() }
        runCurrent()
        interrupted.cancelAndJoin()

        source.detect = { true }
        assertEquals("https://www.nodeseek.com/post-42-1", reader.readNewUrlText())
        assertEquals(2, source.detections)
        assertEquals(1, source.reads)
    }

    private class TestClipboardSource(var text: String) : ClipboardUrlSource {
        override var changeCount = 0L
        override var isOwnCopy = false
        override val hasText = true
        var detect: suspend () -> Boolean = { true }
        var detections = 0
        var reads = 0

        override suspend fun containsWebLink(): Boolean {
            detections++
            return detect()
        }

        override fun readText(): String {
            reads++
            return text
        }
    }
}
