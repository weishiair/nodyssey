package io.github.nodyssey.ui.postlist

import io.github.nodyssey.model.FeedSort
import io.github.nodyssey.ui.common.FeedSortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SortMenuActionsTest {
    private val labels = mapOf(
        FeedSort.LAST_REPLY to "By reply time",
        FeedSort.POST_TIME to "By post time",
    )

    @Test
    fun `each sort menu action dispatches its FeedSort value and marks only the active sort selected`() {
        for (active in FeedSortOrder) {
            val selected = mutableListOf<FeedSort>()
            val actions = sortMenuActions(
                current = active,
                labelFor = labels::getValue,
                onSortChange = { selected += it },
            )

            assertEquals(FeedSortOrder.map(labels::getValue), actions.map { it.label })
            assertEquals(FeedSortOrder.map { it == active }, actions.map { it.selected })
            assertTrue(actions.all { it.enabled })

            actions.forEach { it.onClick() }
            assertEquals(FeedSortOrder, selected)
        }
    }
}
