package io.github.plaza.designsys.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun nativeModalBottomSheet(onDismissRequest: () -> Unit, modifier: Modifier, expandFully: Boolean, dismissEnabled: Boolean, showDragHandle: Boolean, content: @Composable ColumnScope.() -> Unit): Boolean = false
