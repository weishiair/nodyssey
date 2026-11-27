package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable

internal actual val platformHasBackgroundNotificationPolling: Boolean = false
internal actual val platformHasInAppUpdates: Boolean = false
internal actual val platformHasUpdateChecks: Boolean = false
internal actual val platformHasPredictiveBack: Boolean = true

@Composable
internal actual fun rememberPadLayoutOptionAvailable(): Boolean = false
