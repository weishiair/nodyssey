package io.github.plaza.designsys.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import io.github.plaza.core.ansi.AnsiSpan

/**
 * The ground a terminal block is drawn on in a dark theme: the black the NodeSeek post page uses for
 * terminal output, and the same ground [rememberTerminalText]'s dark palette is read on.
 *
 * Kept as a public constant for callers that already know they are drawing the dark terminal; the
 * composable [terminalGround] picks this or the light paper ground from the current theme.
 */
val TerminalGround = Color(0xFF000000)

/**
 * The default ink on [TerminalGround] — the near-white the web terminal uses, and the colour the dark
 * palette's unstyled runs fall back to.
 */
val TerminalInk = Color(0xFFE6E6E6)

/** The ground the current theme's terminal should sit on. */
@Composable
fun terminalGround(): Color = if (terminalIsDark()) TerminalGround else LightGround

/** The default ink for the current theme's terminal. */
@Composable
fun terminalInk(): Color = if (terminalIsDark()) TerminalInk else LightInk

@Composable
private fun terminalIsDark(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/**
 * Paints the colour runs an ANSI decoder recovered back onto the text.
 *
 * A card that reads a report's colours for their *meaning* — a red 高风险 becoming the theme's error
 * colour — is a different job, and belongs to the app that knows what the report says. This is the
 * other one: the output as posted, drawn with a terminal palette.
 *
 * The palette follows the theme: a dark ground in a dark theme, and a paper ground in a light one.
 * An ANSI stream has no way to say "and my background is light", so the light theme gets its own
 * darker foreground colours and tinted fills rather than the same escapes on a white surface, where
 * white-on-green verdict chips would disappear.
 */
@Composable
fun rememberTerminalText(
    text: String,
    spans: List<AnsiSpan>,
): AnnotatedString {
    val palette = if (terminalIsDark()) DarkTerminalPalette else LightTerminalPalette
    return remember(text, spans, palette) {
        if (spans.isEmpty()) {
            AnnotatedString(text)
        } else {
            buildAnnotatedString {
                append(text)
                spans.forEach { span ->
                    val fill = palette.background.getOrElse(span.bg ?: -1) { Color.Unspecified }
                    addStyle(
                        SpanStyle(
                            color = inkFor(span.fg, fill, palette),
                            background = fill,
                            fontWeight = if (span.bold) FontWeight.Bold else null,
                            textDecoration = if (span.underline) TextDecoration.Underline else null,
                        ),
                        start = span.start,
                        end = span.end,
                    )
                }
            }
        }
    }
}

/**
 * The colour a run is drawn in, decided against the fill it sits on rather than looked up on its own.
 *
 * [TerminalPalette.foreground] and [TerminalPalette.background] each solve for the case they were
 * written for, and the two answers contradict each other where the scripts put them together.
 * `hardware.sh` heads its CPU, memory and disk lines with `ESC[47m ESC[30m` — black on white, the
 * inverse-video bar a terminal draws as the loudest thing on the screen, and what the site shows as
 * 白底黑字. Looked up separately that became `#5C6370` on `#6C7380`: black lifted off a ground it
 * was never going to sit on, white darkened for an ink that was never going to be light. 1.27:1,
 * which is not a bar, it is a smudge.
 *
 * So a light fill decides its own ink. It is only ever set to carry something dark — that is the
 * whole reason a script asks for one — and on it black means black again rather than the grey the
 * foreground palette lifts it to. Dark fills keep their ink exactly as it was.
 */
private fun inkFor(
    fg: Int?,
    fill: Color,
    palette: TerminalPalette,
): Color {
    val onGround = palette.foreground.getOrElse(fg ?: -1) { Color.Unspecified }
    if (!fill.isSpecified || fill.luminance() <= LIGHT_FILL) return onGround
    // Unset means the terminal's default ink, which is light in the dark palette and would vanish
    // here; the light palette's default is already dark, but the same rule keeps a chip legible.
    if (fg == null || fg == BLACK || fg == BRIGHT_BLACK) return palette.chipInk
    return if (contrastOf(onGround, fill) >= MIN_INK_CONTRAST) onGround else palette.chipInk
}

/** WCAG's ratio, which is the only part of the standard that is a number rather than a judgement. */
private fun contrastOf(a: Color, b: Color): Float {
    val (hi, lo) = maxOf(a.luminance(), b.luminance()) to minOf(a.luminance(), b.luminance())
    return (hi + CONTRAST_OFFSET) / (lo + CONTRAST_OFFSET)
}

private const val BLACK = 0
private const val BRIGHT_BLACK = 8
private const val LIGHT_FILL = 0.5f
private const val MIN_INK_CONTRAST = 4.5f
private const val CONTRAST_OFFSET = 0.05f

private data class TerminalPalette(
    val foreground: List<Color>,
    val background: List<Color>,
    val chipInk: Color,
)

/**
 * The sixteen colours as text on [TerminalGround].
 *
 * The web terminal's xterm palette — the same vivid green, yellow and red the NodeSeek post page
 * shows — on a black ground. Index 0 is grey rather than black: a terminal's own black is its
 * background, so `ESC[30m` drawn literally would be invisible, and the scripts do use it — as the
 * ink inside a coloured badge, where it survives here as a legible dark grey.
 */
private val DarkTerminalPalette =
    TerminalPalette(
        foreground =
        listOf(
            Color(0xFF7F7F7F),
            Color(0xFFCD0000),
            Color(0xFF00CD00),
            Color(0xFFCDCD00),
            Color(0xFF0000EE),
            Color(0xFFCD00CD),
            Color(0xFF00CDCD),
            Color(0xFFE5E5E5),
            Color(0xFF7F7F7F),
            Color(0xFFFF0000),
            Color(0xFF00FF00),
            Color(0xFFFFFF00),
            Color(0xFF5C5CFF),
            Color(0xFFFF00FF),
            Color(0xFF00FFFF),
            Color(0xFFFFFFFF),
        ),
        background =
        listOf(
            Color(0xFF1A1A1A),
            Color(0xFFAA0000),
            Color(0xFF00E676),
            Color(0xFFFFEB3B),
            Color(0xFF0000AA),
            Color(0xFFAA00AA),
            Color(0xFF00AAAA),
            Color(0xFFFFFFFF),
            Color(0xFF333333),
            Color(0xFFFF5555),
            Color(0xFF00FF00),
            Color(0xFFFFFF00),
            Color(0xFF5555FF),
            Color(0xFFFF55FF),
            Color(0xFF00FFFF),
            Color(0xFFFFFFFF),
        ),
        chipInk = Color(0xFF000000),
    )

/** A paper terminal for a light theme: dark ink, darker hues, and tinted fills. */
private val LightGround = Color(0xFFF6F8FA)
private val LightInk = Color(0xFF24292F)

private val LightTerminalPalette =
    TerminalPalette(
        foreground =
        listOf(
            Color(0xFF57606A),
            Color(0xFFB42318),
            Color(0xFF1A7F37),
            Color(0xFF8A6100),
            Color(0xFF0969DA),
            Color(0xFF8250DF),
            Color(0xFF1B7C83),
            Color(0xFF6E7781),
            Color(0xFF6E7781),
            Color(0xFFCF222E),
            Color(0xFF2DA44E),
            Color(0xFF9A6700),
            Color(0xFF218BFF),
            Color(0xFFA475F9),
            Color(0xFF3192AA),
            Color(0xFF24292F),
        ),
        background =
        listOf(
            Color(0xFFEAEEF2),
            Color(0xFFFFEBE9),
            Color(0xFFDAFBE1),
            Color(0xFFFFF8C5),
            Color(0xFFDDF4FF),
            Color(0xFFFBEFFF),
            Color(0xFFDDF4FF),
            Color(0xFFE6E9EF),
            Color(0xFFD0D7DE),
            Color(0xFFFFD8D3),
            Color(0xFFACF2BD),
            Color(0xFFFFE9A8),
            Color(0xFFB6E3FF),
            Color(0xFFEEDBFF),
            Color(0xFFB6E3FF),
            Color(0xFFF1F3F6),
        ),
        chipInk = Color(0xFF1F2328),
    )
