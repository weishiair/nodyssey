package io.github.plaza.designsys.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/** Complete light/dark palettes for quiet reading surfaces, independent of a generated seed. */
enum class PlazaSoftPalette(
    private val light: SoftPaletteTokens,
    private val dark: SoftPaletteTokens,
) {
    BLUE(
        SoftPaletteTokens(0xFFF3F5F8, 0xFFFFFFFF, 0xFF263246, 0xFF38465A, 0xFF627084, 0xFF3969CE, 0xFFEDF3FF, 0xFFF3F5F8, 0xFF8D6141, 0xFFF7E9DF),
        SoftPaletteTokens(0xFF151C25, 0xFF222C38, 0xFFE7EDF6, 0xFFD5DEEB, 0xFFA2AFC0, 0xFFB0C9FF, 0xFF304462, 0xFF2B3746, 0xFFE0BA9B, 0xFF503F34),
    ),
    MINT(
        SoftPaletteTokens(0xFFEAF4EE, 0xFFFCFFFD, 0xFF283D34, 0xFF3C5047, 0xFF5F7266, 0xFF267358, 0xFFE0F0E6, 0xFFEDF5EF, 0xFF7F693C, 0xFFF1ECDF),
        SoftPaletteTokens(0xFF14231F, 0xFF20342D, 0xFFE5F0EB, 0xFFCEDED5, 0xFFA2B9AC, 0xFF9DDABB, 0xFF2D4A3B, 0xFF293E35, 0xFFE6D3A9, 0xFF4A4433),
    ),
    SAND(
        SoftPaletteTokens(0xFFF3ECE0, 0xFFFFFCF6, 0xFF3F3730, 0xFF51483E, 0xFF756859, 0xFF875731, 0xFFF0E2CE, 0xFFF5EDE0, 0xFF566F49, 0xFFE3EBDD),
        SoftPaletteTokens(0xFF231F19, 0xFF332D24, 0xFFF0E8DC, 0xFFE0D5C4, 0xFFBBAD98, 0xFFE4BF90, 0xFF4C3C29, 0xFF3C3429, 0xFFBFD0B1, 0xFF354335),
    ),
    VIOLET(
        SoftPaletteTokens(0xFFF0EBF7, 0xFFFEFCFF, 0xFF382F49, 0xFF4A4258, 0xFF736582, 0xFF79539F, 0xFFECE2F5, 0xFFF3EDF8, 0xFF925B72, 0xFFF3E4E9),
        SoftPaletteTokens(0xFF211D2C, 0xFF302A3C, 0xFFF0E9F8, 0xFFDDD3E9, 0xFFB6A9C6, 0xFFD5B8F4, 0xFF483655, 0xFF393044, 0xFFE3B5C4, 0xFF503844),
    ),
    ;

    private val lightScheme by lazy { light.scheme(darkTheme = false, seed = light.accent, inverseAccent = dark.accent) }
    private val darkScheme by lazy { dark.scheme(darkTheme = true, seed = light.accent, inverseAccent = light.accent) }

    fun colorScheme(darkTheme: Boolean): ColorScheme = if (darkTheme) darkScheme else lightScheme

    internal fun inset(darkTheme: Boolean): Color = Color(if (darkTheme) dark.quote else light.quote)
}

internal data class SoftPaletteTokens(
    val canvas: Long,
    val paper: Long,
    val ink: Long,
    val body: Long,
    val muted: Long,
    val accent: Long,
    val accentSoft: Long,
    val quote: Long,
    val companion: Long,
    val companionSoft: Long,
)

/** Keep Material's generated error/fixed roles while supplying the approved surfaces and ink. */
private fun SoftPaletteTokens.scheme(darkTheme: Boolean, seed: Long, inverseAccent: Long): ColorScheme {
    val filledInk = Color(if (darkTheme) canvas else paper)
    val high = Color(ink).copy(alpha = 0.08f).compositeOver(Color(paper))
    return plazaSeedColorScheme(Color(seed), darkTheme, PlazaPaletteStyle.SOFT).copy(
        primary = Color(accent),
        onPrimary = filledInk,
        primaryContainer = Color(accentSoft),
        onPrimaryContainer = Color(accent),
        inversePrimary = Color(inverseAccent),
        secondary = Color(body),
        onSecondary = filledInk,
        secondaryContainer = Color(quote),
        onSecondaryContainer = Color(body),
        tertiary = Color(companion),
        onTertiary = filledInk,
        tertiaryContainer = Color(companionSoft),
        onTertiaryContainer = Color(companion),
        background = Color(canvas),
        onBackground = Color(ink),
        surface = Color(if (darkTheme) canvas else paper),
        onSurface = Color(ink),
        surfaceVariant = Color(quote),
        onSurfaceVariant = Color(muted),
        surfaceTint = Color(accent),
        outline = Color(muted),
        outlineVariant = Color(muted).copy(alpha = 0.25f).compositeOver(Color(paper)),
        surfaceDim = Color(canvas),
        surfaceBright = Color(paper),
        surfaceContainerLowest = Color(if (darkTheme) canvas else paper),
        surfaceContainerLow = Color(quote),
        surfaceContainer = Color(if (darkTheme) paper else canvas),
        surfaceContainerHigh = Color(quote),
        surfaceContainerHighest = high,
        inverseSurface = Color(ink),
        inverseOnSurface = Color(canvas),
    )
}
