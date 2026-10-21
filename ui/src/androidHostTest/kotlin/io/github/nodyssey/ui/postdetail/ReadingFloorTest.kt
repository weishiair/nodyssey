package io.github.nodyssey.ui.postdetail

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingFloorTest {
    @Test
    fun `opening post remains current while the first reply only peeks into the viewport`() {
        assertNull(layout(item(0, -100, 650), item(1, 550, 100), item(2, 650, 300)).readingCommentIndex(2, 10))
    }

    @Test
    fun `first reply waits for the top even after reaching the middle of the viewport`() {
        assertNull(layout(item(0, -600, 700), item(1, 100, 80), item(2, 180, 300)).readingCommentIndex(2, 10))
        assertNull(layout(item(1, -30, 80), item(2, 50, 300)).readingCommentIndex(2, 10))
        assertEquals(0, layout(item(2, 0, 300), item(3, 300, 300)).readingCommentIndex(2, 10))
    }

    @Test
    fun `a gap keeps the preceding floor until the next reply reaches the top`() {
        val before = layout(item(6, 1, 100), item(7, 101, 100))
        val reached = layout(item(6, 0, 100), item(7, 100, 100))
        assertEquals(4, reached.readingCommentIndex(2, 10))
        assertEquals(3, before.readingCommentIndex(2, 10))
    }

    @Test
    fun `a jumped reply counts at the content edge even when the preceding reply peeks into padding`() {
        assertEquals(
            4,
            layout(item(5, -108, 104), item(6, 0, 100), beforePadding = 8).readingCommentIndex(2, 10),
        )
        assertEquals(0, layout(item(2, 0, 300), beforePadding = 8).readingCommentIndex(2, 10))
    }

    @Test
    fun `partially visible long reply stays current until its text has scrolled away`() {
        assertEquals(3, layout(item(5, -600, 1000), item(6, 400, 100), item(7, 500, 100)).readingCommentIndex(2, 10))
        assertEquals(4, layout(item(6, -1, 100), item(7, 99, 100)).readingCommentIndex(2, 10))
    }

    @Test
    fun `footer retains the last floor and empty loading state has no reply`() {
        assertEquals(9, layout(item(12, -50, 900)).readingCommentIndex(2, 10))
        assertNull(layout(item(0, 0, 900)).readingCommentIndex(2, 0))
    }

    @Test
    fun `reaching the bottom reports the last reply even while earlier replies remain visible`() {
        val viewport = layout(item(9, -150, 180), item(10, 30, 160), item(11, 190, 160), item(12, 350, 200))
        assertEquals(7, viewport.readingCommentIndex(2, 10))
        assertEquals(9, viewport.readingCommentIndex(2, 10, atEnd = true))
    }

    @Test
    fun `an appended batch cannot become current before it has been laid out`() {
        val oldViewport = layout(item(10, -50, 160), item(11, 110, 160), item(12, 270, 100))
        assertEquals(8, oldViewport.readingCommentIndex(2, 20, atEnd = true))
    }

    private fun layout(vararg items: LazyListItemInfo, beforePadding: Int = 0) = object : LazyListLayoutInfo {
        override val visibleItemsInfo = items.toList()
        override val viewportStartOffset = -beforePadding
        override val viewportEndOffset = 800
        override val beforeContentPadding = beforePadding
        override val totalItemsCount = 13
    }

    private fun item(index: Int, offset: Int, size: Int) = object : LazyListItemInfo {
        override val index = index
        override val key = index
        override val offset = offset
        override val size = size
    }
}
