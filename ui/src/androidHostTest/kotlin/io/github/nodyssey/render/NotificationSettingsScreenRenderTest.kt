package io.github.nodyssey.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.nodyssey.data.TrackedThread
import io.github.nodyssey.data.TrackedThreadStore
import io.github.nodyssey.data.settings.UserSettings
import io.github.nodyssey.model.TitleKeywordKind
import io.github.nodyssey.ui.settings.NotificationSettingsScreen
import io.github.nodyssey.ui.settings.TitleKeywordsScreen
import io.github.nodyssey.ui.settings.TitleKeywordsUiState
import io.github.nodyssey.ui.settings.TrackedThreadsScreen
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 通知提醒 in both themes, tall enough to show the Telegram card at the bottom (6d). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1100dp")
class NotificationSettingsScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @Composable
    private fun Screen(darkTheme: Boolean, backgroundPollingSupported: Boolean = true) {
        PlazaTheme(darkTheme = darkTheme) {
            NotificationSettingsScreen(
                settings = UserSettings(notificationsEnabled = true, notificationQuietHours = true),
                onBack = {},
                onEnabledChange = {},
                onPollMinutesChange = {},
                onWifiOnlyChange = {},
                onQuietHoursChange = {},
                onNotifyInteractionsChange = {},
                onNotifyMessagesChange = {},
                onOpenTelegram = {},
                onOpenLottery = {},
                backgroundPollingSupported = backgroundPollingSupported,
            )
        }
    }

    @Test
    fun `notification settings in light`() {
        composeRule.setContent { Screen(darkTheme = false) }

        composeRule.onRoot().captureRender("notification-settings-light")
    }

    @Test
    fun `notification settings in dark`() {
        composeRule.setContent { Screen(darkTheme = true) }

        composeRule.onRoot().captureRender("notification-settings-dark")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun `available iOS reminders in light`() {
        composeRule.setContent { Screen(darkTheme = false, backgroundPollingSupported = false) }
        composeRule.onRoot().captureRender("ios-notification-settings-light")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun `available iOS reminders in dark`() {
        composeRule.setContent { Screen(darkTheme = true, backgroundPollingSupported = false) }
        composeRule.onRoot().captureRender("ios-notification-settings-dark")
    }

    /** 检查频率's sheet (6d2), a window of its own — hence the screen capture. */
    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun `the frequency sheet in light`() {
        composeRule.setContent { Screen(darkTheme = false) }
        composeRule.onNodeWithText("检查频率").performClick()
        composeRule.waitForIdle()

        captureScreenRoboImage(filePath = "build/outputs/renders/notification-frequency-light.png")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun `alert keywords populated without background polling in light`() {
        composeRule.setContent {
            PlazaTheme(darkTheme = false) {
                TitleKeywordsScreen(
                    state = TitleKeywordsUiState(
                        kind = TitleKeywordKind.ALERT,
                        keywords = listOf("racknerd", "香港优化线路优惠", "rn"),
                        input = "dmit",
                    ),
                    onBack = {},
                    onInputChange = {},
                    onAdd = {},
                    onRemove = {},
                    backgroundPollingSupported = false,
                )
            }
        }
        composeRule.onRoot().captureRender("title-keywords-alert-light")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun `alert keywords empty without background polling in dark`() {
        composeRule.setContent {
            PlazaTheme(darkTheme = true) {
                TitleKeywordsScreen(
                    state = TitleKeywordsUiState(kind = TitleKeywordKind.ALERT),
                    onBack = {},
                    onInputChange = {},
                    onAdd = {},
                    onRemove = {},
                    backgroundPollingSupported = false,
                )
            }
        }
        composeRule.onRoot().captureRender("title-keywords-empty-dark")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun `tracked threads populated in light`() {
        composeRule.setContent {
            PlazaTheme(darkTheme = false) {
                TrackedThreadsScreen(
                    threads = listOf(
                        TrackedThread(
                            postId = 1L,
                            title = "NodeSeek 夜间线路测评与延迟对比汇总",
                            lastKnownCount = 42,
                            authorName = "tester",
                            categoryTitle = "测评",
                            categorySlug = "review",
                        ),
                        TrackedThread(
                            postId = 2L,
                            title = "刚追踪的新帖子（回复数未知）",
                            lastKnownCount = TrackedThreadStore.UNKNOWN_COUNT,
                            authorName = "alice",
                            categoryTitle = "技术",
                            categorySlug = "tech",
                        ),
                    ),
                    onBack = {},
                    onOpenThread = {},
                    onUntrack = {},
                )
            }
        }
        composeRule.onRoot().captureRender("tracked-threads-light")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun `tracked threads empty in dark`() {
        composeRule.setContent {
            PlazaTheme(darkTheme = true) {
                TrackedThreadsScreen(
                    threads = emptyList(),
                    onBack = {},
                    onOpenThread = {},
                    onUntrack = {},
                )
            }
        }
        composeRule.onRoot().captureRender("tracked-threads-empty-dark")
    }
}
