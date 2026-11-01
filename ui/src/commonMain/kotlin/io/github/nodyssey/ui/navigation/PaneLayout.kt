package io.github.nodyssey.ui.navigation

/**
 * How the iOS shell groups a back stack into the controllers its `UINavigationController` holds.
 *
 * On a narrow window every entry is its own screen. On a wide one a list and the details opened from
 * it share one: the list on the left, the details pushed on a navigation stack of their own on the
 * right — what `ListDetailSceneStrategy` draws on Android, with UIKit's push and swipe kept for the
 * details.
 *
 * The grouping follows the scene strategy's: a list takes the run of details directly above it, and
 * anything that is neither ends the run. A thread opened from 追踪的帖子 is therefore full-screen, the
 * same thread opened from the feed is beside it, and a user's space opened from that thread starts a
 * pair of its own on top.
 *
 * Common code rather than iOS code for the reason [StackMirror] is: none of it names UIKit, and a
 * mistake here shows as a back swipe that drops the wrong screens.
 */
internal sealed interface PaneGroup {
    /** How many back-stack entries this group stands for. */
    val size: Int

    /** One entry, full-window. */
    data class Single(val index: Int) : PaneGroup {
        override val size: Int get() = 1
    }

    /** A list at [listIndex] and the [detailCount] details directly above it, bottom first. */
    data class Split(val listIndex: Int, val detailCount: Int) : PaneGroup {
        override val size: Int get() = 1 + detailCount
    }
}

/** What a back-stack entry is to a list-detail layout. */
internal enum class PaneKind { LIST, DETAIL, NONE }

/**
 * Groups [stack] for a window that does ([expanded]) or does not have room for two panes.
 *
 * The groups cover the stack exactly, in order, so the sum of their sizes is the stack's size.
 */
internal fun <K> paneGroups(
    stack: List<K>,
    expanded: Boolean,
    kindOf: (K) -> PaneKind,
): List<PaneGroup> {
    if (!expanded) return stack.indices.map { PaneGroup.Single(it) }
    val groups = mutableListOf<PaneGroup>()
    var index = 0
    while (index < stack.size) {
        if (kindOf(stack[index]) == PaneKind.LIST) {
            var details = 0
            while (index + 1 + details < stack.size && kindOf(stack[index + 1 + details]) == PaneKind.DETAIL) details++
            groups += PaneGroup.Split(index, details)
            index += 1 + details
        } else {
            groups += PaneGroup.Single(index)
            index++
        }
    }
    return groups
}
