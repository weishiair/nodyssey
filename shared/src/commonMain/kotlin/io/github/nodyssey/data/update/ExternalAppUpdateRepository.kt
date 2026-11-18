package io.github.nodyssey.data.update

import io.github.plaza.core.AppClock
import io.github.plaza.core.update.AppRelease
import io.github.plaza.core.update.AppUpdateException
import io.github.plaza.core.update.AppUpdateState
import io.github.plaza.core.update.InstallOutcome
import io.github.plaza.core.update.ReleaseNote
import io.github.plaza.core.update.UpdateCheck
import io.github.plaza.core.update.UpdateCheckRecord
import io.github.plaza.core.update.isNewerVersionName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Version checks and reminders for packages installed externally, such as a re-signed IPA. */
class ExternalAppUpdateRepository(
    private val loadReleases: suspend (includePreRelease: Boolean) -> List<AppRelease>,
    private val store: UpdateCheckStore,
    private val clock: AppClock,
    private val scope: CoroutineScope,
    private val currentVersionName: String,
    private val sourceId: String,
) : AppUpdateRepository {
    private val mutableState = MutableStateFlow(AppUpdateState())
    override val state = mutableState.asStateFlow()
    private val mutableLaunchReminder = MutableStateFlow<AppRelease?>(null)
    override val launchReminder = mutableLaunchReminder.asStateFlow()
    private var checkJob: Job? = null
    private var reminding = false
    private var cachedReleases: CachedReleases? = null

    override fun check(force: Boolean) = runCheck(force, remind = false)

    override fun checkOnLaunch() = runCheck(force = false, remind = true)

    private fun runCheck(force: Boolean, remind: Boolean) {
        reminding = reminding || remind
        if (checkJob?.isActive == true) {
            if (!force) return
            // Changing channels is a forced check too: a slow answer for the old channel must
            // not make that request disappear. Its transport is cancelled before the new check.
            checkJob?.cancel()
        }
        checkJob = scope.launch {
            val devChannel = store.devChannelEnabled()
            val record = store.updateCheckRecord()
            val fresh = record.checkedAtMillis > 0L &&
                clock.nowMillis() - record.checkedAtMillis in 0 until CHECK_INTERVAL_MILLIS &&
                record.devChannel == devChannel && record.sourceId == sourceId
            if (!force && fresh) {
                publish(record.release)
            } else {
                mutableState.update { it.copy(check = UpdateCheck.Checking) }
                try {
                    val release = releases(devChannel, force = true).firstOrNull()
                    store.setUpdateCheckRecord(UpdateCheckRecord(clock.nowMillis(), release, devChannel, sourceId))
                    publish(release)
                } catch (e: AppUpdateException) {
                    mutableLaunchReminder.value = null
                    mutableState.update { it.copy(check = UpdateCheck.Failed(e.failure)) }
                }
            }
            if (reminding) {
                val release = state.value.available
                if (release != null && release.versionName != store.postponedUpdateVersion()) {
                    mutableLaunchReminder.value = release
                }
            }
            reminding = false
        }
    }

    private fun publish(release: AppRelease?) {
        val newer = release?.takeIf { isNewerVersionName(it.versionName, currentVersionName) }
        if (launchReminder.value?.versionName != newer?.versionName) mutableLaunchReminder.value = null
        mutableState.update { it.copy(check = newer?.let { release -> UpdateCheck.Available(release) } ?: UpdateCheck.UpToDate) }
    }

    /** The caller opens the release page; IPA installation belongs to the user's signing tool. */
    override fun acceptLaunchReminder() {
        mutableLaunchReminder.value = null
    }

    override fun postponeLaunchReminder() {
        val release = launchReminder.value ?: return
        mutableLaunchReminder.value = null
        scope.launch { store.setPostponedUpdateVersion(release.versionName) }
    }

    override suspend fun releaseNotes(force: Boolean): List<ReleaseNote> =
        releases(store.devChannelEnabled(), force).map {
            ReleaseNote(it.versionName, it.tag, it.notes, "", it.preRelease, it.htmlUrl)
        }

    private suspend fun releases(devChannel: Boolean, force: Boolean): List<AppRelease> {
        val cached = cachedReleases
        if (!force && cached != null && cached.devChannel == devChannel &&
            clock.nowMillis() - cached.checkedAt in 0 until CHECK_INTERVAL_MILLIS
        ) {
            return cached.releases
        }
        return loadReleases(devChannel).also { cachedReleases = CachedReleases(clock.nowMillis(), devChannel, it) }
    }

    // External installation never creates an APK download or an Android install session.
    override fun download() = Unit

    override fun cancelDownload() = Unit

    override fun onInstallOutcome(outcome: InstallOutcome) = Unit

    private data class CachedReleases(val checkedAt: Long, val devChannel: Boolean, val releases: List<AppRelease>)

    companion object {
        const val CHECK_INTERVAL_MILLIS = 6L * 60 * 60 * 1_000
    }
}
