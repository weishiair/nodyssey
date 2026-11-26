package io.github.nodyssey.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.Spacing

internal val LocalIosPad = staticCompositionLocalOf { false }

/**
 * Window-level orientation on iPad (`null` when derived from the pane's own aspect ratio).
 * Keeping this anchored to the window stops a landscape split-pane squeeze from snapping
 * [IosPadTabContentMaxWidth] down to [TabContentMaxWidth] when the pane narrows past its height.
 */
internal val LocalIosPadLandscape = compositionLocalOf<Boolean?> { null }

internal val TabContentMaxWidth = 720.dp
internal val IosPadTabContentMaxWidth = 960.dp
internal val WideTabMinWidth = 600.dp
internal val TabSqueezePaddingMinWidth = 520.dp

internal fun tabContentMaxWidth(isIosPad: Boolean, landscape: Boolean): Dp =
    if (isIosPad && landscape) IosPadTabContentMaxWidth else TabContentMaxWidth

/**
 * Horizontal outer gutter for [TabScaffold]. Full [Spacing.lg] at [WideTabMinWidth] and above,
 * zero at [TabSqueezePaddingMinWidth] and below, ramped linearly in between so squeezing through
 * [WideTabMinWidth] shrinks the content column monotonically without a sudden outward pop.
 */
internal fun tabHorizontalPadding(availableWidth: Dp): Dp =
    when {
        availableWidth >= WideTabMinWidth -> Spacing.lg

        availableWidth <= TabSqueezePaddingMinWidth -> 0.dp

        else -> {
            val fraction =
                (availableWidth - TabSqueezePaddingMinWidth) /
                    (WideTabMinWidth - TabSqueezePaddingMinWidth)
            Spacing.lg * fraction
        }
    }

internal fun tabContentWidth(
    availableWidth: Dp,
    availableHeight: Dp,
    isIosPad: Boolean,
    windowLandscape: Boolean? = null,
): Dp {
    val padding = tabHorizontalPadding(availableWidth)
    val inner = (availableWidth - padding * 2).coerceAtLeast(0.dp)
    if (availableWidth < WideTabMinWidth && padding <= 0.dp) return inner
    val maxContentWidth = tabContentMaxWidth(isIosPad, landscape = windowLandscape ?: (availableWidth > availableHeight))
    return inner.coerceAtMost(maxContentWidth)
}

/** Keeps the header, body and floating actions in the same column within the safe area. */
@Composable
internal fun TabScaffold(
    modifier: Modifier,
    enabled: Boolean = true,
    topBar: @Composable (wide: Boolean) -> Unit,
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues, wide: Boolean) -> Unit,
) {
    if (!enabled) {
        TabScaffoldBody(
            modifier = modifier,
            wide = false,
            topBar = topBar,
            snackbarHost = snackbarHost,
            floatingActionButton = floatingActionButton,
            content = content,
        )
        return
    }
    val isIosPad = LocalIosPad.current
    val windowLandscape = LocalIosPadLandscape.current
    // SubcomposeLayout instead of BoxWithConstraints so intermediate widths during a split-pane
    // squeeze re-measure in the layout phase without recomposing Scaffold, headers or native glass
    // controls unless the `wide` breakpoint actually changes.
    SubcomposeLayout(
        modifier = modifier
            .fillMaxSize()
            .background(LocalPlazaLayers.current.page)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) { constraints ->
        val availableWidth = constraints.maxWidth.toDp()
        val availableHeight = constraints.maxHeight.toDp()
        val wide = availableWidth >= WideTabMinWidth
        val contentWidthPx =
            tabContentWidth(
                availableWidth = availableWidth,
                availableHeight = availableHeight,
                isIosPad = isIosPad,
                windowLandscape = windowLandscape,
            ).roundToPx().coerceIn(0, constraints.maxWidth)
        val childConstraints = constraints.copy(minWidth = contentWidthPx, maxWidth = contentWidthPx)
        val placeables =
            subcompose(Unit) {
                TabScaffoldBody(
                    modifier = Modifier.fillMaxSize(),
                    wide = wide,
                    topBar = topBar,
                    snackbarHost = snackbarHost,
                    floatingActionButton = floatingActionButton,
                    content = content,
                )
            }.map { it.measure(childConstraints) }
        val x = ((constraints.maxWidth - contentWidthPx) / 2).coerceAtLeast(0)
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEach { it.placeRelative(x, 0) }
        }
    }
}

@Composable
private fun TabScaffoldBody(
    modifier: Modifier,
    wide: Boolean,
    topBar: @Composable (wide: Boolean) -> Unit,
    snackbarHost: @Composable () -> Unit,
    floatingActionButton: @Composable () -> Unit,
    content: @Composable (PaddingValues, wide: Boolean) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = { topBar(wide) },
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
    ) { padding -> content(padding, wide) }
}
