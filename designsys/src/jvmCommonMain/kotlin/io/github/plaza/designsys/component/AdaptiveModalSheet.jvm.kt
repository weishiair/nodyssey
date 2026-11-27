package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
actual fun AdaptiveModalSheet(
    onDismiss: () -> Unit,
    fallback: @Composable () -> Unit,
    containerColor: Color?,
    fitContent: Boolean,
    content: @Composable () -> Unit,
) = fallback()
