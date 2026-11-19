package io.github.plaza.designsys.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * [page] as the platform's own settings list. On iOS that is the whole page in UIKit — a navigation
 * bar and an inset-grouped table with `UISwitch` and pop-up menus — hosted as one view rather than a
 * native control per Compose row. Everywhere else it is [fallback], the page's Compose rows.
 */
@Composable
expect fun AdaptiveSettingsPage(
    page: SettingsPage,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit,
)

/** Whether [AdaptiveSettingsPage] draws the system list here rather than its Compose fallback. */
expect val nativeSettingsPages: Boolean
