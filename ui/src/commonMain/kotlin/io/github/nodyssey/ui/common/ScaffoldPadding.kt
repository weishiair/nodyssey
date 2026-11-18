package io.github.nodyssey.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Extra clearance for bottom tabs drawn over the screen, excluding the window's safe area. */
internal val LocalFloatingBottomBarHeight = compositionLocalOf { 0.dp }

/**
 * The `Scaffold`'s bottom inset plus any floating bottom navigation pill clearance.
 */
@Composable
internal fun PaddingValues.bottomWithFloatingBar(): Dp =
    calculateBottomPadding() + LocalFloatingBottomBarHeight.current

/**
 * A `Scaffold`'s inner padding minus its bottom edge, for a tab root whose list runs on under the
 * bottom bar.
 *
 * The bottom inset and controls' clearance go to the list's content padding. This lets content
 * scroll behind the bar and still lets the last row scroll clear of it.
 */
@Composable
internal fun PaddingValues.withoutBottom(): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(layoutDirection),
        top = calculateTopPadding(),
        end = calculateEndPadding(layoutDirection),
        bottom = 0.dp,
    )
}

/** Let content scroll behind a glass header without dropping horizontal safe-area insets. */
@Composable
internal fun PaddingValues.withoutTop(): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(layoutDirection),
        top = 0.dp,
        end = calculateEndPadding(layoutDirection),
        bottom = calculateBottomPadding(),
    )
}
