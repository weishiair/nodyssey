@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.nodyssey.ios

import io.github.nodyssey.data.attendance.AutoAttendanceOutcome
import io.github.nodyssey.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGAppRefreshTask
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSinceNow

/** Best-effort refresh; foreground catch-up uses the same runner and persisted receipts. */
class BgTaskAttendanceScheduler(
    private val scope: CoroutineScope,
    private val container: suspend () -> AppContainer,
) {
    fun register() {
        BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(TASK_ID, null) { task ->
            val refresh = task as? BGAppRefreshTask
            if (refresh == null) task?.setTaskCompletedWithSuccess(false) else run(refresh)
        }
    }

    fun sync(enabled: Boolean, delaySeconds: Double = 6.0 * 60.0 * 60.0) {
        val scheduler = BGTaskScheduler.sharedScheduler
        scheduler.cancelTaskRequestWithIdentifier(TASK_ID)
        if (!enabled) return
        val request = BGAppRefreshTaskRequest(TASK_ID).apply {
            earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(delaySeconds)
        }
        // Background refresh may be disabled or unavailable (including on the Simulator).
        // BGTaskScheduler is the scheduling authority; foreground catch-up remains available.
        scheduler.submitTaskRequest(request, error = null)
    }

    private fun run(task: BGAppRefreshTask) {
        // Schedule first: expiration must not break the chain of future requests.
        sync(true)
        var success = false
        val job = scope.launch {
            val graph = container()
            val outcome = graph.withAccountWork { graph.automaticAttendance?.run() ?: AutoAttendanceOutcome.SKIPPED }
            success = outcome != AutoAttendanceOutcome.RETRY
            sync(
                graph.automaticAttendance?.enabled?.first() ?: false,
                if (outcome == AutoAttendanceOutcome.RETRY) 30.0 * 60.0 else 6.0 * 60.0 * 60.0,
            )
        }
        task.expirationHandler = { job.cancel() }
        job.invokeOnCompletion { cause -> task.setTaskCompletedWithSuccess(cause == null && success) }
    }

    private companion object {
        const val TASK_ID = "io.github.nodyssey.attendance"
    }
}
