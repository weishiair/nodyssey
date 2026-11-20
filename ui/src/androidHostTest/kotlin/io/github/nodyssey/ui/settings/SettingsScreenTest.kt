package io.github.nodyssey.ui.settings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import io.github.nodyssey.data.attendance.AutoAttendanceConfig
import io.github.nodyssey.data.settings.ReadMarkStyle
import io.github.nodyssey.data.settings.ReportFormat
import io.github.nodyssey.data.settings.UserSettings
import io.github.plaza.designsys.theme.PlazaTheme
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
@Config(qualifiers = "w360dp-h800dp")
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `font size label follows drag state without applying it`() {
        var appliedScale: Float? = null
        composeRule.setContent {
            PlazaTheme {
                SettingsScreen(
                    state = SettingsUiState(UserSettings(fontScale = 1f)),
                    onBack = {},
                    onOpenTheme = {},
                    onThemeModeChange = {},
                    onReadMarkStyleChange = {},
                    onReadMarkColorChange = {},
                    onOneHandModeChange = {},
                    onFontScaleChange = { appliedScale = it },
                    onStickerUniformSizeChange = {},
                    onStickerSizeChange = {},
                    onImagesOnWifiOnlyChange = {},
                    onReportFormatChange = {},
                    onHomePageBarChange = {},
                    onUpdateCheckOnLaunchChange = {},
                    onUpdateDevChannelChange = {},
                    onClearCache = {},
                    appLinkHandlingEnabled = null,
                    onOpenAppLinkSettings = {},
                )
            }
        }

        // 阅读字号 is no longer in the first viewport: 已读标记 and 标记颜色 were added above it, so the
        // row has to be brought into view before a drag can land on it.
        val slider = composeRule.onNodeWithTag(BODY_FONT_SIZE_SLIDER_TAG)
        slider.performScrollTo()
        composeRule.onNodeWithText("16", useUnmergedTree = true).assertTextEquals("16")
        slider.performTouchInput {
            down(percentOffset(0.2f, 0.5f))
            moveTo(center, delayMillis = 100)
        }

        composeRule.onNodeWithText("16", useUnmergedTree = true).assertDoesNotExist()
        assertTrue(appliedScale == null)

        slider.performTouchInput { up() }
    }

    @Test
    fun `font size applies only after the drag is released`() {
        var appliedScale: Float? = null
        composeRule.setContent {
            PlazaTheme {
                SettingsScreen(
                    state = SettingsUiState(UserSettings(fontScale = 1f)),
                    onBack = {},
                    onOpenTheme = {},
                    onThemeModeChange = {},
                    onReadMarkStyleChange = {},
                    onReadMarkColorChange = {},
                    onOneHandModeChange = {},
                    onFontScaleChange = { appliedScale = it },
                    onStickerUniformSizeChange = {},
                    onStickerSizeChange = {},
                    onImagesOnWifiOnlyChange = {},
                    onReportFormatChange = {},
                    onHomePageBarChange = {},
                    onUpdateCheckOnLaunchChange = {},
                    onUpdateDevChannelChange = {},
                    onClearCache = {},
                    appLinkHandlingEnabled = null,
                    onOpenAppLinkSettings = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(BODY_FONT_SIZE_SLIDER_TAG)
            .performScrollTo()
            .performTouchInput { swipe(start = center, end = centerRight) }

        composeRule.waitForIdle()
        assertTrue(appliedScale != null)
    }

    @Test
    fun `e ink mode disables light and dark choices`() {
        showSettings(state = { SettingsUiState(UserSettings(einkMode = true)) })

        composeRule.onNodeWithText("浅色").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("深色").assertIsNotEnabled()
    }

    @Test
    fun `disabling uniform stickers hides their size slider`() {
        val state = mutableStateOf(SettingsUiState(UserSettings(stickerUniformSize = false)))
        showSettings(state = { state.value })
        composeRule.onNodeWithTag(STICKER_SIZE_SLIDER_TAG).assertDoesNotExist()

        composeRule.runOnIdle {
            state.value = state.value.copy(settings = state.value.settings.copy(stickerUniformSize = true))
        }
        composeRule.onNodeWithTag(STICKER_SIZE_SLIDER_TAG).performScrollTo().assertExists()
    }

    @Test
    fun `the read mark menu sends the chosen style and shows colors only when it paints a block`() {
        val state = mutableStateOf(SettingsUiState(UserSettings(readMarkStyle = ReadMarkStyle.DIM)))
        showSettings(
            state = { state.value },
            onReadMarkStyleChange = { style ->
                state.value = state.value.copy(settings = state.value.settings.copy(readMarkStyle = style))
            },
        )
        composeRule.onNodeWithText("标记颜色").assertDoesNotExist()

        composeRule.onNodeWithText("已读标记").performScrollTo().performClick()
        composeRule.onNodeWithText("整块染色（标已读）").performClick()

        composeRule.runOnIdle { assertEquals(ReadMarkStyle.BLOCK, state.value.settings.readMarkStyle) }
        composeRule.onNodeWithText("标记颜色").performScrollTo().assertExists()
    }

    @Test
    fun `the report format segment sends the report format enum`() {
        var selected: ReportFormat? = null
        showSettings(
            state = { SettingsUiState(UserSettings(reportFormat = ReportFormat.SOURCE)) },
            onReportFormatChange = { selected = it },
        )

        composeRule.onNodeWithText("数据卡片").performScrollTo().performClick()

        composeRule.runOnIdle { assertEquals(ReportFormat.ADAPTED, selected) }
    }

    @Test
    fun `clearing the cache cannot be requested again until the current clear finishes`() {
        val state = mutableStateOf(SettingsUiState(isClearingCache = true))
        var requests = 0
        showSettings(state = { state.value }, onClearCache = { requests++ })
        val clear = composeRule.onNodeWithText("清除缓存")

        clear.performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(0, requests)
            state.value = state.value.copy(isClearingCache = false)
        }
        clear.performClick()
        composeRule.runOnIdle { assertEquals(1, requests) }
    }

    @Test
    fun `attendance status is readable without becoming a clickable action`() {
        showSettings(
            state = { SettingsUiState() },
            attendanceConfig = AutoAttendanceConfig(enabled = true),
        )

        composeRule.onNodeWithText("签到状态").performScrollTo().assertHasNoClickAction()
    }

    private fun showSettings(
        state: () -> SettingsUiState,
        attendanceConfig: AutoAttendanceConfig = AutoAttendanceConfig(),
        onReadMarkStyleChange: (ReadMarkStyle) -> Unit = {},
        onReportFormatChange: (ReportFormat) -> Unit = {},
        onClearCache: () -> Unit = {},
    ) {
        composeRule.setContent {
            PlazaTheme {
                SettingsScreen(
                    state = state(),
                    appLinkHandlingEnabled = null,
                    attendanceConfig = attendanceConfig,
                    onOpenAppLinkSettings = {},
                    onBack = {},
                    onOpenTheme = {},
                    onThemeModeChange = {},
                    onReadMarkStyleChange = onReadMarkStyleChange,
                    onReadMarkColorChange = {},
                    onOneHandModeChange = {},
                    onFontScaleChange = {},
                    onStickerUniformSizeChange = {},
                    onStickerSizeChange = {},
                    onImagesOnWifiOnlyChange = {},
                    onReportFormatChange = onReportFormatChange,
                    onHomePageBarChange = {},
                    onUpdateCheckOnLaunchChange = {},
                    onUpdateDevChannelChange = {},
                    onClearCache = onClearCache,
                )
            }
        }
    }
}
