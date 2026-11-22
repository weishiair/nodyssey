package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable

@Composable
internal actual fun ContentHeightNativeSheet(content: @Composable () -> Unit) = content()
