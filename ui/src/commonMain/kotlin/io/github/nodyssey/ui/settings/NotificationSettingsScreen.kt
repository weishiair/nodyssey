package io.github.nodyssey.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Email
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.data.settings.UserSettings
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.keywords_alert_title
import io.github.nodyssey.ui.resources.keywords_summary_count
import io.github.nodyssey.ui.resources.keywords_summary_none
import io.github.nodyssey.ui.resources.lottery_title
import io.github.nodyssey.ui.resources.notifications_interactions
import io.github.nodyssey.ui.resources.notifications_messages
import io.github.nodyssey.ui.resources.notify_app_alerts_footer
import io.github.nodyssey.ui.resources.notify_app_alerts_section
import io.github.nodyssey.ui.resources.notify_background_unavailable_body
import io.github.nodyssey.ui.resources.notify_background_unavailable_title
import io.github.nodyssey.ui.resources.notify_channels_section
import io.github.nodyssey.ui.resources.notify_check_section
import io.github.nodyssey.ui.resources.notify_frequency
import io.github.nodyssey.ui.resources.notify_frequency_15
import io.github.nodyssey.ui.resources.notify_frequency_30
import io.github.nodyssey.ui.resources.notify_frequency_60
import io.github.nodyssey.ui.resources.notify_frequency_every
import io.github.nodyssey.ui.resources.notify_frequency_hint
import io.github.nodyssey.ui.resources.notify_keyword_alerts
import io.github.nodyssey.ui.resources.notify_lottery_hint
import io.github.nodyssey.ui.resources.notify_mail
import io.github.nodyssey.ui.resources.notify_mail_hint
import io.github.nodyssey.ui.resources.notify_master_hint
import io.github.nodyssey.ui.resources.notify_master_title
import io.github.nodyssey.ui.resources.notify_quiet_hours
import io.github.nodyssey.ui.resources.notify_quiet_hours_hint
import io.github.nodyssey.ui.resources.notify_saved_alerts_section
import io.github.nodyssey.ui.resources.notify_settings_title
import io.github.nodyssey.ui.resources.notify_telegram_action
import io.github.nodyssey.ui.resources.notify_telegram_body
import io.github.nodyssey.ui.resources.notify_telegram_title
import io.github.nodyssey.ui.resources.notify_tracked_threads
import io.github.nodyssey.ui.resources.notify_wifi_only
import io.github.nodyssey.ui.resources.notify_wifi_only_hint
import io.github.nodyssey.ui.resources.tracked_threads_title
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.settings.SettingsItem
import io.github.plaza.designsys.settings.SettingsPage
import io.github.plaza.designsys.settings.SettingsSection
import io.github.plaza.designsys.settings.SettingsSectionStyle
import io.github.plaza.designsys.theme.PlazaTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun NotificationSettingsRoute(
    viewModel: NotificationSettingsViewModel,
    onBack: () -> Unit,
    onOpenTelegram: () -> Unit,
    onOpenAlertKeywords: () -> Unit,
    onOpenTrackedThreads: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenLottery: (() -> Unit)? = null,
) {
    val settings by viewModel.uiState.collectAsStateWithLifecycle()
    val alertCounts by viewModel.alertCounts.collectAsStateWithLifecycle()
    val requestNotificationPermission = rememberNotificationPermissionRequest()
    NotificationSettingsScreen(
        settings = settings,
        onBack = onBack,
        onEnabledChange = { enabled ->
            if (enabled) requestNotificationPermission()
            viewModel.setEnabled(enabled)
        },
        onPollMinutesChange = viewModel::setPollMinutes,
        onWifiOnlyChange = viewModel::setWifiOnly,
        onQuietHoursChange = viewModel::setQuietHours,
        onNotifyInteractionsChange = viewModel::setNotifyInteractions,
        onNotifyMessagesChange = viewModel::setNotifyMessages,
        onNotifyMailChange = viewModel::setNotifyMail,
        onOpenTelegram = onOpenTelegram,
        alertCounts = alertCounts,
        onNotifyKeywordAlertsChange = viewModel::setNotifyKeywordAlerts,
        onNotifyTrackedThreadsChange = viewModel::setNotifyTrackedThreads,
        onOpenAlertKeywords = onOpenAlertKeywords,
        onOpenTrackedThreads = onOpenTrackedThreads,
        modifier = modifier,
        onOpenLottery = onOpenLottery,
    )
}

/**
 * Board f4, in 6d's cards: the master switch on a tinted card of its own, then 检查 and 通知渠道, then
 * the Telegram pointer. On iOS the same page is the system list — see [SettingsPageScreen].
 *
 * 检查频率 is a row with its answer on the second line and the three choices in a sheet (6d2) rather
 * than a segmented control in the card: it is set once, and the sheet has the room to say what a
 * shorter interval costs. On iOS the sheet is the row's pop-up menu, and the cost is its title.
 */
@Composable
fun NotificationSettingsScreen(
    settings: UserSettings,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onPollMinutesChange: (Int) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onQuietHoursChange: (Boolean) -> Unit,
    onNotifyInteractionsChange: (Boolean) -> Unit,
    onNotifyMessagesChange: (Boolean) -> Unit,
    onOpenTelegram: () -> Unit,
    modifier: Modifier = Modifier,
    onNotifyMailChange: (Boolean) -> Unit = {},
    alertCounts: AlertCounts = AlertCounts(),
    onNotifyKeywordAlertsChange: (Boolean) -> Unit = {},
    onNotifyTrackedThreadsChange: (Boolean) -> Unit = {},
    onOpenAlertKeywords: () -> Unit = {},
    onOpenTrackedThreads: () -> Unit = {},
    onOpenLottery: (() -> Unit)? = null,
    backgroundPollingSupported: Boolean = platformHasBackgroundNotificationPolling,
) {
    val keywordsRow =
        SettingsItem.Link(
            key = "alert-keywords",
            title = stringResource(Res.string.keywords_alert_title),
            value = countSummary(alertCounts.keywords),
            onClick = onOpenAlertKeywords,
        )
    val trackedThreadsRow =
        SettingsItem.Link(
            key = "tracked-threads",
            title = stringResource(Res.string.tracked_threads_title),
            value = countSummary(alertCounts.trackedThreads),
            onClick = onOpenTrackedThreads,
        )
    // The Telegram pointer stays active regardless of the master switch: the site's own channel is the
    // alternative to polling, not part of it.
    val telegram =
        SettingsSection(
            key = "telegram",
            style = SettingsSectionStyle.Callout,
            items =
            listOf(
                SettingsItem.Link(
                    key = "telegram",
                    title = stringResource(Res.string.notify_telegram_title),
                    description = stringResource(Res.string.notify_telegram_body),
                    value = stringResource(Res.string.notify_telegram_action),
                    icon = Icons.AutoMirrored.Filled.Send,
                    onClick = onOpenTelegram,
                ),
            ),
        )
    val sections =
        if (backgroundPollingSupported) {
            pollingSections(
                settings = settings,
                onEnabledChange = onEnabledChange,
                onPollMinutesChange = onPollMinutesChange,
                onWifiOnlyChange = onWifiOnlyChange,
                onQuietHoursChange = onQuietHoursChange,
                onNotifyInteractionsChange = onNotifyInteractionsChange,
                onNotifyMessagesChange = onNotifyMessagesChange,
                onNotifyMailChange = onNotifyMailChange,
                onNotifyKeywordAlertsChange = onNotifyKeywordAlertsChange,
                onNotifyTrackedThreadsChange = onNotifyTrackedThreadsChange,
                keywordsRow = keywordsRow,
                trackedThreadsRow = trackedThreadsRow,
            )
        } else {
            listOfNotNull(
                SettingsSection(
                    key = "background-unavailable",
                    header = stringResource(Res.string.notify_background_unavailable_title),
                    footer = stringResource(Res.string.notify_background_unavailable_body),
                ),
                onOpenLottery?.let { open ->
                    SettingsSection(
                        key = "lottery",
                        items =
                        listOf(
                            SettingsItem.Link(
                                key = "lottery",
                                title = stringResource(Res.string.lottery_title),
                                description = stringResource(Res.string.notify_lottery_hint),
                                icon = PlazaIcons.Campaign,
                                onClick = open,
                            ),
                        ),
                    )
                },
                SettingsSection(
                    key = "saved-alerts",
                    header = stringResource(Res.string.notify_saved_alerts_section),
                    items = listOf(keywordsRow, trackedThreadsRow),
                ),
            )
        } + telegram
    SettingsPageScreen(
        page = SettingsPage(title = stringResource(Res.string.notify_settings_title), sections = sections),
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Everything below the master switch is inert while it is off — f4's "主开关关闭时下方全部禁用" — except
 * the two 管理 rows: what they manage is kept whether or not anything is being checked.
 */
@Composable
private fun pollingSections(
    settings: UserSettings,
    onEnabledChange: (Boolean) -> Unit,
    onPollMinutesChange: (Int) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onQuietHoursChange: (Boolean) -> Unit,
    onNotifyInteractionsChange: (Boolean) -> Unit,
    onNotifyMessagesChange: (Boolean) -> Unit,
    onNotifyMailChange: (Boolean) -> Unit,
    onNotifyKeywordAlertsChange: (Boolean) -> Unit,
    onNotifyTrackedThreadsChange: (Boolean) -> Unit,
    keywordsRow: SettingsItem,
    trackedThreadsRow: SettingsItem,
): List<SettingsSection> {
    val enabled = settings.notificationsEnabled
    val choices = SettingsRepository.POLL_MINUTE_CHOICES
    val frequencyLabels =
        choices.map { minutes ->
            stringResource(Res.string.notify_frequency_every, stringResource(pollMinutesLabel(minutes)))
        }
    // A value this build never wrote reads as the default, the way [pollMinutesLabel] names it.
    val selectedFrequency =
        choices.indexOf(settings.notificationPollMinutes).takeIf { it >= 0 }
            ?: choices.indexOf(SettingsRepository.DEFAULT_POLL_MINUTES)
    return listOf(
        SettingsSection(
            key = "master",
            style = SettingsSectionStyle.Prominent,
            items =
            listOf(
                SettingsItem.Toggle(
                    key = "enabled",
                    title = stringResource(Res.string.notify_master_title),
                    description = stringResource(Res.string.notify_master_hint),
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                ),
            ),
        ),
        SettingsSection(
            key = "check",
            header = stringResource(Res.string.notify_check_section),
            items =
            listOf(
                SettingsItem.Choice(
                    key = "frequency",
                    title = stringResource(Res.string.notify_frequency),
                    prompt = stringResource(Res.string.notify_frequency_hint),
                    options = frequencyLabels,
                    selectedIndex = selectedFrequency,
                    onSelect = { index -> onPollMinutesChange(choices[index]) },
                    icon = PlazaIcons.Schedule,
                    enabled = enabled,
                ),
                SettingsItem.Toggle(
                    key = "wifi-only",
                    title = stringResource(Res.string.notify_wifi_only),
                    description = stringResource(Res.string.notify_wifi_only_hint),
                    checked = settings.notificationsWifiOnly,
                    onCheckedChange = onWifiOnlyChange,
                    icon = PlazaIcons.Wifi,
                    enabled = enabled,
                ),
                SettingsItem.Toggle(
                    key = "quiet-hours",
                    title = stringResource(Res.string.notify_quiet_hours),
                    description = stringResource(Res.string.notify_quiet_hours_hint),
                    checked = settings.notificationQuietHours,
                    onCheckedChange = onQuietHoursChange,
                    icon = PlazaIcons.Bedtime,
                    enabled = enabled,
                ),
            ),
        ),
        // One row for the two site groups, because one comment is filed under both and the app posts
        // one notification for it — see `NotificationChannels`.
        SettingsSection(
            key = "channels",
            header = stringResource(Res.string.notify_channels_section),
            items =
            listOf(
                SettingsItem.Toggle(
                    key = "interactions",
                    title = stringResource(Res.string.notifications_interactions),
                    checked = settings.notifyInteractions,
                    onCheckedChange = onNotifyInteractionsChange,
                    icon = PlazaIcons.AlternateEmail,
                    enabled = enabled,
                ),
                SettingsItem.Toggle(
                    key = "messages",
                    title = stringResource(Res.string.notifications_messages),
                    checked = settings.notifyMessages,
                    onCheckedChange = onNotifyMessagesChange,
                    icon = Icons.Default.Email,
                    enabled = enabled,
                ),
                SettingsItem.Toggle(
                    key = "mail",
                    title = stringResource(Res.string.notify_mail),
                    description = stringResource(Res.string.notify_mail_hint),
                    checked = settings.notifyMail,
                    onCheckedChange = onNotifyMailChange,
                    icon = Icons.Default.Email,
                    enabled = enabled,
                ),
            ),
        ),
        // The app's own alerts. They read public lists, so unlike the two above they work signed out —
        // the section says so rather than leaving it to be discovered.
        SettingsSection(
            key = "app-alerts",
            header = stringResource(Res.string.notify_app_alerts_section),
            footer = stringResource(Res.string.notify_app_alerts_footer),
            items =
            listOf(
                SettingsItem.Toggle(
                    key = "keyword-alerts",
                    title = stringResource(Res.string.notify_keyword_alerts),
                    checked = settings.notifyKeywordAlerts,
                    onCheckedChange = onNotifyKeywordAlertsChange,
                    icon = PlazaIcons.Campaign,
                    enabled = enabled,
                ),
                keywordsRow,
                SettingsItem.Toggle(
                    key = "tracked-threads-alerts",
                    title = stringResource(Res.string.notify_tracked_threads),
                    checked = settings.notifyTrackedThreads,
                    onCheckedChange = onNotifyTrackedThreadsChange,
                    icon = PlazaIcons.Forum,
                    enabled = enabled,
                ),
                trackedThreadsRow,
            ),
        ),
    )
}

/** The three intervals the store accepts, by name. Anything else is a value this build never wrote. */
internal fun pollMinutesLabel(minutes: Int): StringResource =
    when (minutes) {
        15 -> Res.string.notify_frequency_15
        60 -> Res.string.notify_frequency_60
        else -> Res.string.notify_frequency_30
    }

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun NotificationSettingsPreview() {
    PlazaTheme {
        NotificationSettingsScreen(
            settings = UserSettings(notificationsEnabled = true),
            onBack = {},
            onEnabledChange = {},
            onPollMinutesChange = {},
            onWifiOnlyChange = {},
            onQuietHoursChange = {},
            onNotifyInteractionsChange = {},
            onNotifyMessagesChange = {},
            onOpenTelegram = {},
        )
    }
}

@Composable
private fun countSummary(count: Int): String =
    if (count == 0) stringResource(Res.string.keywords_summary_none) else stringResource(Res.string.keywords_summary_count, count)
