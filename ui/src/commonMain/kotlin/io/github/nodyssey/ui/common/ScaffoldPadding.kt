package io.github.nodyssey.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Extra bottom clearance reserved when the floating liquid-glass bottom navigation pill is active
 * in place of `NavigationSuiteScaffold`'s docked bar.
 */
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
 * On iOS the tab bar is Liquid Glass floating over the content, and the glass only shows anything if
 * there is content under it. Padding the whole screen by the scaffold's bottom inset — which on that
 * side is the bar's height — stopped every list at the bar's top edge and left the glass over an empty
 * strip of page. The bottom goes to the list as content padding instead, so the last row still scrolls
 * clear of the bar, and to whatever floats at the bottom of the screen so it stays above it.
 *
 * On Android the bar is opaque and the inset under it is already consumed by `NavigationSuiteScaffold`,
 * so the same split draws what it drew before.
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
