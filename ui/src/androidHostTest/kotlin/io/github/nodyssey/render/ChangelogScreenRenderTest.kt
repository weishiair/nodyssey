package io.github.nodyssey.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.nodyssey.ui.settings.ChangelogScreen
import io.github.nodyssey.ui.settings.ChangelogUiState
import io.github.plaza.core.update.ReleaseNote
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 更新日志 in both themes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1000dp")
class ChangelogScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @Composable
    private fun Screen(darkTheme: Boolean) {
        PlazaTheme(darkTheme = darkTheme) {
            ChangelogScreen(
                state = STATE,
                onBack = {},
                onRefresh = {},
                onRetry = {},
                onOpenReleases = {},
                onOpenRelease = {},
            )
        }
    }

    @Test
    fun `changelog in light`() {
        composeRule.setContent { Screen(darkTheme = false) }

        composeRule.onRoot().captureRender("changelog-light")
    }

    @Test
    fun `changelog in dark`() {
        composeRule.setContent { Screen(darkTheme = true) }

        composeRule.onRoot().captureRender("changelog-dark")
    }

    private companion object {
        val STATE =
            ChangelogUiState(
                currentVersionName = "1.3.12",
                releases =
                listOf(
                    ReleaseNote(
                        versionName = "1.3.12",
                        tag = "v1.3.12",
                        notes = "- 补齐通知与私信设置\n- 修复部分贴图加载失败",
                        publishedOn = "2026-11-18",
                        preRelease = false,
                        htmlUrl = "https://github.com/5151561/nodyssey/releases/tag/v1.3.12",
                    ),
                    ReleaseNote(
                        versionName = "1.3.11",
                        tag = "v1.3.11",
                        notes = "- 收藏页支持离线缓存与多选管理",
                        publishedOn = "2026-11-05",
                        preRelease = false,
                        htmlUrl = "https://github.com/5151561/nodyssey/releases/tag/v1.3.11",
                    ),
                ),
            )
    }
}
