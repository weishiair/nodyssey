package io.github.nodyssey.ui.navigation

/**
 * Keeps a platform navigation stack — one view per entry — in step with a back stack of keys.
 *
 * The back stack stays the one source of truth: every screen still navigates with `backStack.add`
 * and `removeLastOrNull`, and [reconcile] turns the result into the views the platform should show.
 * The platform is allowed exactly one move of its own, popping — a swipe, a back button it draws, the
 * tab bar's pop-to-root — and [platformPopped] is how that is written back.
 *
 * Entries are matched by position and key equality, the way `NavDisplay` keys a stack it is handed
 * again: the run that two stacks share from the bottom keeps its views, and so its state; everything
 * above that run is released and rebuilt. Releasing is the caller's business — a view leaving the
 * screen may still be animating out, so [release] only says that it is no longer part of the stack.
 *
 * Common code rather than iOS code because none of it names UIKit, and it is the part of the shell a
 * mistake in would show as state lost or kept for the wrong screen — the part worth a test.
 */
internal class StackMirror<K : Any, V : Any>(
    private val create: (index: Int, key: K) -> V,
    private val release: (V) -> Unit,
) {
    private val keys = mutableListOf<K>()
    private val mirrored = mutableListOf<V>()

    /** The views in stack order, bottom first. */
    val views: List<V> get() = mirrored.toList()

    /**
     * Brings the mirror in line with [stack].
     *
     * @return the views the platform should now show, or null when [stack] is what is already shown —
     * which is what keeps a pop the platform made, written back through [platformPopped], from being
     * handed straight back to it as a change.
     */
    fun reconcile(stack: List<K>): List<V>? {
        val shared = sharedPrefixSize(stack)
        if (shared == keys.size && shared == stack.size) return null

        while (mirrored.size > shared) {
            keys.removeAt(keys.lastIndex)
            release(mirrored.removeAt(mirrored.lastIndex))
        }
        for (index in shared until stack.size) {
            keys += stack[index]
            mirrored += create(index, stack[index])
        }
        return views
    }

    /**
     * The platform is down to [remaining] views on its own.
     *
     * Only remove popped entries still in the prefix [stack] shares with the displayed keys. A list
     * can select another thread, or a screen push a link, while UIKit is animating a pop and the next
     * [reconcile] is deferred. Dropping that many entries from the current tail would erase the new
     * navigation instead of the pages UIKit actually popped.
     *
     * @return how many mirrored views were dropped — zero when nothing was popped, which is also
     * what a cancelled swipe reports. The caller must reconcile any navigation still pending.
     */
    fun platformPopped(remaining: Int, stack: MutableList<K>): Int {
        val shared = sharedPrefixSize(stack)
        val dropped = (mirrored.size - remaining).coerceAtLeast(0)
        repeat(dropped) {
            keys.removeAt(keys.lastIndex)
            release(mirrored.removeAt(mirrored.lastIndex))
        }
        for (index in shared - 1 downTo remaining) stack.removeAt(index)
        return dropped
    }

    private fun sharedPrefixSize(stack: List<K>): Int {
        var shared = 0
        while (shared < keys.size && shared < stack.size && keys[shared] == stack[shared]) shared++
        return shared
    }
}
