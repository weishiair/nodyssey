package io.github.nodyssey.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Navigation and the smaller reading controls sit on the same vertical centre line. */
internal val FloatingBottomBarBandHeight = 58.dp
internal val FloatingBottomBarTopGap = 8.dp
internal val FloatingBottomBarBottomGap = 16.dp

/** The whole bar enters from below, without changing the arrangement of its controls. */
@Composable
internal fun FloatingBottomBarVisibility(
    visibleState: MutableTransitionState<Boolean>,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(
        visibleState = visibleState,
        enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } +
            fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = slideOutVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } +
            fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
        modifier = modifier,
        content = content,
    )
}
