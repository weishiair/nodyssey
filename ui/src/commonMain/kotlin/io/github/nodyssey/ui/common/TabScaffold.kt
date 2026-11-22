package io.github.nodyssey.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.Spacing

internal val LocalIosPad = staticCompositionLocalOf { false }

internal val TabContentMaxWidth = 720.dp
internal val IosPadTabContentMaxWidth = 960.dp
internal val WideTabMinWidth = 600.dp

internal fun tabContentMaxWidth(isIosPad: Boolean, landscape: Boolean): Dp =
    if (isIosPad && landscape) IosPadTabContentMaxWidth else TabContentMaxWidth

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
    val scaffold: @Composable (Modifier, Boolean) -> Unit = { frame, wide ->
        Scaffold(
            modifier = frame,
            topBar = { topBar(wide) },
            snackbarHost = snackbarHost,
            floatingActionButton = floatingActionButton,
        ) { padding -> content(padding, wide) }
    }
    if (!enabled) {
        scaffold(modifier, false)
        return
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(LocalPlazaLayers.current.page)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        contentAlignment = Alignment.TopCenter,
    ) {
        val wide = maxWidth >= WideTabMinWidth
        val maxContentWidth = tabContentMaxWidth(LocalIosPad.current, landscape = maxWidth > maxHeight)
        val frame = if (wide) Modifier.padding(horizontal = Spacing.lg).widthIn(max = maxContentWidth) else Modifier
        scaffold(frame.fillMaxSize(), wide)
    }
}
