package io.github.plaza.designsys.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
actual fun AdaptiveAlertDialog(
    title: String,
    message: String?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String?,
    destructive: Boolean,
    prominentConfirm: Boolean,
    icon: ImageVector?,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        // AlertDialog centres both icon and title on its own once an icon is present, which is the
        // layout the design shows; there is nothing to override here.
        icon = icon?.let { { Icon(it, contentDescription = null) } },
        title = { Text(title) },
        text = message?.let { { Text(it) } },
        confirmButton = {
            if (prominentConfirm) {
                Button(onClick = onConfirm) { Text(confirmLabel) }
            } else {
                TextButton(onClick = onConfirm) {
                    Text(
                        confirmLabel,
                        color =
                        if (destructive) {
                            MaterialTheme.colorScheme.error
                        } else {
                            Color.Unspecified
                        },
                    )
                }
            }
        },
        dismissButton = dismissLabel?.let { label -> { TextButton(onClick = onDismiss) { Text(label) } } },
    )
}
