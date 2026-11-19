package io.github.nodyssey.ui.account

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import io.github.nodyssey.data.imagehost.CustomHostFields
import io.github.nodyssey.data.imagehost.HostedImage
import io.github.nodyssey.data.imagehost.ImageHostConfig
import io.github.nodyssey.data.imagehost.ImageHostError
import io.github.nodyssey.data.imagehost.ImageHostException
import io.github.nodyssey.data.imagehost.ImageHostProvider
import io.github.nodyssey.data.imagehost.ImageHostRepository
import io.github.nodyssey.data.imagehost.ImageHostUpload
import io.github.plaza.designsys.theme.PlazaTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1400dp")
class ImageHostScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    /**
     * C3.1: `nativeChrome = true` folds the 6 hosts behind SettingsMenuRow (cancelable without
     * mutating selection), while `nativeChrome = false` keeps the 6 side-by-side FilterChips on
     * Android/JVM.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `nativeChrome uses dropdown menu provider selector while non-native keeps inline chips`() {
        var selected by mutableStateOf(ImageHostProvider.NODE_IMAGE)
        var nativeChrome by mutableStateOf(true)

        composeRule.setContent {
            PlazaTheme {
                ImageHostContent(
                    state = ImageHostUiState(isLoading = false, provider = selected),
                    snackbarHostState = SnackbarHostState(),
                    onBack = {},
                    onSelectProvider = { selected = it },
                    onSiteUrlChange = {},
                    onTokenChange = {},
                    onCustomChange = {},
                    onToggleCustomFields = {},
                    onSave = {},
                    onRequestDisconnect = {},
                    onDismissDisconnect = {},
                    onConfirmDisconnect = {},
                    onRefresh = {},
                    onRequestDelete = {},
                    onDismissDelete = {},
                    onConfirmDelete = {},
                    onOpenSite = {},
                    onOpenImage = {},
                    nativeChrome = nativeChrome,
                )
            }
        }

        // At rest, nativeChrome shows the grouped selector value, not inline FilterChip copies.
        composeRule.onNodeWithText("图床服务").assertExists()
        composeRule.onNodeWithText("NodeImage").assertExists()
        composeRule.onAllNodesWithText("兰空图床 Lsky Pro · 自建").assertCountEquals(0)
        composeRule.onAllNodesWithText("自定义图床 · 手动配置").assertCountEquals(0)

        // Opening the menu lists all 6 providers; Escape dismisses without changing selection.
        composeRule.onNodeWithText("图床服务").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("NodeImage · 论坛常用").assertExists()
        composeRule.onNodeWithText("兰空图床 Lsky Pro · 自建").assertExists()
        composeRule.onNodeWithText("简单图床 EasyImage · 自建").assertExists()
        composeRule.onNodeWithText("SM.MS · 公共").assertExists()
        composeRule.onNodeWithText("imgbb · 公共").assertExists()
        composeRule.onNodeWithText("自定义图床 · 手动配置").assertExists()

        composeRule.onNodeWithText("兰空图床 Lsky Pro · 自建").performKeyInput {
            pressKey(Key.Escape)
        }
        composeRule.waitForIdle()
        assertEquals(ImageHostProvider.NODE_IMAGE, selected)

        // Selecting Lsky switches trailing short name, section note, and site URL field.
        composeRule.onNodeWithText("图床服务").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("兰空图床 Lsky Pro · 自建").performClick()
        composeRule.waitForIdle()
        assertEquals(ImageHostProvider.LSKY_PRO, selected)
        composeRule.onNodeWithText("兰空图床").assertExists()
        composeRule.onNodeWithText("填自己搭的站点地址，令牌在兰空后台「设置 › 接口」生成").assertExists()
        composeRule.onNode(hasSetTextAction() and hasText("站点地址")).assertExists()

        // Android/JVM fallback still shows all 6 FilterChips directly on the card.
        nativeChrome = false
        composeRule.waitForIdle()
        composeRule.onNodeWithText("NodeImage · 论坛常用").assertExists()
        composeRule.onNodeWithText("自定义图床 · 手动配置").assertExists()
    }

    /**
     * C3.1 / C3.2: Top-bar refresh action exists only when connected AND browsable, save failure
     * from gallery listing keeps `connected = true`, and non-browsable hosts surface unsupported
     * recovery pointing to `siteUrlFor`.
     */
    @Test
    fun `top bar refresh and gallery state preserve connection when images fail or host is upload-only`() {
        val repo = FakeUiImageHostRepository()
        val viewModel = ImageHostViewModel(repo)
        val openedUrls = mutableListOf<String>()

        composeRule.setContent {
            PlazaTheme {
                ImageHostRoute(
                    viewModel = viewModel,
                    onBack = {},
                    onOpenUrl = { openedUrls += it },
                    onOpenImages = { _, _ -> },
                )
            }
        }
        composeRule.waitForIdle()

        // Unconnected NodeImage: no refresh icon in top bar.
        composeRule.onAllNodes(hasContentDescription("刷新")).assertCountEquals(0)

        // Connected SMMS whose images() initially fails: stays connected, shows credential mask,
        // keeps refresh visible, and recovers when refresh runs.
        viewModel.selectProvider(ImageHostProvider.SMMS)
        composeRule.waitForIdle()
        repo.imagesError = ImageHostException(ImageHostError.Http(502))
        composeRule.onNode(hasSetTextAction() and hasText("API Token")).performTextInput("smms-secret-123456")
        composeRule.onNodeWithText("保存并连接").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("已连接").assertExists()
        composeRule.onNodeWithText("smms……3456").assertExists()
        composeRule.onAllNodesWithText("smms-secret-123456").assertCountEquals(0)
        composeRule.onNodeWithText("图床暂时无法访问，请稍后重试").assertExists()
        composeRule.onAllNodes(hasContentDescription("刷新")).assertCountEquals(1)

        // Fix backend error and click header refresh -> loads images.
        repo.imagesError = null
        repo.storedImages[ImageHostProvider.SMMS] =
            mutableListOf(
                HostedImage(id = "s1", fileName = "first.png", url = "https://sm.ms/1.png", sizeBytes = 2048L),
            )
        composeRule.onNode(hasContentDescription("刷新")).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("共 1 张 · 2.0 KB").assertExists()

        // Connected IMGBB (browsable = false): hides refresh button even while connected, shows
        // unsupported banner, and opens https://imgbb.com from the recovery button.
        viewModel.selectProvider(ImageHostProvider.IMGBB)
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction() and hasText("API Key")).performTextInput("imgbb-secret-9999")
        composeRule.onNodeWithText("保存并连接").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("已连接").assertExists()
        composeRule.onAllNodes(hasContentDescription("刷新")).assertCountEquals(0)
        composeRule.onNodeWithText("这个图床只提供上传接口，图片列表和删除要到它自己的网页上操作").performScrollTo().assertExists()
        composeRule.onAllNodesWithText("打开图床网站").get(1).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("https://imgbb.com"), openedUrls)
    }

    /**
     * C3.2: Per-provider configs remain isolated, token never echoes back after switching back,
     * blank token on edit preserves the stored secret, keystore refusal keeps disconnected with
     * clear error message, and CUSTOM exposes 6 clear-text fields & keyless validity.
     */
    @Test
    fun `provider isolation non-echo keystore refusal and custom clear-text keyless contract`() {
        val repo = FakeUiImageHostRepository()
        val viewModel = ImageHostViewModel(repo)

        composeRule.setContent {
            PlazaTheme {
                ImageHostRoute(
                    viewModel = viewModel,
                    onBack = {},
                    onOpenUrl = {},
                    onOpenImages = { _, _ -> },
                )
            }
        }
        composeRule.waitForIdle()

        // Configure NODE_IMAGE with plausible key, switch to LSKY_PRO, configure it, switch back.
        composeRule.onNode(hasSetTextAction() and hasText("API Key")).performTextInput(VALID_NODE_IMAGE_KEY)
        composeRule.onNodeWithText("保存并连接").performClick()
        composeRule.waitForIdle()

        viewModel.selectProvider(ImageHostProvider.LSKY_PRO)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("未连接").assertExists()
        composeRule.onNode(hasSetTextAction() and hasText("站点地址")).performTextInput("https://lsky.one/")
        composeRule.onNode(hasSetTextAction() and hasText("令牌")).performTextInput("lsky-abcd-1234")
        composeRule.onNodeWithText("保存并连接").performClick()
        composeRule.waitForIdle()
        assertEquals("lsky……1234", viewModel.uiState.value.credentialMask)

        // Switching to SMMS then back to LSKY_PRO keeps saved URL without echoing token.
        viewModel.selectProvider(ImageHostProvider.SMMS)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("未连接").assertExists()

        viewModel.selectProvider(ImageHostProvider.LSKY_PRO)
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction() and hasText("站点地址") and hasText("https://lsky.one")).assertExists()
        composeRule.onAllNodesWithText("lsky-abcd-1234").assertCountEquals(0)
        assertEquals("", viewModel.uiState.value.tokenInput)

        // Editing URL with blank token preserves the previously stored token.
        composeRule.onNode(hasSetTextAction() and hasText("站点地址")).performTextReplacement("https://lsky.two")
        composeRule.onNodeWithText("保存").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("https://lsky.two", repo.savedConfig(ImageHostProvider.LSKY_PRO).siteUrl)
        assertEquals("lsky-abcd-1234", repo.savedConfig(ImageHostProvider.LSKY_PRO).token)

        // Keystore refusal drops the secret on save -> must stay disconnected & show error notice.
        viewModel.selectProvider(ImageHostProvider.EASY_IMAGE)
        composeRule.waitForIdle()
        repo.refuseSecretStorage = true
        composeRule.onNode(hasSetTextAction() and hasText("站点地址")).performTextInput("https://easy.example.com")
        composeRule.onNode(hasSetTextAction() and hasText("令牌")).performTextInput("easy-secret-0001")
        composeRule.onNodeWithText("保存并连接").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("未连接").assertExists()
        composeRule.onNodeWithText("没保存成功：本机的密钥库拒绝写入，密钥没有存下来").assertExists()
        repo.refuseSecretStorage = false

        // CUSTOM: supports keyless connection (empty headerValue & formFields) showing upload URL
        // in status, plus 6 expandable clear-text fields kept readable on revisit.
        viewModel.selectProvider(ImageHostProvider.CUSTOM)
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction() and hasText("上传地址")).performTextInput("https://custom.local/upload")
        composeRule.onNodeWithText("保存并连接").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("已连接").assertExists()
        composeRule.onNode(hasText("https://custom.local/upload") and !hasSetTextAction()).assertExists()

        // Expand custom fields, enter multiline form fields & clear-text header value, save & revisit.
        composeRule.onNodeWithText("高级字段").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction() and hasText("文件字段名")).performTextReplacement("image")
        composeRule.onNode(hasSetTextAction() and hasText("鉴权请求头")).performTextInput("X-Token")
        composeRule.onNode(hasSetTextAction() and hasText("请求头的值")).performTextInput("Bearer clear-secret-9")
        composeRule.onNode(hasSetTextAction() and hasText("附加表单字段")).performScrollTo().performTextInput("k1=v1\nk2=v2")
        composeRule.onNode(hasSetTextAction() and hasText("取值路径")).performScrollTo().performTextReplacement("data.url")
        composeRule.onNode(hasSetTextAction() and hasText("地址前缀")).performScrollTo().performTextInput("https://cdn.local")
        composeRule.onNodeWithText("保存").performScrollTo().performClick()
        composeRule.waitForIdle()
        // Collapse before leaving so re-expanding on revisit also verifies the toggle path.
        composeRule.onNodeWithText("高级字段").performScrollTo().performClick()
        composeRule.waitForIdle()

        viewModel.selectProvider(ImageHostProvider.NODE_IMAGE)
        composeRule.waitForIdle()
        viewModel.selectProvider(ImageHostProvider.CUSTOM)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Bear……et-9").assertExists()
        composeRule.onNodeWithText("高级字段").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction() and hasText("请求头的值") and hasText("Bearer clear-secret-9")).performScrollTo().assertExists()
        composeRule.onNode(hasSetTextAction() and hasText("附加表单字段") and hasText("k1=v1\nk2=v2")).performScrollTo().assertExists()
    }

    /**
     * C3.3: Standard vs CUSTOM disconnect confirmations show distinct warnings and cancel cleanly;
     * gallery tile open vs delete button stay isolated, zero-byte items omit `0 B`, failed delete
     * preserves the item, and confirmed delete updates count and removes only the target.
     */
    @Test
    fun `disconnect and image delete confirmations preserve isolation failure rollback and zero byte semantics`() {
        val repo = FakeUiImageHostRepository(initial = ImageHostProvider.NODE_IMAGE)
        repo.seedConfig(ImageHostConfig(provider = ImageHostProvider.NODE_IMAGE, token = VALID_NODE_IMAGE_KEY))
        repo.storedImages[ImageHostProvider.NODE_IMAGE] =
            mutableListOf(
                HostedImage(id = "1", fileName = "a.png", url = "https://img.test/a.png", sizeBytes = 2048L),
                HostedImage(id = "2", fileName = "b.png", url = "https://img.test/b.png", sizeBytes = 0L),
            )
        val viewModel = ImageHostViewModel(repo)
        val openedGalleries = mutableListOf<Pair<List<String>, Int>>()

        composeRule.setContent {
            PlazaTheme {
                ImageHostRoute(
                    viewModel = viewModel,
                    onBack = {},
                    onOpenUrl = {},
                    onOpenImages = { urls, index -> openedGalleries += urls to index },
                )
            }
        }
        composeRule.waitForIdle()

        // Verify summary and zero-byte semantics (a.png includes size, b.png omits 0 B).
        composeRule.onNodeWithText("共 2 张 · 2.0 KB").assertExists()
        composeRule.onNode(hasContentDescription("a.png · 2.0 KB")).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(listOf("https://img.test/a.png", "https://img.test/b.png") to 0), openedGalleries)
        composeRule.onNode(hasContentDescription("b.png")).assertExists()

        // Tap delete icon on b.png: opens confirmation without triggering viewer navigation.
        composeRule.onAllNodes(hasContentDescription("删除"))[1].performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(1, openedGalleries.size)
        composeRule.onNodeWithText("删除这张图片？").assertExists()
        composeRule.onNodeWithText("b.png", substring = true).assertExists()

        // Cancel delete leaves repository untouched.
        composeRule.onNodeWithText("取消").performClick()
        composeRule.waitForIdle()
        assertEquals(2, repo.storedImages.getValue(ImageHostProvider.NODE_IMAGE).size)

        // Failed remote delete keeps image intact and reports error.
        repo.deleteError = ImageHostException(ImageHostError.Http(500))
        composeRule.onAllNodes(hasContentDescription("删除"))[1].performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("图床暂时无法访问，请稍后重试").assertExists()
        composeRule.onNodeWithText("共 2 张 · 2.0 KB").assertExists()

        // Successful remote delete removes b.png only and updates summary.
        repo.deleteError = null
        composeRule.onAllNodes(hasContentDescription("删除"))[1].performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("共 1 张 · 2.0 KB").assertExists()
        assertEquals(listOf("1"), repo.storedImages.getValue(ImageHostProvider.NODE_IMAGE).map(HostedImage::id))

        // Standard disconnect uses key-only warning, cancel keeps connected, confirm disconnects.
        composeRule.onNodeWithText("断开").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("本机保存的密钥会被删除，之后发帖和回复将无法插入图片。已上传的图片不受影响。").assertExists()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("已连接").assertExists()

        composeRule.onNodeWithText("断开").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("断开").get(1).performClick()
        composeRule.waitForIdle()
        assertFalse(viewModel.uiState.value.connected)
        assertNull(viewModel.uiState.value.credentialMask)

        // CUSTOM disconnect uses custom-record warning and wipes URL + advanced fields when confirmed.
        repo.seedConfig(
            ImageHostConfig(
                provider = ImageHostProvider.CUSTOM,
                siteUrl = "https://up.custom/upload",
                custom = CustomHostFields(
                    fileField = "upload_file",
                    headerName = "Authorization",
                    headerValue = "Bearer custom-key",
                    formFields = "bucket=main",
                    urlPath = "result.url",
                    urlPrefix = "https://cdn.custom",
                ),
            ),
        )
        viewModel.selectProvider(ImageHostProvider.CUSTOM)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("已连接").assertExists()
        composeRule.onNodeWithText("断开").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(
            "这家自定义图床的地址、字段和密钥都会从本机删除，之后发帖和回复将无法插入图片。已上传的图片不受影响。",
        ).assertExists()

        composeRule.onAllNodesWithText("断开").get(1).performClick()
        composeRule.waitForIdle()
        assertFalse(viewModel.uiState.value.connected)
        assertEquals("", viewModel.uiState.value.siteUrlInput)
        assertEquals(CustomHostFields(), viewModel.uiState.value.custom)
        assertTrue(repo.savedConfig(ImageHostProvider.CUSTOM).siteUrl.isEmpty())
    }
}

private class FakeUiImageHostRepository(
    initial: ImageHostProvider = ImageHostProvider.DEFAULT,
) : ImageHostRepository {
    private val selectedFlow = MutableStateFlow(initial)
    private val configs =
        ImageHostProvider.entries.associateWith { provider ->
            MutableStateFlow(ImageHostConfig(provider = provider))
        }

    var refuseSecretStorage: Boolean = false
    var imagesError: Throwable? = null
    var deleteError: Throwable? = null
    val storedImages = ImageHostProvider.entries.associateWith { mutableListOf<HostedImage>() }.toMutableMap()

    override val selected: Flow<ImageHostProvider> = selectedFlow

    override fun config(provider: ImageHostProvider): Flow<ImageHostConfig> = configs.getValue(provider)

    @Suppress("OPT_IN_USAGE")
    override val current: Flow<ImageHostConfig> = selectedFlow.flatMapLatest { config(it) }

    fun seedConfig(config: ImageHostConfig) {
        configs.getValue(config.provider).value = config
    }

    fun savedConfig(provider: ImageHostProvider): ImageHostConfig = configs.getValue(provider).value

    override suspend fun select(provider: ImageHostProvider) {
        selectedFlow.value = provider
    }

    override suspend fun save(config: ImageHostConfig) {
        configs.getValue(config.provider).value =
            if (refuseSecretStorage) {
                config.copy(
                    token = "",
                    custom = config.custom.copy(headerValue = "", formFields = ""),
                )
            } else {
                config.copy(siteUrl = config.siteUrl.trim().trimEnd('/'))
            }
    }

    override suspend fun disconnect(provider: ImageHostProvider) {
        configs.getValue(provider).value =
            if (provider == ImageHostProvider.CUSTOM) {
                ImageHostConfig(provider = ImageHostProvider.CUSTOM)
            } else {
                configs.getValue(provider).value.copy(token = "")
            }
    }

    override suspend fun upload(upload: ImageHostUpload, onProgress: (Float) -> Unit): HostedImage =
        error("Not used in settings UI test")

    override suspend fun images(): List<HostedImage> {
        val currentProvider = selectedFlow.value
        if (!currentProvider.browsable) throw ImageHostException(ImageHostError.Unsupported)
        imagesError?.let { throw it }
        return storedImages.getValue(currentProvider).toList()
    }

    override suspend fun delete(image: HostedImage) {
        deleteError?.let { throw it }
        storedImages.getValue(selectedFlow.value).removeAll { it.id == image.id }
    }
}

private const val VALID_NODE_IMAGE_KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
