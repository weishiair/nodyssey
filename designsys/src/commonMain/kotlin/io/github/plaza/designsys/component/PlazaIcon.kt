package io.github.plaza.designsys.component

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Draws the platform's own glyph for a mapped icon: on iOS, the SF Symbol in
 * [SfSymbolByIconName], rendered the way every other app renders it; anywhere else the original
 * [ImageVector], unchanged. An unmapped icon — or a symbol the OS cannot resolve — falls back to
 * the vector, so the worst case is the icon we already had. Swapped in for [Icon] by name only:
 * same parameters, same `Icon` call underneath, so tinting and sizing behave identically.
 */
@Composable
fun PlazaIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val symbol = rememberSystemSymbolPainter(imageVector)
    if (symbol == null) {
        Icon(imageVector, contentDescription, modifier, tint)
    } else {
        Icon(symbol, contentDescription, modifier, tint)
    }
}

@Composable
internal expect fun rememberSystemSymbolPainter(imageVector: ImageVector): Painter?
