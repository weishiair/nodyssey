package io.github.nodyssey.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.nodyssey.MainActivity
import io.github.nodyssey.R
import io.github.nodyssey.core.ActiveSite
import io.github.nodyssey.core.Site
import io.github.nodyssey.data.lottery.LotteryEntry
import io.github.nodyssey.data.lottery.LotteryOutcome
import io.github.nodyssey.data.lottery.LotteryPlatform
import io.github.nodyssey.data.lottery.LotteryRepository
import io.github.nodyssey.data.lottery.LotteryStore
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

internal val Context.lotteryDataStore by preferencesDataStore(name = "lottery")
private const val ACTION_DRAW = "io.github.nodyssey.action.LOTTERY_DRAW"

class AndroidLotteryPlatform(private val context: Context, private val site: Site) : LotteryPlatform {
    override suspend fun sync(entries: List<LotteryEntry>) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val manager = WorkManager.getInstance(context)
        val preferences = context.getSharedPreferences("lottery_scheduled", Context.MODE_PRIVATE)
        val old = preferences.getStringSet(site.name, emptySet()).orEmpty().toSet()
        val keys = entries.mapTo(mutableSetOf()) { it.key }
        (old - keys).forEach { key ->
            alarms.cancel(alarmIntent(context, site, key))
            manager.cancelUniqueWork(workName(site, key))
        }
        val now = System.currentTimeMillis()
        entries.forEach { entry ->
            val intent = alarmIntent(context, site, entry.key)
            if (entry.outcome == null && entry.draw.drawAtMillis > now) {
                // WorkManager cannot deliver a wall-clock reminder. AlarmManager is its official
                // counterpart; without special access use the OS's inexact idle-capable alarm.
                try {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
                        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, entry.draw.drawAtMillis, intent)
                    } else {
                        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, entry.draw.drawAtMillis, intent)
                    }
                } catch (_: SecurityException) {
                    // Exact-alarm access can change between checking and scheduling.
                    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, entry.draw.drawAtMillis, intent)
                }
            } else {
                alarms.cancel(intent)
            }
            if (entry.outcome == null) {
                val delay = (maxOf(entry.draw.drawAtMillis + 1_000, entry.nextCheckMillis) - now).coerceAtLeast(0)
                val work = OneTimeWorkRequestBuilder<LotteryResultWorker>()
                    .setInputData(workDataOf("site" to site.name, "draw" to entry.key))
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                    .build()
                manager.enqueueUniqueWork(workName(site, entry.key), ExistingWorkPolicy.KEEP, work)
            }
        }
        preferences.edit { putStringSet(site.name, keys) }
    }

    override suspend fun notifyResult(entry: LotteryEntry): Boolean {
        val title = context.getString(
            when (entry.outcome) {
                LotteryOutcome.WON -> R.string.lottery_notify_won
                LotteryOutcome.LOST -> R.string.lottery_notify_lost
                LotteryOutcome.NOT_ENTERED -> R.string.lottery_notify_not_entered
                null -> return false
            },
            entry.ownerName,
        )
        return postLotteryNotification(context, site, entry, title, "result")
    }
}

/** Only our alarm PendingIntents and system restore broadcasts can reach this receiver. */
class LotteryAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DRAW,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> Unit

            else -> return
        }
        val completion = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                runCatchingExceptCancellation {
                    val namedSite = intent.getStringExtra("site")
                    val sites = namedSite?.let { value -> Site.entries.filter { it.name == value } } ?: Site.entries
                    for (site in sites) {
                        val entries = LotteryStore(context.lotteryDataStore, site).all()
                        val key = intent.getStringExtra("draw")
                        if (key != null) {
                            entries.firstOrNull { it.key == key && it.outcome == null }?.let { entry ->
                                postLotteryNotification(context, site, entry, context.getString(R.string.lottery_notify_due), "due")
                            }
                        }
                        AndroidLotteryPlatform(context, site).sync(entries)
                    }
                }
            } finally {
                completion.finish()
            }
        }
    }
}

class LotteryResultWorker(
    context: Context,
    parameters: WorkerParameters,
    private val repository: LotteryRepository?,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (inputData.getString("site") != ActiveSite.current.name) return Result.retry()
        val repo = repository ?: return Result.success()
        val key = inputData.getString("draw") ?: return Result.success()
        repo.refreshDue(key)
        val entry = repo.entries.first().firstOrNull { it.key == key } ?: return Result.success()
        return if (entry.outcome == null) Result.retry() else Result.success()
    }
}

private fun workName(site: Site, key: String) = "lottery-result-${site.name}-$key"

private fun alarmIntent(context: Context, site: Site, key: String): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, LotteryAlarmReceiver::class.java)
            .setAction(ACTION_DRAW)
            .setData("nodyssey-lottery://${site.name}/$key".toUri())
            .putExtra("site", site.name)
            .putExtra("draw", key),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

@SuppressLint("MissingPermission")
private fun postLotteryNotification(context: Context, site: Site, entry: LotteryEntry, title: String, kind: String): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return false
    }
    val manager = NotificationManagerCompat.from(context)
    if (!manager.areNotificationsEnabled()) return false
    NotificationChannels.ensure(context)
    val tag = "lottery-${site.name}-${entry.key}-$kind"
    val open = PendingIntent.getActivity(
        context,
        0,
        Intent(Intent.ACTION_VIEW, entry.postUrl.toUri())
            .setClass(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = NotificationCompat.Builder(context, NotificationChannels.LOTTERY)
        .setSmallIcon(R.drawable.ic_stat_notification)
        .setContentTitle(title)
        .setContentText(entry.title)
        .setContentIntent(open)
        .setAutoCancel(true)
        .build()
    return try {
        manager.notify(tag, 101, notification)
        true
    } catch (_: SecurityException) {
        false
    }
}
