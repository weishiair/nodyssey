package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

internal actual val platformHasBackgroundNotificationPolling: Boolean = true
internal actual val platformHasInAppUpdates: Boolean = true
internal actual val platformHasUpdateChecks: Boolean = true
internal actual val platformHasPredictiveBack: Boolean = true

private const val TABLET_SMALLEST_WIDTH_DP = 600

@Composable
internal actual fun rememberPadLayoutOptionAvailable(): Boolean =
    LocalConfiguration.current.smallestScreenWidthDp >= TABLET_SMALLEST_WIDTH_DP
