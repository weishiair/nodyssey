package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

actual val usesNativeChrome: Boolean = false

@Composable
actual fun platformActionButton(action: PlatformBarAction, modifier: Modifier, showLabel: Boolean, prominent: Boolean): Boolean = false

@Composable
actual fun platformActionToolbar(actions: List<PlatformBarAction>, modifier: Modifier): Boolean = false
