package io.github.nodyssey.ui.bookmarks

import io.github.nodyssey.data.BookmarksSync
import io.github.nodyssey.data.CollectedPostMeta
import io.github.nodyssey.data.CollectedPostMetaStore
import io.github.nodyssey.data.NoOpPostRepository
import io.github.nodyssey.data.OfflineLibrary
import io.github.nodyssey.data.OfflineSettings
import io.github.nodyssey.data.OfflineState
import io.github.nodyssey.data.OfflineUsage
import io.github.nodyssey.data.SpaceComment
import io.github.nodyssey.data.SpacePage
import io.github.nodyssey.data.SpacePost
import io.github.nodyssey.data.UserSpaceRepository
import io.github.nodyssey.data.session.AccountRequestGate
import io.github.plaza.core.AppClock
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 收藏's view model, whose whole reason to exist is that it does *not* page.
 *
 * 「全部 12 / 已下载 5」, 全选 and 「全部下载 · 7 篇」 are statements about the whole collection, so the
 * walk to the end of the endpoint is the behaviour under test — along with the bound on it, and the
 * optimistic removal that the screen offers no confirmation for.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookmarksViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val space = FakeSpaceRepository()
    private val posts = FakePostRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val known = FakeCollectedPostMetaStore()

    private fun TestScope.synchronizer(
        offline: OfflineLibrary = FakeOfflineLibrary(),
        gate: AccountRequestGate = AccountRequestGate(),
    ) = BookmarksSync(space, known, offline, AppClock { testScheduler.currentTime }, this, gate)

    private fun TestScope.viewModel(offline: OfflineLibrary = FakeOfflineLibrary()) =
        BookmarksViewModel(synchronizer(offline), posts, offline, known)

    @Test
    fun `the whole collection is loaded, not one page of it`() =
        runTest(dispatcher) {
            space.pages = listOf(page(1, 1..3, hasNext = true), page(2, 4..5, hasNext = false))
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(5, viewModel.uiState.value.entries.size)
            assertEquals(listOf(1, 2), space.requested)
            assertEquals(false, viewModel.uiState.value.truncated)
        }

    /**
     * The collection payload carries no author at all, so the avatar can only come from memory.
     *
     * The stored URL wins over the one built from the uid, because that is the address an offline
     * download stored a file under and therefore the one that resolves with the network off.
     */
    @Test
    fun `the avatar comes from what was remembered, page URL first`() =
        runTest(dispatcher) {
            known.remember(
                listOf(
                    CollectedPostMeta(postId = 1, authorUid = 77, avatarUrl = "https://ns/uploaded.png"),
                    CollectedPostMeta(postId = 2, authorUid = 88),
                ),
            )
            space.pages = listOf(page(1, 1..2, hasNext = false, row = ::bare))
            val viewModel = viewModel()
            advanceUntilIdle()

            val rows = viewModel.uiState.value.entries
            assertEquals("https://ns/uploaded.png", rows[0].avatarUrl)
            assertTrue(rows[1].avatarUrl.orEmpty().endsWith("/avatar/88.png"))
        }

    /**
     * A download backfills the row, and the list must pick it up without being reloaded.
     *
     * This is the route that reaches a collection made on the web years ago: nothing on the device
     * has ever seen the thread, and the pages the download fetches are what finally name it.
     */
    @Test
    fun `a row fills in as soon as something learns more about the thread`() =
        runTest(dispatcher) {
            space.pages = listOf(page(1, 1..1, hasNext = false, row = ::bare))
            val viewModel = viewModel()
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.entries.single().authorName)

            known.remember(CollectedPostMeta(postId = 1, authorName = "原作者"))
            advanceUntilIdle()

            assertEquals("原作者", viewModel.uiState.value.entries.single().authorName)
        }

    /** An empty page ends the walk even when the payload claims another — see the loader's comment. */
    @Test
    fun `an empty page ends the walk regardless of what hasNextPage says`() =
        runTest(dispatcher) {
            space.pages = listOf(page(1, 1..2, hasNext = true), page(2, IntRange.EMPTY, hasNext = true))
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.entries.size)
            assertEquals(listOf(1, 2), space.requested)
        }

    @Test
    fun `reopening while page two waits shares the walk and shows the first page`() =
        runTest(dispatcher) {
            val secondPage = CompletableDeferred<Unit>()
            space.beforePage = { if (it == 2) secondPage.await() }
            space.pages = listOf(page(1, 1..2, hasNext = true), page(2, 3..4, hasNext = false))
            val sync = synchronizer()
            val offline = FakeOfflineLibrary()
            BookmarksViewModel(sync, posts, offline, known)
            runCurrent()
            val reopened = BookmarksViewModel(sync, posts, offline, known)
            runCurrent()

            assertEquals(listOf(1L, 2L), reopened.uiState.value.entries.map { it.postId })
            assertEquals(false, reopened.uiState.value.isLoading)
            assertTrue(reopened.uiState.value.isSyncing)
            assertEquals(listOf(1, 2), space.requested)
            secondPage.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf(1L, 2L, 3L, 4L), reopened.uiState.value.entries.map { it.postId })
        }

    @Test
    fun `a recent sync is reused on reentry but manual refresh and collection changes bypass it`() =
        runTest(dispatcher) {
            val sync = synchronizer()
            val offline = FakeOfflineLibrary()
            space.pages = listOf(page(1, 1..1, hasNext = false))
            BookmarksViewModel(sync, posts, offline, known)
            advanceUntilIdle()

            space.pages = listOf(page(1, 2..2, hasNext = false))
            val reopened = BookmarksViewModel(sync, posts, offline, known)
            advanceUntilIdle()
            assertEquals(listOf(1), space.requested)
            assertEquals(1L, reopened.uiState.value.entries.single().postId)

            reopened.refresh()
            advanceUntilIdle()
            assertEquals(2L, reopened.uiState.value.entries.single().postId)

            sync.invalidate()
            space.pages = listOf(page(1, 3..3, hasNext = false))
            reopened.refreshIfNeeded()
            advanceUntilIdle()
            assertEquals(3L, reopened.uiState.value.entries.single().postId)
            assertEquals(listOf(1, 1, 1), space.requested)
        }

    @Test
    fun `switching accounts cancels the old snapshot and does not carry freshness over`() =
        runTest(dispatcher) {
            val gate = AccountRequestGate()
            val sync = synchronizer(gate = gate)
            val offline = FakeOfflineLibrary()
            sync.refreshIfNeeded()
            advanceUntilIdle()
            val secondPage = CompletableDeferred<Unit>()
            space.beforePage = { if (it == 2) secondPage.await() }
            space.pages = listOf(page(1, 1..1, true), page(2, 2..2, false))
            sync.refresh()
            runCurrent()
            assertEquals(listOf(1, 1, 2), space.requested)

            gate.pause()
            known.relist(emptyList())
            space.pages = listOf(page(1, 3..3, false))
            gate.resume()
            secondPage.complete(Unit)
            val current = BookmarksViewModel(sync, posts, offline, known)
            advanceUntilIdle()

            assertEquals(listOf(1, 1, 2, 1), space.requested)
            assertEquals(listOf(3L), current.uiState.value.entries.map { it.postId })
        }

    @Test
    fun `a capped batch preserves the cached tail and continue reaches the real end`() =
        runTest(dispatcher) {
            known.rememberCollection(listOf(CollectedPostMeta(postId = 99, title = "cached")))
            val limit = BookmarksSync.PAGES_PER_BATCH
            space.pages = (1..limit + 1).map { page(it, it..it, hasNext = it <= limit) }
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals((1..limit).toList(), space.requested)
            assertEquals((1..limit).map(Int::toLong) + 99L, viewModel.uiState.value.entries.map { it.postId })
            assertTrue(viewModel.uiState.value.truncated)

            viewModel.loadMore()
            advanceUntilIdle()
            assertEquals(listOf(limit, limit + 1), space.requested.takeLast(2))
            assertEquals((1..limit + 1).map(Int::toLong), viewModel.uiState.value.entries.map { it.postId })
            assertEquals(false, viewModel.uiState.value.truncated)
        }

    @Test
    fun `continuing restarts if a new collection changed the page boundary`() =
        runTest(dispatcher) {
            val limit = BookmarksSync.PAGES_PER_BATCH
            space.pages = (1..limit + 1).map { page(it, it..it, hasNext = it <= limit) }
            val viewModel = viewModel()
            advanceUntilIdle()
            space.pages = (1..limit + 2).map { page(it, it - 1..it - 1, hasNext = it <= limit + 1) }
            space.requested.clear()

            viewModel.loadMore()
            advanceUntilIdle()
            assertEquals(listOf(limit) + (1..limit), space.requested)
            assertEquals(0L, viewModel.uiState.value.entries.first().postId)
            assertTrue(viewModel.uiState.value.truncated)
        }

    @Test
    fun `a failed load reports the site's reason rather than an empty collection`() =
        runTest(dispatcher) {
            space.failure = SiteException(SiteError.LoginRequired)
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(SiteError.LoginRequired, viewModel.uiState.value.error)
            assertEquals(false, viewModel.uiState.value.isLoading)
        }

    /**
     * The reason this screen reads from disk at all.
     *
     * 收藏 is the only way into a downloaded thread, so a version of it that could only be drawn
     * from a fresh response made the download feature useless in the one situation it exists for.
     */
    @Test
    fun `a failed load keeps the stored list on screen and says it is stale`() =
        runTest(dispatcher) {
            known.rememberCollection(
                listOf(
                    CollectedPostMeta(postId = 1, title = "上次同步到的一篇"),
                    CollectedPostMeta(postId = 2, title = "还有一篇"),
                ),
            )
            space.failure = SiteException(SiteError.Network)
            val viewModel = viewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf(1L, 2L), state.entries.map { it.postId })
            assertEquals(SiteError.Network, state.error)
            assertTrue(state.isStale)
            // The full-screen error is for a device that has nothing, and this one has two.
            assertEquals(false, state.isLoading)
        }

    @Test
    fun `filters and counts run over the whole collection`() =
        runTest(dispatcher) {
            space.pages = listOf(page(1, 1..4, hasNext = false))
            val offline =
                FakeOfflineLibrary(
                    states =
                    mapOf(
                        1L to OfflineState.Downloaded(bytes = 100),
                        2L to OfflineState.Stale(behindReplies = 3, bytes = 100),
                        3L to OfflineState.Failed(io.github.nodyssey.data.OfflineFailure.Network),
                    ),
                )
            val viewModel = viewModel(offline)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(1, state.downloadedCount)
            assertEquals(1, state.newReplyCount)
            // 未下载 (#4) plus 失败 (#3): both are threads with nothing stored.
            assertEquals(2, state.pendingDownloadCount)

            viewModel.setFilter(BookmarkFilter.DOWNLOADED)
            // 已下载 includes the stale copy: it is stored, it is merely behind.
            assertEquals(listOf(1L, 2L), viewModel.uiState.value.visible.map { it.postId })
        }

    @Test
    fun `search looks at titles and authors and nothing else`() =
        runTest(dispatcher) {
            space.pages = listOf(page(1, 1..3, hasNext = false))
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.setSearching(true)
            viewModel.setQuery("帖子 2")
            assertEquals(listOf(2L), viewModel.uiState.value.visible.map { it.postId })

            viewModel.setQuery("技术")
            assertEquals(emptyList<Long>(), viewModel.uiState.value.visible.map { it.postId })
        }

    /** 全选 is a toggle; a second tap on a full selection has to clear it, not re-select it. */
    @Test
    fun `select-all toggles`() =
        runTest(dispatcher) {
            space.pages = listOf(page(1, 1..3, hasNext = false))
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.startSelection(1L)
            viewModel.toggleSelectAll()
            assertEquals(setOf(1L, 2L, 3L), viewModel.uiState.value.selection)
            assertTrue(viewModel.uiState.value.allVisibleSelected)

            viewModel.toggleSelectAll()
            assertEquals(emptySet<Long>(), viewModel.uiState.value.selection)
        }

    /** 全选 selects what is on screen. A filter is a claim about what the reader means by "all". */
    @Test
    fun `select-all under a filter selects only what the filter shows`() =
        runTest(dispatcher) {
            space.pages = listOf(page(1, 1..3, hasNext = false))
            val offline =
                FakeOfflineLibrary(
                    states =
                    mapOf(
                        2L to OfflineState.Downloaded(bytes = 100),
                        3L to OfflineState.Downloaded(bytes = 100),
                    ),
                )
            val viewModel = viewModel(offline)
            advanceUntilIdle()

            viewModel.setFilter(BookmarkFilter.DOWNLOADED)
            viewModel.startSelection(2L)
            viewModel.toggleSelectAll()

            // #1 is in the collection and not in the selection: it is not on screen.
            assertEquals(setOf(2L, 3L), viewModel.uiState.value.selection)
        }

    @Test
    fun `removal takes the rows out before the site answers`() =
        runTest(dispatcher) {
            space.pages = listOf(page(1, 1..3, hasNext = false))
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.startSelection(1L)
            viewModel.toggleSelection(3L)
            var removed: List<BookmarkEntry>? = null
            var failure: SiteError? = null
            viewModel.removeSelected { entries, error ->
                removed = entries
                failure = error
            }
            advanceUntilIdle()

            assertEquals(listOf(2L), viewModel.uiState.value.entries.map { it.postId })
            assertEquals(listOf(1L to false, 3L to false), posts.collectionWrites)
            assertEquals(listOf(1L, 3L), removed?.map { it.postId })
            assertNull(failure)
            assertNull(viewModel.uiState.value.selection)
        }

    /** A refusal has to put the rows back — the screen removed them without asking anyone. */
    @Test
    fun `a refused removal reloads and reports why`() =
        runTest(dispatcher) {
            space.pages = listOf(page(1, 1..3, hasNext = false))
            posts.collectionFailure = SiteException(SiteError.LoginRequired)
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.startSelection(1L)
            var failure: SiteError? = null
            viewModel.removeSelected { _, error -> failure = error }
            advanceUntilIdle()

            assertEquals(SiteError.LoginRequired, failure)
            assertEquals(listOf(1L, 2L, 3L), viewModel.uiState.value.entries.map { it.postId })
        }

    /**
     * The removal is written to disk now, so the reload that used to undo a refusal is not enough.
     *
     * With no network there is nothing to reload *from*, and a row left off the stored list would
     * be gone from 收藏 on this device while the site still has it — until some later day when the
     * network came back, which is exactly when nobody would connect the two.
     */
    @Test
    fun `a removal refused with no network is undone on this device too`() =
        runTest(dispatcher) {
            space.pages = listOf(page(1, 1..3, hasNext = false))
            val viewModel = viewModel()
            advanceUntilIdle()

            posts.collectionFailure = SiteException(SiteError.Network)
            space.failure = SiteException(SiteError.Network)
            viewModel.startSelection(2L)
            viewModel.removeSelected { _, _ -> }
            advanceUntilIdle()

            assertEquals(listOf(1L, 2L, 3L), known.observeCollection().first().map { it.postId })
            assertEquals(listOf(1L, 2L, 3L), viewModel.uiState.value.entries.map { it.postId })
        }

    @Test
    fun `download-all queues everything with nothing stored, and only that`() =
        runTest(dispatcher) {
            space.pages = listOf(page(1, 1..4, hasNext = false))
            val offline =
                FakeOfflineLibrary(
                    states =
                    mapOf(
                        1L to OfflineState.Downloaded(bytes = 100),
                        2L to OfflineState.Downloading(progress = 0.5f),
                        3L to OfflineState.Failed(io.github.nodyssey.data.OfflineFailure.Network),
                    ),
                )
            val viewModel = viewModel(offline)
            advanceUntilIdle()

            viewModel.downloadPending()
            advanceUntilIdle()

            assertEquals(listOf(3L, 4L), offline.queued.flatten())
        }

    // --- fakes ------------------------------------------------------------------------------------

    /**
     * @param row shapes each generated row — used to reproduce what `list-collection` actually
     * answers with, which is a title and, for most of these fields, nothing.
     */
    private fun page(
        page: Int,
        ids: IntRange,
        hasNext: Boolean,
        row: (SpacePost) -> SpacePost = { it },
    ) = SpacePage(
        items =
        ids.map {
            row(
                SpacePost(
                    postId = it.toLong(),
                    title = "帖子 $it",
                    categoryTitle = "日常",
                    categorySlug = "daily",
                    authorName = "作者$it",
                    commentCount = it,
                    viewCount = null,
                    createdAtText = "上周",
                ),
            )
        },
        page = page,
        hasNextPage = hasNext,
    )

    /** What the collection endpoint is observed to answer with: a title, and nothing else. */
    private fun bare(post: SpacePost) =
        post.copy(
            categoryTitle = null,
            categorySlug = null,
            authorName = null,
            commentCount = null,
            createdAtText = null,
        )

    private class FakeSpaceRepository : UserSpaceRepository {
        var pages: List<SpacePage<SpacePost>> = emptyList()
        var failure: Throwable? = null
        val requested = mutableListOf<Int>()
        var beforePage: suspend (Int) -> Unit = {}

        override suspend fun collections(page: Int): SpacePage<SpacePost> {
            failure?.let { throw it }
            requested += page
            beforePage(page)
            return pages.getOrElse(page - 1) { SpacePage(emptyList(), page, false) }
        }

        override suspend fun topics(uid: Long, page: Int) = throw UnsupportedOperationException()

        override suspend fun comments(uid: Long, page: Int): SpacePage<SpaceComment> =
            throw UnsupportedOperationException()
    }

    /** Records the collection writes and can be told to refuse them. */
    private class FakePostRepository : NoOpPostRepository() {
        val collectionWrites = mutableListOf<Pair<Long, Boolean>>()
        var collectionFailure: Throwable? = null

        override suspend fun setCollected(
            postId: Long,
            collected: Boolean,
        ) {
            collectionFailure?.let { throw it }
            collectionWrites += postId to collected
        }
    }

    /** In-memory, with the same fill-the-gaps rule and the same list marks the Room store applies. */
    private class FakeCollectedPostMetaStore : CollectedPostMetaStore {
        private val rows = MutableStateFlow(emptyMap<Long, CollectedPostMeta>())

        /** The membership mark, kept beside the rows exactly as `listedOrder` is beside them. */
        private val listed = MutableStateFlow(emptyList<Long>())

        override fun observe(): Flow<Map<Long, CollectedPostMeta>> = rows

        override fun observeCollection(): Flow<List<CollectedPostMeta>> =
            combine(rows, listed) { stored, order -> order.mapNotNull(stored::get) }

        override suspend fun rememberCollection(metas: List<CollectedPostMeta>) {
            remember(metas)
            listed.value = metas.map { it.postId }
        }

        override suspend fun rememberCollectionPrefix(metas: List<CollectedPostMeta>) {
            remember(metas)
            val prefix = metas.map { it.postId }.distinct()
            val prefixIds = prefix.toSet()
            listed.update { order -> prefix + order.filterNot { it in prefixIds } }
        }

        override suspend fun forget(postIds: Collection<Long>) {
            listed.update { order -> order.filterNot { it in postIds } }
        }

        override suspend fun relist(postIds: List<Long>) {
            listed.value = postIds
        }

        override suspend fun remember(metas: List<CollectedPostMeta>) {
            rows.update { stored ->
                stored +
                    metas.associate { fresh ->
                        val old = stored[fresh.postId]
                        fresh.postId to
                            CollectedPostMeta(
                                postId = fresh.postId,
                                title = fresh.title ?: old?.title,
                                categoryTitle = fresh.categoryTitle ?: old?.categoryTitle,
                                categorySlug = fresh.categorySlug ?: old?.categorySlug,
                                authorName = fresh.authorName ?: old?.authorName,
                                avatarUrl = fresh.avatarUrl ?: old?.avatarUrl,
                                authorUid = fresh.authorUid ?: old?.authorUid,
                                commentCount = fresh.commentCount ?: old?.commentCount,
                                createdAtText = fresh.createdAtText ?: old?.createdAtText,
                            )
                    }
            }
        }
    }

    private class FakeOfflineLibrary(
        states: Map<Long, OfflineState> = emptyMap(),
        override val isAvailable: Boolean = true,
    ) : OfflineLibrary {
        val queued = mutableListOf<List<Long>>()
        override val states: Flow<Map<Long, OfflineState>> = MutableStateFlow(states)
        override val usage: Flow<OfflineUsage> = MutableStateFlow(OfflineUsage())
        override val settings: Flow<OfflineSettings> = MutableStateFlow(OfflineSettings())

        val notedCounts = mutableListOf<Map<Long, Int>>()

        override suspend fun download(postIds: Collection<Long>) {
            queued += postIds.toList()
        }

        override suspend fun noteReplyCounts(counts: Map<Long, Int>) {
            notedCounts += counts
        }

        override suspend fun estimateBytes(postIds: Collection<Long>): Long? = null

        override suspend fun cancel(postId: Long) = Unit

        override suspend fun clearAll() = Unit

        override suspend fun updateSettings(settings: OfflineSettings) = Unit
    }
}
