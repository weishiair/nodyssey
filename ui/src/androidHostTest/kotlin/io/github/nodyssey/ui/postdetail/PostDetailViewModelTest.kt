package io.github.nodyssey.ui.postdetail

import io.github.nodyssey.ThreadPreview
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.html.PostDetailParser
import io.github.nodyssey.data.FakePostRemoteDataSource
import io.github.nodyssey.data.MutableClock
import io.github.nodyssey.data.NoReadingPositions
import io.github.nodyssey.data.OfflineFirstPostRepository
import io.github.nodyssey.data.PostRepository
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.ReadingPosition
import io.github.nodyssey.data.ReadingPositionStore
import io.github.nodyssey.data.TrackedThreadStore
import io.github.nodyssey.data.UserProfile
import io.github.nodyssey.data.inMemoryDatabase
import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.nodyssey.data.local.toEntity
import io.github.nodyssey.data.session.SessionState
import io.github.nodyssey.model.ReactionAction
import io.github.plaza.core.AppClock
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Offline-first behaviour of the detail screen, against a real database. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PostDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val remote = FakePostRemoteDataSource()
    private val clock = MutableClock()
    private lateinit var database: NodeSeekDatabase
    private lateinit var repository: OfflineFirstPostRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        database = inMemoryDatabase(dispatcher)
        repository = OfflineFirstPostRepository(database, remote, clock)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    private val session = MutableStateFlow(SessionState())

    @Test
    fun `cached HTML replies are read in numeric floor order without a refetch`() = runTest(dispatcher) {
        val html = javaClass.classLoader!!.getResourceAsStream("fixtures/post-703863-1.html")!!.bufferedReader().use { it.readText() }
        val detail = PostDetailParser.parse(html, postId = 42, page = 1)
        assertEquals("#4", detail.comments.first().floor)
        remote.detailResult = { _, _ -> detail }
        repository.refreshThread(postId = 42, page = 1)
        val requests = remote.detailRequests.size

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals((1..10).map { "#$it" }, vm.uiState.value.comments.map { it.floor })
        assertEquals(requests, remote.detailRequests.size)
        assertEquals(detail.comments.map { it.commentId }.toSet(), vm.uiState.value.comments.map { it.commentId }.toSet())
    }

    @Test
    fun `sorting across loaded pages keeps reply source pages and author filtering aligned`() = runTest(dispatcher) {
        val serverFloors = listOf(listOf(10, 13, 1, 2, 3, 4, 5, 6, 7, 8), listOf(9, 11, 12, 14, 15, 16, 17))
        remote.detailResult = { postId, page ->
            FakePostRemoteDataSource.detail(postId, page, totalPages = 2).copy(
                body = FakePostRemoteDataSource.content("body").copy(authorUid = 7),
                comments = serverFloors[page - 1].map { floor ->
                    FakePostRemoteDataSource.content("floor $floor").copy(
                        floor = "#$floor",
                        authorUid = if (floor % 2 == 0) 7 else 8,
                    )
                },
            )
        }
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(serverFloors[0].sorted().map { "#$it" }, vm.uiState.value.comments.map { it.floor })
        vm.loadNextPage()
        advanceUntilIdle()

        val expectedPages = (1..17).map { if (it in serverFloors[0]) 1 else 2 }
        assertEquals((1..17).map { "#$it" }, vm.uiState.value.comments.map { it.floor })
        assertEquals(expectedPages, vm.uiState.value.commentPages)
        vm.toggleOnlyAuthor()
        advanceUntilIdle()
        assertEquals((2..16 step 2).map { "#$it" }, vm.uiState.value.comments.map { it.floor })
        assertEquals((2..16 step 2).map { expectedPages[it - 1] }, vm.uiState.value.commentSourcePages)
        assertEquals(17, vm.uiState.value.readerLastFloor())
        vm.toggleOnlyAuthor()
        advanceUntilIdle()
        assertEquals((1..17).map { "#$it" }, vm.uiState.value.comments.map { it.floor })
        assertEquals(expectedPages, vm.uiState.value.commentPages)
    }

    /**
     * A reply can move across a page boundary while the previous page is still cached. Both pages
     * then contain the same comment id, which used to become the same LazyColumn key twice and
     * crashed the app while the long thread was still loading.
     */
    @Test
    fun `a reply repeated on adjacent pages is drawn once`() = runTest(dispatcher) {
        remote.detailResult = { postId, page ->
            val replies =
                when (page) {
                    1 ->
                        listOf(
                            FakePostRemoteDataSource.content("first").copy(commentId = 13_321_590, floor = "#1"),
                        )

                    else ->
                        listOf(
                            FakePostRemoteDataSource.content("first, repeated by the next page").copy(commentId = 13_321_590, floor = "#1"),
                            FakePostRemoteDataSource.content("second").copy(commentId = 13_321_591, floor = "#2"),
                        )
                }
            FakePostRemoteDataSource.detail(postId, page, commentCount = replies.size, totalPages = 2)
                .copy(comments = replies)
        }
        val vm = viewModel()
        advanceUntilIdle()

        vm.loadNextPage()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(listOf(13_321_590L, 13_321_591L), state.comments.map { it.commentId })
        assertEquals(listOf("#1", "#2"), state.comments.map { it.floor })
        assertEquals(listOf(1, 2), state.commentPages)
    }

    private fun givenAuthorReplies() {
        remote.detailResult = { postId, page ->
            val detail = FakePostRemoteDataSource.detail(postId, page, commentCount = 10, totalPages = 3)
            detail.copy(
                body = detail.body?.copy(authorUid = 7, isOriginalPoster = true).takeIf { page == 1 },
                comments = detail.comments.mapIndexed { index, comment ->
                    val floor = (page - 1) * 10 + index + 1
                    comment.copy(commentId = floor.toLong(), floor = "#$floor", authorUid = if (index % 3 == 0) 7 else 8, isOriginalPoster = index % 3 == 0)
                },
            )
        }
    }

    @Test
    fun `author filter finds replies outside the current window and preserves their source pages`() = runTest(dispatcher) {
        givenAuthorReplies()
        val vm = viewModel(initialPage = 2)
        advanceUntilIdle()
        vm.toggleOnlyAuthor()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.onlyAuthor)
        assertFalse(state.isScanningAuthor)
        assertEquals(listOf(2, 1, 3), remote.detailRequests.map { it.second })
        assertEquals(listOf(1, 4, 7, 10, 11, 14, 17, 20, 21, 24, 27, 30).map { "#$it" }, state.comments.map { it.floor })
        assertEquals(List(4) { 1 } + List(4) { 2 } + List(4) { 3 }, state.commentSourcePages)
        assertEquals(List(10) { 1 } + List(2) { 2 }, state.commentPages)
        assertEquals(2, state.totalPages)
        assertEquals(12, state.replyCount)
        assertEquals(30, state.readerLastFloor())
    }

    @Test
    fun `leaving author filter restores the unfiltered floor and does not record filtered page numbers`() = runTest(dispatcher) {
        givenAuthorReplies()
        val positions = FakeReadingPositions()
        val vm = viewModel(initialPage = 2, readingPositions = positions)
        advanceUntilIdle()
        vm.recordReadingPosition(2, "#14")
        advanceUntilIdle()
        vm.toggleOnlyAuthor()
        advanceUntilIdle()
        vm.recordReadingPosition(1, "#24")
        vm.toggleOnlyAuthor()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.onlyAuthor)
        assertEquals(PendingScroll(2, "#14"), vm.uiState.value.pendingScroll)
        assertEquals(30, vm.uiState.value.comments.size)
        assertEquals(listOf(ReadingPosition(2, "#14")), positions.writes)
    }

    @Test
    fun `leaving author filter cancels outstanding scans`() = runTest(dispatcher) {
        givenAuthorReplies()
        val vm = viewModel(initialPage = 2)
        advanceUntilIdle()
        remote.gate = CompletableDeferred()
        vm.toggleOnlyAuthor()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isScanningAuthor)
        vm.toggleOnlyAuthor()
        remote.gate!!.complete(Unit)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.onlyAuthor)
        assertFalse(vm.uiState.value.isScanningAuthor)
        assertEquals(listOf(2, 1), remote.detailRequests.map { it.second })
        assertEquals(2..2, repository.cachedPages(42))
    }

    @Test
    fun `failed author scan keeps matching replies and retry finishes the rest`() = runTest(dispatcher) {
        givenAuthorReplies()
        val vm = viewModel(initialPage = 2)
        advanceUntilIdle()
        remote.detailError = SiteException(SiteError.Network)
        vm.toggleOnlyAuthor()
        advanceUntilIdle()
        assertEquals(4, vm.uiState.value.comments.size)
        assertEquals(SiteError.Network, vm.uiState.value.authorScanError)
        assertNull(vm.uiState.value.readerLastFloor())
        remote.detailError = null
        vm.refresh()
        advanceUntilIdle()

        assertNull(vm.uiState.value.authorScanError)
        assertEquals(12, vm.uiState.value.comments.size)
    }

    @Test
    fun `quoted original floor leaves author filter instead of using filtered pagination`() = runTest(dispatcher) {
        givenAuthorReplies()
        val vm = viewModel(initialPage = 2)
        advanceUntilIdle()
        vm.toggleOnlyAuthor()
        advanceUntilIdle()
        val requests = remote.detailRequests.size
        vm.jumpToFloor("#12")
        advanceUntilIdle()

        assertFalse(vm.uiState.value.onlyAuthor)
        assertEquals(PendingScroll(2, "#12"), vm.uiState.value.pendingScroll)
        assertEquals(requests, remote.detailRequests.size)
    }

    @Test
    fun `same thread link exits author filter and uses the original site page`() = runTest(dispatcher) {
        givenAuthorReplies()
        val vm = viewModel(initialPage = 2)
        advanceUntilIdle()
        vm.toggleOnlyAuthor()
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.totalPages)

        assertTrue(vm.openLinkInPlace("https://www.nodeseek.com/post-42-3"))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.onlyAuthor)
        assertEquals(PendingScroll(3), vm.uiState.value.pendingScroll)
    }

    @Test
    fun `empty author results on an uncached opening post still allow leaving the filter`() = runTest(dispatcher) {
        remote.detailResult = { postId, page ->
            FakePostRemoteDataSource.detail(postId, page, body = null, commentCount = 10, totalPages = 3)
        }
        val vm = viewModel(initialPage = 2)
        advanceUntilIdle()
        remote.detailError = SiteException(SiteError.Network)
        vm.toggleOnlyAuthor()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.comments.isEmpty())
        assertTrue(vm.uiState.value.hasContent)
        vm.toggleOnlyAuthor()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.onlyAuthor)
        assertEquals(10, vm.uiState.value.comments.size)
    }

    private fun viewModel(
        postId: Long = 42,
        initialFloor: String? = null,
        initialPage: Int? = null,
        preview: ThreadPreview? = null,
        readingPositions: ReadingPositionStore = NoReadingPositions,
        trackedThreads: TrackedThreadStore? = null,
        profiles: ProfileRepository? = null,
        clock: AppClock = this.clock,
    ) = PostDetailViewModel(
        postId,
        repository,
        session,
        initialFloor,
        initialPage,
        preview,
        readingPositions = readingPositions,
        trackedThreads = trackedThreads,
        profiles = profiles,
        clock = clock,
    )

    /**
     * Puts one post in the `posts` table with [commentCount] replies — what a feed refresh leaves
     * behind, and the baseline both the badge and [PostRepository.seenRepliesIfUnread] compare against.
     */
    private suspend fun givenListedPost(
        postId: Long,
        commentCount: Int,
    ) {
        val summary =
            FakePostRemoteDataSource
                .summary(slug = null, page = 1, commentCount = commentCount)
                .copy(postId = postId)
        database.feedDao().upsertPosts(listOf(summary.toEntity(clock.nowMillis())))
    }

    /** A [ReadingPositionStore] a test can seed and then read back, in place of DataStore. */
    private class FakeReadingPositions(
        private var stored: ReadingPosition? = null,
    ) : ReadingPositionStore {
        val writes = mutableListOf<ReadingPosition>()

        override suspend fun readingPosition(postId: Long): ReadingPosition? = stored

        override suspend fun setReadingPosition(
            postId: Long,
            position: ReadingPosition,
        ) {
            stored = position
            writes += position
        }
    }

    /**
     * A thread that answered "登录后查看" a second ago has content now. The freshness window would
     * otherwise suppress the one request that matters.
     */
    @Test
    fun `signing in refetches the thread even inside the cache window`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            val requestsBefore = remote.detailRequests.size
            assertTrue("expected an initial fetch", requestsBefore > 0)

            session.value = SessionState(isSignedIn = true, fingerprint = 1, generation = 1)
            advanceUntilIdle()

            assertTrue(
                "expected a refetch, still at $requestsBefore",
                remote.detailRequests.size > requestsBefore,
            )
        }

    /**
     * The title the feed row already showed is the screen's until the thread itself says otherwise.
     *
     * Room answers "nothing cached" for a thread nobody has opened, and that answer used to clear
     * the whole mirrored state — including a title that had just been handed over deliberately. It
     * is also the frame the row's title flies into, so losing it loses the animation.
     */
    @Test
    fun `states the title the list knew before the thread arrives`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 1, totalPages = 1)
            }
            val gate = CompletableDeferred<Unit>()
            remote.gate = gate

            val vm = viewModel(preview = ThreadPreview(title = "what the row said", authorName = "op"))
            // Let the fetch start and park on the gate, so the assertions below are made against
            // the frame the reader actually sees while the thread is on its way.
            advanceUntilIdle()

            assertEquals("what the row said", vm.uiState.value.title)
            assertFalse("nothing has loaded yet", vm.uiState.value.hasContent)

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals("thread 42", vm.uiState.value.title)
        }

    @Test
    fun `appends later comment pages into one thread`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 2, totalPages = 3)
            }
            val vm = viewModel()
            advanceUntilIdle()
            assertEquals(2, vm.uiState.value.comments.size)

            vm.loadNextPage()
            advanceUntilIdle()

            assertEquals(4, vm.uiState.value.comments.size)
            assertEquals(2, vm.uiState.value.lastLoadedPage)
            assertTrue(vm.uiState.value.hasNextPage)
        }

    @Test
    fun `stops paging at the last page`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 1, totalPages = 2)
            }
            val vm = viewModel()
            advanceUntilIdle()

            vm.loadNextPage()
            advanceUntilIdle()
            assertFalse(vm.uiState.value.hasNextPage)

            val before = remote.detailRequests.size
            vm.loadNextPage()
            advanceUntilIdle()

            assertEquals(before, remote.detailRequests.size)
        }

    @Test
    fun `prepending keeps later pages and refuses duplicate and opposite direction loads`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 2, totalPages = 5)
            }
            val vm = viewModel(initialPage = 3)
            advanceUntilIdle()
            val original = vm.uiState.value.comments
            val gate = CompletableDeferred<Unit>()
            remote.gate = gate

            vm.loadPreviousPage()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.isPrepending)
            assertFalse(vm.uiState.value.isAppending)
            vm.loadPreviousPage()
            vm.loadNextPage()
            vm.refreshTail()
            vm.extendToPage(4)
            advanceUntilIdle()
            assertEquals(listOf(42L to 3, 42L to 2), remote.detailRequests)

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(2, vm.uiState.value.firstLoadedPage)
            assertEquals(3, vm.uiState.value.lastLoadedPage)
            assertEquals(listOf(2, 2, 3, 3), vm.uiState.value.commentPages)
            assertEquals(original, vm.uiState.value.comments.takeLast(2))
            assertFalse(vm.uiState.value.isPrepending)
        }

    @Test
    fun `a failed prepend leaves the read intact and can be retried`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 2, totalPages = 3)
            }
            val vm = viewModel(initialPage = 3)
            advanceUntilIdle()
            val original = vm.uiState.value.comments
            remote.detailError = SiteException(SiteError.Network)

            vm.loadPreviousPage()
            advanceUntilIdle()
            assertEquals(SiteError.Network, vm.uiState.value.error)
            assertEquals(original, vm.uiState.value.comments)
            assertEquals(3, vm.uiState.value.firstLoadedPage)
            assertFalse(vm.uiState.value.isPrepending)

            remote.detailError = null
            vm.loadPreviousPage()
            advanceUntilIdle()
            assertNull(vm.uiState.value.error)
            assertEquals(2, vm.uiState.value.firstLoadedPage)
            assertEquals(original, vm.uiState.value.comments.takeLast(2))
        }

    @Test
    fun `backward loading stops at page one without discarding later replies`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 2, totalPages = 3)
            }
            val vm = viewModel(initialPage = 3)
            advanceUntilIdle()
            repeat(3) {
                vm.loadPreviousPage()
                advanceUntilIdle()
            }

            assertEquals(listOf(42L to 3, 42L to 2, 42L to 1), remote.detailRequests)
            assertEquals(listOf(1, 1, 2, 2, 3, 3), vm.uiState.value.commentPages)
            assertEquals(1, vm.uiState.value.firstLoadedPage)
            assertEquals(3, vm.uiState.value.lastLoadedPage)
        }

    /**
     * The pull past the foot of a finished thread — see [PostDetailViewModel.refreshTail]. It re-reads
     * the last page in place, so the pages the reader scrolled through to get there are still there.
     */
    @Test
    fun `pulling past the end re-reads the last page and keeps the ones above it`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 2, totalPages = 2)
            }
            val vm = viewModel()
            advanceUntilIdle()
            vm.loadNextPage()
            advanceUntilIdle()
            assertFalse(vm.uiState.value.hasNextPage)

            // Two replies landed on the last page while it was being read.
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 4, totalPages = 2)
            }
            vm.refreshTail()
            advanceUntilIdle()

            assertEquals(42L to 2, remote.detailRequests.last())
            assertEquals(1, vm.uiState.value.firstLoadedPage)
            assertEquals(2, vm.uiState.value.lastLoadedPage)
            // Page 1 as it was, page 2 as it is now — not a window replaced by its own last page.
            assertEquals(6, vm.uiState.value.comments.size)
        }

    /** While a next page exists the same drag means "load it", and the two must not both fire. */
    @Test
    fun `pulling past the end does nothing while the thread still has a next page`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 2, totalPages = 3)
            }
            val vm = viewModel()
            advanceUntilIdle()
            val before = remote.detailRequests.size

            vm.refreshTail()
            advanceUntilIdle()

            assertEquals(before, remote.detailRequests.size)
        }

    /** Its own indicator owns the feedback, so neither the top bar nor the append spinner claims it. */
    @Test
    fun `a tail refresh raises only its own indicator`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 2, totalPages = 1)
            }
            val vm = viewModel()
            advanceUntilIdle()

            vm.refreshTail()
            assertTrue(vm.uiState.value.isRefreshingTail)
            assertFalse(vm.uiState.value.isLoading)
            assertFalse(vm.uiState.value.isAppending)
            advanceUntilIdle()

            assertFalse(vm.uiState.value.isRefreshingTail)
            assertNull(vm.uiState.value.error)
        }

    @Test
    fun `a failed tail refresh lowers its indicator and reports the error`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 2, totalPages = 1)
            }
            val vm = viewModel()
            advanceUntilIdle()
            remote.detailError = SiteException(SiteError.Network)

            vm.refreshTail()
            advanceUntilIdle()

            assertFalse(vm.uiState.value.isRefreshingTail)
            assertEquals(SiteError.Network, vm.uiState.value.error)
        }

    /** The pull gesture's indicator is its own feedback; it must rise with the load and fall with it. */
    @Test
    fun `a pull refresh raises its indicator and lowers it when the load lands`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()

            vm.pullRefresh()
            assertTrue(vm.uiState.value.isRefreshing)
            advanceUntilIdle()

            assertFalse(vm.uiState.value.isRefreshing)
            assertNull(vm.uiState.value.error)
        }

    @Test
    fun `a failed pull refresh lowers the indicator and reports the error`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            remote.detailError = SiteException(SiteError.Network)

            vm.pullRefresh()
            advanceUntilIdle()

            assertFalse(vm.uiState.value.isRefreshing)
            assertEquals(SiteError.Network, vm.uiState.value.error)
        }

    /**
     * The point of phase two: a thread read moments ago paints from the database and issues no
     * request at all.
     */
    @Test
    fun `a freshly cached thread is shown without hitting the network`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 2, totalPages = 1)
            }
            repository.refreshThread(postId = 42, page = 1)
            val requestsAfterSeeding = remote.detailRequests.size

            val vm = viewModel()
            advanceUntilIdle()

            assertEquals(requestsAfterSeeding, remote.detailRequests.size)
            assertEquals(2, vm.uiState.value.comments.size)
            assertNotNull(vm.uiState.value.body)
        }

    /**
     * The badge on the row the reader tapped said replies had arrived; the cache window said the
     * thread was fresh. The badge is the one with evidence — see [PostRepository.seenRepliesIfUnread].
     */
    @Test
    fun `a fresh cached thread is refetched when the list says replies arrived`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 1, totalPages = 1)
            }
            repository.refreshThread(postId = 42, page = 1)
            givenListedPost(postId = 42, commentCount = 1)
            repository.markThreadRead(42)
            // The list refreshes; the row now carries replies this read never saw.
            givenListedPost(postId = 42, commentCount = 4)
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 4, totalPages = 1)
            }

            val vm = viewModel()
            advanceUntilIdle()

            assertEquals(4, vm.uiState.value.comments.size)
        }

    /**
     * The reader saw 12 replies; the list now has 15. The thread opens on page 2, where #13 is, and
     * marks #13 as where the new ones begin — read before this visit moves the baseline to 15.
     */
    @Test
    fun `a thread with new replies opens at the first one it has not seen`() =
        runTest(dispatcher) {
            givenListedPost(postId = 42, commentCount = 12)
            repository.markThreadRead(42)
            givenListedPost(postId = 42, commentCount = 15)

            val vm = viewModel()
            advanceUntilIdle()

            assertEquals(13, vm.uiState.value.firstNewFloor)
            assertEquals(PendingScroll(page = 2, floor = "#13"), vm.uiState.value.pendingScroll)
        }

    /** A notification names its own floor, and that is where the reader asked to go. */
    @Test
    fun `a named floor wins over the first new reply`() =
        runTest(dispatcher) {
            givenListedPost(postId = 42, commentCount = 12)
            repository.markThreadRead(42)
            givenListedPost(postId = 42, commentCount = 15)

            val vm = viewModel(initialFloor = "#3")
            advanceUntilIdle()

            assertEquals(13, vm.uiState.value.firstNewFloor)
            assertEquals(PendingScroll(page = 1, floor = "#3"), vm.uiState.value.pendingScroll)
        }

    /** Read before anyone replied: every floor is new, so there is no line to draw and no jump. */
    @Test
    fun `a thread read before its first reply opens at the top`() =
        runTest(dispatcher) {
            givenListedPost(postId = 42, commentCount = 0)
            repository.markThreadRead(42)
            givenListedPost(postId = 42, commentCount = 3)

            val vm = viewModel()
            advanceUntilIdle()

            assertNull(vm.uiState.value.firstNewFloor)
            assertNull(vm.uiState.value.pendingScroll)
        }

    /**
     * The last page is on screen, so its replies are read: a followed thread must not announce them
     * on the next poll. A first page of many says nothing about the total and must not move it.
     */
    @Test
    fun `reading a followed thread to its last floor moves what it has been told`() =
        runTest(dispatcher) {
            val tracked = TrackedThreadStore(database.trackedThreadDao(), clock)
            tracked.track(42, "thread 42", commentCount = 1)
            remote.detailResult = { postId, page ->
                val detail = FakePostRemoteDataSource.detail(postId, page, commentCount = 3, totalPages = 1)
                detail.copy(comments = detail.comments.mapIndexed { i, c -> c.copy(floor = "#${i + 1}") })
            }

            viewModel(trackedThreads = tracked)
            advanceUntilIdle()

            assertEquals(listOf(3), tracked.all().map { it.lastKnownCount })
        }

    @Test
    fun `reading only the first page of a followed thread leaves its count alone`() =
        runTest(dispatcher) {
            val tracked = TrackedThreadStore(database.trackedThreadDao(), clock)
            tracked.track(42, "thread 42", commentCount = 1)
            remote.detailResult = { postId, page ->
                val detail = FakePostRemoteDataSource.detail(postId, page, commentCount = 3, totalPages = 5)
                detail.copy(comments = detail.comments.mapIndexed { i, c -> c.copy(floor = "#${i + 1}") })
            }

            viewModel(trackedThreads = tracked)
            advanceUntilIdle()

            assertEquals(listOf(1), tracked.all().map { it.lastKnownCount })
        }

    /** The window is still what suppresses the request when the list has nothing new to report. */
    @Test
    fun `a fresh cached thread the list agrees with is still shown without a request`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 2, totalPages = 1)
            }
            repository.refreshThread(postId = 42, page = 1)
            givenListedPost(postId = 42, commentCount = 2)
            repository.markThreadRead(42)
            val requestsAfterSeeding = remote.detailRequests.size

            val vm = viewModel()
            advanceUntilIdle()

            assertEquals(requestsAfterSeeding, remote.detailRequests.size)
        }

    @Test
    fun `a stale cached thread is shown and then refreshed`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 1, totalPages = 1)
            }
            repository.refreshThread(postId = 42, page = 1)
            clock.advanceBy(OfflineFirstPostRepository.THREAD_CACHE_TTL_MILLIS + 1)

            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 5, totalPages = 1)
            }
            val vm = viewModel()
            advanceUntilIdle()

            assertEquals(5, vm.uiState.value.comments.size)
        }

    @Test
    fun `removing session data clears a still alive detail entry`() =
        runTest(dispatcher) {
            repository.refreshThread(postId = 42, page = 1)
            val vm = viewModel()
            advanceUntilIdle()
            assertNotNull(vm.uiState.value.body)

            repository.clearSessionData()
            advanceUntilIdle()

            assertNull(vm.uiState.value.body)
            assertTrue(vm.uiState.value.comments.isEmpty())
            assertEquals(1, vm.uiState.value.lastLoadedPage)
        }

    @Test
    fun `refresh after page three resumes from page two without a gap`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 1, totalPages = 5)
            }
            val vm = viewModel()
            advanceUntilIdle()
            vm.loadNextPage()
            advanceUntilIdle()
            vm.loadNextPage()
            advanceUntilIdle()
            assertEquals(3, vm.uiState.value.lastLoadedPage)

            vm.refresh()
            advanceUntilIdle()
            assertEquals(1, vm.uiState.value.lastLoadedPage)

            vm.loadNextPage()
            advanceUntilIdle()

            assertEquals(2, remote.detailRequests.last().second)
            assertEquals(2, vm.uiState.value.lastLoadedPage)
        }

    /** A flaky connection must not throw away content the user is already reading. */
    @Test
    fun `a failed refresh keeps the cached thread on screen`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 2, totalPages = 1)
            }
            repository.refreshThread(postId = 42, page = 1)
            val vm = viewModel()
            advanceUntilIdle()

            remote.detailError = SiteException(SiteError.Network)
            vm.refresh()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(SiteError.Network, state.error)
            assertEquals(2, state.comments.size)
            assertNotNull(state.body)
        }

    @Test
    fun `an unclassified failure becomes Unknown, not a crash`() =
        runTest(dispatcher) {
            remote.detailError = IllegalStateException("boom")

            val vm = viewModel()
            advanceUntilIdle()

            assertEquals(SiteError.Unknown, vm.uiState.value.error)
        }

    /**
     * Regression: `runCatching` catches `CancellationException` too, so a load cut short by a refresh
     * used to surface a failure the user never caused.
     */
    @Test
    fun `cancelling an in-flight load does not surface an error`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 1, totalPages = 3)
            }
            val vm = viewModel()
            advanceUntilIdle()

            val gate = CompletableDeferred<Unit>()
            remote.gate = gate
            vm.loadNextPage()
            // Let it start and park on the gate — otherwise there is nothing to cancel.
            advanceUntilIdle()

            remote.gate = null
            vm.refresh()
            advanceUntilIdle()
            gate.complete(Unit)
            advanceUntilIdle()

            assertNull(vm.uiState.value.error)
            assertFalse(vm.uiState.value.isLoading)
            assertFalse(vm.uiState.value.isAppending)
        }

    /**
     * Found on a device in aeroplane mode: opening an uncached post shows nothing but an error, and the
     * thread was still marked read and dimmed in the list as though it had been.
     */
    @Test
    fun `a thread that failed to load and has no cache is not marked read`() =
        runTest(dispatcher) {
            remote.detailError = SiteException(SiteError.Network)

            val vm = viewModel(postId = 42)
            advanceUntilIdle()

            assertNull(vm.uiState.value.body)
            assertEquals(SiteError.Network, vm.uiState.value.error)
            assertNull("marked read despite showing only an error", database.readMarkDao().find(42))
        }

    /** The other half of the same rule: content read from the cache offline does count as read. */
    @Test
    fun `a cached thread read with no network is marked read`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 2, totalPages = 1)
            }
            repository.refreshThread(postId = 42, page = 1)

            remote.detailError = SiteException(SiteError.Network)
            clock.advanceBy(OfflineFirstPostRepository.THREAD_CACHE_TTL_MILLIS + 1)
            val vm = viewModel(postId = 42)
            advanceUntilIdle()

            assertEquals(2, vm.uiState.value.comments.size)
            assertNotNull("cached content was read but not recorded", database.readMarkDao().find(42))
        }

    /**
     * The whole point of the control. Jumping used to walk pages 2, 3, 4 to keep the loaded pages a
     * prefix of the thread, which on a thread of any length is dozens of requests and, against the
     * site's own throttle, a jump that never arrives.
     */
    @Test
    fun `jumping to a distant page fetches that page and nothing else`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 2, totalPages = 40)
            }
            val vm = viewModel()
            advanceUntilIdle()
            val requestsBefore = remote.detailRequests.size

            vm.loadPage(30)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(listOf(42L to 30), remote.detailRequests.drop(requestsBefore))
            assertEquals(30, state.firstLoadedPage)
            assertEquals(30, state.lastLoadedPage)
            assertEquals(2, state.comments.size)
            assertEquals(listOf(30, 30), state.commentPages)
            assertEquals(PendingScroll(page = 30), state.pendingScroll)

            vm.onScrollHandled()
            assertNull(vm.uiState.value.pendingScroll)
        }

    /**
     * The editor closes, and the next thing the reader must see is their own reply. `refresh()` used
     * to re-read the window's *first* page here, so on a multi-page thread the new floor stayed out
     * of sight and the only feedback was the sheet closing. The floor's page is re-fetched even when
     * it is the loaded tail — the copy on screen is stale by exactly this reply — and the scroll
     * rides the fetch so the landing is the new floor itself.
     */
    @Test
    fun `a published reply re-fetches its own page and scrolls to the new floor`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 2, totalPages = 5)
            }
            val vm = viewModel()
            advanceUntilIdle()
            vm.loadPage(5)
            advanceUntilIdle()
            val requestsBefore = remote.detailRequests.size

            // Floor #43 lives on page 5 — the loaded tail, on screen and missing this reply.
            vm.showPublishedReply(floor = 43)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(listOf(42L to 5), remote.detailRequests.drop(requestsBefore))
            assertEquals(PendingScroll(page = 5, floor = "#43"), state.pendingScroll)
            assertEquals(5, state.lastLoadedPage)
        }

    /** No floor in the site's answer means nothing to land on; the old whole-window refresh stands. */
    @Test
    fun `a published reply without a floor number falls back to refreshing the window`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            val requestsBefore = remote.detailRequests.size

            vm.showPublishedReply(floor = null)
            advanceUntilIdle()

            assertEquals(listOf(42L to 1), remote.detailRequests.drop(requestsBefore))
            assertNull(vm.uiState.value.pendingScroll)
        }

    /** The pages either side of the slice are what a reader scrolls into, so they join it. */
    @Test
    fun `paging back from a jumped-to page keeps the page it came from`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 2, totalPages = 40)
            }
            val vm = viewModel()
            advanceUntilIdle()
            vm.loadPage(30)
            advanceUntilIdle()

            vm.loadPage(29)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(29, state.firstLoadedPage)
            assertEquals(30, state.lastLoadedPage)
            assertEquals(listOf(29, 29, 30, 30), state.commentPages)
            assertEquals(PendingScroll(page = 29), state.pendingScroll)
        }

    @Test
    fun `jumping to an already loaded page issues no request`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 2, totalPages = 3)
            }
            val vm = viewModel()
            advanceUntilIdle()
            vm.loadNextPage()
            advanceUntilIdle()
            val requestsBefore = remote.detailRequests.size

            vm.loadPage(1)
            advanceUntilIdle()

            assertEquals(requestsBefore, remote.detailRequests.size)
            assertEquals(PendingScroll(page = 1), vm.uiState.value.pendingScroll)
            assertEquals(2, vm.uiState.value.lastLoadedPage)
        }

    /**
     * Regression: a link back to the thread being read — typically the author's signature — pushed a
     * second copy of the screen, so the tap looked inert and Back needed one extra press per tap.
     */
    @Test
    fun `a link to this same thread jumps in place and any other thread is left to navigation`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 2, totalPages = 40)
            }
            val vm = viewModel()
            advanceUntilIdle()

            assertFalse(vm.openLinkInPlace(NodeSeekSite.BASE_URL + "/post-43-1"))
            assertFalse(vm.openLinkInPlace(NodeSeekSite.BASE_URL + "/space/42"))
            assertNull(vm.uiState.value.pendingScroll)

            assertTrue(vm.openLinkInPlace(NodeSeekSite.BASE_URL + "/post-42-30"))
            advanceUntilIdle()

            assertEquals(30, vm.uiState.value.firstLoadedPage)
            assertEquals(PendingScroll(page = 30), vm.uiState.value.pendingScroll)
        }

    /**
     * Regression: the page control sits in the bar at the foot of the list, which is exactly where
     * auto-append runs — so a jump taken from it landed on an in-flight append more often than not,
     * and was dropped without a word. A jump is the reader overriding the append, not queuing behind it.
     */
    @Test
    fun `a jump taken while a page is appending still arrives`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 2, totalPages = 40)
            }
            val vm = viewModel()
            advanceUntilIdle()

            val gate = CompletableDeferred<Unit>()
            remote.gate = gate
            vm.loadNextPage()
            // Let it start and park on the gate, so the jump really does land mid-append.
            advanceUntilIdle()
            assertTrue("expected an append in flight", vm.uiState.value.isAppending)

            remote.gate = null
            vm.loadPage(30)
            advanceUntilIdle()
            gate.complete(Unit)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(30, state.firstLoadedPage)
            assertEquals(30, state.lastLoadedPage)
            assertEquals(PendingScroll(page = 30), state.pendingScroll)
        }

    /**
     * Regression: 上次阅读 went to the far end of what *this* read had loaded, which on a thread just
     * opened is the page already on screen — a button that could not do anything.
     */
    @Test
    fun `the place a previous visit left is offered and returned to`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 2, totalPages = 40)
            }
            val vm = viewModel(readingPositions = FakeReadingPositions(ReadingPosition(page = 12, floor = "#231")))
            advanceUntilIdle()

            assertEquals(ReadingPosition(page = 12, floor = "#231"), vm.uiState.value.resumePosition)

            vm.resumeReading()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(12, state.firstLoadedPage)
            assertEquals(PendingScroll(page = 12, floor = "#231"), state.pendingScroll)
        }

    /**
     * The offer is read once and then held: the thread opens at its top, so a resume offer recomputed
     * from the store would be overwritten with page 1 before the reader could reach for it.
     */
    @Test
    fun `reading on records the new place without moving the offer`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 2, totalPages = 40)
            }
            val positions = FakeReadingPositions(ReadingPosition(page = 12))
            val vm = viewModel(readingPositions = positions)
            advanceUntilIdle()

            vm.recordReadingPosition(3, "#25")
            vm.recordReadingPosition(4, "#31")
            advanceUntilIdle()

            // Debounced: a fling through a page of floors is one write, and it is the last one.
            assertEquals(listOf(ReadingPosition(page = 4, floor = "#31")), positions.writes)
            assertEquals(ReadingPosition(page = 12), vm.uiState.value.resumePosition)
        }

    /** A thread that lost pages between visits still has a last page to come back to. */
    @Test
    fun `a place past the end of a shortened thread lands on its last page`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 2, totalPages = 3)
            }
            val vm = viewModel(readingPositions = FakeReadingPositions(ReadingPosition(page = 12, floor = "#231")))
            advanceUntilIdle()

            vm.resumeReading()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(3, state.lastLoadedPage)
            assertEquals(PendingScroll(page = 3), state.pendingScroll)
        }

    /**
     * A reply notification carries `floor_id` and no page at all, which is why this used to strand the
     * reader on page 1 of a thread they had just been told they were @-ed in.
     */
    @Test
    fun `opening on a floor loads the page that floor lives on`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 10, totalPages = 40)
            }

            val vm = viewModel(initialFloor = "#127")
            advanceUntilIdle()

            // Ten floors a page, #1 opening page 1, so #127 is the seventh floor of page 13.
            assertEquals(listOf(42L to 13), remote.detailRequests)
            assertEquals(13, vm.uiState.value.firstLoadedPage)
            assertEquals(PendingScroll(page = 13, floor = "#127"), vm.uiState.value.pendingScroll)
        }

    /**
     * The gap between "Room has nothing" and "the fetch came back" is where every notification was
     * lost: a thread nobody has cached emits null first, and the branch that clears the screen after a
     * logout was clearing the request to scroll along with it. The test dispatcher normally hides this
     * by finishing the fetch before Room's first emission, so the fetch is held open here — which is
     * what a phone does anyway, for as long as the round trip takes.
     */
    @Test
    fun `the floor survives the empty cache a thread opens in`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 10, totalPages = 40)
            }
            val gate = CompletableDeferred<Unit>()
            remote.gate = gate

            val vm = viewModel(initialFloor = "#127")
            advanceUntilIdle()

            assertEquals(PendingScroll(page = 13, floor = "#127"), vm.uiState.value.pendingScroll)

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(PendingScroll(page = 13, floor = "#127"), vm.uiState.value.pendingScroll)
            assertEquals(13, vm.uiState.value.firstLoadedPage)
        }

    @Test
    fun `a quote pointing at an unloaded floor fetches its page`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 10, totalPages = 40)
            }
            val vm = viewModel()
            advanceUntilIdle()

            vm.jumpToFloor("#35")
            advanceUntilIdle()

            assertEquals(listOf(42L to 1, 42L to 4), remote.detailRequests)
            assertEquals(PendingScroll(page = 4, floor = "#35"), vm.uiState.value.pendingScroll)
        }

    /** A `/post-703863-4` link names its page; opening it at the top throws that away. */
    @Test
    fun `opening a link to a later page starts there`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 2, totalPages = 9)
            }

            val vm = viewModel(initialPage = 4)
            advanceUntilIdle()

            assertEquals(listOf(42L to 4), remote.detailRequests)
            assertEquals(4, vm.uiState.value.firstLoadedPage)
        }

    /** Freshness covers the pages the cache holds; it says nothing about one nobody has fetched. */
    @Test
    fun `a fresh thread is still fetched when the floor is on a page it does not hold`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 10, totalPages = 40)
            }
            repository.refreshThread(postId = 42, page = 1)
            val requestsBefore = remote.detailRequests.size

            val vm = viewModel(initialFloor = "#55")
            advanceUntilIdle()

            assertEquals(listOf(42L to 6), remote.detailRequests.drop(requestsBefore))
            assertEquals(6, vm.uiState.value.firstLoadedPage)
        }

    /**
     * Two taps on a slow connection must send one request. The site would reject the second with
     * "已经进行过加鸡腿操作", but only after it had already taken the first one's chicken leg — and on
     * an unlucky interleaving it would take two.
     */
    @Test
    fun `will not send a second reaction while one is in flight`() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val reactions = GatedReactionRepository(repository, gate)
            val vm = PostDetailViewModel(42, reactions, session)
            advanceUntilIdle()

            vm.react(commentId = 7L, action = ReactionAction.ChickenLeg)
            advanceUntilIdle()
            vm.react(commentId = 7L, action = ReactionAction.ChickenLeg)
            advanceUntilIdle()

            assertEquals(PendingReaction(7L, ReactionAction.ChickenLeg), vm.uiState.value.pendingReaction)
            assertEquals(1, reactions.calls)

            gate.complete(Unit)
            advanceUntilIdle()
            assertNull(vm.uiState.value.pendingReaction)
        }

    /** The site's own sentence is the message worth showing; ours would only paraphrase a 500. */
    @Test
    fun `surfaces the site's refusal and then clears it`() =
        runTest(dispatcher) {
            val reactions =
                GatedReactionRepository(
                    repository,
                    failure = SiteException(SiteError.Unknown, detail = "鸡腿不足"),
                )
            val vm = PostDetailViewModel(42, reactions, session)
            advanceUntilIdle()

            vm.react(commentId = 7L, action = ReactionAction.ChickenLeg)
            advanceUntilIdle()

            assertEquals("鸡腿不足", vm.uiState.value.reactionFailure?.detail)
            assertNull(vm.uiState.value.pendingReaction)

            vm.onReactionFailureShown()
            assertNull(vm.uiState.value.reactionFailure)
        }

    /**
     * Nothing has said which way the toggle points, so "add" would be a guess — and the guess that
     * loses silently un-collects a thread the reader had already saved.
     */
    @Test
    fun `toggling does nothing while the collection state is unknown`() =
        runTest(dispatcher) {
            val collecting = CollectingRepository(repository)
            val vm = PostDetailViewModel(42, collecting, session)
            advanceUntilIdle()
            assertNull(vm.uiState.value.collected)

            vm.toggleCollect()
            advanceUntilIdle()

            assertEquals(0, collecting.calls.size)
        }

    @Test
    fun `toggling sends the opposite of what the page said`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, collected = true, collectionCount = 7)
            }
            val collecting = CollectingRepository(repository)
            val vm = PostDetailViewModel(42, collecting, session)
            advanceUntilIdle()

            vm.toggleCollect()
            advanceUntilIdle()

            assertEquals(listOf(false), collecting.calls)
        }

    /** A second tap while the first is in flight would send the toggle straight back again. */
    @Test
    fun `a toggle in flight refuses a second tap`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, collected = false, collectionCount = 6)
            }
            val gate = CompletableDeferred<Unit>()
            val collecting = CollectingRepository(repository, gate = gate)
            val vm = PostDetailViewModel(42, collecting, session)
            advanceUntilIdle()

            vm.toggleCollect()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.collectPending)

            vm.toggleCollect()
            advanceUntilIdle()
            assertEquals(1, collecting.calls.size)

            gate.complete(Unit)
            advanceUntilIdle()
            assertFalse(vm.uiState.value.collectPending)
        }

    @Test
    fun `a refused toggle keeps the site's sentence and clears the pending flag`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, collected = false, collectionCount = 6)
            }
            val collecting =
                CollectingRepository(
                    repository,
                    failure = SiteException(SiteError.Unknown, detail = "收藏夹已满"),
                )
            val vm = PostDetailViewModel(42, collecting, session)
            advanceUntilIdle()

            vm.toggleCollect()
            advanceUntilIdle()

            assertEquals("收藏夹已满", vm.uiState.value.collectFailure?.detail)
            assertFalse(vm.uiState.value.collectPending)

            vm.onCollectFailureShown()
            assertNull(vm.uiState.value.collectFailure)
        }

    @Test
    fun `fetches the opening post author's level and joined days`() =
        runTest(dispatcher) {
            val profiles =
                object : ProfileRepository {
                    override suspend fun profile(refresh: Boolean): UserProfile = error("unused")

                    override suspend fun profile(uid: Long): UserProfile =
                        UserProfile(
                            uid = uid,
                            name = "op",
                            avatarUrl = "",
                            rank = 1,
                            createdAt = "2026-07-01T00:00:00.000Z",
                        )
                }
            val vm = viewModel(profiles = profiles, clock = AppClock { 1_784_980_800_000L })
            advanceUntilIdle()

            assertEquals(1, vm.uiState.value.authorLevel)
            assertEquals(24, vm.uiState.value.authorJoinedDays)
        }
}

/** The same trick as [GatedReactionRepository] for the star: a real thread, a recorded toggle. */
private class CollectingRepository(
    private val delegate: PostRepository,
    private val gate: CompletableDeferred<Unit>? = null,
    private val failure: Throwable? = null,
) : PostRepository by delegate {
    val calls = mutableListOf<Boolean>()

    override suspend fun setCollected(
        postId: Long,
        collected: Boolean,
    ) {
        calls += collected
        gate?.await()
        failure?.let { throw it }
    }
}

/** Delegates the thread to a real repository and only stands in for the write. */
private class GatedReactionRepository(
    private val delegate: PostRepository,
    private val gate: CompletableDeferred<Unit>? = null,
    private val failure: Throwable? = null,
) : PostRepository by delegate {
    var calls = 0

    override suspend fun react(
        postId: Long,
        commentId: Long,
        action: ReactionAction,
    ) {
        calls++
        gate?.await()
        failure?.let { throw it }
    }
}
