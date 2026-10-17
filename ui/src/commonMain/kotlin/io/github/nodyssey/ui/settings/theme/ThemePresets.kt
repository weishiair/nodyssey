package io.github.nodyssey.ui.settings.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.preset_avatar_marisa
import io.github.nodyssey.ui.resources.preset_avatar_miku
import io.github.nodyssey.ui.resources.preset_avatar_reimu
import io.github.nodyssey.ui.resources.preset_avatar_tianyi
import io.github.nodyssey.ui.resources.preset_avatar_twins
import io.github.nodyssey.ui.resources.settings_preset_graphite
import io.github.nodyssey.ui.resources.settings_preset_marisa
import io.github.nodyssey.ui.resources.settings_preset_miku
import io.github.nodyssey.ui.resources.settings_preset_pure_white
import io.github.nodyssey.ui.resources.settings_preset_reimu
import io.github.nodyssey.ui.resources.settings_preset_tianyi
import io.github.nodyssey.ui.resources.settings_preset_twins
import io.github.plaza.designsys.theme.PlazaCharacterPalette
import io.github.plaza.designsys.theme.PlazaDefaultSeed
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/** The warm half of the graphite preset's swatch; a sample colour rather than a theme token. */
internal val GraphiteCompanion = Color(0xFF7E5700)

/**
 * Three columns: 石墨青, pure white, then the five character palettes.
 *
 * 石墨青 remains the default and the only generated seed. Pure white uses fixed neutral surfaces;
 * the characters use hand-written schemes (see [PlazaCharacterPalette]). Those fixed palettes do
 * not use 色彩风格. Custom seeds belong in 自定义 and 我的主题.
 */
internal data class ThemePreset(
    /** What the store holds. Stable across releases — the label is not. */
    val id: String,
    /**
     * The name under a swatch, or a character scheme's three-colour summary.
     */
    val label: StringResource,
    /** A character scheme; graphite uses a seed and pure white is selected separately. */
    val palette: PlazaCharacterPalette?,
    /** The flat portrait, or null for presets represented by a two-tone swatch. */
    val avatar: DrawableResource?,
    val pureWhite: Boolean = false,
    val swatch: Color = PlazaDefaultSeed,
    val companion: Color = GraphiteCompanion,
) {
    val hasFixedColors: Boolean get() = palette != null || pureWhite
}

internal val ThemePresets =
    listOf(
        ThemePreset(
            id = SettingsRepository.DEFAULT_PRESET_ID,
            label = Res.string.settings_preset_graphite,
            palette = null,
            avatar = null,
        ),
        ThemePreset(
            id = "pure_white",
            label = Res.string.settings_preset_pure_white,
            palette = null,
            avatar = null,
            pureWhite = true,
            swatch = Color.White,
            companion = Color(0xFF0866D9),
        ),
        ThemePreset(
            id = "miku",
            label = Res.string.settings_preset_miku,
            palette = PlazaCharacterPalette.MIKU,
            avatar = Res.drawable.preset_avatar_miku,
        ),
        ThemePreset(
            id = "twins",
            label = Res.string.settings_preset_twins,
            palette = PlazaCharacterPalette.TWINS,
            avatar = Res.drawable.preset_avatar_twins,
        ),
        ThemePreset(
            id = "tianyi",
            label = Res.string.settings_preset_tianyi,
            palette = PlazaCharacterPalette.TIANYI,
            avatar = Res.drawable.preset_avatar_tianyi,
        ),
        ThemePreset(
            id = "reimu",
            label = Res.string.settings_preset_reimu,
            palette = PlazaCharacterPalette.REIMU,
            avatar = Res.drawable.preset_avatar_reimu,
        ),
        ThemePreset(
            id = "marisa",
            label = Res.string.settings_preset_marisa,
            palette = PlazaCharacterPalette.MARISA,
            avatar = Res.drawable.preset_avatar_marisa,
        ),
    )

/** The preset a stored id names, or 石墨青 when the store holds one this build no longer ships. */
internal fun presetById(id: String): ThemePreset =
    ThemePresets.firstOrNull { it.id == id } ?: ThemePresets.first()

/** `#RRGGBB`, upper case — the form the hex field reads back and the form the labels print. */
internal fun Color.toHexString(): String =
    "#" + (toArgb() and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')

/** `#RRGGBB` or bare `RRGGBB`; anything else is still being typed. */
internal fun parseHexColor(text: String): Color? {
    val digits = text.removePrefix("#")
    if (digits.length != 6 || digits.any { it.digitToIntOrNull(16) == null }) return null
    return Color(0xFF000000.toInt() or digits.toInt(16))
}
