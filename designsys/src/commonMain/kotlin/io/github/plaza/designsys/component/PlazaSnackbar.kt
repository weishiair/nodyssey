package io.github.plaza.designsys.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.resources.Res
import io.github.plaza.designsys.resources.snackbar_dismiss
import io.github.plaza.designsys.theme.GlassSurfaceStyle
import io.github.plaza.designsys.theme.LocalLiquidGlassBackdrop
import io.github.plaza.designsys.theme.LocalPlazaDarkTheme
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.backdropGlass
import io.github.plaza.designsys.theme.glassChromeSurface
import org.jetbrains.compose.resources.stringResource

/** Material owns the queue, accessibility announcements, timeout and entrance/exit motion. */
@Composable
fun PlazaSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState, modifier) { PlazaSnackbar(it) }
}

@Composable
fun PlazaSnackbar(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier,
) {
    if (!LocalPlazaLayers.current.frostedGlass) {
        Snackbar(snackbarData, modifier)
        return
    }
    val actionLabel = snackbarData.visuals.actionLabel
    val dismissLabel = stringResource(Res.string.snackbar_dismiss)
    AdaptiveGlassNotice(
        message = snackbarData.visuals.message,
        action = actionLabel?.let { GlassAction(it, snackbarData::performAction, title = it) },
        dismissAction = if (snackbarData.visuals.withDismissAction) GlassAction(dismissLabel, snackbarData::dismiss, symbol = "xmark") else null,
        modifier = modifier.padding(12.dp).widthIn(max = 600.dp).fillMaxWidth(),
    ) {
        PlazaSnackbar(
            modifier = modifier.padding(12.dp),
            action = actionLabel?.let { label -> { FilledTonalButton(onClick = snackbarData::performAction) { Text(label) } } },
            dismissAction = if (snackbarData.visuals.withDismissAction) {
                { IconButton(onClick = snackbarData::dismiss) { Icon(Icons.Default.Close, contentDescription = dismissLabel) } }
            } else {
                null
            },
        ) { Text(snackbarData.visuals.message) }
    }
}

/** Text notices, including undo, share native material without changing who owns their lifetime. */
@Composable
fun PlazaSnackbar(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val dismissLabel = stringResource(Res.string.snackbar_dismiss)
    AdaptiveGlassNotice(
        message = message,
        action = actionLabel?.let { label -> onAction?.let { GlassAction(label, it, title = label) } },
        dismissAction = onDismiss?.let { GlassAction(dismissLabel, it, symbol = "xmark") },
        modifier = modifier.widthIn(max = 600.dp).fillMaxWidth(),
    ) {
        PlazaSnackbar(
            modifier = modifier,
            action = actionLabel?.let { label -> onAction?.let { { TextButton(onClick = it) { Text(label) } } } },
            dismissAction = onDismiss?.let { dismiss ->
                { IconButton(onClick = dismiss) { Icon(Icons.Default.Close, contentDescription = dismissLabel) } }
            },
        ) { Text(message) }
    }
}

/** Slot-based counterpart for notices such as undo, with the same glass-only treatment. */
@Composable
fun PlazaSnackbar(
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
    dismissAction: @Composable (() -> Unit)? = null,
    actionOnNewLine: Boolean = false,
    content: @Composable () -> Unit,
) {
    val layers = LocalPlazaLayers.current
    if (!layers.backdropGlass) {
        Snackbar(modifier, action, dismissAction, actionOnNewLine, content = content)
        return
    }
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    // Snackbar fixes shadowElevation internally and exposes no override. Its Android elevation
    // shadow shows through a translucent container. Use Material Surface and slots here until
    // Snackbar offers a zero-elevation container; SnackbarHost still owns the snackbar lifecycle.
    Surface(
        modifier = modifier.widthIn(max = 600.dp).fillMaxWidth().glassChromeSurface(
            layers = layers,
            darkTheme = LocalPlazaDarkTheme.current,
            shape = shape,
            backdrop = LocalLiquidGlassBackdrop.current,
            style = GlassSurfaceStyle.Notice,
        ),
        shape = shape,
        color = Color.Transparent,
        contentColor = scheme.onSurface,
    ) {
        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f).padding(top = 14.dp, bottom = 14.dp, end = 8.dp)) {
                        content()
                    }
                    if (!actionOnNewLine) {
                        SnackbarAction(action, scheme.primary)
                    }
                    CompositionLocalProvider(LocalContentColor provides scheme.onSurfaceVariant) {
                        dismissAction?.invoke()
                    }
                }
                if (actionOnNewLine && action != null) {
                    Box(Modifier.align(Alignment.End).padding(end = 8.dp, bottom = 4.dp)) {
                        SnackbarAction(action, scheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SnackbarAction(action: @Composable (() -> Unit)?, color: Color) {
    CompositionLocalProvider(LocalContentColor provides color) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) { action?.invoke() }
    }
}
