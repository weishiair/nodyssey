package io.github.nodyssey.ui.sticker

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import io.github.nodyssey.data.inMemoryDatabase
import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.nodyssey.data.sticker.MINE_GROUP_KEY
import io.github.nodyssey.data.sticker.MySticker
import io.github.nodyssey.data.sticker.RepoFolder
import io.github.nodyssey.data.sticker.RepoListing
import io.github.nodyssey.data.sticker.StickerLibrary
import io.github.nodyssey.data.testSettingsRepository
import io.github.plaza.designsys.theme.PlazaTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class StickerConfirmationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val viewModels = ViewModelStore()
    private val http = FakeStickerHttp()
    private lateinit var database: NodeSeekDatabase
    private lateinit var library: StickerLibrary

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        database = inMemoryDatabase(dispatcher)
        library = testStickerLibrary(database, testSettingsRepository(scope), http, scope)
    }

    @After
    fun tearDown() {
        composeRule.runOnUiThread { viewModels.clear() }
        scope.cancel()
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `only confirmation removes the broken sticker and keeps the reachable one`() {
        val gone = "https://img.example.org/gone.png"
        val reachable = "https://img.example.org/reachable.png"
        http.missingUrls = setOf(gone)
        runBlocking {
            library.add(listOf(MySticker(gone, "gone"), MySticker(reachable, "reachable")))
            library.probeMine()
        }
        val viewModel = StickerGroupViewModel(library, MINE_GROUP_KEY, emptyList())
        viewModels.put("group", viewModel)
        composeRule.setContent { PlazaTheme { StickerGroupRoute(viewModel, onBack = {}, onOpenUrl = {}) } }
        composeRule.waitUntil(5_000) { viewModel.page.value?.brokenCount == 1 }

        val remove = composeRule.onNode(hasText("移除") and !hasAnyAncestor(isDialog()))
        remove.performClick()
        composeRule.onNodeWithText("取消").performClick()
        assertEquals(setOf(gone, reachable), runBlocking { library.mine.first().map { it.url }.toSet() })

        remove.performClick()
        composeRule.onNode(hasText("移除") and hasAnyAncestor(isDialog())).performClick()
        composeRule.waitUntil(5_000) { viewModel.page.value?.brokenCount == 0 }
        assertEquals(listOf(reachable), runBlocking { library.mine.first().map { it.url } })
    }

    @Test
    fun `only confirmation unsubscribes the missing source`() {
        http.missingUrls = setOf("https://api.github.com/repos/owner/gone/commits/main")
        runBlocking {
            for (name in listOf("gone", "reachable")) {
                library.subscribe(
                    RepoListing("owner", name, "main", OLD_SHA, listOf(RepoFolder("a", listOf("a/1.png"), 1)), truncated = false),
                    paths = setOf("a"),
                )
            }
            library.checkForUpdates(force = true)
        }

        val viewModel = StickerManageViewModel(library, emptyList())
        viewModels.put("sources", viewModel)
        composeRule.setContent { PlazaTheme { StickerSourcesRoute(viewModel, onBack = {}) } }
        composeRule.waitUntil(5_000) { viewModel.subscriptions.value.size == 2 }

        val unsubscribe = composeRule.onNode(hasText("取消订阅") and !hasAnyAncestor(isDialog()))
        unsubscribe.performClick()
        composeRule.onNodeWithText("取消").performClick()
        assertEquals(setOf("owner/gone", "owner/reachable"), runBlocking { library.subscriptions.first().map { it.slug }.toSet() })

        unsubscribe.performClick()
        composeRule.onNode(hasText("取消订阅") and hasAnyAncestor(isDialog())).performClick()
        composeRule.waitUntil(5_000) { viewModel.subscriptions.value.size == 1 }
        assertEquals(listOf("owner/reachable"), runBlocking { library.subscriptions.first().map { it.slug } })
    }
}
