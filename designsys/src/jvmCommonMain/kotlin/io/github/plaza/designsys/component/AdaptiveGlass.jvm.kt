package io.github.plaza.designsys.component

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
actual fun AdaptiveGlassButton(action: GlassAction, modifier: Modifier, fallback: @Composable () -> Unit) = fallback()

@Composable
actual fun AdaptiveGlassToolbar(
    actions: List<GlassAction>,
    modifier: Modifier,
    vertical: Boolean,
    fallback: @Composable () -> Unit,
) = fallback()

@Composable
actual fun AdaptiveGlassTopBar(
    title: String,
    titleAction: GlassAction?,
    back: GlassAction?,
    actions: List<GlassAction>,
    modifier: Modifier,
    subtitle: String?,
    containerColor: Color,
    fallback: @Composable () -> Unit,
) = fallback()

@Composable
actual fun AdaptiveNativeMenu(
    label: String,
    actions: List<GlassMenuAction>,
    modifier: Modifier,
    enabled: Boolean,
    cooperativeInScroll: Boolean,
    fallback: @Composable (expanded: Boolean, onDismiss: () -> Unit) -> Unit,
    trigger: @Composable (onClick: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        trigger { if (enabled) expanded = true }
        fallback(expanded && enabled) { expanded = false }
    }
}

@Composable
actual fun AdaptiveGlassNotice(
    message: String,
    action: GlassAction?,
    dismissAction: GlassAction?,
    modifier: Modifier,
    fallback: @Composable () -> Unit,
) = fallback()
