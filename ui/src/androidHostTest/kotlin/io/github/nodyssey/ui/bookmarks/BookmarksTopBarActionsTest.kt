package io.github.nodyssey.ui.bookmarks

import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.github.plaza.designsys.component.TopBarAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BookmarksTopBarActionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `sort menu dispatches each BookmarkSort value and marks only the active sort selected`() {
        val labels = mapOf(
            BookmarkSort.SITE to "Bookmark order",
            BookmarkSort.REPLIES to "Most replies",
            BookmarkSort.PENDING_FIRST to "Not downloaded first",
        )
        for (active in BookmarkSort.entries) {
            val selected = mutableListOf<BookmarkSort>()
            val actions = bookmarkSortMenuActions(
                current = active,
                labelFor = labels::getValue,
                onSort = { selected += it },
            )

            assertEquals(BookmarkSort.entries.map(labels::getValue), actions.map { it.label })
            assertEquals(BookmarkSort.entries.map { it == active }, actions.map { it.selected })
            assertTrue(actions.all { it.enabled })

            actions.forEach { it.onClick() }
            assertEquals(BookmarkSort.entries, selected)
        }
    }

    @Test
    fun `native top bar actions wire each callback and gate selection and offline entries`() {
        var searched = 0
        val sorts = mutableListOf<BookmarkSort>()
        var enteredSelection = 0
        var managed = 0
        lateinit var full: List<TopBarAction>
        lateinit var emptyOfflineOff: List<TopBarAction>

        composeRule.setContent {
            full = bookmarksTopBarActions(
                sort = BookmarkSort.REPLIES,
                hasEntries = true,
                offlineAvailable = true,
                onSearch = { searched++ },
                onSort = { sorts += it },
                onEnterSelection = { enteredSelection++ },
                onManage = { managed++ },
            )
            emptyOfflineOff = bookmarksTopBarActions(
                sort = BookmarkSort.SITE,
                hasEntries = false,
                offlineAvailable = false,
                onSearch = {},
                onSort = {},
                onEnterSelection = {},
                onManage = {},
            )
        }
        composeRule.waitForIdle()

        assertEquals(4, full.size)
        assertEquals(4, full.map { it.glass.label }.distinct().size)

        val (search, sort, select, manage) = full
        search.glass.onClick()
        sort.glass.menu.forEach { it.onClick() }
        select.glass.onClick()
        manage.glass.onClick()

        assertEquals(1, searched)
        assertEquals(BookmarkSort.entries, sorts)
        assertEquals(BookmarkSort.entries.map { it == BookmarkSort.REPLIES }, sort.glass.menu.map { it.selected })
        assertEquals(1, enteredSelection)
        assertEquals(1, managed)

        assertEquals(listOf("magnifyingglass", "arrow.up.arrow.down"), emptyOfflineOff.map { it.glass.symbol })
    }
}
