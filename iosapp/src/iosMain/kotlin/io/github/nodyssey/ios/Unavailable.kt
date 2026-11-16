package io.github.nodyssey.ios

import io.github.nodyssey.data.update.AppUpdateRepository
import io.github.plaza.core.update.ApkInstaller
import io.github.plaza.core.update.AppRelease
import io.github.plaza.core.update.AppUpdateState
import io.github.plaza.core.update.InstallOutcome
import io.github.plaza.core.update.ReleaseNote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** iOS packages are distributed as unsigned IPAs; installation is handled by the user's signing tool. */
internal object NoApkInstaller : ApkInstaller {
    override fun canInstallPackages(): Boolean = false

    override suspend fun install(apkPath: String): Boolean = false
}

/**
 * iOS has no in-app update checker or installer. The UI opens the GitHub release page for versions,
 * downloads and release notes; it does not present this idle repository as a working update check.
 */
internal object NoAppUpdates : AppUpdateRepository {
    override val state: StateFlow<AppUpdateState> = MutableStateFlow(AppUpdateState())

    override val launchReminder: StateFlow<AppRelease?> = MutableStateFlow(null)

    override fun check(force: Boolean) = Unit

    override fun checkOnLaunch() = Unit

    override fun acceptLaunchReminder() = Unit

    override fun postponeLaunchReminder() = Unit

    override fun download() = Unit

    override fun cancelDownload() = Unit

    override suspend fun releaseNotes(force: Boolean): List<ReleaseNote> = emptyList()

    override fun onInstallOutcome(outcome: InstallOutcome) = Unit
}
