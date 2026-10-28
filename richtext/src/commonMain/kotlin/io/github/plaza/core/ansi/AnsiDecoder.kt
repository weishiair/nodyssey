package io.github.plaza.core.ansi

import io.github.plaza.core.TerminalColumns
import kotlinx.serialization.Serializable

/**
 * One run of SGR-styled text, as a half-open range into the text it belongs to.
 *
 * [fg] and [bg] are palette indices 0–15 (the eight ANSI colours then their bright variants), not
 * packed colours: a report's `✔ 解锁` green has to become the *theme's* green, and only the renderer
 * knows what that is.
 */
@Serializable
data class AnsiSpan(
    val start: Int,
    val end: Int,
    val fg: Int? = null,
    val bg: Int? = null,
    val bold: Boolean = false,
    val underline: Boolean = false,
)

/**
 * Turns an ANSI escape stream into plain text plus the styling the escapes were describing.
 *
 * Pure string work, and nothing about it is specific to one forum: the escape grammar is the
 * terminal's. Recovering the stream in the first place is not — a site that cannot put a raw `ESC`
 * in its HTML has to encode it somehow, and that trick belongs to whoever knows the site's markup.
 * So the caller hands this a source string and keeps the recovery to itself.
 *
 * The decoder lays the stream out on a terminal screen rather than appending it to a string. The
 * reports use carriage returns, backspaces and cursor-width CJK to overstrike their bar graphs and
 * align their tables; dropping those controls (as this used to) left every bar and every value after
 * one shifted, which read as a rendering bug because it was one. The screen below honours the moves
 * and rebuilds the final rows, so what is drawn is the screen a terminal would have shown.
 */
object AnsiDecoder {

    private const val ESC = '\u001B'
    private const val TAB_STOP = 8

    /** A CSI sequence runs until the first byte in this range, which also says what it does. */
    private val FINAL_BYTE = '@'..'~'

    data class Decoded(
        val text: String,
        val spans: List<AnsiSpan>,
        val columns: Int,
    )

    /** One written glyph, its width in terminal cells, and the SGR state it was written under. */
    private data class Cell(
        val text: String,
        val width: Int,
        val style: CellStyle,
    )

    private data class CellStyle(
        val fg: Int?,
        val bg: Int?,
        val bold: Boolean,
        val underline: Boolean,
    ) {
        val styled: Boolean get() = fg != null || bg != null || bold || underline
    }

    private val DefaultStyle = CellStyle(fg = null, bg = null, bold = false, underline = false)

    /**
     * A terminal's rows, cursor and overstrike state.
     *
     * A continuation cell of a wide glyph is left as `null`; [Cell.width] on the glyph's first cell
     * says the next column belongs to it, so a later write can clear both halves before replacing it.
     */
    private class Screen {
        private val rows = mutableListOf<MutableList<Cell?>>()
        private var row = 0
        private var col = 0

        init {
            ensure(row)
        }

        fun newline() {
            row++
            col = 0
            ensure(row)
        }

        fun carriageReturn() {
            col = 0
        }

        fun backspace() {
            if (col > 0) col--
        }

        fun tab() {
            col = (col / TAB_STOP + 1) * TAB_STOP
        }

        fun write(text: String, style: CellStyle) {
            val width = TerminalColumns.widthOf(text)
            if (width == 0) {
                appendCombining(text)
                return
            }
            ensure(row)
            val line = rows[row]
            // Landing inside a wide glyph clears that glyph as a whole; a half replaced leaves the
            // other half drawing over the next character.
            if (col > 0 && line.getOrNull(col - 1)?.width == 2) {
                line[col - 1] = null
                if (col < line.size) line[col] = null
            }
            clearAt(line, col)
            if (width == 2) clearAt(line, col + 1)
            while (line.size < col + width) line.add(null)
            line[col] = Cell(text, width, style)
            col += width
        }

        fun decoded(): Decoded {
            val builder = StringBuilder()
            val spans = mutableListOf<AnsiSpan>()
            var offset = 0

            rows.forEachIndexed { rowIndex, line ->
                val last = line.indexOfLast { it != null }
                if (last >= 0) {
                    var column = 0
                    var runStart = offset
                    var runStyle = DefaultStyle
                    while (column <= last) {
                        val cell = line.getOrNull(column)
                        val style = cell?.style ?: DefaultStyle
                        if (style != runStyle) {
                            addRun(spans, runStart, offset, runStyle)
                            runStart = offset
                            runStyle = style
                        }
                        val content = cell?.text ?: " "
                        builder.append(content)
                        offset += content.length
                        column += cell?.width ?: 1
                    }
                    addRun(spans, runStart, offset, runStyle)
                }
                if (rowIndex < rows.lastIndex) {
                    builder.append('\n')
                    offset++
                }
            }

            val text = builder.toString().trimEnd()
            return Decoded(
                text = text,
                spans = spans.mapNotNull { span -> span.clampedTo(text.length) },
                columns = TerminalColumns.widthOf(text),
            )
        }

        private fun addRun(
            spans: MutableList<AnsiSpan>,
            start: Int,
            end: Int,
            style: CellStyle,
        ) {
            if (style.styled && end > start) {
                spans += AnsiSpan(start, end, style.fg, style.bg, style.bold, style.underline)
            }
        }

        private fun clearAt(line: MutableList<Cell?>, at: Int) {
            val cell = line.getOrNull(at) ?: return
            if (cell.width == 2) {
                line[at] = null
                if (at + 1 < line.size) line[at + 1] = null
            } else {
                val previous = line.getOrNull(at - 1)
                if (previous?.width == 2) {
                    line[at - 1] = null
                    line[at] = null
                } else {
                    line[at] = null
                }
            }
        }

        private fun appendCombining(text: String) {
            if (col == 0) return
            val line = rows.getOrNull(row) ?: return
            val previous = line.getOrNull(col - 1) ?: return
            line[col - 1] = previous.copy(text = previous.text + text)
        }

        private fun ensure(at: Int) {
            while (rows.size <= at) rows.add(mutableListOf())
        }
    }

    /**
     * Strips the escape sequences and returns what they were saying about the text they enclosed.
     *
     * Ordinary code goes through the same screen, which is why it stays unchanged: it has no
     * controls to honour and no wide glyphs unless it contains CJK.
     */
    fun decode(source: String): Decoded {
        val screen = Screen()

        var fg: Int? = null
        var bg: Int? = null
        var bold = false
        var underline = false

        fun style() = CellStyle(fg = fg, bg = bg, bold = bold, underline = underline)

        fun applySgr(parameters: String) {
            // `ESC[m` is `ESC[0m`, and so is any parameter we cannot read.
            parameters.split(';').forEach { parameter ->
                when (val code = parameter.trim().toIntOrNull() ?: 0) {
                    0 -> {
                        fg = null
                        bg = null
                        bold = false
                        underline = false
                    }

                    1 -> bold = true

                    4 -> underline = true

                    22 -> bold = false

                    24 -> underline = false

                    39 -> fg = null

                    49 -> bg = null

                    in 30..37 -> fg = code - 30

                    in 40..47 -> bg = code - 40

                    in 90..97 -> fg = code - 90 + BRIGHT_OFFSET

                    in 100..107 -> bg = code - 100 + BRIGHT_OFFSET

                    // Italic is parsed and dropped: Chinese has no italic form, which is the same
                    // reason a renderer turns emphasis into weight rather than slant.
                    else -> Unit
                }
            }
        }

        var index = 0
        while (index < source.length) {
            val char = source[index]
            when {
                char == ESC && source.getOrNull(index + 1) == '[' -> {
                    var end = index + 2
                    while (end < source.length && source[end] !in FINAL_BYTE) end++
                    // A sequence running off the end of the source is truncated output, and the
                    // parameters behind it are not text either.
                    if (end >= source.length) break

                    if (source[end] == 'm') {
                        applySgr(source.substring(index + 2, end))
                    }
                    index = end + 1
                }

                char == '\n' -> {
                    screen.newline()
                    index++
                }

                char == '\r' -> {
                    screen.carriageReturn()
                    index++
                }

                char == '\b' -> {
                    screen.backspace()
                    index++
                }

                char == '\t' -> {
                    screen.tab()
                    index++
                }

                char.code < 0x20 || char.code == 0x7F -> index++

                else -> {
                    val pair =
                        char.isHighSurrogate() &&
                            index + 1 < source.length &&
                            source[index + 1].isLowSurrogate()
                    val text = if (pair) source.substring(index, index + 2) else char.toString()
                    screen.write(text, style())
                    index += text.length
                }
            }
        }

        return screen.decoded()
    }

    private fun AnsiSpan.clampedTo(length: Int): AnsiSpan? = when {
        start >= length -> null
        end > length -> copy(end = length)
        else -> this
    }

    /** Palette indices 8–15 are the bright half of the sixteen. */
    private const val BRIGHT_OFFSET = 8
}
