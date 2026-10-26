package io.github.nodyssey.ui.settings.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.nodyssey.data.settings.ColorSource
import io.github.nodyssey.data.settings.PaletteStyle
import io.github.nodyssey.data.settings.UserSettings
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.settings_color_source_wallpaper
import io.github.nodyssey.ui.resources.settings_palette_style_expressive
import io.github.nodyssey.ui.resources.settings_palette_style_monochrome
import io.github.nodyssey.ui.resources.settings_palette_style_neutral
import io.github.nodyssey.ui.resources.settings_palette_style_soft
import io.github.nodyssey.ui.resources.settings_palette_style_vibrant
import io.github.nodyssey.ui.settings.DISABLED_ALPHA
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * 主题 in one line, for the row on 设置 that opens it: the colour in force, then the style — 「石墨青 ·
 * 柔和」.
 *
 * The colour is named the way 主题's own screen names it: a preset by its label, a custom seed by the
 * name it was saved under or else its hex, 动态取色 by the source itself (its colour changes with the
 * wallpaper, so a hex would be stale by the next launch). The style is left off under a hand-written
 * preset, which never reaches the generator the style steers.
 */
@Composable
fun themeSummary(settings: UserSettings): String {
    val colour =
        when (settings.colorSource) {
            ColorSource.PRESET -> stringResource(presetById(settings.presetId).label)

            ColorSource.WALLPAPER -> stringResource(Res.string.settings_color_source_wallpaper)

            ColorSource.CUSTOM ->
                settings.savedThemes
                    .firstOrNull { it.color == settings.seedColor }
                    ?.name
                    ?.takeIf { it.isNotBlank() }
                    ?: Color(settings.seedColor).toHexString()
        }
    if (activePreset(settings)?.hasFixedColors == true) return colour
    return colour + " · " + stringResource(paletteStyleLabel(settings.paletteStyle))
}

/** The five 色彩风格 in the order the picker offers them. */
internal val PaletteStyleChoices =
    listOf(
        PaletteStyle.SOFT,
        PaletteStyle.VIBRANT,
        PaletteStyle.EXPRESSIVE,
        PaletteStyle.NEUTRAL,
        PaletteStyle.MONOCHROME,
    )

internal fun paletteStyleLabel(style: PaletteStyle): StringResource =
    when (style) {
        PaletteStyle.SOFT -> Res.string.settings_palette_style_soft
        PaletteStyle.VIBRANT -> Res.string.settings_palette_style_vibrant
        PaletteStyle.EXPRESSIVE -> Res.string.settings_palette_style_expressive
        PaletteStyle.NEUTRAL -> Res.string.settings_palette_style_neutral
        PaletteStyle.MONOCHROME -> Res.string.settings_palette_style_monochrome
    }

/**
 * The selected preset's own swatch or portrait, matching its face in the theme picker in both modes.
 *
 * Wallpaper and custom colours have no preset face, so they sample the live primary and tertiary
 * colours instead. In particular, no stored seed knows what the current wallpaper produced.
 */
@Composable
fun ThemeSummaryDot(
    settings: UserSettings,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val preset = activePreset(settings)
    val avatar = preset?.avatar
    Box(
        modifier
            .size(24.dp)
            // Dimmed with the row it sits in: a full-colour dot on a greyed-out row reads as live.
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(CircleShape),
    ) {
        if (avatar != null) {
            Image(painterResource(avatar), contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    swatchBrush(preset?.swatch ?: scheme.primary, preset?.companion ?: scheme.tertiary),
                ),
            )
        }
    }
}
