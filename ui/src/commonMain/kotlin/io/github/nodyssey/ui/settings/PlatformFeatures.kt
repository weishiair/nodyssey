package io.github.nodyssey.ui.settings

/** Whether this shell schedules the forum, mail, keyword and tracked-thread poller. */
internal expect val platformHasBackgroundNotificationPolling: Boolean

/** Whether this shell can compare the installed version with published packages. */
internal expect val platformHasUpdateChecks: Boolean

/** Whether this shell downloads and installs updates inside the app. */
internal expect val platformHasInAppUpdates: Boolean
