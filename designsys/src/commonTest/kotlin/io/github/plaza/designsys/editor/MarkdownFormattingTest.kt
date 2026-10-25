package io.github.plaza.designsys.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The caret arithmetic that used to exist twice — once in the post composer, once in the signature
 * editor — and was the thing most likely to drift when only one copy got fixed.
 */
class MarkdownFormattingTest {
    private val bold = MarkdownInsertion(prefix = "**", suffix = "**", placeholder = "加粗文字")
    private val link =
        MarkdownInsertion(
            prefix = "[",
            suffix = MARKDOWN_LINK_SUFFIX,
            placeholder = "链接文字",
            caretInSuffix = MARKDOWN_LINK_CARET,
        )

    private fun field(text: String, selection: IntRange? = null) =
        TextFieldState(
            initialText = text,
            initialSelection =
            selection?.let { TextRange(it.first, it.last) } ?: TextRange(text.length),
        )

    @Test
    fun `with nothing selected the placeholder is inserted and the caret sits on it`() {
        val state = field("")

        state.edit { applyMarkdown(bold) }

        assertEquals("**加粗文字**", state.text.toString())
        // Just past the opening delimiter, so typing replaces the placeholder in place.
        assertEquals(TextRange(2), state.selection)
    }

    @Test
    fun `a selection is wrapped and the caret goes to the end`() {
        val state = field("出杭州轻量", selection = 0..5)

        state.edit { applyMarkdown(bold) }

        assertEquals("**出杭州轻量**", state.text.toString())
        assertEquals(TextRange("**出杭州轻量**".length), state.selection)
    }

    /** Link is the exception: with the text written, the URL is the only thing left to type. */
    @Test
    fun `link puts the caret in the url slot rather than at the end`() {
        val state = field("星辰担保", selection = 0..4)

        state.edit { applyMarkdown(link) }

        assertEquals("[星辰担保](https://)", state.text.toString())
        assertEquals(TextRange("[星辰担保](".length), state.selection)
    }

    @Test
    fun `formatting applies in the middle of existing text`() {
        val state = field("交易走星辰担保，勿私", selection = 3..7)

        state.edit { applyMarkdown(bold) }

        assertEquals("交易走**星辰担保**，勿私", state.text.toString())
        assertEquals(TextRange("交易走**星辰担保**".length), state.selection)
    }

    @Test
    fun `an image splits existing text at the caret and typing continues below it`() {
        val state = field("beforeafter", selection = 6..6)

        state.edit { insertBlockAtCaret("![a](url)") }

        assertEquals("before\n\n![a](url)\n\nafter", state.text.toString())
        assertEquals(TextRange("before\n\n![a](url)\n\n".length), state.selection)
    }

    @Test
    fun `image insertion reuses existing blank lines on either side`() {
        for (beforeBreaks in 0..2) {
            for (afterBreaks in 0..2) {
                val before = "before" + "\n".repeat(beforeBreaks)
                val state = field(before + "\n".repeat(afterBreaks) + "after", selection = before.length..before.length)

                state.edit { insertBlockAtCaret("![a](url)") }

                assertEquals("before\n\n![a](url)\n\nafter", state.text.toString())
                assertEquals(TextRange("before\n\n![a](url)\n\n".length), state.selection)
            }
        }
    }

    @Test
    fun `an image follows a forward or reversed selection without deleting its text`() {
        for (range in listOf(TextRange(1, 4), TextRange(4, 1))) {
            val state = TextFieldState(initialText = "abcdef", initialSelection = range)

            state.edit { insertBlockAtCaret("![a](url)") }

            assertEquals("abcd\n\n![a](url)\n\nef", state.text.toString())
            assertEquals(TextRange("abcd\n\n![a](url)\n\n".length), state.selection)
        }
    }

    @Test
    fun `an image at the beginning does not add leading blank lines`() {
        val state = field("after", selection = 0..0)

        state.edit { insertBlockAtCaret("![a](url)") }

        assertEquals("![a](url)\n\nafter", state.text.toString())
        assertEquals(TextRange("![a](url)\n\n".length), state.selection)
    }

    @Test
    fun `consecutive images stay in insertion order before the following text`() {
        val state = field("beforeafter", selection = 6..6)

        state.edit {
            insertBlockAtCaret("![a](first)")
            insertBlockAtCaret("![b](second)")
        }

        assertEquals("before\n\n![a](first)\n\n![b](second)\n\nafter", state.text.toString())
        assertEquals(TextRange("before\n\n![a](first)\n\n![b](second)\n\n".length), state.selection)
    }

    @Test
    fun `removing a final image also removes the typing line it introduced`() {
        for (body in listOf("", "before")) {
            val state = field(body)
            state.edit { insertBlockAtCaret("![a](url)") }

            assertEquals(body + (if (body.isEmpty()) "" else "\n\n") + "![a](url)\n", state.text.toString())
            assertEquals(TextRange(state.text.length), state.selection)

            state.edit { removeBlock("![a](url)") }

            assertEquals(body, state.text.toString())
        }
    }
}
