package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable

@Composable
actual fun AdaptiveConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructiveConfirm: Boolean,
    destructiveDismiss: Boolean,
    dismissIsCancel: Boolean,
    fallback: @Composable () -> Unit,
) = fallback()
