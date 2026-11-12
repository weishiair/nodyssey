package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
    fallback: @Composable () -> Unit,
) = fallback()

@Composable
actual fun AdaptiveGlassNotice(
    message: String,
    action: GlassAction?,
    dismissAction: GlassAction?,
    modifier: Modifier,
    fallback: @Composable () -> Unit,
) = fallback()
