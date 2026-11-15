package io.github.nodyssey.core.html

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostPinParserTest {
    @Test
    fun `the OP may pin other readers and their own replies but not the opening post`() {
        val detail = parse("owner")
        assertFalse(requireNotNull(detail.body).canPin)
        assertEquals(listOf(true, true), detail.comments.map { it.canPin })
        assertEquals(listOf(false, true), detail.comments.map { it.isMine })
        assertEquals(listOf(false, true), detail.comments.map { it.isPinned })
    }

    @Test
    fun `ownership is available on later pages without the opening post`() {
        val detail = parse("owner-page2", page = 2)
        assertNull(detail.body)
        assertEquals(listOf(true, true), detail.comments.map { it.canPin })
    }

    @Test
    fun `writing a reply does not grant the right to pin in someone else's thread`() {
        val detail = parse("reader")
        assertTrue(detail.comments.first().isMine)
        assertEquals(listOf(false, false), detail.comments.map { it.canPin })
    }

    @Test
    fun `guests and pages without an OP identity cannot offer a paid pin`() {
        for (name in listOf("guest", "unknown-op")) {
            val detail = parse(name)
            assertEquals(listOf(false, false), detail.comments.map { it.canPin }, name)
        }
    }

    private fun parse(name: String, page: Int = 1) =
        PostDetailParser.parse(Fixtures.load("post-pin-$name.html"), postId = 42, page = page)
}
