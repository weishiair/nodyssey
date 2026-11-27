package io.github.nodyssey.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import io.github.nodyssey.model.TermsBlock
import io.github.nodyssey.model.TermsDocument
import io.github.nodyssey.ui.settings.PrivacyScreen
import io.github.nodyssey.ui.settings.PrivacyUiState
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 隐私与条款 in both themes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1000dp")
class PrivacyScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @Composable
    private fun Screen(
        darkTheme: Boolean,
        fontScale: Float = 1f,
        systemFontScale: Float = 1f,
    ) {
        val body: @Composable () -> Unit = {
            PlazaTheme(darkTheme = darkTheme, fontScale = fontScale) {
                PrivacyScreen(
                    state = STATE,
                    onBack = {},
                    onOpenOriginal = {},
                    onRetry = {},
                    onOpenWebFallback = {},
                )
            }
        }
        if (systemFontScale != 1f) {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = systemFontScale)) {
                body()
            }
        } else {
            body()
        }
    }

    @Test
    fun `privacy in light`() {
        composeRule.setContent { Screen(darkTheme = false) }

        composeRule.onRoot().captureRender("privacy-light")
    }

    @Test
    fun `privacy in dark`() {
        composeRule.setContent { Screen(darkTheme = true) }

        composeRule.onRoot().captureRender("privacy-dark")
    }

    @Test
    fun `privacy with large text in light`() {
        composeRule.setContent {
            Screen(
                darkTheme = false,
                fontScale = 1.5f,
                systemFontScale = 1.35f,
            )
        }

        composeRule.onRoot().captureRender("privacy-large-text-light")
    }

    private companion object {
        val STATE =
            PrivacyUiState.Content(
                document =
                TermsDocument(
                    title = "服务条款与隐私说明",
                    effectiveDate = "2026-01-01",
                    blocks =
                    listOf(
                        TermsBlock.Heading(level = 2, text = "一、账号与会话"),
                        TermsBlock.Paragraph("登录凭据仅保存在本机安全存储中，用于访问当前选定的社区站点。"),
                        TermsBlock.Heading(level = 2, text = "二、本地缓存"),
                        TermsBlock.ListBlock(
                            ordered = false,
                            items =
                            listOf(
                                "阅读历史与离线帖子仅存放在设备本地。",
                                "用户可随时在设置中清空缓存或注销登录。",
                            ),
                        ),
                    ),
                ),
            )
    }
}
