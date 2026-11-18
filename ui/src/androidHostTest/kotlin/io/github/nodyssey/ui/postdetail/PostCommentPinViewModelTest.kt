package io.github.nodyssey.ui.postdetail

import io.github.nodyssey.core.net.JsonWriteSource
import io.github.nodyssey.data.FakePostRemoteDataSource
import io.github.nodyssey.data.MutableClock
import io.github.nodyssey.data.OfflineFirstPostRepository
import io.github.nodyssey.data.PostPinWriter
import io.github.nodyssey.data.inMemoryDatabase
import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.nodyssey.data.session.SessionState
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Paid writes must be confirmed once; a failed follow-up read must never repeat the spend. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PostCommentPinViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val remote = FakePostRemoteDataSource()
    private val session = MutableStateFlow(SessionState(isSignedIn = true))
    private lateinit var database: NodeSeekDatabase
    private lateinit var repository: OfflineFirstPostRepository
    private var pinned = false
    private var answer = """{"success":true,"message":"added"}"""
    private var gate: CompletableDeferred<Unit>? = null
    private val writes = mutableListOf<String>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        database = inMemoryDatabase(dispatcher)
        val api = object : JsonWriteSource {
            override suspend fun postJson(path: String, body: String, referer: String): String {
                writes += body
                gate?.await()
                if (answer.contains("\"success\":true")) pinned = answer.contains("added")
                return answer
            }
        }
        repository = OfflineFirstPostRepository(database, remote, MutableClock(), pins = PostPinWriter(api))
        remote.detailResult = { postId, page ->
            FakePostRemoteDataSource.detail(postId, page, totalPages = 3).copy(
                comments = listOf(FakePostRemoteDataSource.content("reply").copy(commentId = 101, floor = "#21", canPin = true, isPinned = pinned)),
            )
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun `opening and cancelling the confirmation cannot spend chicken legs`() = runTest(dispatcher) {
        val vm = PostDetailViewModel(42, repository, session)
        advanceUntilIdle()
        vm.requestCommentPin(101)
        assertEquals(CommentPinConfirmation(101, true), vm.uiState.value.pinConfirmation)
        advanceUntilIdle()
        assertTrue(writes.isEmpty())
        vm.dismissCommentPin()
        vm.confirmCommentPin()
        advanceUntilIdle()
        assertTrue(writes.isEmpty())
    }

    @Test
    fun `confirmation is consumed once and a later-page pin reloads page one`() = runTest(dispatcher) {
        val vm = PostDetailViewModel(42, repository, session, initialPage = 3)
        advanceUntilIdle()
        gate = CompletableDeferred()
        vm.requestCommentPin(101)
        vm.confirmCommentPin()
        advanceUntilIdle()
        vm.confirmCommentPin()
        vm.requestCommentPin(101)
        vm.confirmCommentPin()
        advanceUntilIdle()
        assertEquals(1, writes.size)
        assertEquals(101L, vm.uiState.value.pendingCommentPin)

        gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(42L to 3, 42L to 1), remote.detailRequests)
        assertEquals(1, vm.uiState.value.firstLoadedPage)
        assertTrue(vm.uiState.value.comments.single().isPinned)
        assertEquals(PendingScroll(1), vm.uiState.value.pendingScroll)
        assertNull(vm.uiState.value.pendingCommentPin)
    }

    @Test
    fun `a refused pin leaves the comment unpinned and shows the site's reason`() = runTest(dispatcher) {
        val vm = PostDetailViewModel(42, repository, session)
        advanceUntilIdle()
        answer = """{"success":false,"message":"鸡腿不足"}"""
        vm.requestCommentPin(101)
        vm.confirmCommentPin()
        advanceUntilIdle()

        assertEquals("鸡腿不足", vm.uiState.value.pinFailure?.detail)
        assertFalse(vm.uiState.value.comments.single().isPinned)
        assertNull(vm.uiState.value.pinMessage)
        assertNull(vm.uiState.value.pendingCommentPin)
        assertEquals(listOf(42L to 1), remote.detailRequests)
    }

    @Test
    fun `a failed refresh after acceptance retries the read not the paid write`() = runTest(dispatcher) {
        val vm = PostDetailViewModel(42, repository, session, initialPage = 3)
        advanceUntilIdle()
        remote.detailError = SiteException(SiteError.Network)
        vm.requestCommentPin(101)
        vm.confirmCommentPin()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.comments.single().isPinned)
        assertEquals(true, vm.uiState.value.pinMessage)
        assertNull(vm.uiState.value.pinFailure)
        assertEquals(SiteError.Network, vm.uiState.value.error)

        remote.detailError = null
        vm.refresh()
        advanceUntilIdle()
        assertEquals(1, writes.size)
        assertEquals(listOf(42L to 3, 42L to 1, 42L to 1), remote.detailRequests)
        assertNull(vm.uiState.value.error)
        assertEquals(1, vm.uiState.value.firstLoadedPage)
    }

    @Test
    fun `an existing pin is removed rather than bought again`() = runTest(dispatcher) {
        pinned = true
        val vm = PostDetailViewModel(42, repository, session)
        advanceUntilIdle()
        answer = """{"success":true,"message":"removed"}"""
        vm.requestCommentPin(101)
        vm.confirmCommentPin()
        advanceUntilIdle()

        assertEquals(listOf("""{"commentId":101,"action":"remove"}"""), writes)
        assertFalse(vm.uiState.value.comments.single().isPinned)
        assertEquals(false, vm.uiState.value.pinMessage)
    }

    @Test
    fun `signing out while a confirmation is open withdraws the paid action`() = runTest(dispatcher) {
        val vm = PostDetailViewModel(42, repository, session)
        advanceUntilIdle()
        vm.requestCommentPin(101)
        session.value = SessionState(isSignedIn = false)
        advanceUntilIdle()
        vm.confirmCommentPin()
        advanceUntilIdle()
        assertNull(vm.uiState.value.pinConfirmation)
        assertTrue(writes.isEmpty())
    }
}
