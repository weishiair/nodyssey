package io.github.nodyssey.ui.navigation

/**
 * Coalesces window layouts and waits for an active UIKit transition before changing containers.
 *
 * Reparenting a Compose controller from inside layout, while its rotation animation still owns the
 * old hierarchy, can tear down the scene that animation is using. Even a transition's completion
 * posts the update: UIKit must finish unwinding its callbacks before the hierarchy changes.
 *
 * The caller reads the current bounds in [onLayout], so a cancelled rotation or another resize
 * while waiting cannot apply an old width. Kept free of UIKit so the ordering can be tested.
 */
internal class WindowLayoutDispatcher(
    private val post: (() -> Unit) -> Unit,
    private val onLayout: () -> Unit,
) {
    private var waitingForTransition = false
    private var updatePosted = false

    /** [afterTransition] returns whether the platform accepted the completion callback. */
    fun request(afterTransition: ((() -> Unit) -> Boolean)? = null) {
        if (waitingForTransition) return
        if (afterTransition != null) {
            waitingForTransition = true
            val registered = afterTransition {
                waitingForTransition = false
                postUpdate()
            }
            if (registered) return
            waitingForTransition = false
        }
        postUpdate()
    }

    private fun postUpdate() {
        if (updatePosted) return
        updatePosted = true
        post {
            updatePosted = false
            if (!waitingForTransition) onLayout()
        }
    }
}
