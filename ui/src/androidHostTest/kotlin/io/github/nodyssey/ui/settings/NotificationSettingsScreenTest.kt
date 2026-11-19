package io.github.nodyssey.ui.settings

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.nodyssey.data.settings.UserSettings
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
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

    private var openedKeywords = 0
    private var pickedMinutes: Int? = null

    private fun show(settings: UserSettings) {
        composeRule.setContent {
            PlazaTheme {
                NotificationSettingsScreen(
                    settings = settings,
                    onBack = {},
                    onEnabledChange = {},
                    onPollMinutesChange = { pickedMinutes = it },
                    onWifiOnlyChange = {},
                    onQuietHoursChange = {},
                    onNotifyInteractionsChange = {},
                    onNotifyMessagesChange = {},
                    onOpenTelegram = {},
                    onOpenAlertKeywords = { openedKeywords++ },
                    backgroundPollingSupported = true,
                )
            }
        }
    }

    @Test
    fun `with the master switch off, polling rows go inert but the saved alerts stay manageable`() {
        show(UserSettings(notificationsEnabled = false))

        composeRule.onNodeWithText("检查频率").assertIsNotEnabled()
        composeRule.onNodeWithText("仅 Wi-Fi 下检查").assertIsNotEnabled()
        composeRule.onNodeWithText("提醒关键词").performScrollTo().assertIsEnabled().performClick()

        assertEquals(1, openedKeywords)
    }

    @Test
    fun `picking a frequency stores the minutes behind the label, not its position`() {
        show(UserSettings(notificationsEnabled = true, notificationPollMinutes = 30))

        composeRule.onNodeWithText("检查频率").performClick()
        composeRule.onNodeWithText("每 1 小时").performClick()

        assertEquals(60, pickedMinutes)
    }
}
