package io.github.nodyssey.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.nodyssey.data.dns.DefaultDohServers
import io.github.nodyssey.data.imagehost.ImageHostProvider
import io.github.nodyssey.data.settings.UserSettings
import io.github.nodyssey.ui.settings.SettingsScreen
import io.github.nodyssey.ui.settings.SettingsUiState
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 设置 in both themes — the top of the settings list. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class SettingsScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @Composable
    private fun Screen(darkTheme: Boolean, pureWhite: Boolean = false) {
        PlazaTheme(darkTheme = darkTheme, pureWhite = pureWhite) {
            SettingsScreen(
                state = STATE.copy(settings = STATE.settings.copy(presetId = if (pureWhite) "pure_white" else "graphite")),
                appLinkHandlingEnabled = true,
                onOpenAppLinkSettings = {},
                onBack = {},
                onOpenTheme = {},
                onThemeModeChange = {},
                onOneHandModeChange = {},
                onFontScaleChange = {},
                onStickerUniformSizeChange = {},
                onStickerSizeChange = {},
                onImagesOnWifiOnlyChange = {},
                onReportFormatChange = {},
                onHomePageBarChange = {},
                onUpdateCheckOnLaunchChange = {},
                onUpdateDevChannelChange = {},
                onClearCache = {},
                supportedRefreshRates = setOf(60, 90, 120),
            )
        }
    }

    @Test
    fun `the settings list in light`() {
        composeRule.setContent { Screen(darkTheme = false) }

        composeRule.onRoot().captureRender("settings-light")
    }

    @Test
    fun `the settings list in dark`() {
        composeRule.setContent { Screen(darkTheme = true) }

        composeRule.onRoot().captureRender("settings-dark")
    }

    @Test
    fun `the settings list in pure white`() {
        composeRule.setContent { Screen(darkTheme = false, pureWhite = true) }

        composeRule.onRoot().captureRender("settings-pure-white")
    }

    @Test
    fun `the settings list in neutral dark`() {
        composeRule.setContent { Screen(darkTheme = true, pureWhite = true) }

        composeRule.onRoot().captureRender("settings-neutral-dark")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun `the search history menu in neutral dark`() {
        composeRule.setContent { Screen(darkTheme = true, pureWhite = true) }
        composeRule.onNodeWithText("图床").performScrollTo()
        composeRule.onNodeWithText("搜索历史保留数量").performClick()
        composeRule.waitForIdle()

        captureScreenRoboImage(filePath = "build/outputs/renders/settings-search-history-menu-neutral-dark.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun `the search history menu in pure white`() {
        composeRule.setContent { Screen(darkTheme = false, pureWhite = true) }
        composeRule.onNodeWithText("图床").performScrollTo()
        composeRule.onNodeWithText("搜索历史保留数量").performClick()
        composeRule.waitForIdle()

        captureScreenRoboImage(filePath = "build/outputs/renders/settings-search-history-menu-pure-white.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun `the refresh rate menu in neutral dark`() {
        composeRule.setContent { Screen(darkTheme = true, pureWhite = true) }
        composeRule.onNodeWithText("屏幕刷新率").performScrollTo().performClick()
        composeRule.waitForIdle()

        captureScreenRoboImage(filePath = "build/outputs/renders/settings-refresh-rate-menu-neutral-dark.png")
    }

    /** The whole list at once, so every group can be compared with 6a / 6b without scrolling. */
    @Test
    @Config(qualifiers = "w360dp-h2000dp")
    fun `the whole settings list in light`() {
        composeRule.setContent { Screen(darkTheme = false) }

        composeRule.onRoot().captureRender("settings-full-light")
    }

    @Test
    @Config(qualifiers = "w360dp-h2000dp")
    fun `the whole settings list in dark`() {
        composeRule.setContent { Screen(darkTheme = true) }

        composeRule.onRoot().captureRender("settings-full-dark")
    }

    private companion object {
        val STATE =
            SettingsUiState(
                settings = UserSettings(),
                cacheSizeBytes = 34_500_000,
                versionName = "1.2.21",
                imageHostProvider = ImageHostProvider.SMMS,
                imageHostConnected = true,
                dohChain = DefaultDohServers.filter { it.checked },
                hasNetworkCheck = true,
            )
    }
}
