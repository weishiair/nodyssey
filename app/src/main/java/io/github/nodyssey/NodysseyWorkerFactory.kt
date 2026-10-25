package io.github.nodyssey

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import io.github.nodyssey.data.offline.OfflineDownloadWorker
import io.github.nodyssey.data.offline.OfflineDownloads
import io.github.nodyssey.data.offline.OfflineMaintenanceWorker
import io.github.nodyssey.di.AndroidAppContainer
import io.github.nodyssey.notifications.NotificationPollWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Hands each worker its dependencies out of the container, which is what makes a worker a plain
 * constructor-injected class a test can build with fakes.
 *
 * Before this factory existed every worker began with `applicationContext as NodysseyApp` — a cast
 * the compiler cannot check and a test cannot satisfy, which is why the workers had no tests. The
 * container is taken as a function rather than a value for the same reason the cast used to be
 * inside `doWork`: this factory is built inside `workManagerConfiguration`, and the container it
 * reads from is assigned in `onCreate` — asking at [createWorker] time keeps the order of those two
 * a fact WorkManager owns rather than one this class has to be lucky about.
 */
class NodysseyWorkerFactory(
    private val container: () -> AndroidAppContainer,
    private val isReady: () -> Boolean = { true },
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        if (workerClassName !in setOf(
                OfflineDownloadWorker::class.qualifiedName,
                OfflineMaintenanceWorker::class.qualifiedName,
                NotificationPollWorker::class.qualifiedName,
            )
        ) {
            return null
        }
        val container = container()
        if (!isReady() || !container.isActive) return SiteSwitchRetryWorker(appContext, workerParameters)
        return SiteScopedWorker(appContext, workerParameters, container) {
            when (workerClassName) {
                OfflineDownloadWorker::class.qualifiedName ->
                    OfflineDownloadWorker(
                        appContext,
                        workerParameters,
                        // `as?`, and the null genuinely means something: a library built without offline
                        // reading does not implement the download half, and a worker holding null
                        // finishes with nothing to do — see the note on [OfflineDownloads].
                        container.offlineLibrary as? OfflineDownloads,
                    )

                OfflineMaintenanceWorker::class.qualifiedName ->
                    OfflineMaintenanceWorker(
                        appContext,
                        workerParameters,
                        container.offlineLibrary,
                        container.offlineLibrary as? OfflineDownloads,
                        container.sessionRepository,
                        container.userSpaceRepository,
                    )

                NotificationPollWorker::class.qualifiedName ->
                    NotificationPollWorker(
                        appContext,
                        workerParameters,
                        container.settingsRepository,
                        container.sessionRepository,
                        container.notificationRepository,
                        container.clock,
                        container.backgroundAlerts,
                    )

                // Not an error: null sends WorkManager down its reflective default path, which is the
                // right answer for a worker some library enqueued for itself.
                else -> error("Unsupported site worker")
            }
        }
    }
}

/** Cancelling a graph also waits for its workers before ActiveSite can change underneath them. */
private class SiteScopedWorker(
    context: Context,
    parameters: WorkerParameters,
    private val container: AndroidAppContainer,
    private val delegate: () -> CoroutineWorker,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        container.runSiteWork { delegate().doWork() }
    } catch (cancelled: CancellationException) {
        currentCoroutineContext().ensureActive()
        if (container.isActive) throw cancelled else Result.retry()
    }
}

private class SiteSwitchRetryWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = Result.retry()
}
