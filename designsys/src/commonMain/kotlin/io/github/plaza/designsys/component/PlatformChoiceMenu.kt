package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable

/** A system action sheet on iOS; the caller keeps its anchored Material menu elsewhere. */
@Composable
expect fun platformChoiceMenu(expanded: Boolean, onDismissRequest: () -> Unit, actions: List<PlatformBarAction>): Boolean
