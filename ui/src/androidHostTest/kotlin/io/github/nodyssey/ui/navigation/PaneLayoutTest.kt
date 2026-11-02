package io.github.nodyssey.ui.navigation

import io.github.nodyssey.ui.navigation.PaneGroup.Single
import io.github.nodyssey.ui.navigation.PaneGroup.Split
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The iOS shell's grouping of a back stack into one- and two-pane screens.
 *
 * What goes wrong for a reader if this does: a thread drawn full-screen where it should sit beside
 * its list, or the other way round; and, because a back swipe is written back by counting the
 * entries the remaining groups stand for, a swipe that drops more screens than it showed leaving.
 */
class PaneLayoutTest {
    private fun kindOf(key: String): PaneKind =
        when {
            key.startsWith("list") -> PaneKind.LIST
            key.startsWith("thread") -> PaneKind.DETAIL
            else -> PaneKind.NONE
        }

    private fun groups(vararg stack: String, expanded: Boolean = true) = paneGroups(stack.toList(), expanded, ::kindOf)

    @Test
    fun `a narrow window draws every entry on its own`() {
        assertEquals(listOf(Single(0), Single(1)), groups("list", "thread", expanded = false))
    }

    @Test
    fun `a list on its own still gets the empty detail beside it`() {
        assertEquals(listOf(Split(0, 0)), groups("list"))
    }

    @Test
    fun `details opened from a list share its screen`() {
        assertEquals(listOf(Split(0, 2)), groups("list", "thread1", "thread2"))
    }

    @Test
    fun `a page that is neither ends the run`() {
        assertEquals(
            listOf(Split(0, 1), Single(2), Single(3)),
            groups("list", "thread1", "settings", "thread2"),
        )
    }

    @Test
    fun `a list opened from a detail starts a pair of its own`() {
        assertEquals(listOf(Split(0, 1), Split(2, 1)), groups("list", "thread1", "list-space", "thread2"))
    }

    @Test
    fun `the groups account for every entry`() {
        val stack = listOf("settings", "list", "thread1", "thread2", "about", "list-space")
        assertEquals(stack.size, paneGroups(stack, expanded = true, ::kindOf).sumOf { it.size })
    }
}
