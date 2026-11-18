package io.github.nodyssey.ui.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import io.github.nodyssey.data.dns.DefaultDohServers
import io.github.nodyssey.data.dns.DnsResolution
import io.github.nodyssey.data.dns.DnsResolutionTester
import io.github.nodyssey.data.dns.DohCapabilities
import io.github.nodyssey.data.dns.DohConfig
import io.github.nodyssey.data.dns.DohProvider
import io.github.nodyssey.data.dns.DohServer
import io.github.nodyssey.data.dns.DohSettings
import io.github.nodyssey.data.proxy.ProxyConfig
import io.github.nodyssey.data.proxy.ProxyConnectionFailure
import io.github.nodyssey.data.proxy.ProxyConnectionTester
import io.github.nodyssey.data.proxy.ProxyScope
import io.github.nodyssey.data.proxy.ProxySettings
import io.github.nodyssey.data.proxy.ProxyType
import io.github.plaza.designsys.theme.PlazaTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1200dp")
class NetworkSettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Draft preservation + immediate master switch + C2.1 testing button contract through real Route:
     * invalid draft blocks persistence, testing disables only 测试连接 while keeping 保存 active,
     * and 测试连接 saves before probing.
     */
    @Test
    fun `proxy route preserves draft across immediate switch toggle and enforces save-before-test`() {
        val settings = FakeUiProxySettings()
        val tester = FakeUiProxyTester { settings.saved.value }
        val viewModel = ProxySettingsViewModel(settings, tester)

        composeRule.setContent {
            PlazaTheme {
                ProxySettingsRoute(viewModel = viewModel, onBack = {})
            }
        }
        composeRule.waitForIdle()

        // While disabled, notes remain present and Save/Test remain inert.
        composeRule.onNodeWithText("VLESS、VMess 这类节点").assertExists()
        composeRule.onNodeWithText("保存").assertIsNotEnabled()
        composeRule.onNodeWithText("测试连接").assertIsNotEnabled()

        // Turn master switch on, type host only, then turn switch off and back on — draft survives.
        composeRule.onNodeWithText("启用代理").performClick()
        composeRule.waitForIdle()
        assertTrue(settings.saved.value.enabled)

        composeRule.onNode(hasSetTextAction() and hasText("地址")).performTextInput("192.0.2.10")
        composeRule.onNodeWithText("启用代理").performClick()
        composeRule.waitForIdle()
        assertFalse(settings.saved.value.enabled)
        assertEquals("", settings.saved.value.host)

        composeRule.onNodeWithText("启用代理").performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction() and hasText("地址") and hasText("192.0.2.10")).assertExists()

        // Missing port blocks save cleanly.
        composeRule.onNodeWithText("保存").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("端口需要在 1–65535 之间").assertExists()
        assertEquals("", settings.saved.value.host)

        // Complete valid SOCKS5 + forum-only config and test: saves before testing, reports DNS failure.
        composeRule.onNodeWithText("SOCKS5").performClick()
        composeRule.onNode(hasSetTextAction() and hasText("端口")).performTextInput("1080")
        composeRule.onNodeWithText("只代理论坛").performScrollTo().performClick()
        tester.error = IllegalStateException("unreachable")
        composeRule.onNodeWithText("测试连接").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(
            ProxyConfig(
                enabled = true,
                type = ProxyType.SOCKS,
                host = "192.0.2.10",
                port = 1080,
                scope = ProxyScope.FORUM_ONLY,
            ),
            tester.configAtProbe.single(),
        )
        assertEquals(tester.configAtProbe.single(), settings.saved.value)
        composeRule.onNodeWithText("连接失败（IllegalStateException）").assertExists()
    }

    /**
     * C2.1 contract: while a connection test is in flight, only 测试连接 becomes disabled —
     * 保存 remains enabled.
     */
    @Test
    fun `proxy testing flag disables only 测试连接 and leaves 保存 enabled`() {
        composeRule.setContent {
            PlazaTheme {
                ProxySettingsContent(
                    state = ProxySettingsUiState(
                        enabled = true,
                        hostInput = "192.0.2.10",
                        portInput = "1080",
                        testing = true,
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
        composeRule.onNode(hasText("测试连接") and hasClickAction()).assertIsNotEnabled()
        composeRule.onNode(hasText("保存") and hasClickAction()).assertIsEnabled()
    }

    /**
     * C2.2: current iOS capabilities show IPv6, omit system fallback, show encrypted-only hint
     * even while DoH is off, and separate Checkbox/Radio selection from row details and move actions.
     */
    @Test
    fun `doh ios capabilities keep ipv6, hide system fallback, and separate toggle from row open and move`() {
        val toggled = mutableListOf<String>()
        val opened = mutableListOf<String>()
        val moved = mutableListOf<Pair<String, Int>>()
        val iosCaps = DohCapabilities(canChooseRecordTypes = true, canFallBackToSystem = false, triesServersInOrder = true)
        val servers =
            listOf(
                DohServer.preset(DohProvider.WEISHIAIR, checked = true),
                DohServer.preset(DohProvider.CLOUDFLARE, checked = true),
                DohServer.preset(DohProvider.ALIDNS),
            )

        composeRule.setContent {
            PlazaTheme {
                DohSettingsContent(
                    state = DohSettingsUiState(
                        capabilities = iosCaps,
                        enabled = true,
                        servers = servers,
                    ),
                    snackbarHostState = SnackbarHostState(),
                    onBack = {},
                    onEnabledChange = {},
                    onToggleServer = { toggled += it },
                    onMoveServer = { id, to -> moved += id to to },
                    onOpenServer = { opened += it },
                    onAddServer = {},
                    onIncludeIPv6Change = {},
                    onFallbackChange = {},
                    onSave = {},
                    onTest = {},
                    nativeChrome = true,
                )
            }
        }

        composeRule.onNodeWithText("同时查 IPv6 地址").assertExists()
        composeRule.onNodeWithText("解析失败时回退系统 DNS").assertDoesNotExist()
        composeRule.onNodeWithText("这台设备上，开着的时候解析不出来就是解析不出来，不会退回明文 DNS；系统里已经配了加密 DNS 的话，以系统那份为准。", substring = true)
            .performScrollTo()
            .assertExists()

        // Tapping Cloudflare checkbox toggles only; tapping row opens details only.
        composeRule.onNode(hasContentDescription("Cloudflare")).performClick()
        assertEquals(listOf("CLOUDFLARE"), toggled)
        assertTrue(opened.isEmpty())

        composeRule.onNode(hasText("Cloudflare")).performClick()
        assertEquals(listOf("CLOUDFLARE"), opened)
        assertEquals(1, toggled.size)

        // Custom a11y action moves rank 1 (Cloudflare) up to index 0 without firing row click/toggle.
        val rowActions =
            composeRule.onNode(hasText("Cloudflare")).fetchSemanticsNode()
                .config.getOrNull(SemanticsActions.CustomActions)
                .orEmpty()
        val moveUp = rowActions.firstOrNull { it.label == "上移" }
        assertTrue("Expected 上移 accessibility action on ranked backup server", moveUp != null)
        moveUp!!.action()
        assertEquals(listOf("CLOUDFLARE" to 0), moved)

        // Unticked ALIDNS has neither rank nor move actions.
        val alidnsActions =
            composeRule.onNode(hasText("阿里 DNS")).fetchSemanticsNode()
                .config.getOrNull(SemanticsActions.CustomActions)
                .orEmpty()
        assertTrue(alidnsActions.isEmpty())
    }

    @Test
    fun `doh single-server capabilities use radio buttons and paused state disables all actions`() {
        val singleServerCaps = DohCapabilities(canChooseRecordTypes = false, canFallBackToSystem = false, triesServersInOrder = false)

        composeRule.setContent {
            PlazaTheme {
                DohSettingsContent(
                    state = DohSettingsUiState(
                        capabilities = singleServerCaps,
                        enabled = true,
                        pausedForProxy = true,
                        servers = listOf(
                            DohServer.preset(DohProvider.WEISHIAIR, checked = true),
                            DohServer.preset(DohProvider.GOOGLE),
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

        composeRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertCountEquals(2)
        composeRule.onNodeWithText("首选").assertDoesNotExist()
        composeRule.onNodeWithText("加密 DNS 因代理暂停；关闭代理后恢复原设置。").assertExists()
        composeRule.onNodeWithText("启用加密 DNS").assertIsNotEnabled()
        composeRule.onNodeWithText("添加自定义 DoH 地址").assertIsNotEnabled()
        composeRule.onNodeWithText("测试解析").assertIsNotEnabled()
        composeRule.onNodeWithText("保存").assertIsNotEnabled()
    }

    /**
     * C2.3: sheet validation keeps sheet open on invalid URL, 取消 discards draft, 确定 writes only to
     * page draft until 保存, and presets obey `supportsBootstrapOverride`.
     */
    @Test
    fun `doh route preserves two-layer draft and respects preset bootstrap override capability`() {
        val settings = FakeUiDohSettings(DohConfig(enabled = false))
        val tester = FakeUiDnsTester()
        val iosCaps = DohCapabilities(canChooseRecordTypes = true, canFallBackToSystem = false, triesServersInOrder = true)
        val viewModel = DohSettingsViewModel(settings, tester, iosCaps)

        composeRule.setContent {
            PlazaTheme {
                DohSettingsRoute(viewModel = viewModel, onBack = {})
            }
        }
        composeRule.waitForIdle()

        // Enable master switch first so server actions are enabled.
        composeRule.onNodeWithText("启用加密 DNS").performClick()
        composeRule.waitForIdle()
        assertTrue(viewModel.uiState.value.enabled)

        // Open WEISHIAIR preset: bootstrap field & reset must NOT exist.
        composeRule.onNode(hasText("Weishiair DoH（ECH）")).performClick()
        composeRule.waitUntil(3_000) { viewModel.uiState.value.editing != null }
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction() and hasText("引导 IP（可选）")).assertDoesNotExist()
        composeRule.onNodeWithText("恢复默认").assertDoesNotExist()
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.Dismiss)
        composeRule.waitUntil(3_000) { viewModel.uiState.value.editing == null }

        // Add custom server: invalid non-HTTPS URL rejected inline; Cancel discards.
        composeRule.onNodeWithText("添加自定义 DoH 地址").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction() and hasText("服务器地址")).performTextInput("http://doh.example.invalid/dns-query")
        composeRule.onNodeWithText("确定").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("需要是 https:// 开头的完整地址").assertExists()

        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.Dismiss)
        composeRule.waitUntil(3_000) { viewModel.uiState.value.editing == null }
        composeRule.onNodeWithText("doh.example.invalid").assertDoesNotExist()

        // Add valid custom server -> Confirm updates page draft only; Save commits to storage.
        composeRule.onNodeWithText("添加自定义 DoH 地址").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction() and hasText("服务器地址")).performTextReplacement("https://doh.example.invalid/dns-query")
        composeRule.onNodeWithText("确定").performClick()
        composeRule.waitUntil(3_000) { viewModel.uiState.value.editing == null }

        // Shows in list on screen, but NOT yet in global settings!
        composeRule.onNode(hasText("doh.example.invalid") and hasText("doh.example.invalid/dns-query")).assertExists()
        assertEquals(DefaultDohServers.size, settings.saved.value.servers.size)

        composeRule.onNodeWithText("保存").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(DefaultDohServers.size + 1, settings.saved.value.servers.size)
        assertTrue(settings.saved.value.servers.any { it.url == "https://doh.example.invalid/dns-query" && it.checked })
    }

    private fun onAllNodes(matcher: SemanticsMatcher) = composeRule.onAllNodes(matcher)
}

private class FakeUiProxySettings : ProxySettings {
    val saved = MutableStateFlow(ProxyConfig())
    override val config: Flow<ProxyConfig> = saved
    override suspend fun save(config: ProxyConfig) {
        saved.value = config
    }

    override suspend fun setEnabled(enabled: Boolean) {
        saved.value = saved.value.copy(enabled = enabled)
    }
}

private class FakeUiProxyTester(private val config: () -> ProxyConfig) : ProxyConnectionTester {
    val configAtProbe = mutableListOf<ProxyConfig>()
    var error: Throwable? = null

    override suspend fun test(): Result<Unit> {
        configAtProbe += config()
        return error?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    override fun classify(failure: Throwable): ProxyConnectionFailure =
        ProxyConnectionFailure(ProxyConnectionFailure.Kind.OTHER, failure::class.simpleName.orEmpty())
}

private class FakeUiDohSettings(initial: DohConfig = DohConfig()) : DohSettings {
    val saved = MutableStateFlow(initial)
    override val config: Flow<DohConfig> = saved
    override suspend fun save(config: DohConfig) {
        saved.value = config
    }

    override suspend fun setEnabled(enabled: Boolean) {
        saved.value = saved.value.copy(enabled = enabled)
    }
}

private class FakeUiDnsTester : DnsResolutionTester {
    override suspend fun resolve(): Result<DnsResolution> =
        Result.success(DnsResolution("www.nodeseek.com", listOf("192.0.2.1"), 32))
}
