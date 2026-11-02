package io.github.nodyssey.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The iOS shell's back stack ↔ `UINavigationController` bookkeeping.
 *
 * What goes wrong for a reader if this does: a view rebuilt that should have been kept loses its
 * scroll position and its view model; a view kept that should have been released shows the wrong
 * screen; and a pop the platform made, handed back to it as a change, pushes the screen the reader
 * just swiped away.
 */
class StackMirrorTest {
    private var built = 0
    private val released = mutableListOf<String>()
    private val mirror =
        StackMirror<String, String>(
            create = { index, key -> "$key#${index}v${built++}" },
            release = { released += it },
        )

    @Test
    fun `a push keeps every view underneath it`() {
        val before = mirror.reconcile(listOf("feed"))!!
        val after = mirror.reconcile(listOf("feed", "thread"))!!

        assertEquals(before.single(), after.first())
        assertEquals(2, after.size)
        assertEquals(emptyList<String>(), released)
    }

    @Test
    fun `popping several screens at once releases exactly those`() {
        val shown = mirror.reconcile(listOf("feed", "thread", "space", "thread2"))!!

        val after = mirror.reconcile(listOf("feed", "thread"))!!

        assertEquals(shown.take(2), after)
        assertEquals(shown.drop(2).toSet(), released.toSet())
    }

    @Test
    fun `replacing the top screen rebuilds only the top`() {
        val shown = mirror.reconcile(listOf("signIn", "web"))!!

        val after = mirror.reconcile(listOf("signIn", "thread"))!!

        assertEquals(shown.first(), after.first())
        assertEquals(listOf(shown.last()), released)
        assertEquals(true, after.last().startsWith("thread"))
    }

    @Test
    fun `a change below the top rebuilds from that point up`() {
        val shown = mirror.reconcile(listOf("feed", "a", "b"))!!

        val after = mirror.reconcile(listOf("feed", "x", "b"))!!

        assertEquals(shown.first(), after.first())
        assertEquals(shown.drop(1).toSet(), released.toSet())
        // `b` is the same key at the same place, but it sat on top of a screen that is gone.
        assertEquals(false, after.last() == shown.last())
    }

    @Test
    fun `the same stack again is not a change`() {
        mirror.reconcile(listOf("feed", "thread"))

        assertNull(mirror.reconcile(listOf("feed", "thread")))
    }

    @Test
    fun `a swipe back written to the stack is not handed back to the platform`() {
        val stack = mutableListOf("feed", "thread", "space")
        val shown = mirror.reconcile(stack)!!

        val dropped = mirror.platformPopped(remaining = 1, stack)

        assertEquals(2, dropped)
        assertEquals(listOf("feed"), stack)
        assertEquals(shown.drop(1).toSet(), released.toSet())
        assertNull(mirror.reconcile(stack))
        assertEquals(listOf(shown.first()), mirror.views)
    }

    @Test
    fun `a cancelled swipe drops nothing`() {
        val stack = mutableListOf("feed", "thread")
        mirror.reconcile(stack)

        assertEquals(0, mirror.platformPopped(remaining = 2, stack))
        assertEquals(listOf("feed", "thread"), stack)
        assertEquals(emptyList<String>(), released)
    }

    @Test
    fun `selecting another thread during a detail swipe keeps the new selection`() {
        val stack = mutableListOf("feed", "first", "linked")
        val shown = mirror.reconcile(stack)!!
        // The list replaces the details while UIKit is still returning from linked to first.
        stack.subList(1, stack.size).clear()
        stack += "selected"

        mirror.platformPopped(remaining = 2, stack)

        assertEquals(listOf("feed", "selected"), stack)
        val after = mirror.reconcile(stack)!!
        assertEquals(shown.first(), after.first())
        assertEquals(shown.drop(1).toSet(), released.toSet())
    }

    @Test
    fun `a completed swipe removes the old thread beneath a queued push`() {
        val stack = mutableListOf("feed", "first", "linked")
        val shown = mirror.reconcile(stack)!!
        stack += "new link"

        mirror.platformPopped(remaining = 2, stack)

        assertEquals(listOf("feed", "first", "new link"), stack)
        val after = mirror.reconcile(stack)!!
        assertEquals(shown.take(2), after.take(2))
        assertEquals(listOf(shown.last()), released)
    }

    @Test
    fun `an outer pop does not remove a replacement selected from the parent list`() {
        val stack = mutableListOf("feed", "thread", "space", "space thread")
        val shown = mirror.reconcile(stack)!!
        stack.subList(1, stack.size).clear()
        stack += "selected"

        mirror.platformPopped(remaining = 1, stack)

        assertEquals(listOf("feed", "selected"), stack)
        val after = mirror.reconcile(stack)!!
        assertEquals(shown.first(), after.first())
        assertEquals(shown.drop(1).toSet(), released.toSet())
    }

    @Test
    fun `a pop already reflected in the stack is not applied twice`() {
        val stack = mutableListOf("feed", "first", "linked")
        val shown = mirror.reconcile(stack)!!
        stack.removeAt(stack.lastIndex)

        mirror.platformPopped(remaining = 2, stack)

        assertEquals(listOf("feed", "first"), stack)
        assertNull(mirror.reconcile(stack))
        assertEquals(shown.take(2), mirror.views)
        assertEquals(listOf(shown.last()), released)
    }

    @Test
    fun `cancelling a swipe preserves the selection waiting to be mirrored`() {
        val stack = mutableListOf("feed", "first", "linked")
        val shown = mirror.reconcile(stack)!!
        stack.subList(1, stack.size).clear()
        stack += "selected"

        assertEquals(0, mirror.platformPopped(remaining = 3, stack))

        assertEquals(listOf("feed", "selected"), stack)
        assertEquals(shown, mirror.views)
        assertEquals(emptyList<String>(), released)
        val after = mirror.reconcile(stack)!!
        assertEquals(shown.first(), after.first())
    }
}
