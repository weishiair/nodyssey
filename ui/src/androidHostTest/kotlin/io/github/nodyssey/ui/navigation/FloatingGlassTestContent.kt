package io.github.nodyssey.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.nodyssey.ui.common.LocalFloatingBottomBarHeight
import io.github.plaza.designsys.theme.PlazaTheme

/** Keep the real navigation overlay in pointer tests: semantics clicks bypass its hit targets. */
@Composable
internal fun FloatingGlassTestContent(
    current: TopLevelDestination,
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    PlazaTheme(darkTheme = darkTheme, frostedGlass = true) {
        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalFloatingBottomBarHeight provides FloatingGlassBarClearance) {
                content()
            }
            GlassBottomNavigationBar(
                current = current,
                onSelect = {},
                modifier = Modifier.align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
            )
        }
    }
}
