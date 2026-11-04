package io.github.nodyssey.data.attendance

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

object AutoAttendanceWork {
    private const val WORK_NAME = "automatic-attendance"

    fun sync(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutoAttendanceWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, AutoAttendance.RETRY_DELAY_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

class AutoAttendanceWorker(
    context: Context,
    parameters: WorkerParameters,
    private val attendance: AutomaticAttendance?,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result =
        if (attendance?.run() == AutoAttendanceOutcome.RETRY) Result.retry() else Result.success()
}
