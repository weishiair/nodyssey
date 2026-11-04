package io.github.plaza.designsys.editor

import androidx.compose.foundation.text.input.TextFieldBuffer

/**
 * Everything the editor toolbar can do.
 *
 * The site's own editor carries fifteen keys (§1.6). A phone toolbar that tried to match it would
 * either scroll — hiding the actions people actually use behind a swipe — or shrink below the 48dp
 * target, so the boards pick four to six per surface and this enum is the union of those picks.
 *
 * Everything here mutates text at the caret. Preview does not — it changes what the whole screen
 * shows — and it is deliberately absent: it lives in each surface's own chrome, alongside 发布 and
 * the close button, rather than competing with 加粗 for a slot.
 *
 * [IMAGE], [HOSTED_IMAGE] and [EMOJI] open something rather than rewriting text; the screen handles
 * them. [IMAGE] uploads a new picture; [HOSTED_IMAGE] links one that is already on the image host.
 */
enum class EditorAction {
    BOLD,
    ITALIC,
    STRIKETHROUGH,
    HEADING,
    CODE,
    QUOTE,
    LIST,
    LINK,
    MENTION,
    IMAGE,
    HOSTED_IMAGE,
    EMOJI,
}

/**
 * True when the key opens a panel that stays open, rather than performing a one-shot edit.
 *
 * The toolbar needs this statically, not just from whichever keys are currently lit: an unlit 表情
 * key is still a checkbox in the "off" position, while 加粗 is never a checkbox at all, and giving
 * the latter a toggled role would tell a screen reader something untrue.
 */
val EditorAction.opensPanel: Boolean
    get() = this in setOf(EditorAction.IMAGE, EditorAction.EMOJI)

/**
 * Applies one toolbar action to the current selection.
 *
 * Two shapes, both leaving the caret somewhere useful — an editor that formats correctly but drops
 * the caret at the end of the document makes people stop using the toolbar:
 *
 * - **Wrapping** (bold, inline code, links) surrounds the selection, or inserts a placeholder and
 *   selects it so the next keystroke replaces it.
 * - **Line prefixes** (heading, quote, list) toggle at the start of the caret's line, so tapping
 *   the same key twice undoes it instead of stacking `>> `.
 *
 * The mechanics live in `MarkdownFormatting`; this file only says which insertion each key
 * stands for.
 */
fun TextFieldBuffer.applyMarkdown(action: EditorAction) {
    when (action) {
        EditorAction.BOLD -> applyMarkdown(BOLD)
        EditorAction.ITALIC -> applyMarkdown(ITALIC)
        EditorAction.STRIKETHROUGH -> applyMarkdown(STRIKETHROUGH)
        EditorAction.CODE -> applyMarkdown(CODE)
        EditorAction.LINK -> applyMarkdown(LINK)
        EditorAction.MENTION -> applyMarkdown(MENTION)
        EditorAction.HEADING -> toggleLinePrefix("## ")
        EditorAction.QUOTE -> toggleLinePrefix("> ")
        EditorAction.LIST -> toggleLinePrefix("- ")
        EditorAction.IMAGE, EditorAction.HOSTED_IMAGE, EditorAction.EMOJI -> Unit
    }
}

/** The link key's tap: `[链接文字](https://)`, the template the site's own editor writes. */
internal fun TextFieldBuffer.applyLinkTemplate() {
    applyMarkdown(LINK)
}

/**
 * The link key's long press: `![image](https://)`, the site editor's second template.
 *
 * For a picture that is not going through the image host — a temporary link, or one copied from
 * somewhere else. The 图片 key cannot carry this: it is the upload flow, and a photo picker is the
 * wrong answer to "I have an address".
 */
internal fun TextFieldBuffer.applyImageLinkTemplate() {
    applyMarkdown(IMAGE_LINK)
}

private val BOLD =
    MarkdownInsertion(
        prefix = "**",
        suffix = "**",
        placeholder = "加粗文字",
        caret = MarkdownCaret.SELECT_CONTENT,
    )

private val ITALIC =
    MarkdownInsertion(
        prefix = "*",
        suffix = "*",
        placeholder = "斜体文字",
        caret = MarkdownCaret.SELECT_CONTENT,
    )

private val STRIKETHROUGH =
    MarkdownInsertion(
        prefix = "~~",
        suffix = "~~",
        placeholder = "删除线",
        caret = MarkdownCaret.SELECT_CONTENT,
    )

private val CODE =
    MarkdownInsertion(
        prefix = "`",
        suffix = "`",
        placeholder = "code",
        caret = MarkdownCaret.SELECT_CONTENT,
    )

/** The caret goes into the empty address, never onto the label: the scheme is already typed. */
private val LINK =
    MarkdownInsertion(
        prefix = "[",
        suffix = MARKDOWN_LINK_SUFFIX,
        placeholder = "链接文字",
        caretInSuffix = MARKDOWN_LINK_CARET + URL_SCHEME.length,
        caret = MarkdownCaret.IN_SUFFIX,
    )

/**
 * [LINK] with a `!` in front.
 *
 * The alt text is the site's own `image` rather than a translated word: it is what a post composed
 * in the browser already carries, and it is never read unless the picture fails to load.
 */
private val IMAGE_LINK =
    MarkdownInsertion(
        prefix = "![",
        suffix = MARKDOWN_LINK_SUFFIX,
        placeholder = "image",
        caretInSuffix = MARKDOWN_LINK_CARET + URL_SCHEME.length,
        caret = MarkdownCaret.IN_SUFFIX,
    )

/** A bare `@` with no placeholder: the name follows immediately, typed by hand. */
private val MENTION = MarkdownInsertion(prefix = "@", placeholder = "")

private const val URL_SCHEME = "https://"
