package io.github.plaza.designsys.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/**
 * The three depths every screen is drawn at: the page, the cards on it, and what floats over both.
 *
 * This is the whole of the 轻盈层叠 direction. Rather than rows split by dividers on one flat surface,
 * the page recedes to a grey (`surfaceContainer`), content sits on the brightest surface there is
 * with a shadow barely strong enough to register, and floating controls — the FAB, a menu — sit one
 * step higher. Front-to-back order does the grouping.
 *
 * Material has no single role for "the page" or "a card on it", and which role plays each part is
 * not the same in every mode, which is why this is its own object rather than a set of call-site
 * choices:
 *
 * - **Light**: page `surfaceContainer`, card `surfaceContainerLowest` (white), soft shadows.
 * - **Pure white**: white by day, neutral charcoal by night. Dark insets are slightly lighter than
 *   their cards so quotes remain visible in both the flat and the card reading layouts.
 * - **Dark**: the ladder inverts — lower tones are darker — so the page takes `surface` and a card
 *   is *lighter* than it (`surfaceContainer`). No shadows: a shadow on a near-black page is
 *   invisible and only costs a blur.
 * - **墨水屏**: everything is paper. A card is told apart by an outline, the one thing a panel can
 *   draw crisply; see [LocalEinkMode].
 *
 * [PlazaTheme] also writes [page] into `ColorScheme.background`, so every `Scaffold` — whose default
 * container is `background` — takes the page colour without forty call sites saying so.
 */
@Immutable
data class PlazaLayers(
    /** Behind everything: the `Scaffold`, the gaps between cards, a top bar that sits flush. */
    val page: Color,
    /** A card, a group of settings rows, a post — anything that is *content*. */
    val card: Color,
    /**
     * A control that sits on the page rather than in a card — the search pill, an unselected board
     * chip. It is the card colour in light and one step lighter in dark, where a control the same
     * tone as the cards around it would sink into them.
     */
    val raised: Color,
    /** Recessed *inside* a card: a code block, a stats tile, a quoted post. */
    val inset: Color,
    /** The inset hairline between rows of one card. */
    val divider: Color,
    /** Whether [cardShadow] and [floatShadow] draw anything. */
    val shadows: Boolean,
    /** An outline for cards, where shadows cannot be drawn and tone cannot separate them (墨水屏). */
    val cardBorder: Color?,
    /**
     * 毛玻璃 — whether navigation bars and floating chrome controls wear a translucent liquid-glass
     * gradient and a directional specular rim highlight. Reading cards and floors remain solid.
     */
    val frostedGlass: Boolean = false,
    /** Active theme accent blended at low alpha into frosted glass surfaces and rims. */
    val glassTint: Color = Color.Unspecified,
)

/**
 * [PlazaLayers.cardBorder] as the 1dp stroke a `Surface`, a chip or a `Card` takes — null wherever
 * there is no outline to draw, which is everywhere but 墨水屏.
 */
val PlazaLayers.cardBorderStroke: BorderStroke?
    get() = cardBorder?.let { BorderStroke(1.dp, it) }

/** [PlazaLayers.cardBorder] around a node that takes no border of its own; nothing where there is none. */
fun Modifier.cardBorder(
    layers: PlazaLayers,
    shape: Shape,
): Modifier = layers.cardBorder?.let { border(1.dp, it, shape) } ?: this

/**
 * Derives the layers from whichever scheme won — seed, character palette, system or 墨水屏 — so a
 * reader's colour choice reaches the page and cards the same way it reaches everything else.
 */
fun plazaLayers(
    scheme: ColorScheme,
    darkTheme: Boolean,
    einkMode: Boolean,
    pureWhite: Boolean = false,
    frostedGlass: Boolean = false,
): PlazaLayers {
    val activeGlass = frostedGlass && !einkMode
    return when {
        einkMode ->
            PlazaLayers(
                page = scheme.surface,
                card = scheme.surface,
                raised = scheme.surface,
                inset = scheme.surfaceContainer,
                divider = scheme.outlineVariant,
                shadows = false,
                cardBorder = scheme.outline,
                frostedGlass = false,
            )

        pureWhite && darkTheme ->
            PlazaLayers(
                page = scheme.surface,
                card = scheme.surfaceContainer,
                raised = scheme.surfaceContainerHighest,
                inset = scheme.surfaceContainerHigh,
                divider = scheme.outlineVariant,
                shadows = false,
                cardBorder = null,
                frostedGlass = activeGlass,
                glassTint = scheme.primary,
            )

        darkTheme ->
            PlazaLayers(
                page = scheme.surface,
                card = scheme.surfaceContainer,
                raised = scheme.surfaceContainerHigh,
                // Two steps under the card rather than one: `surfaceContainerLow` sat so close to the
                // card's `surfaceContainer` that a code block or a stats tile vanished into it.
                inset = scheme.surfaceContainerLowest,
                divider = scheme.outlineVariant.copy(alpha = DIVIDER_ALPHA),
                shadows = false,
                cardBorder = null,
                frostedGlass = activeGlass,
                glassTint = scheme.primary,
            )

        pureWhite ->
            PlazaLayers(
                page = scheme.surface,
                card = scheme.surfaceContainerLowest,
                raised = scheme.surfaceContainerLowest,
                inset = scheme.surfaceContainer,
                divider = scheme.outlineVariant,
                shadows = true,
                cardBorder = null,
                frostedGlass = activeGlass,
                glassTint = scheme.primary,
            )

        else ->
            PlazaLayers(
                page = scheme.surfaceContainer,
                card = scheme.surfaceContainerLowest,
                raised = scheme.surfaceContainerLowest,
                inset = scheme.surfaceContainer,
                divider = scheme.outlineVariant.copy(alpha = DIVIDER_ALPHA),
                shadows = true,
                cardBorder = null,
                frostedGlass = activeGlass,
                glassTint = scheme.primary,
            )
    }
}

private const val DIVIDER_ALPHA = 0.6f

/**
 * Defaults to the light layers of a stock scheme, so a composable previewed or tested outside
 * [PlazaTheme] still draws something sensible instead of `Color.Unspecified`.
 */
val LocalPlazaLayers = staticCompositionLocalOf { plazaLayers(lightColorScheme(), darkTheme = false, einkMode = false) }

/**
 * The ink every shadow in the app is drawn with: the neutral hue of the page at a very low alpha,
 * rather than black. Black at the same strength reads as dirt on a tinted page.
 */
private val ShadowInk = Color(0xFF172830)

/**
 * A card's shadow — two layers, a tight contact shadow and a wide soft one, because a single blur
 * either looks like a smudge (wide) or like a border (tight).
 *
 * A plain function of [enabled] rather than a composable modifier: a caller reads
 * [PlazaLayers.shadows] once and passes it, which keeps this an ordinary chain of `dropShadow`s.
 */
fun Modifier.cardShadow(
    shape: Shape,
    enabled: Boolean,
): Modifier =
    if (!enabled) {
        this
    } else {
        this
            .dropShadow(shape, Shadow(radius = 2.dp, color = ShadowInk, offset = DpOffset(0.dp, 1.dp), alpha = 0.05f))
            .dropShadow(shape, Shadow(radius = 18.dp, color = ShadowInk, offset = DpOffset(0.dp, 6.dp), alpha = 0.06f))
    }

/** One step above [cardShadow]: a FAB, a floating toolbar, a popup menu. */
fun Modifier.floatShadow(
    shape: Shape,
    enabled: Boolean,
): Modifier =
    if (!enabled) {
        this
    } else {
        this
            .dropShadow(shape, Shadow(radius = 4.dp, color = ShadowInk, offset = DpOffset(0.dp, 2.dp), alpha = 0.12f))
            .dropShadow(shape, Shadow(radius = 24.dp, color = ShadowInk, offset = DpOffset(0.dp, 10.dp), alpha = 0.16f))
    }

/** Hairline width for frosted glass specular rims — crisp on high-DPI panels without looking boxy. */
val GlassRimWidth = 0.75.dp

/**
 * Translucent diagonal fill for floating liquid-glass chrome (the floating bottom navigation pill,
 * page jump keys, and lifted toolbars). Blends a subtle top specular mist and the active theme's
 * [PlazaLayers.glassTint] so dark mode reads as refractive frosted glass rather than flat charcoal.
 */
fun PlazaLayers.glassChromeBrush(darkTheme: Boolean): Brush {
    val accent = if (glassTint != Color.Unspecified) glassTint else raised
    return if (darkTheme) {
        val topCatch = lerp(raised, Color.White, 0.10f)
        val midBody = lerp(card, accent, 0.08f)
        val bottomCatch = lerp(raised, accent, 0.14f)
        Brush.linearGradient(
            listOf(
                topCatch.copy(alpha = 0.80f),
                midBody.copy(alpha = 0.66f),
                bottomCatch.copy(alpha = 0.74f),
            ),
        )
    } else {
        val topCatch = lerp(raised, Color.White, 0.55f)
        val midBody = lerp(page, accent, 0.06f)
        val bottomCatch = lerp(raised, accent, 0.10f)
        Brush.linearGradient(
            listOf(
                topCatch.copy(alpha = 0.86f),
                midBody.copy(alpha = 0.72f),
                bottomCatch.copy(alpha = 0.82f),
            ),
        )
    }
}

/**
 * Translucent vertical gradient for top app bars and headers when [PlazaLayers.frostedGlass] is on.
 */
fun PlazaLayers.glassHeaderBrush(
    darkTheme: Boolean,
    baseColor: Color = page,
): Brush {
    val accent = if (glassTint != Color.Unspecified) glassTint else raised
    return if (darkTheme) {
        val top = lerp(lerp(baseColor, raised, 0.35f), accent, 0.06f)
        val bottom = lerp(baseColor, card, 0.20f)
        Brush.verticalGradient(
            listOf(
                top.copy(alpha = 0.84f),
                bottom.copy(alpha = 0.72f),
            ),
        )
    } else {
        val top = lerp(baseColor, Color.White, 0.50f)
        val bottom = lerp(baseColor, Color.White, 0.30f)
        Brush.verticalGradient(
            listOf(
                top.copy(alpha = 0.85f),
                bottom.copy(alpha = 0.74f),
            ),
        )
    }
}

/**
 * Horizontal specular hairline for the bottom edge of a frosted glass top bar.
 */
fun PlazaLayers.glassHeaderDividerBrush(darkTheme: Boolean): Brush {
    val accent = if (glassTint != Color.Unspecified) glassTint else Color.White
    return Brush.horizontalGradient(
        if (darkTheme) {
            listOf(
                Color.White.copy(alpha = 0.05f),
                lerp(Color.White, accent, 0.25f).copy(alpha = 0.22f),
                Color.White.copy(alpha = 0.05f),
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.20f),
                Color.White.copy(alpha = 0.72f),
                Color.White.copy(alpha = 0.20f),
            )
        },
    )
}

/**
 * Translucent vertical gradient for modal bottom sheets (such as the reply composer) under
 * [PlazaLayers.frostedGlass].
 */
fun PlazaLayers.glassSheetBrush(darkTheme: Boolean): Brush {
    val accent = if (glassTint != Color.Unspecified) glassTint else raised
    return if (darkTheme) {
        val top = lerp(lerp(raised, Color.White, 0.08f), accent, 0.10f)
        Brush.verticalGradient(
            listOf(
                top.copy(alpha = 0.90f),
                card.copy(alpha = 0.86f),
            ),
        )
    } else {
        val top = lerp(card, Color.White, 0.60f)
        Brush.verticalGradient(
            listOf(
                top.copy(alpha = 0.92f),
                card.copy(alpha = 0.88f),
            ),
        )
    }
}

/**
 * Three-stop diagonal specular rim highlight for liquid-glass chrome: bright at the top-left catch
 * edge, softly tinted across the body, and a secondary reflection at the bottom-right corner.
 */
fun PlazaLayers.glassRimBrush(darkTheme: Boolean): Brush {
    val accent = if (glassTint != Color.Unspecified) glassTint else Color.White
    return Brush.linearGradient(
        if (darkTheme) {
            listOf(
                Color.White.copy(alpha = 0.30f),
                accent.copy(alpha = 0.10f),
                Color.White.copy(alpha = 0.16f),
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.86f),
                accent.copy(alpha = 0.20f),
                Color.White.copy(alpha = 0.50f),
            )
        },
    )
}

/**
 * [GlassRimWidth] stroke for a control that wears a specular rim under [PlazaLayers.frostedGlass],
 * or falls back to [cardBorderStroke] (the 墨水屏 outline, null elsewhere).
 */
fun PlazaLayers.glassRimBorderStroke(darkTheme: Boolean): BorderStroke? =
    if (backdropGlass) BorderStroke(GlassRimWidth, glassRimBrush(darkTheme)) else cardBorderStroke

/**
 * [glassRimBorderStroke] as a [Modifier] for controls that take no `BorderStroke` parameter of
 * their own.
 */
fun Modifier.glassRimBorder(
    layers: PlazaLayers,
    darkTheme: Boolean,
    shape: Shape,
): Modifier =
    if (layers.backdropGlass) {
        border(GlassRimWidth, layers.glassRimBrush(darkTheme), shape)
    } else {
        cardBorder(layers, shape)
    }

/**
 * Ambient [Backdrop] supplied by the enclosing screen or navigation shell so floating chrome
 * (bottom navigation capsule, thread top-bar pills, floating action buttons, and page jump rails)
 * can sample, blur, and refract the scrolling content directly behind them.
 */
val LocalLiquidGlassBackdrop = compositionLocalOf<Backdrop?> { null }

/**
 * Remembers a [LayerBackdrop] cleared with [backgroundColor] when [PlazaLayers.backdropGlass] is
 * enabled, or returns `null` when off so zero offscreen layer capture occurs in standard modes.
 */
@Composable
fun rememberLiquidGlassBackdrop(
    backgroundColor: Color = LocalPlazaLayers.current.page,
): LayerBackdrop? {
    if (!LocalPlazaLayers.current.backdropGlass) return null
    return rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
}

/**
 * Attaches [backdrop] as a capture source when non-null (i.e. when [PlazaLayers.backdropGlass] is
 * active); a no-op otherwise.
 */
fun Modifier.liquidGlassBackdropSource(backdrop: LayerBackdrop?): Modifier =
    if (backdrop != null) this.layerBackdrop(backdrop) else this

/**
 * Full-width frosted matte header backdrop for top app bars (covering the status bar and toolbar
 * strip) when [PlazaLayers.backdropGlass] is active. Blurs the scrolling content underneath with a
 * soft Gaussian blur and overlays [glassHeaderBrush] without lens distortion or outer box shadows.
 */
fun Modifier.glassFrostedBar(
    layers: PlazaLayers,
    darkTheme: Boolean,
    backdrop: Backdrop?,
    baseColor: Color = layers.page,
): Modifier {
    if (!layers.backdropGlass) return this
    val headerBrush = layers.glassHeaderBrush(darkTheme, baseColor)
    return if (backdrop != null) {
        this.drawBackdrop(
            backdrop = backdrop,
            shape = { RectangleShape },
            effects = {
                vibrancy()
                blur(16.dp.toPx())
            },
            highlight = null,
            shadow = null,
            onDrawSurface = {
                drawRect(headerBrush)
            },
        )
    } else {
        this.background(headerBrush)
    }
}

/**
 * Full floating liquid-glass chrome treatment:
 * - When [backdrop] is provided and [PlazaLayers.backdropGlass] is active, uses Kyant's
 *   `drawBackdrop` (`vibrancy` + Gaussian `blur` + SDF `lens` edge refraction with chromatic
 *   aberration) and tints the glass surface with [surfaceTint].
 * - When [backdrop] is null (e.g. static theme preview or fallback), uses a soft elevation shadow,
 *   clipped translucent gradient fill, and [GlassRimWidth] directional specular rim highlight.
 */
fun Modifier.glassChromeSurface(
    layers: PlazaLayers,
    darkTheme: Boolean,
    shape: Shape,
    elevated: Boolean = true,
    backdrop: Backdrop? = null,
    surfaceTint: Color = Color.Unspecified,
): Modifier {
    if (!layers.backdropGlass) {
        return this
            .floatShadow(shape, enabled = elevated && layers.shadows)
            .clip(shape)
            .background(layers.raised, shape)
            .cardBorder(layers, shape)
    }
    val resolvedTint =
        if (surfaceTint != Color.Unspecified) {
            surfaceTint
        } else if (darkTheme) {
            lerp(layers.raised, Color.Black, 0.10f).copy(alpha = 0.52f)
        } else {
            lerp(layers.card, Color.White, 0.45f).copy(alpha = 0.56f)
        }
    return if (backdrop != null) {
        this.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(8.dp.toPx())
                lens(
                    refractionHeight = 14.dp.toPx(),
                    refractionAmount = 28.dp.toPx(),
                    chromaticAberration = true,
                )
            },
            onDrawSurface = {
                drawRect(resolvedTint)
            },
        )
    } else {
        this
            .floatShadow(shape, enabled = elevated && (layers.shadows || layers.backdropGlass))
            .clip(shape)
            .background(layers.glassChromeBrush(darkTheme), shape)
            .border(GlassRimWidth, layers.glassRimBrush(darkTheme), shape)
    }
}
