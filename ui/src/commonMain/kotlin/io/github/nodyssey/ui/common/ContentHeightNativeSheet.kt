package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable

/** Fits a native sheet to the measured height of short content. */
@Composable
internal expect fun ContentHeightNativeSheet(content: @Composable () -> Unit)
