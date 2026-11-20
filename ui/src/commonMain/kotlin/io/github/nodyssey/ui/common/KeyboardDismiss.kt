package io.github.nodyssey.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Whether a tap on nothing in particular puts the keyboard away.
 *
 * iOS: the iPhone keyboard has no key that hides it and there is no system back, so a reader who has
 * tapped into a Compose text field had no way out of the keyboard at all — UIKit's own fields teach
 * them to tap somewhere else, and Compose's do not answer that. Android keeps its back gesture and
 * the keyboard's own hide key, and its behaviour as it was.
 */
internal expect val dismissesKeyboardOnBlankTap: Boolean

/**
 * Clears focus — and with it the keyboard — when a tap lands where nothing took it.
 *
 * The main pass, so the children have had the event first: a button, a link or another text field
 * consumes its own tap and the focus stays where it was, as it does under a UIKit button. A press
 * that travels is a scroll and one held past the long-press timeout is somebody else's gesture;
 * neither counts. Compose Multiplatform 1.13 has no such behaviour on iOS; remove this when it does.
 */
internal fun Modifier.clearFocusOnBlankTap(focusManager: FocusManager): Modifier =
    pointerInput(focusManager) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val downTaken = down.isConsumed
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                if (change.changedToUpIgnoreConsumed()) {
                    val quick = change.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis
                    if (quick && !downTaken && !change.isConsumed) focusManager.clearFocus()
                    break
                }
            }
        }
    }
