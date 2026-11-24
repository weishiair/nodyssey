package io.github.nodyssey.ui.settings

/** Whether this shell schedules the forum, mail, keyword and tracked-thread poller. */
internal expect val platformHasBackgroundNotificationPolling: Boolean

/** Whether this shell can compare the installed version with published packages. */
internal expect val platformHasUpdateChecks: Boolean

/** Whether this shell downloads and installs updates inside the app. */
internal expect val platformHasInAppUpdates: Boolean

/** Whether Compose runs the back stack here, so 预测式返回动画 has an animation to turn off. */
internal expect val platformHasPredictiveBack: Boolean
