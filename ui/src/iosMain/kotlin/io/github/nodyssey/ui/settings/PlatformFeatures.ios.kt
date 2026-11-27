package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable
import io.github.nodyssey.ui.common.LocalIosPad

internal actual val platformHasBackgroundNotificationPolling: Boolean = false
internal actual val platformHasInAppUpdates: Boolean = false
internal actual val platformHasUpdateChecks: Boolean = true
internal actual val platformHasPredictiveBack: Boolean = false

@Composable
internal actual fun rememberPadLayoutOptionAvailable(): Boolean = LocalIosPad.current
