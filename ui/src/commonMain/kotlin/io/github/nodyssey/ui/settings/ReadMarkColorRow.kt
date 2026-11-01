package io.github.nodyssey.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.settings_read_mark_color
import io.github.nodyssey.ui.resources.settings_read_mark_color_theme
import io.github.nodyssey.ui.settings.theme.SeedColorSheet
import io.github.plaza.designsys.theme.PlazaPaletteStyle
import org.jetbrains.compose.resources.stringResource

/**
 * 标记颜色 — the swatches that decide what colour 已读标记 paints with.
 *
 * [color] null is 跟随主题 and always comes first, because it is the answer every reader starts
 * with: a mark that belongs to their theme rather than a colour they had to choose. The ten beside
 * it are mid-tone on purpose — the card takes the hue and a tone of our own (see `readMarkCardColor`),
 * so what these decide is the hue, and two swatches that differ only in brightness would produce the
 * same mark.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReadMarkColorRow(
    color: Int?,
    onPick: (Int?) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    SettingsBlock(
        title = stringResource(Res.string.settings_read_mark_color),
        value = if (color == null) stringResource(Res.string.settings_read_mark_color_theme) else null,
    ) {
        // Wrapped rather than scrolled sideways: 跟随主题, ten presets and 自定义 is eleven dots, and a
        // row that hides the last of them behind a swipe hides the only way to pick an arbitrary colour.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 跟随主题 — the theme's primary, which is what an unpicked mark paints with.
            Swatch(
                color = MaterialTheme.colorScheme.primary,
                selected = color == null,
                onClick = { onPick(null) },
            )
            MarkColors.forEach { argb ->
                Swatch(
                    color = Color(argb),
                    selected = color == argb,
                    onClick = { onPick(argb) },
                )
            }
            // A colour that came out of the picker rather than off this row keeps its place on it,
            // so the answer is never a row of dots none of which is ticked.
            if (color != null && color !in MarkColors) {
                Swatch(color = Color(color), selected = true, onClick = { picking = true })
            }
            // 自定义… — the same picker 主题 uses, minus the theme half of it: this is a colour, not
            // a palette to keep.
            Box(contentAlignment = Alignment.Center) {
                Swatch(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    selected = false,
                    glyph = { Glyph(Icons.Default.Add, MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = { picking = true },
                )
            }
        }
    }

    if (picking) {
        SeedColorSheet(
            initial = color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary,
            // Reaches only the preview strip, which shows the scheme this colour would build if it
            // were a seed. 柔和 is the app's own default, so it is the honest one to show.
            paletteStyle = PlazaPaletteStyle.SOFT,
            onDismiss = { picking = false },
            titleOverride = stringResource(Res.string.settings_read_mark_color),
            hideThemeSave = true,
            onApply = { picked, _ ->
                picking = false
                onPick(picked.toArgb())
            },
        )
    }
}

/**
 * The ten a reader can pick without a picker.
 *
 * Chosen to stay apart in hue rather than in brightness: the mark keeps every swatch's hue and
 * throws its tone away, so two dots of one hue would be one choice drawn twice.
 */
private val MarkColors =
    listOf(
        0xFF3B6FF5.toInt(),
        0xFF1F9AAE.toInt(),
        0xFF2E9E6B.toInt(),
        0xFF7FA32A.toInt(),
        0xFFE0A02B.toInt(),
        0xFFE0703C.toInt(),
        0xFFD8453C.toInt(),
        0xFFD2508A.toInt(),
        0xFF8A5BD6.toInt(),
        0xFF6B7280.toInt(),
    )

/** 30dp: big enough to hit, small enough that eleven of them fit beside a scroll. */
private val SwatchSize = 30.dp

@Composable
private fun Swatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    glyph: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier =
        Modifier
            .size(SwatchSize)
            .clip(CircleShape)
            .background(color)
            // The ring is the scheme's ink rather than a fixed grey: on the dark swatches of a dark
            // theme a light ring is the only one that shows, and it is the same swap the tick makes.
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            selected -> Glyph(Icons.Default.Check, tickColor(color))
            glyph != null -> glyph()
        }
    }
}

@Composable
private fun Glyph(icon: ImageVector, tint: Color) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
}

/** Against the swatch, not against the page: the scheme's ink would vanish on half of these. */
private fun tickColor(swatch: Color): Color = if (swatch.luminance() > 0.5f) Color.Black else Color.White
