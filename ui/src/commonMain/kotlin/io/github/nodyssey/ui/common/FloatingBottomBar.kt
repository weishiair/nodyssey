package io.github.nodyssey.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.nodyssey.data.settings.BottomBarPosition
import io.github.nodyssey.data.settings.BottomBarStyle
import io.github.plaza.designsys.component.PlazaFabHeight
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.nativeControls

/** Navigation and the smaller reading controls sit on the same vertical centre line. */
internal val FloatingBottomBarBandHeight = 58.dp
private val FloatingBottomBarTopGap = 8.dp
private val HomeIndicatorClearance = 13.dp

/**
 * How far above the safe area a list's floating action button is centred: the [Spacing.lg] margin
 * the feed's page bar keeps, and half the button. Where no bar runs along the bottom, the reading
 * controls of a thread are centred here too, so the two panes side by side share a line.
 */
internal val ListFabCentreAboveSafeArea = Spacing.lg + PlazaFabHeight / 2

internal val LocalBottomBarStyle = compositionLocalOf { BottomBarStyle.FLOATING }
internal val LocalBottomBarPosition = compositionLocalOf { BottomBarPosition.MEDIUM }
internal val LocalFixedBottomBarHeight = compositionLocalOf<Dp?> { null }
internal val LocalBottomBarSafeAreaOverlap = staticCompositionLocalOf { 0.dp }
internal val LocalKeepBottomBarVisibleOnScroll = staticCompositionLocalOf { false }

private val BottomBarPosition.bottomGap
    get() = when (this) {
        BottomBarPosition.LOW -> 0.dp
        BottomBarPosition.MEDIUM -> 10.dp
        BottomBarPosition.HIGH -> 20.dp
    }

internal val BottomBarPosition.bottomBarClearance
    get() = FloatingBottomBarTopGap + FloatingBottomBarBandHeight + bottomGap

@Composable
internal fun bottomBarInset(safeBottom: Dp): Dp = bottomBarInset(safeBottom, LocalBottomBarSafeAreaOverlap.current)

/** [bottomBarInset] outside composition, for the iOS shell measuring its tab bar against the same base line. */
internal fun bottomBarInset(safeBottom: Dp, overlap: Dp): Dp =
    (safeBottom - overlap).coerceAtLeast(minOf(safeBottom, HomeIndicatorClearance))

/** Additional space above the safe area already reserved by the page. */
@Composable
internal fun bottomBarClearance(): Dp {
    val safeBottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
    val overlap = safeBottom - bottomBarInset(safeBottom)
    val fixedHeight = LocalFixedBottomBarHeight.current
    val bandClearance = if (fixedHeight == null) {
        LocalBottomBarPosition.current.bottomBarClearance
    } else {
        FloatingBottomBarTopGap + fixedHeight
    }
    return bandClearance - overlap
}

/** Places the navigation and reading controls on the same center line. */
@Composable
internal fun BottomBarBand(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val fixedHeight = LocalFixedBottomBarHeight.current
    Box(
        modifier = modifier
            .padding(top = FloatingBottomBarTopGap, bottom = if (fixedHeight == null) LocalBottomBarPosition.current.bottomGap else 0.dp)
            .height(fixedHeight ?: FloatingBottomBarBandHeight),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** The whole bar enters from below, without changing the arrangement of its controls. */
@Composable
internal fun FloatingBottomBarVisibility(
    visibleState: MutableTransitionState<Boolean>,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    // UIKitView in Compose 1.13 tracks layout bounds, but not a parent graphics layer's alpha.
    // Keep the shared slide for native controls; remove this split when interop propagates fading.
    val nativeChrome = nativeControls
    AnimatedVisibility(
        visibleState = visibleState,
        enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } +
            if (nativeChrome) EnterTransition.None else fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = slideOutVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } +
            if (nativeChrome) ExitTransition.None else fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
        modifier = modifier,
        content = content,
    )
}
