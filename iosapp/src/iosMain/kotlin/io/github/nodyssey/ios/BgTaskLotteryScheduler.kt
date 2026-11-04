@file:OptIn(ExperimentalForeignApi::class)

package io.github.nodyssey.ios

import io.github.nodyssey.data.lottery.LotteryEntry
import io.github.nodyssey.data.lottery.LotteryRepository
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGAppRefreshTask
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970

/** iOS chooses when to fetch results; the separate local reminder needs no background execution. */
class BgTaskLotteryScheduler(
    private val scope: CoroutineScope,
    private val repository: suspend () -> LotteryRepository?,
) {
    private var registered = false

    fun register() {
        if (registered) return
        registered = BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(TASK_ID, null) { task ->
            val refresh = task as? BGAppRefreshTask
            if (refresh == null) {
                task?.setTaskCompletedWithSuccess(false)
            } else {
                var success = false
                val job = scope.launch {
                    success = runCatchingExceptCancellation {
                        repository()?.let {
                            it.refreshDue()
                            it.synchronize()
                        }
                    }.isSuccess
                }
                refresh.expirationHandler = { job.cancel() }
                job.invokeOnCompletion { cause -> refresh.setTaskCompletedWithSuccess(cause == null && success) }
            }
        }
    }

    fun sync(entries: List<LotteryEntry>) {
        if (!registered) return
        val scheduler = BGTaskScheduler.sharedScheduler
        scheduler.cancelTaskRequestWithIdentifier(TASK_ID)
        if (entries.any { it.outcome == null && it.blockedFingerprint != null }) return
        val next = entries.filter { it.outcome == null && it.blockedFingerprint == null }
            .minOfOrNull { maxOf(it.draw.drawAtMillis + 1_000, it.nextCheckMillis) } ?: return
        val now = (NSDate().timeIntervalSince1970 * 1000).toLong()
        val request = BGAppRefreshTaskRequest(TASK_ID).apply {
            earliestBeginDate = NSDate.dateWithTimeIntervalSince1970(maxOf(next, now + 60_000) / 1000.0)
        }
        // Simulator / OS budget refusal leaves the local notification and foreground catch-up intact.
        try {
            scheduler.submitTaskRequest(request, error = null)
        } catch (_: Exception) {
            return
        }
    }

    private companion object {
        const val TASK_ID = "io.github.nodyssey.lottery.refresh"
    }
}
