@file:OptIn(ExperimentalForeignApi::class)

package io.github.nodyssey.ios

import io.github.nodyssey.core.Site
import io.github.nodyssey.data.lottery.LotteryEntry
import io.github.nodyssey.data.lottery.LotteryOutcome
import io.github.nodyssey.data.lottery.LotteryPlatform
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.lottery_notify_due
import io.github.nodyssey.ui.resources.lottery_notify_lost
import io.github.nodyssey.ui.resources.lottery_notify_not_entered
import io.github.nodyssey.ui.resources.lottery_notify_won
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.compose.resources.getString
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

class IosLotteryPlatform(
    private val site: Site,
    private val scheduler: BgTaskLotteryScheduler,
) : LotteryPlatform {
    private val center get() = UNUserNotificationCenter.currentNotificationCenter()
    private val prefix get() = "lottery-${site.name}-"

    override suspend fun sync(entries: List<LotteryEntry>) {
        val pending = suspendCancellableCoroutine<List<String>> { continuation ->
            center.getPendingNotificationRequestsWithCompletionHandler { requests ->
                continuation.resume(requests.orEmpty().filterIsInstance<UNNotificationRequest>().map { it.identifier })
            }
        }
        val now = (NSDate().timeIntervalSince1970 * 1000).toLong()
        val upcoming = entries.filter { it.outcome == null && it.draw.drawAtMillis > now }.sortedBy { it.draw.drawAtMillis }
        val identifiers = upcoming.map { prefix + it.key }.toSet()
        center.removePendingNotificationRequestsWithIdentifiers(pending.filter { it.startsWith(prefix) && it !in identifiers })
        upcoming.forEach { entry ->
            val content = content(entry, getString(Res.string.lottery_notify_due))
            val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(
                ((entry.draw.drawAtMillis - now) / 1000.0).coerceAtLeast(1.0),
                repeats = false,
            )
            center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(prefix + entry.key, content, trigger), null)
        }
        scheduler.sync(entries)
    }

    override suspend fun notifyResult(entry: LotteryEntry): Boolean {
        val permitted = suspendCancellableCoroutine<Boolean> { continuation ->
            center.getNotificationSettingsWithCompletionHandler { settings ->
                continuation.resume(
                    settings?.authorizationStatus in setOf(
                        UNAuthorizationStatusAuthorized,
                        UNAuthorizationStatusProvisional,
                        UNAuthorizationStatusEphemeral,
                    ),
                )
            }
        }
        if (!permitted) return false
        val title = getString(
            when (entry.outcome) {
                LotteryOutcome.WON -> Res.string.lottery_notify_won
                LotteryOutcome.LOST -> Res.string.lottery_notify_lost
                LotteryOutcome.NOT_ENTERED -> Res.string.lottery_notify_not_entered
                null -> return false
            },
            entry.ownerName,
        )
        return suspendCancellableCoroutine { continuation ->
            center.addNotificationRequest(
                UNNotificationRequest.requestWithIdentifier(prefix + entry.key + "-result", content(entry, title), null),
            ) { error -> continuation.resume(error == null) }
        }
    }

    private fun content(entry: LotteryEntry, title: String) = UNMutableNotificationContent().apply {
        // Kotlin/Native imports these inherited properties as val; the mutable subclass exposes setters.
        setTitle(title)
        setBody(entry.title)
        setSound(UNNotificationSound.defaultSound)
        setUserInfo(mapOf("lotteryPostId" to entry.draw.postId.toString(), "lotteryOrigin" to site.origin))
    }
}
