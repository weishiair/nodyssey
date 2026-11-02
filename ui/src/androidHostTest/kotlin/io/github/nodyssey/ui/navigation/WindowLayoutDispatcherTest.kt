package io.github.nodyssey.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowLayoutDispatcherTest {
    private val posted = ArrayDeque<() -> Unit>()
    private var width = 768
    private val appliedWidths = mutableListOf<Int>()
    private val dispatcher = WindowLayoutDispatcher(
        post = { posted.addLast(it) },
        onLayout = { appliedWidths += width },
    )

    private fun drain() {
        while (posted.isNotEmpty()) posted.removeFirst().invoke()
    }

    @Test
    fun `layout never reparents controllers inside the layout callback`() {
        dispatcher.request()

        assertEquals(emptyList<Int>(), appliedWidths)
        drain()
        assertEquals(listOf(768), appliedWidths)
    }

    @Test
    fun `rotation completes and unwinds before the latest width is applied`() {
        lateinit var finish: () -> Unit
        dispatcher.request { completion ->
            finish = completion
            true
        }
        width = 1024
        dispatcher.request()
        drain()
        assertEquals(emptyList<Int>(), appliedWidths)

        finish()
        assertEquals(emptyList<Int>(), appliedWidths)
        drain()
        assertEquals(listOf(1024), appliedWidths)
    }

    @Test
    fun `an update posted before rotation cannot change containers during it`() {
        dispatcher.request()
        lateinit var finish: () -> Unit
        dispatcher.request { completion ->
            finish = completion
            true
        }
        drain()
        assertEquals(emptyList<Int>(), appliedWidths)

        width = 1024
        finish()
        drain()
        assertEquals(listOf(1024), appliedWidths)
    }

    @Test
    fun `cancelling rotation uses the restored bounds instead of the proposed width`() {
        lateinit var finish: () -> Unit
        dispatcher.request { completion ->
            finish = completion
            true
        }
        width = 1024
        dispatcher.request()
        width = 768
        finish()
        drain()

        assertEquals(listOf(768), appliedWidths)
    }

    @Test
    fun `repeated layouts share one completion and one update`() {
        val completions = mutableListOf<() -> Unit>()
        repeat(3) {
            dispatcher.request { completion ->
                completions += completion
                true
            }
        }
        assertEquals(1, completions.size)
        completions.single().invoke()
        dispatcher.request()
        dispatcher.request()
        assertEquals(1, posted.size)
        drain()

        assertEquals(listOf(768), appliedWidths)
    }

    @Test
    fun `a coordinator that has stopped accepting completions does not lose the update`() {
        dispatcher.request { false }
        assertEquals(emptyList<Int>(), appliedWidths)
        drain()

        assertEquals(listOf(768), appliedWidths)
    }
}
