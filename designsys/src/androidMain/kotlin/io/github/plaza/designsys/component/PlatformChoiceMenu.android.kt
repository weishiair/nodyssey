package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable

@Composable
actual fun platformChoiceMenu(expanded: Boolean, onDismissRequest: () -> Unit, actions: List<PlatformBarAction>): Boolean = false
