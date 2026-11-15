package io.github.nodyssey.ui.postdetail

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModelStore
import io.github.nodyssey.core.net.JsonWriteSource
import io.github.nodyssey.data.FakePostRemoteDataSource
import io.github.nodyssey.data.MutableClock
import io.github.nodyssey.data.OfflineFirstPostRepository
import io.github.nodyssey.data.PostPinWriter
import io.github.nodyssey.data.inMemoryDatabase
import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.nodyssey.data.session.SessionState
import io.github.plaza.designsys.theme.PlazaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.util.concurrent.atomic.AtomicInteger

/** Exercises the paid gate through the actual menu, dialog, ViewModel, writer and Room. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
@OptIn(ExperimentalCoroutinesApi::class)
class PostCommentPinScreenTest {
    @get:Rule
    val composeRule = createComposeRule()
    private lateinit var database: NodeSeekDatabase
    private val store = ViewModelStore()
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        composeRule.runOnUiThread { store.clear() }
        if (::database.isInitialized) database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `cancelling the paid dialog sends nothing and confirming it sends one pin`() {
        database = inMemoryDatabase(dispatcher)
        val writes = AtomicInteger()
        val remote = FakePostRemoteDataSource().apply {
            detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, totalPages = 1).copy(
                    comments = listOf(
                        FakePostRemoteDataSource.content("reply").copy(commentId = 101, floor = "#1", canPin = true, isPinned = writes.get() > 0),
                    ),
                )
            }
        }
        val api = object : JsonWriteSource {
            override suspend fun postJson(path: String, body: String, referer: String): String {
                writes.incrementAndGet()
                return """{"success":true,"message":"added"}"""
            }
        }
        val repository = OfflineFirstPostRepository(database, remote, MutableClock(), pins = PostPinWriter(api))
        runBlocking { repository.refreshThread(42, 1) }
        lateinit var vm: PostDetailViewModel
        composeRule.runOnUiThread {
            vm = PostDetailViewModel(42, repository, MutableStateFlow(SessionState(isSignedIn = true)))
            store.put("post", vm)
        }
        composeRule.setContent {
            val state by vm.uiState.collectAsState()
            PlazaTheme {
                PostDetailScreen(
                    state = state,
                    postUrl = "https://www.nodeseek.com/post-42-1",
                    onBack = {},
                    onOpenBrowser = {},
                    onImageClick = {},
                    onRetry = vm::refresh,
                    onLoadMore = {},
                    onVerify = {},
                    onRequestCommentPin = vm::requestCommentPin,
                    onConfirmCommentPin = vm::confirmCommentPin,
                    onDismissCommentPin = vm::dismissCommentPin,
                )
            }
        }
        composeRule.waitUntil(5_000) { vm.uiState.value.comments.isNotEmpty() }
        openPinDialog()
        assertEquals(0, writes.get())
        composeRule.onNodeWithText("取消", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(0, writes.get())

        openPinDialog()
        assertEquals(0, writes.get())
        composeRule.onNodeWithText("置顶（5 鸡腿）").performClick()
        composeRule.waitUntil(5_000) { writes.get() == 1 && vm.uiState.value.pendingCommentPin == null }
        assertEquals(1, writes.get())
    }

    private fun openPinDialog() {
        composeRule.onAllNodesWithContentDescription("楼层操作")[1].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("置顶评论").performClick()
    }
}
