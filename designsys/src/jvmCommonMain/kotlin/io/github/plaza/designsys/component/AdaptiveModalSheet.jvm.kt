package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable

@Composable
actual fun AdaptiveModalSheet(
    onDismiss: () -> Unit,
    fallback: @Composable () -> Unit,
    content: @Composable () -> Unit,
) = fallback()
