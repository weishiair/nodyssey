package io.github.nodyssey.ui.settings

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.nodyssey.data.settings.UserSettings
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.keywords_alert_title
import io.github.nodyssey.ui.resources.lottery_title
import io.github.nodyssey.ui.resources.notifications_interactions
import io.github.nodyssey.ui.resources.notifications_messages
import io.github.nodyssey.ui.resources.notify_background_unavailable_title
import io.github.nodyssey.ui.resources.notify_frequency
import io.github.nodyssey.ui.resources.notify_frequency_15
import io.github.nodyssey.ui.resources.notify_frequency_every
import io.github.nodyssey.ui.resources.notify_keyword_alerts
import io.github.nodyssey.ui.resources.notify_mail
import io.github.nodyssey.ui.resources.notify_master_title
import io.github.nodyssey.ui.resources.notify_quiet_hours
import io.github.nodyssey.ui.resources.notify_telegram_title
import io.github.nodyssey.ui.resources.notify_tracked_threads
import io.github.nodyssey.ui.resources.notify_wifi_only
import io.github.nodyssey.ui.resources.tracked_threads_title
import io.github.plaza.designsys.theme.PlazaTheme
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1100dp")
class NotificationSettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `unsupported background polling hides polling controls and respects optional lottery entry`() {
        var masterTitle = ""
        var frequencyTitle = ""
        var wifiTitle = ""
        var unavailableTitle = ""
        var lotteryTitle = ""
        var keywordsTitle = ""
        var trackedTitle = ""
        var telegramTitle = ""
        var openedKeywords = false
        var openedTracked = false
        var openedTelegram = false

        composeRule.setContent {
            PlazaTheme {
                masterTitle = stringResource(Res.string.notify_master_title)
                frequencyTitle = stringResource(Res.string.notify_frequency)
                wifiTitle = stringResource(Res.string.notify_wifi_only)
                unavailableTitle = stringResource(Res.string.notify_background_unavailable_title)
                lotteryTitle = stringResource(Res.string.lottery_title)
                keywordsTitle = stringResource(Res.string.keywords_alert_title)
                trackedTitle = stringResource(Res.string.tracked_threads_title)
                telegramTitle = stringResource(Res.string.notify_telegram_title)
                NotificationSettingsScreen(
                    settings = UserSettings(notificationsEnabled = true),
                    onBack = {},
                    onEnabledChange = {},
                    onPollMinutesChange = {},
                    onWifiOnlyChange = {},
                    onQuietHoursChange = {},
                    onNotifyInteractionsChange = {},
                    onNotifyMessagesChange = {},
                    onOpenTelegram = { openedTelegram = true },
                    alertCounts = AlertCounts(keywords = 2, trackedThreads = 3),
                    onOpenAlertKeywords = { openedKeywords = true },
                    onOpenTrackedThreads = { openedTracked = true },
                    onOpenLottery = null,
                    backgroundPollingSupported = false,
                )
            }
        }

        composeRule.onNodeWithText(unavailableTitle).assertExists()
        composeRule.onNodeWithText(masterTitle).assertDoesNotExist()
        composeRule.onNodeWithText(frequencyTitle).assertDoesNotExist()
        composeRule.onNodeWithText(wifiTitle).assertDoesNotExist()
        composeRule.onNodeWithText(lotteryTitle).assertDoesNotExist()

        composeRule.onNodeWithText(keywordsTitle).performClick()
        composeRule.onNodeWithText(trackedTitle).performClick()
        composeRule.onNodeWithText(telegramTitle).performClick()

        assertTrue(openedKeywords)
        assertTrue(openedTracked)
        assertTrue(openedTelegram)
    }

    @Test
    fun `available lottery entry navigates when clicked`() {
        var lotteryTitle = ""
        var openedLottery = false

        composeRule.setContent {
            PlazaTheme {
                lotteryTitle = stringResource(Res.string.lottery_title)
                NotificationSettingsScreen(
                    settings = UserSettings(notificationsEnabled = false),
                    onBack = {},
                    onEnabledChange = {},
                    onPollMinutesChange = {},
                    onWifiOnlyChange = {},
                    onQuietHoursChange = {},
                    onNotifyInteractionsChange = {},
                    onNotifyMessagesChange = {},
                    onOpenTelegram = {},
                    onOpenLottery = { openedLottery = true },
                    backgroundPollingSupported = false,
                )
            }
        }

        composeRule.onNodeWithText(lotteryTitle).performClick()
        assertTrue(openedLottery)
    }

    @Test
    fun `master switch off disables polling rows while keeping management and telegram enabled`() {
        var wifiTitle = ""
        var quietTitle = ""
        var interactionsTitle = ""
        var messagesTitle = ""
        var mailTitle = ""
        var keywordToggleTitle = ""
        var trackedToggleTitle = ""
        var keywordsManageTitle = ""
        var trackedManageTitle = ""
        var telegramTitle = ""
        var openedKeywords = false
        var openedTracked = false
        var openedTelegram = false

        composeRule.setContent {
            PlazaTheme {
                wifiTitle = stringResource(Res.string.notify_wifi_only)
                quietTitle = stringResource(Res.string.notify_quiet_hours)
                interactionsTitle = stringResource(Res.string.notifications_interactions)
                messagesTitle = stringResource(Res.string.notifications_messages)
                mailTitle = stringResource(Res.string.notify_mail)
                keywordToggleTitle = stringResource(Res.string.notify_keyword_alerts)
                trackedToggleTitle = stringResource(Res.string.notify_tracked_threads)
                keywordsManageTitle = stringResource(Res.string.keywords_alert_title)
                trackedManageTitle = stringResource(Res.string.tracked_threads_title)
                telegramTitle = stringResource(Res.string.notify_telegram_title)
                NotificationSettingsScreen(
                    settings = UserSettings(notificationsEnabled = false),
                    onBack = {},
                    onEnabledChange = {},
                    onPollMinutesChange = {},
                    onWifiOnlyChange = {},
                    onQuietHoursChange = {},
                    onNotifyInteractionsChange = {},
                    onNotifyMessagesChange = {},
                    onOpenTelegram = { openedTelegram = true },
                    onOpenAlertKeywords = { openedKeywords = true },
                    onOpenTrackedThreads = { openedTracked = true },
                    backgroundPollingSupported = true,
                )
            }
        }

        composeRule.onNodeWithText(wifiTitle).performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText(quietTitle).performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText(interactionsTitle).performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText(messagesTitle).performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText(mailTitle).performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText(keywordToggleTitle).performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText(trackedToggleTitle).performScrollTo().assertIsNotEnabled()

        composeRule.onNodeWithText(keywordsManageTitle).performScrollTo().assertIsEnabled().performClick()
        composeRule.onNodeWithText(trackedManageTitle).performScrollTo().assertIsEnabled().performClick()
        composeRule.onNodeWithText(telegramTitle).performScrollTo().assertIsEnabled().performClick()

        assertTrue(openedKeywords)
        assertTrue(openedTracked)
        assertTrue(openedTelegram)
    }

    @Test
    fun `frequency sheet selection updates poll minutes and closes`() {
        var frequencyTitle = ""
        var fifteenMinutesOption = ""
        val selectedMinutes = mutableListOf<Int>()

        composeRule.setContent {
            PlazaTheme {
                frequencyTitle = stringResource(Res.string.notify_frequency)
                fifteenMinutesOption = stringResource(
                    Res.string.notify_frequency_every,
                    stringResource(Res.string.notify_frequency_15),
                )
                NotificationSettingsScreen(
                    settings = UserSettings(notificationsEnabled = true, notificationPollMinutes = 30),
                    onBack = {},
                    onEnabledChange = {},
                    onPollMinutesChange = { selectedMinutes += it },
                    onWifiOnlyChange = {},
                    onQuietHoursChange = {},
                    onNotifyInteractionsChange = {},
                    onNotifyMessagesChange = {},
                    onOpenTelegram = {},
                    backgroundPollingSupported = true,
                )
            }
        }

        composeRule.onNodeWithText(frequencyTitle).performClick()
        composeRule.onNodeWithText(fifteenMinutesOption).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(15), selectedMinutes)
        composeRule.onNodeWithText(fifteenMinutesOption).assertDoesNotExist()
    }
}
