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
 * replies. Updating the label throughout that hand-off makes the floor jump away and back. The new
 * value is accepted only after the load state clears, when the replies have already been laid out.
 */
@Composable
internal fun rememberStableReadingFloor(
    currentFloor: Int,
    loading: Boolean,
): Int {
    var displayedFloor by rememberSaveable { mutableIntStateOf(currentFloor) }
    LaunchedEffect(currentFloor, loading) {
        if (!loading) displayedFloor = currentFloor
    }
    return displayedFloor
}
