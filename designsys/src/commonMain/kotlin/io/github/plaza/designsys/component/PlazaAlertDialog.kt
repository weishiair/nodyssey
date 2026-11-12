package io.github.plaza.designsys.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.plaza.designsys.theme.Spacing

/** Forms stay shared; UIKit owns the iOS modal and its dismissal instead of a Compose dialog. */
@Composable
fun PlazaAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    dismissEnabled: Boolean = true,
) {
    if (nativeModalBottomSheet(onDismissRequest, modifier, true, dismissEnabled, true) {
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                icon?.invoke()
                title?.invoke()
                Box(Modifier.weight(1f, fill = false)) { text?.invoke() }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    ) {
        return
    }
    AlertDialog(onDismissRequest, confirmButton, modifier, dismissButton, icon, title, text, shape)
}

@Composable
fun PlazaDialog(onDismissRequest: () -> Unit, properties: DialogProperties = DialogProperties(), content: @Composable () -> Unit) {
    if (nativeModalBottomSheet(onDismissRequest, Modifier, true, properties.dismissOnBackPress && properties.dismissOnClickOutside, true) { content() }) return
    Dialog(onDismissRequest, properties, content)
}
