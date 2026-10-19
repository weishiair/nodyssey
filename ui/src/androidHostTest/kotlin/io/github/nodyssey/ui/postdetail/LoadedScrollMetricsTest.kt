package io.github.nodyssey.ui.postdetail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoadedScrollMetricsTest {
    @Test
    fun `drag endpoints span all loaded batches and preserve deleted floor gaps`() {
        val keys = listOf("body", "header", "#121", "#125", "#130", "#131", "#140")
        val metrics = LoadedScrollMetrics(keys, 1400, 400, 500, 0, 0)
        assertEquals(LoadedScrollTarget("body", 0), metrics.targetAt(0f))
        assertEquals(LoadedScrollTarget("#140", 0), metrics.targetAt(1f))
        assertEquals(LoadedScrollTarget("#121", 100), metrics.targetAt(0.5f))
    }

    @Test
    fun `content padding is removed before mapping a drag onto a lazy item`() {
        val metrics = LoadedScrollMetrics(listOf("a", "b", "c", "d"), 1200, 400, 0, 100, 100)
        assertEquals(LoadedScrollTarget("b", 50), metrics.targetAt(0.5f))
    }

    @Test
    fun `a frozen drag retains its reply key when earlier replies arrive`() {
        val before = LoadedScrollMetrics(listOf("#121", "#125", "#130", "#140"), 800, 200, 0, 0, 0)
        val target = before.targetAt(0.5f)!!
        val keysAfterPrepend = listOf("#111", "#120") + before.keys
        assertEquals("#125", target.key)
        assertEquals(3, keysAfterPrepend.indexOf(target.key))
        assertEquals(100, target.offset)
    }

    @Test
    fun `a short or empty list cannot produce an invalid target or scroll fraction`() {
        val empty = LoadedScrollMetrics(emptyList(), 0, 400, 0, 0, 0)
        assertNull(empty.targetAt(0.5f))
        assertEquals(0f, empty.fraction)
        val short = empty.copy(keys = listOf("#8"), contentSize = 200)
        assertEquals(LoadedScrollTarget("#8", 0), short.targetAt(2f))
        assertEquals(0f, short.fraction)
    }
}
