package io.github.nodyssey.ui.bookmarks

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.BookmarksSync
import io.github.nodyssey.data.CollectedPostMeta
import io.github.nodyssey.data.CollectedPostMetaStore
import io.github.nodyssey.data.OfflineLibrary
import io.github.nodyssey.data.OfflineSettings
import io.github.nodyssey.data.OfflineState
import io.github.nodyssey.data.OfflineUsage
import io.github.nodyssey.data.PostRepository
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.postlist.toSiteError
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One row of 收藏: the thread as the site lists it, plus what this device has of it. */
@Immutable
data class BookmarkEntry(
    val postId: Long,
    val title: String,
    val categoryTitle: String?,
    val categorySlug: String?,
    val authorName: String?,
    /** Resolved from what this device remembers; the collection payload carries no author at all. */
    val avatarUrl: String? = null,
    val commentCount: Int?,
    val createdAtText: String?,
    val offline: OfflineState = OfflineState.NotDownloaded,
)

/** The three filter chips. Counts come from the whole list, which is why the list is loaded whole. */
enum class BookmarkFilter {
    ALL,
    DOWNLOADED,
    NEW_REPLIES,
}

/**
 * What ⇅ offers.
 *
 * All three are local orderings of an already-loaded list — the collection endpoint takes no sort
 * parameter, so anything else here would be a control that reorders the visible page and silently
 * lies about the rest.
 */
enum class BookmarkSort {
    /** The order the site returned, which is collection order. */
    SITE,
    REPLIES,

    /** Everything still to download first — the order you want when you are about to leave Wi-Fi. */
    PENDING_FIRST,
}

@Immutable
data class BookmarksUiState(
    val entries: List<BookmarkEntry> = emptyList(),
    /** True while a walk of the endpoint is in flight, whatever is already on screen. */
    val isSyncing: Boolean = true,
    /** Why the last walk failed, or null. Non-null *with* [entries] is the offline case, not a dead end. */
    val error: SiteError? = null,
    /** True when the latest batch stopped before the end of the site's list. */
    val truncated: Boolean = false,
    val filter: BookmarkFilter = BookmarkFilter.ALL,
    val sort: BookmarkSort = BookmarkSort.SITE,
    /**
     * 搜索 over the loaded list. Null is "not searching", which is a different screen from an empty
     * query — the latter has the field open and shows everything.
     */
    val query: String? = null,
    /** Non-null only in multi-select; empty-but-non-null is multi-select with nothing ticked. */
    val selection: Set<Long>? = null,
    val offlineAvailable: Boolean = false,
    /** What the selection would cost to download, when [OfflineLibrary.estimateBytes] can say. */
    val selectionEstimateBytes: Long? = null,
    val usage: OfflineUsage = OfflineUsage(),
    val offlineSettings: OfflineSettings = OfflineSettings(),
) {
    /**
     * The full-screen spinner, which is only honest while there is nothing at all to show.
     *
     * The list comes off disk, so the ordinary trip into 收藏 has rows before the first request
     * finishes and never sees this. What is left for it is the first ever visit on this device, and
     * the moment before the stored list arrives.
     */
    val isLoading: Boolean get() = isSyncing && entries.isEmpty()

    /**
     * True when the rows on screen are the stored list and the site could not be asked for a newer
     * one — which is what the screen says out loud rather than replacing the list with the failure.
     */
    val isStale: Boolean get() = error != null && entries.isNotEmpty()

    val inSelection: Boolean get() = selection != null

    val downloadedCount: Int get() = entries.count { it.offline is OfflineState.Downloaded }

    val newReplyCount: Int get() = entries.count { it.offline is OfflineState.Stale }

    /** What 「全部下载 · N 篇」 counts: everything not already stored, failures included. */
    val pendingDownloadCount: Int
        get() =
            entries.count {
                it.offline is OfflineState.NotDownloaded || it.offline is OfflineState.Failed
            }

    val isSearching: Boolean get() = query != null

    val visible: List<BookmarkEntry>
        get() {
            val matched =
                query?.trim()?.takeIf { it.isNotEmpty() }?.let { needle ->
                    entries.filter {
                        it.title.contains(needle, ignoreCase = true) ||
                            it.authorName?.contains(needle, ignoreCase = true) == true
                    }
                } ?: entries
            val filtered =
                when (filter) {
                    BookmarkFilter.ALL -> matched

                    BookmarkFilter.DOWNLOADED ->
                        matched.filter {
                            it.offline is OfflineState.Downloaded || it.offline is OfflineState.Stale
                        }

                    BookmarkFilter.NEW_REPLIES -> matched.filter { it.offline is OfflineState.Stale }
                }
            return when (sort) {
                BookmarkSort.SITE -> filtered
                BookmarkSort.REPLIES -> filtered.sortedByDescending { it.commentCount ?: -1 }
                BookmarkSort.PENDING_FIRST -> filtered.sortedBy { it.offline.downloadRank }
            }
        }

    /** Whether 全选 has nothing left to add — which is when it becomes 取消全选. */
    val allVisibleSelected: Boolean
        get() = visible.isNotEmpty() && selection?.containsAll(visible.map { it.postId }) == true

    val selected: List<BookmarkEntry>
        get() = selection?.let { ids -> entries.filter { it.postId in ids } }.orEmpty()
}

/** Undownloaded first, then failures, then in flight, then what is already here. */
private val OfflineState.downloadRank: Int
    get() =
        when (this) {
            is OfflineState.NotDownloaded -> 0
            is OfflineState.Failed -> 1
            is OfflineState.Stale -> 2
            is OfflineState.Downloading -> 3
            is OfflineState.Downloaded -> 4
        }

/** Draws the current collection from disk while [BookmarksSync] updates it in the background. */
class BookmarksViewModel(
    private val sync: BookmarksSync,
    private val postRepository: PostRepository,
    private val offline: OfflineLibrary,
    private val collectedMeta: CollectedPostMetaStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BookmarksUiState(offlineAvailable = offline.isAvailable))
    val uiState: StateFlow<BookmarksUiState> = _uiState.asStateFlow()

    private var estimateJob: Job? = null

    init {
        // Two sources, rebuilt rather than folded together: the stored collection and what this
        // device has downloaded of it change independently, and a row patched in place would end up
        // depending on which of the two happened to land last.
        viewModelScope.launch {
            sync.refreshIfNeeded()
            combine(collectedMeta.observeCollection(), offline.states, sync.status) { collection, states, status ->
                val entries = collection.map { meta ->
                    meta.toEntry(offline = states[meta.postId] ?: OfflineState.NotDownloaded)
                }
                entries to status
            }.collect { (entries, status) ->
                _uiState.update { state ->
                    state.copy(
                        entries = entries,
                        isSyncing = status.syncing,
                        error = status.error,
                        truncated = status.hasMore,
                        selection = state.selection?.intersect(entries.map { it.postId }.toSet()),
                    )
                }
            }
        }
        viewModelScope.launch {
            combine(offline.usage, offline.settings, ::Pair).collect { (usage, settings) ->
                _uiState.update { it.copy(usage = usage, offlineSettings = settings) }
            }
        }
    }

    fun refreshIfNeeded() {
        viewModelScope.launch { sync.refreshIfNeeded() }
    }

    fun refresh() {
        viewModelScope.launch { sync.refresh() }
    }

    fun loadMore() {
        viewModelScope.launch { sync.loadMore() }
    }

    fun setFilter(filter: BookmarkFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun setSort(sort: BookmarkSort) {
        _uiState.update { it.copy(sort = sort) }
    }

    /**
     * Opens or closes 搜索.
     *
     * Local, over the list already in memory — which is only honest because the list *is* all of it.
     * The site has no endpoint that searches a collection, and a search box that quietly only looked
     * at the loaded page would be worse than none.
     */
    fun setSearching(searching: Boolean) {
        _uiState.update { it.copy(query = if (searching) it.query.orEmpty() else null) }
    }

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    // --- multi-select -------------------------------------------------------------------------

    fun startSelection(postId: Long) {
        _uiState.update { it.copy(selection = setOf(postId)) }
        estimateSelection()
    }

    /** Multi-select with nothing ticked — the bar's ☑, for a reader who has not found the long-press. */
    fun enterSelection() {
        _uiState.update { it.copy(selection = it.selection ?: emptySet()) }
        estimateSelection()
    }

    fun toggleSelection(postId: Long) {
        _uiState.update { state ->
            val current = state.selection ?: return@update state
            state.copy(selection = if (postId in current) current - postId else current + postId)
        }
        estimateSelection()
    }

    /** 全选 toggles: a tap when everything visible is already ticked clears it instead. */
    fun toggleSelectAll() {
        _uiState.update { state ->
            if (state.selection == null) return@update state
            val visible = state.visible.map { it.postId }.toSet()
            state.copy(selection = if (state.selection.containsAll(visible)) emptySet() else visible)
        }
        estimateSelection()
    }

    fun clearSelection() {
        _uiState.update { it.copy(selection = null, selectionEstimateBytes = null) }
    }

    /**
     * Re-asks what the current selection would weigh.
     *
     * Cancelling the previous ask is the point: ticking six rows in a row otherwise leaves six
     * answers racing, and the one that lands last is not necessarily the one for the last selection.
     */
    private fun estimateSelection() {
        estimateJob?.cancel()
        val ids = _uiState.value.selection
        if (ids.isNullOrEmpty()) {
            _uiState.update { it.copy(selectionEstimateBytes = null) }
            return
        }
        estimateJob =
            viewModelScope.launch {
                val bytes = runCatchingExceptCancellation { offline.estimateBytes(ids) }.getOrNull()
                _uiState.update { if (it.selection == ids) it.copy(selectionEstimateBytes = bytes) else it }
            }
    }

    // --- writes -------------------------------------------------------------------------------

    /**
     * Takes the selected threads out of the collection, optimistically.
     *
     * Optimistic because the alternative is a list that sits still for a round trip after an action
     * the design gives no confirmation to — and [restore] puts back exactly what failed, so a refusal
     * costs a flicker rather than a wrong list.
     */
    fun removeSelected(onDone: (removed: List<BookmarkEntry>, failed: SiteError?) -> Unit) {
        val target = _uiState.value.selected
        if (target.isEmpty()) return
        // The list as it stands, kept for the refusal below — the removal is about to change it.
        val before = _uiState.value.entries.map { it.postId }
        viewModelScope.launch {
            sync.invalidate()
            val ids = target.map { it.postId }.toSet()
            // Straight into the store, because that is what the list is drawn from now. Only the
            // list marks come off; the details stay, so 撤销 does not have to carry them back.
            collectedMeta.forget(ids)
            _uiState.update { it.copy(selection = null) }
            val failure =
                target.firstNotNullOfOrNull { entry ->
                    runCatchingExceptCancellation { postRepository.setCollected(entry.postId, collected = false) }
                        .exceptionOrNull()
                        ?.toSiteError()
                }
            if (failure != null) {
                // Put the rows back here as well as asking for the list again. The reload is the
                // better answer when the site can be reached at all, and this is the one that works
                // when the refusal *was* that it could not be — where a removal left on disk would
                // otherwise outlive the screen, the session and the reader's memory of doing it.
                collectedMeta.relist(before)
                sync.refresh()
            }
            onDone(target, failure)
        }
    }

    /** 撤销 for [removeSelected]. */
    fun restore(entries: List<BookmarkEntry>) {
        viewModelScope.launch {
            entries.forEach { entry ->
                runCatchingExceptCancellation { postRepository.setCollected(entry.postId, collected = true) }
            }
            sync.refresh()
        }
    }

    fun downloadSelected() {
        val ids = _uiState.value.selection.orEmpty()
        if (ids.isEmpty()) return
        _uiState.update { it.copy(selection = null) }
        viewModelScope.launch { offline.download(ids) }
    }

    /** 全部下载: everything not already stored. Threads mid-download are left alone. */
    fun downloadPending() {
        val ids =
            _uiState.value.entries
                .filter { it.offline is OfflineState.NotDownloaded || it.offline is OfflineState.Failed }
                .map { it.postId }
        if (ids.isEmpty()) return
        viewModelScope.launch { offline.download(ids) }
    }

    /** The per-row download column: download, retry, catch up on replies — all the same request. */
    fun download(postId: Long) {
        viewModelScope.launch { offline.download(listOf(postId)) }
    }

    fun cancelDownload(postId: Long) {
        viewModelScope.launch { offline.cancel(postId) }
    }

    fun updateOfflineSettings(settings: OfflineSettings) {
        viewModelScope.launch { offline.updateSettings(settings) }
    }

    fun clearOffline() {
        viewModelScope.launch { offline.clearAll() }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    BookmarksViewModel(
                        sync = container.bookmarksSync,
                        postRepository = container.postRepository,
                        offline = container.offlineLibrary,
                        collectedMeta = container.collectedPostMetaStore,
                    )
                }
            }
    }
}

/**
 * One row, out of everything this device has been told about the thread.
 *
 * `list-collection` returns a title and, in practice, nothing else — no board, no author, no reply
 * count — which is why the same `ThreadRow` that carries four pieces of information on the front
 * page would carry one here. The gaps are filled by what the site said about the thread somewhere
 * else: on the feed it was scrolled past on, on the page opened to press the star, in the pages a
 * download fetched. The merge itself happens on the way *into* the store, which coalesces rather
 * than replaces and lets the endpoint's own answer win wherever it has one — so by the time a row
 * is read back it is already the best answer anything has given, and never an invented one.
 */
private fun CollectedPostMeta.toEntry(offline: OfflineState) =
    BookmarkEntry(
        postId = postId,
        // Only ever null for a row nothing has named, which cannot be on the list: the one write
        // that puts a thread there is a walk of an endpoint whose single reliable field is the title.
        title = title.orEmpty(),
        categoryTitle = categoryTitle,
        categorySlug = categorySlug,
        authorName = authorName,
        avatarUrl = resolvedAvatarUrl,
        commentCount = commentCount,
        createdAtText = createdAtText,
        offline = offline,
    )
