package io.github.nodyssey.ui.postdetail

import androidx.compose.foundation.lazy.LazyListLayoutInfo

/**
 * Advance only when a reply reaches the top reading edge, aligned with scrollToItem's content
 * padding. Replies lower in the viewport and the gap before the next reply keep the preceding floor.
 * Resume positions use the actual top item separately, so this display never skips unread text.
 */
internal fun LazyListLayoutInfo.readingCommentIndex(headerItems: Int, commentCount: Int, atEnd: Boolean = false): Int? {
    if (commentCount == 0) return null
    val end = headerItems + commentCount
    // Several short replies can still be visible when the list reaches its end. The top one no
    // longer describes reading progress there. Require the last reply in this layout so a newly
    // appended batch cannot advance the counter before Foundation has placed it.
    if (atEnd && visibleItemsInfo.any { it.index == end - 1 }) return commentCount - 1
    val first = visibleItemsInfo.firstOrNull {
        it.index in headerItems until end && it.offset + it.size > viewportStartOffset
    }
    if (first != null) {
        val readingEdge = viewportStartOffset + beforeContentPadding
        val atTop = visibleItemsInfo.lastOrNull { it.index in headerItems until end && it.offset <= readingEdge }
        return ((atTop?.index ?: (first.index - 1)) - headerItems).takeIf { it >= 0 }
    }
    // At the end marker / loading row, retain the last reply instead of resetting to the first.
    return (commentCount - 1).takeIf { (visibleItemsInfo.firstOrNull()?.index ?: -1) >= end }
}
