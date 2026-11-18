package io.github.nodyssey.render

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.nodyssey.data.dns.DefaultDohServers
import io.github.nodyssey.data.dns.DnsResolution
import io.github.nodyssey.data.dns.DohCapabilities
import io.github.nodyssey.data.dns.DohProvider
import io.github.nodyssey.data.dns.DohServer
import io.github.nodyssey.data.dns.DohServerProblem
import io.github.nodyssey.data.proxy.ProxyType
import io.github.nodyssey.ui.settings.DohServerDraft
import io.github.nodyssey.ui.settings.DohServerSheet
import io.github.nodyssey.ui.settings.DohSettingsContent
import io.github.nodyssey.ui.settings.DohSettingsScreen
import io.github.nodyssey.ui.settings.DohSettingsUiState
import io.github.nodyssey.ui.settings.ProxySettingsContent
import io.github.nodyssey.ui.settings.ProxySettingsScreen
import io.github.nodyssey.ui.settings.ProxySettingsUiState
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 代理 (6e), 加密 DNS (6f), and representative shared iOS chrome / sheet views.
 *
 * The `ios-shared-*` captures exercise `:ui`'s iOS grouping, quiet rank labels, and narrow field
 * stacking on Robolectric using Compose fallback switches/controls; they do not capture real UIKit
 * bars or controls, which still require Mac simulator runs.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1300dp")
class NetworkSettingsRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    private fun proxy(darkTheme: Boolean) {
        composeRule.setContent {
            PlazaTheme(darkTheme = darkTheme) {
                ProxySettingsScreen(
                    state =
                    ProxySettingsUiState(
                        enabled = true,
                        type = ProxyType.SOCKS,
                        hostInput = "127.0.0.1",
                        portInput = "7890",
                    ),
                    snackbarHostState = SnackbarHostState(),
                    onBack = {},
                    onEnabledChange = {},
                    onTypeChange = {},
                    onForumOnlyChange = {},
                    onHostChange = {},
                    onPortChange = {},
                    onUsernameChange = {},
                    onPasswordChange = {},
                    onSave = {},
                    onTest = {},
                )
            }
        }
    }

    private fun doh(darkTheme: Boolean) {
        composeRule.setContent {
            PlazaTheme(darkTheme = darkTheme) {
                DohSettingsScreen(
                    state =
                    DohSettingsUiState(
                        enabled = true,
                        resolution =
                        DnsResolution(
                            host = "www.nodeseek.com",
                            addresses = listOf("104.21.32.1", "172.67.140.1"),
                            elapsedMillis = 86,
                        ),
                    ),
                    snackbarHostState = SnackbarHostState(),
                    onBack = {},
                    onEnabledChange = {},
                    onToggleServer = {},
                    onMoveServer = { _, _ -> },
                    onOpenServer = {},
                    onAddServer = {},
                    onIncludeIPv6Change = {},
                    onFallbackChange = {},
                    onSave = {},
                    onTest = {},
                )
            }
        }
    }

    @Composable
    private fun SharedIosProxy(darkTheme: Boolean) {
        PlazaTheme(darkTheme = darkTheme) {
            ProxySettingsContent(
                state =
                ProxySettingsUiState(
                    enabled = true,
                    type = ProxyType.SOCKS,
                    hostInput = "192.0.2.10",
                    portInput = "1080",
                ),
                snackbarHostState = SnackbarHostState(),
                onBack = {},
                onEnabledChange = {},
                onTypeChange = {},
                onForumOnlyChange = {},
                onHostChange = {},
                onPortChange = {},
                onUsernameChange = {},
                onPasswordChange = {},
                onSave = {},
                onTest = {},
                nativeChrome = true,
            )
        }
    }

    @Test
    fun `proxy in light`() {
        proxy(darkTheme = false)
        composeRule.onRoot().captureRender("proxy-light")
    }

    @Test
    fun `proxy in dark`() {
        proxy(darkTheme = true)
        composeRule.onRoot().captureRender("proxy-dark")
    }

    @Test
    fun `encrypted DNS in light`() {
        doh(darkTheme = false)
        composeRule.onRoot().captureRender("doh-light")
    }

    @Test
    fun `encrypted DNS in dark`() {
        doh(darkTheme = true)
        composeRule.onRoot().captureRender("doh-dark")
    }

    @Test
    fun `ios shared proxy in light`() {
        composeRule.setContent { SharedIosProxy(darkTheme = false) }
        composeRule.onRoot().captureRender("ios-shared-proxy-light")
    }

    @Test
    @Config(qualifiers = "w260dp-h1300dp")
    fun `ios shared proxy narrow in light`() {
        composeRule.setContent { SharedIosProxy(darkTheme = false) }
        composeRule.onRoot().captureRender("ios-shared-proxy-narrow-light")
    }

    @Test
    fun `ios shared doh in dark`() {
        composeRule.setContent {
            PlazaTheme(darkTheme = true) {
                DohSettingsContent(
                    state =
                    DohSettingsUiState(
                        capabilities = DohCapabilities(
                            canChooseRecordTypes = true,
                            canFallBackToSystem = false,
                            triesServersInOrder = true,
                        ),
                        enabled = true,
                        servers = listOf(
                            DohServer.preset(DohProvider.WEISHIAIR, checked = true),
                            DohServer.preset(DohProvider.CLOUDFLARE, checked = true),
                            *DefaultDohServers.filterNot {
                                it.id == DohProvider.WEISHIAIR.name || it.id == DohProvider.CLOUDFLARE.name
                            }.toTypedArray(),
                        ),
                    ),
                    snackbarHostState = SnackbarHostState(),
                    onBack = {},
                    onEnabledChange = {},
                    onToggleServer = {},
                    onMoveServer = { _, _ -> },
                    onOpenServer = {},
                    onAddServer = {},
                    onIncludeIPv6Change = {},
                    onFallbackChange = {},
                    onSave = {},
                    onTest = {},
                    nativeChrome = true,
                )
            }
        }
        composeRule.onRoot().captureRender("ios-shared-doh-dark")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun `doh custom server sheet in light`() {
        composeRule.setContent {
            PlazaTheme(darkTheme = false) {
                DohServerSheet(
                    draft = DohServerDraft(
                        id = null,
                        preset = null,
                        urlInput = "http://doh.example.invalid/dns-query",
                        bootstrapInput = "192.0.2.53",
                        problem = DohServerProblem.INVALID_URL,
                    ),
                    server = null,
                    onUrlChange = {},
                    onBootstrapChange = {},
                    onResetBootstrap = {},
                    onDelete = {},
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(filePath = "build/outputs/renders/doh-sheet-add-light.png")
    }
}
