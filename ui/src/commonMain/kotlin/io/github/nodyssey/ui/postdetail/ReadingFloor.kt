package io.github.nodyssey.ui.postdetail

import androidx.compose.foundation.lazy.LazyListLayoutInfo

/**
 * Keep the top reply while it is readable. When the opening post or reply header still occupies
 * the top, start counting once the first reply reaches the viewport's middle, rather than waiting
 * for that entire header item to disappear. A reply just peeking in at the bottom is not read yet.
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
        val readingEdge = viewportStartOffset + (viewportEndOffset - viewportStartOffset) / 2
        return (first.index - headerItems).takeIf { first.offset <= readingEdge }
    }
    // At the end marker / loading row, retain the last reply instead of resetting to the first.
    return (commentCount - 1).takeIf { (visibleItemsInfo.firstOrNull()?.index ?: -1) >= end }
}
