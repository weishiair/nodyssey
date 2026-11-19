package io.github.plaza.designsys.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun AdaptiveSettingsPage(
    page: SettingsPage,
    onBack: () -> Unit,
    modifier: Modifier,
    fallback: @Composable () -> Unit,
) = fallback()
