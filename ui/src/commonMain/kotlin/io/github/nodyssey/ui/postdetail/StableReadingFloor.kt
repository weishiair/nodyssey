package io.github.nodyssey.ui.postdetail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Holds the floor capsule on the value the reader actually saw while another page is arriving.
 *
 * The list can briefly put its loading row at the reading edge, then replace it with a new batch of
 * replies. Updating the label throughout that hand-off makes the floor jump away and back.
 * A completed request can still be waiting for Room, a list remeasure, or a pending floor jump.
 * Accept the new value only after the screen confirms that those steps have finished as well.
 */
@Composable
internal fun rememberStableReadingFloor(
    currentFloor: Int,
    loading: Boolean,
    positionReady: Boolean,
): Int {
    var displayedFloor by rememberSaveable { mutableIntStateOf(currentFloor) }
    LaunchedEffect(currentFloor, loading, positionReady) {
        if (!loading && positionReady) displayedFloor = currentFloor
    }
    return displayedFloor
}
