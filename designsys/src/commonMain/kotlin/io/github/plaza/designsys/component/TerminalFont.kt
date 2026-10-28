package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import io.github.plaza.designsys.resources.Res
import io.github.plaza.designsys.resources.terminal_mono
import org.jetbrains.compose.resources.Font

/**
 * The terminal's face: a CJK monospace font, not the platform's Latin-only one.
 *
 * The reports are laid out in terminal cells — a CJK glyph occupies two, a Latin glyph one — and the
 * platform's `Monospace` family has no CJK face of its own. Its fallback renders a CJK glyph at a
 * width that is not exactly two Latin cells, so every column after the first Chinese character drifts.
 * The bundled face has 500-unit Latin and 1000-unit CJK glyphs on a 1000-unit em, which is the ratio
 * the scripts padded against.
 *
 * The file is a Noto Sans Mono CJK SC subset containing ASCII, the report punctuation and box-drawing
 * ranges, and GB2312 level-1 Chinese; see `files/terminal_mono_OFL.txt`. It is used only for ANSI
 * terminal blocks, never for ordinary prose or code.
 */
@Composable
fun terminalFontFamily(): FontFamily = FontFamily(Font(Res.font.terminal_mono))
