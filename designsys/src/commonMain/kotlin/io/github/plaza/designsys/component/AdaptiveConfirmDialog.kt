package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable

/** Native confirmation alert on iOS outside e-ink mode; caller's Material fallback elsewhere. */
@Composable
expect fun AdaptiveConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructiveConfirm: Boolean = false,
    destructiveDismiss: Boolean = false,
    dismissIsCancel: Boolean = true,
    fallback: @Composable () -> Unit,
)
