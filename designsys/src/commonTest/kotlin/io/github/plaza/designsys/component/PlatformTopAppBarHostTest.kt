package io.github.plaza.designsys.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class PlatformTopAppBarHostTest {
    @Test
    fun `disposing an outgoing bar does not hide its replacement`() {
        val changes = mutableListOf<PlatformTopAppBar?>()
        val host = PlatformTopAppBarHost { changes += it }
        val outgoing = Any()
        val incoming = Any()
        val oldBar = bar("browse")
        val newBar = bar("selection")

        host.update(outgoing, oldBar)
        host.update(incoming, newBar)
        host.release(outgoing)

        assertSame(newBar, host.current)
        assertEquals(listOf<PlatformTopAppBar?>(oldBar, newBar), changes)

        // Leaving the native mode must still restore the screen's Compose toolbar.
        host.release(incoming)
        assertNull(host.current)
        assertEquals(listOf(oldBar, newBar, null), changes)
    }

    private fun bar(title: String) = PlatformTopAppBar(title, subtitle = null, navigation = null, actions = emptyList())

    @Test
    fun `closing a reply preview restores the underlying thread operations`() {
        val host = PlatformTopAppBarHost {}
        val thread = Any()
        val preview = Any()
        val threadBar = bar("thread")
        val previewBar = bar("preview")
        host.update(thread, threadBar)
        host.update(preview, previewBar)
        host.update(thread, threadBar.copy(title = "updated"))
        assertSame(previewBar, host.current)
        host.release(preview)
        assertEquals("updated", host.current?.title)
    }
}
