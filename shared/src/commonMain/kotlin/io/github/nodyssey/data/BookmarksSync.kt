package io.github.nodyssey.data

import io.github.nodyssey.data.session.AccountRequestGate
import io.github.plaza.core.AppClock
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One collection sync per account, independent of the navigation entry displaying it. */
class BookmarksSync(
    private val space: UserSpaceRepository,
    private val store: CollectedPostMetaStore,
    private val offline: OfflineLibrary,
    private val clock: AppClock,
    private val scope: CoroutineScope,
    private val requests: AccountRequestGate,
) {
    data class Status(
        val syncing: Boolean = false,
        val hasMore: Boolean = false,
        val error: SiteError? = null,
    )

    private data class Batch(
        val posts: List<SpacePost>,
        val nextPage: Int,
        val lastPageIds: List<Long>,
    )

    private data class Snapshot(
        val epoch: Long,
        val status: Status = Status(),
        val lastSyncedAt: Long? = null,
        val continuation: Batch? = null,
    )

    private val snapshot = MutableStateFlow(Snapshot(requests.epoch))
    val status = combine(requests.epochs, snapshot) { epoch, latest ->
        latest.status.takeIf { latest.epoch == epoch } ?: Status()
    }

    private val mutex = Mutex()
    private var job: Job? = null

    suspend fun refreshIfNeeded() = start(force = false, continueLastBatch = false)

    suspend fun refresh() = start(force = true, continueLastBatch = false)

    suspend fun loadMore() = start(force = true, continueLastBatch = true)

    /** Retire an in-flight snapshot before applying a local collection edit. */
    suspend fun invalidate() {
        mutex.withLock {
            job?.cancelAndJoin()
            val current = snapshot.value
            snapshot.value = Snapshot(
                epoch = requests.epoch,
                status = Status(hasMore = current.epoch == requests.epoch && current.status.hasMore),
            )
        }
    }

    private suspend fun start(force: Boolean, continueLastBatch: Boolean) {
        mutex.withLock {
            if (job?.isActive == true) return@withLock
            val epoch = requests.epoch
            val current = snapshot.value.takeIf { it.epoch == epoch } ?: Snapshot(epoch)
            if (!force && current.lastSyncedAt?.let { clock.nowMillis() - it in 0L until FRESH_FOR_MILLIS } == true) return@withLock
            snapshot.value = current.copy(status = current.status.copy(syncing = true, error = null))
            job = scope.launch {
                try {
                    // Guard the whole walk, including Room commits, not just the individual HTTP
                    // calls: account switching must wait for the last old-account write to stop.
                    requests.run {
                        if (requests.epoch == epoch) {
                            val resume = current.continuation.takeIf { continueLastBatch }
                            runCatchingExceptCancellation { syncBatch(resume) }
                                .onSuccess { next ->
                                    snapshot.value = Snapshot(epoch, Status(hasMore = next != null), clock.nowMillis(), next)
                                }.onFailure { failure ->
                                    val error = (failure as? SiteException)?.error ?: SiteError.Unknown
                                    val latest = snapshot.value
                                    snapshot.value = latest.copy(status = latest.status.copy(syncing = false, error = error), lastSyncedAt = null)
                                }
                        }
                    }
                } finally {
                    val latest = snapshot.value
                    if (latest.epoch == epoch && latest.status.syncing) {
                        snapshot.value = latest.copy(status = latest.status.copy(syncing = false))
                    }
                }
            }
        }
    }

    private suspend fun syncBatch(resume: Batch?): Batch? {
        // The endpoint uses page numbers, not a stable cursor. Recheck the boundary before reusing
        // an offset after a pause, so an insertion or removal near the front cannot skip rows.
        val continuation = resume?.takeIf {
            val boundary = space.collections(it.nextPage - 1)
            boundary.hasNextPage && boundary.items.map(SpacePost::postId) == it.lastPageIds
        }
        val posts = continuation?.posts.orEmpty().toMutableList()
        val seen = posts.mapTo(mutableSetOf(), SpacePost::postId)
        val cold = posts.isEmpty() && store.observeCollection().first().isEmpty()
        val firstPage = continuation?.nextPage ?: 1
        for (index in 0 until PAGES_PER_BATCH) {
            val page = firstPage + index
            val result = space.collections(page)
            // A repeated page (or a list reordered during the walk) is not a trustworthy snapshot.
            if (result.items.any { !seen.add(it.postId) }) throw SiteException(SiteError.Unparsable)
            posts += result.items
            if (result.items.isEmpty() || !result.hasNextPage) {
                store.rememberCollection(posts.map(SpacePost::toKnownMeta))
                noteReplyCounts(posts)
                return null
            }
            val lastInBatch = index == PAGES_PER_BATCH - 1
            if (lastInBatch || (cold && (index == 0 || (index + 1) % COLD_COMMIT_PAGES == 0))) {
                val latest = snapshot.value
                snapshot.value = latest.copy(status = latest.status.copy(hasMore = true))
                store.rememberCollectionPrefix(posts.map(SpacePost::toKnownMeta))
            }
            if (lastInBatch) {
                noteReplyCounts(posts)
                return Batch(posts, page + 1, result.items.map(SpacePost::postId))
            }
        }
        error("A collection batch must have at least one page")
    }

    private suspend fun noteReplyCounts(posts: List<SpacePost>) {
        offline.noteReplyCounts(posts.mapNotNull { post -> post.commentCount?.let { post.postId to it } }.toMap())
    }

    companion object {
        const val PAGES_PER_BATCH = 20
        private const val COLD_COMMIT_PAGES = 5
        private const val FRESH_FOR_MILLIS = 2 * 60 * 1000L
    }
}

private fun SpacePost.toKnownMeta() = CollectedPostMeta(
    postId = postId,
    title = title,
    categoryTitle = categoryTitle,
    categorySlug = categorySlug,
    authorName = authorName,
    commentCount = commentCount,
    createdAtText = createdAtText,
)
