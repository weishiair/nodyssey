package io.github.plaza.designsys.richtext

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import io.github.plaza.designsys.component.linkRangeAt
import io.github.plaza.designsys.component.prefetchLinksOnPress
import io.github.plaza.designsys.component.rememberClipboardCopy
import io.github.plaza.designsys.resources.Res
import io.github.plaza.designsys.resources.richtext_action_copy_link
import io.github.plaza.designsys.resources.richtext_action_copy_text
import io.github.plaza.designsys.resources.richtext_link_copied
import io.github.plaza.designsys.resources.richtext_text_copied
import org.jetbrains.compose.resources.stringResource

/** Text's normal links and selection, plus a long-press menu for copying a link's destination. */
@Composable
internal fun RichLinkText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    inlineContent: Map<String, InlineTextContent> = emptyMap(),
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    val content: @Composable (Modifier) -> Unit = { textModifier ->
        Text(
            text = text,
            style = style,
            modifier = textModifier.prefetchLinksOnPress(text) { layout },
            color = color,
            fontWeight = fontWeight,
            maxLines = maxLines,
            overflow = overflow,
            inlineContent = inlineContent,
            onTextLayout = {
                layout = it
                onTextLayout?.invoke(it)
            },
        )
    }
    val state = LocalRichSelectionState.current
    if (state == null || text.getLinkAnnotations(0, text.length).none { it.item is LinkAnnotation.Url }) {
        content(modifier)
        return
    }

    var pressedLink by remember(text) { mutableStateOf<AnnotatedString.Range<LinkAnnotation>?>(null) }
    val copy = rememberClipboardCopy()
    val copyLinkLabel = stringResource(Res.string.richtext_action_copy_link)
    val copyTextLabel = stringResource(Res.string.richtext_action_copy_text)
    val linkCopied = stringResource(Res.string.richtext_link_copied)
    val textCopied = stringResource(Res.string.richtext_text_copied)
    Box(modifier = modifier, propagateMinConstraints = true) {
        content(
            Modifier.pointerInput(text, state) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (down.type == PointerType.Mouse) return@awaitEachGesture
                    val link = text.linkRangeAt(down.position, layout) ?: return@awaitEachGesture
                    if (link.item !is LinkAnnotation.Url) return@awaitEachGesture
                    // LinkAnnotation has no long-press callback in Foundation 1.13 and consumes
                    // down before selection sees it. Watch Initial without claiming taps or scrolls.
                    // Remove this detector when LinkAnnotation exposes a long-press action.
                    val releasedOrMoved = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val changes = awaitPointerEvent(PointerEventPass.Initial).changes
                            val change = changes.singleOrNull() ?: return@withTimeoutOrNull true
                            if (!change.pressed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                return@withTimeoutOrNull true
                            }
                        }
                    }
                    if (releasedOrMoved == true) return@awaitEachGesture

                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                    // Let SelectionContainer finish its up-event cleanup before opening the menu.
                    awaitPointerEvent(PointerEventPass.Final)
                    state.clear()
                    pressedLink = link
                }
            },
        )
        DisableSelection {
            DropdownMenu(expanded = pressedLink != null, onDismissRequest = { pressedLink = null }) {
                val link = pressedLink
                val url = (link?.item as? LinkAnnotation.Url)?.url
                if (link != null && url != null) {
                    DropdownMenuItem(
                        text = { Text(copyLinkLabel) },
                        onClick = {
                            copy(copyLinkLabel, url, linkCopied)
                            pressedLink = null
                        },
                    )
                    val label = text.substring(link.start, link.end)
                    if (label != url) {
                        DropdownMenuItem(
                            text = { Text(copyTextLabel) },
                            onClick = {
                                copy(copyTextLabel, label, textCopied)
                                pressedLink = null
                            },
                        )
                    }
                }
            }
        }
    }
}
