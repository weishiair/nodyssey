package io.github.nodyssey.ui.richtext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_close
import io.github.nodyssey.ui.resources.action_copy
import io.github.nodyssey.ui.resources.post_code_copied
import io.github.nodyssey.ui.resources.report_open_fullscreen
import io.github.nodyssey.ui.resources.report_zoom_actual_size
import io.github.nodyssey.ui.resources.report_zoom_fit
import io.github.nodyssey.ui.resources.report_zoom_hint
import io.github.nodyssey.ui.resources.report_zoom_in
import io.github.nodyssey.ui.resources.report_zoom_out
import io.github.nodyssey.ui.resources.report_zoom_percent
import io.github.plaza.core.ansi.AnsiSpan
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.TerminalGround
import io.github.plaza.designsys.component.TerminalInk
import io.github.plaza.designsys.component.rememberClipboardCopy
import io.github.plaza.designsys.component.rememberTerminalText
import io.github.plaza.designsys.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** The posted terminal output, with a fixed toolbar while the report scrolls and zooms. */
@Composable
fun ReportSourceDialog(
    title: String,
    source: String,
    spans: List<AnsiSpan>,
    onDismiss: () -> Unit,
    target: String? = null,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // A dialog is a separate layout root. Its text must not join the post's selection manager:
        // a long press during a pinch would otherwise map coordinates across different hierarchies.
        DisableSelection {
            Surface(color = TerminalGround, contentColor = TerminalInk) {
                Column(Modifier.fillMaxSize().systemBarsPadding()) {
                    TerminalHeader(title, target, source, onDismiss = onDismiss)
                    TerminalDivider()
                    Text(
                        text = stringResource(Res.string.report_zoom_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = TerminalInk.copy(alpha = 0.65f),
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    )
                    TerminalBody(source, spans, fullscreen = true, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** A width-fitted overview with the original ANSI colours, spacing and charts intact. */
@Composable
fun ReportSourceBlock(
    title: String,
    source: String,
    spans: List<AnsiSpan>,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    target: String? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = TerminalGround,
        contentColor = TerminalInk,
        border = BorderStroke(1.dp, TerminalInk.copy(alpha = 0.12f)),
    ) {
        // Selection competes with pinching; the header's copy action always copies the whole source.
        DisableSelection {
            Column {
                TerminalHeader(title, target, source, onExpand = onExpand)
                TerminalDivider()
                TerminalBody(source, spans, fullscreen = false)
            }
        }
    }
}

@Composable
private fun TerminalHeader(
    title: String,
    target: String?,
    source: String,
    onExpand: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val copy = rememberClipboardCopy()
    val confirmation = stringResource(Res.string.post_code_copied)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalInk.copy(alpha = 0.04f))
            .padding(start = Spacing.md, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = Spacing.sm)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            target?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = TerminalInk.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = { copy("report", source, confirmation) }) {
            Icon(PlazaIcons.ContentCopy, stringResource(Res.string.action_copy), Modifier.size(18.dp))
        }
        onExpand?.let {
            IconButton(onClick = it) {
                Icon(PlazaIcons.Fullscreen, stringResource(Res.string.report_open_fullscreen), Modifier.size(20.dp))
            }
        }
        onDismiss?.let {
            IconButton(onClick = it) {
                Icon(Icons.Default.Close, stringResource(Res.string.action_close), Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun TerminalBody(
    source: String,
    spans: List<AnsiSpan>,
    fullscreen: Boolean,
    modifier: Modifier = Modifier,
) {
    val coloured = rememberTerminalText(source, spans)
    val autoSize = remember { TextAutoSize.StepBased(minFontSize = 1.sp, maxFontSize = ReportTerminalStyle.fontSize, stepSize = 0.1.sp) }
    var fitted by remember(source) { mutableFloatStateOf(1f) }
    // Null keeps the initial overview fitted after rotation. An explicit zoom is a reading choice.
    var selectedScale by rememberSaveable(source) { mutableStateOf<Float?>(null) }
    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()
    Box(modifier.fillMaxWidth()) {
        val scale = selectedScale?.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE) ?: fitted
        val transform = rememberTransformableState { _, zoomChange, _, _ ->
            selectedScale = (scale * zoomChange).coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
        }
        Column(if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
            Box(
                modifier = (if (fullscreen) Modifier.weight(1f) else Modifier)
                    .fillMaxWidth()
                    // The enclosing post owns a one-finger vertical scroll; only a pinch zooms here.
                    .transformable(state = transform, canPan = { false }),
            ) {
                Box(
                    modifier = (if (fullscreen) Modifier.fillMaxSize().verticalScroll(vertical) else Modifier)
                        .then(if (selectedScale != null) Modifier.horizontalScroll(horizontal) else Modifier)
                        .padding(horizontal = Spacing.md, vertical = Spacing.md),
                ) {
                    Text(
                        text = coloured,
                        modifier = if (selectedScale == null) Modifier.fillMaxWidth() else Modifier,
                        style = ReportTerminalStyle.scaledBy(if (selectedScale == null) 1f else scale),
                        color = TerminalInk,
                        softWrap = false,
                        autoSize = if (selectedScale == null) autoSize else null,
                        onTextLayout = { result ->
                            if (selectedScale == null) {
                                fitted = result.layoutInput.style.fontSize.value / ReportTerminalStyle.fontSize.value
                            }
                        },
                    )
                }
            }
            TerminalDivider()
            TerminalZoomControls(
                scale = scale,
                onFit = { selectedScale = null },
                onScale = { selectedScale = it.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE) },
            )
        }
    }
}

@Composable
private fun TerminalZoomControls(
    scale: Float,
    onFit: () -> Unit,
    onScale: (Float) -> Unit,
) {
    val colours = ButtonDefaults.textButtonColors(contentColor = TerminalInk)
    val actualSize = stringResource(Res.string.report_zoom_actual_size)
    val smaller = stringResource(Res.string.report_zoom_out)
    val larger = stringResource(Res.string.report_zoom_in)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalInk.copy(alpha = 0.04f))
            .padding(horizontal = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onFit, colors = colours) {
            Text(stringResource(Res.string.report_zoom_fit))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onScale(scale / ZOOM_STEP) },
                enabled = scale > MIN_FONT_SCALE,
                modifier = Modifier.semantics { contentDescription = smaller },
            ) { Text("−", fontSize = 24.sp) }
            TextButton(
                onClick = { onScale(1f) },
                colors = colours,
                modifier = Modifier.semantics { contentDescription = actualSize },
            ) { Text(stringResource(Res.string.report_zoom_percent, (scale * 100).roundToInt())) }
            IconButton(
                onClick = { onScale(scale * ZOOM_STEP) },
                enabled = scale < MAX_FONT_SCALE,
                modifier = Modifier.semantics { contentDescription = larger },
            ) { Text("+", fontSize = 24.sp) }
        }
    }
}

@Composable
private fun TerminalDivider() = HorizontalDivider(color = TerminalInk.copy(alpha = 0.1f))

private fun TextStyle.scaledBy(scale: Float): TextStyle =
    copy(fontSize = fontSize * scale)

internal val ReportTerminalStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp,
    lineHeight = 1.25.em,
    letterSpacing = 0.sp,
)

private const val MIN_FONT_SCALE = 0.1f
private const val MAX_FONT_SCALE = 3f
private const val ZOOM_STEP = 1.25f
