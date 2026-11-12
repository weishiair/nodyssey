package io.github.nodyssey.data

import androidx.paging.testing.asSnapshot
import io.github.nodyssey.data.local.FeedPositionEntity
import io.github.nodyssey.data.local.FeedRemoteKeyEntity
import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.nodyssey.data.local.toEntity
import io.github.nodyssey.model.FeedSort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PinnedFeedTest {
    private lateinit var database: NodeSeekDatabase
    private val remote = FakePostRemoteDataSource()
    private val clock = MutableClock()
    private val showPinned = MutableStateFlow(true)
    private val showBlocked = MutableStateFlow(false)
    private lateinit var repository: OfflineFirstPostRepository

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        repository = OfflineFirstPostRepository(
            database = database,
            remote = remote,
            clock = clock,
            showBlockedContent = showBlocked,
            showHomePinnedPosts = showPinned,
        )
    }

    @After
    fun tearDown() = database.close()

    private suspend fun givenFeed(slug: String? = null, sort: FeedSort = FeedSort.LAST_REPLY) {
        val feedKey = feedKeyFor(slug, sort)
        val posts = (1L..4L).map { id ->
            FakePostRemoteDataSource.summary(slug, 1).copy(
                postId = id,
                title = "post $id",
                isPinned = id <= 2,
                isBlocked = id == 2L,
            )
        }
        database.feedDao().upsertPosts(posts.map { it.toEntity(clock.nowMillis()) })
        database.feedDao().insertPositions(
            posts.mapIndexed { index, post ->
                FeedPositionEntity(feedKey, post.postId, index, page = index / 2 + 1)
            },
        )
        database.feedDao().upsertRemoteKey(
            FeedRemoteKeyEntity(feedKey, nextPage = null, refreshedAtMillis = clock.nowMillis(), totalPages = 2),
        )
    }

    // Filtering only in Compose would leave Paging's row count and its open stream unchanged.
    @Test
    fun `toggling updates an open feed and restores cached pins without fetching`() = runTest {
        givenFeed()
        val feed = repository.feed(null, FeedSort.LAST_REPLY).produceIn(backgroundScope)
        assertEquals(listOf(1L, 3L, 4L), flowOf(feed.receive()).asSnapshot().map { it.summary.postId })

        showPinned.value = false
        assertEquals(listOf(3L, 4L), flowOf(feed.receive()).asSnapshot().map { it.summary.postId })

        showPinned.value = true
        assertEquals(listOf(1L, 3L, 4L), flowOf(feed.receive()).asSnapshot().map { it.summary.postId })
        assertEquals(emptyList<Pair<String?, Int>>(), remote.listRequests)
    }

    @Test
    fun `both sort orders and board tabs hide pins even when blocked posts are revealed`() = runTest {
        showPinned.value = false
        showBlocked.value = true
        for (slug in listOf(null, "tech")) {
            for (sort in FeedSort.entries) {
                givenFeed(slug, sort)
                assertEquals(listOf(3L, 4L), repository.feed(slug, sort).asSnapshot().map { it.summary.postId })
            }
        }
    }

    // Counting hidden pins would send a page jump two rows past the start of page two.
    @Test
    fun `page navigation counts the same visible rows as the feed`() = runTest {
        givenFeed()
        assertEquals(1, repository.feedRowIndexOfPage(null, FeedSort.LAST_REPLY, 2))

        showPinned.value = false
        assertEquals(0, repository.feedRowIndexOfPage(null, FeedSort.LAST_REPLY, 2))
        assertNull(repository.feedRowIndexOfPage(null, FeedSort.LAST_REPLY, 1))

        showPinned.value = true
        showBlocked.value = true
        assertEquals(2, repository.feedRowIndexOfPage(null, FeedSort.LAST_REPLY, 2))
    }

    // Applying the home preference to the shared pager helper would also remove search hits.
    @Test
    fun `hiding home pins leaves local and remote search results available`() = runTest {
        givenFeed()
        showPinned.value = false
        assertEquals(listOf(1L, 3L, 4L), repository.search("post").first().map { it.summary.postId }.sorted())

        remote.searchResult = { request ->
            FakePostRemoteDataSource.page(null, request.page, firstId = 10, count = 2, hasNextPage = false)
                .let { page -> page.copy(posts = page.posts.map { it.copy(isPinned = true) }) }
        }
        assertEquals(listOf(10L, 11L), repository.searchFeed("post", null, FeedSort.LAST_REPLY).asSnapshot().map { it.summary.postId })
    }
}
