package io.github.nodyssey.ui.postdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.ThreadPreview
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.FreeChickenLegs
import io.github.nodyssey.data.NoReadingPositions
import io.github.nodyssey.data.PostRepository
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.ReadingPosition
import io.github.nodyssey.data.ReadingPositionStore
import io.github.nodyssey.data.TrackedThreadStore
import io.github.nodyssey.data.session.SessionState
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.model.PostContent
import io.github.nodyssey.model.ReactionAction
import io.github.nodyssey.model.ThreadSnapshot
import io.github.nodyssey.ui.postlist.toSiteError
import io.github.nodyssey.ui.space.joinedDays
import io.github.plaza.core.AppClock
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for one thread, reading from the database and writing through it.
 *
 * The thread is *observed*, never fetched-and-held: [PostRepository.thread] emits whatever Room holds,
 * so a cached thread paints on the first frame and offline it paints at all. A refresh writes into
 * Room and the new content arrives through the same observation — there is no second path by which
 * content reaches the screen.
 *
 * A failed refresh therefore does not blank the screen. The error is reported, but cached comments
 * stay visible underneath it, which is the behaviour that makes a flaky connection tolerable.
 */
class PostDetailViewModel(
    private val postId: Long,
    private val repository: PostRepository,
    session: StateFlow<SessionState> = MutableStateFlow(SessionState()),
    /** The floor a notification or a quote came in on, as the site labels it (`"#127"`). */
    private val initialFloor: String? = null,
    /** The page a `/post-703863-4` link named, when the thread was opened from one. */
    private val initialPage: Int? = null,
    /**
     * What the list that opened this thread already knew — see [io.github.nodyssey.PostDetailKey.preview].
     *
     * Held rather than merely seeded, because Room answers "nothing cached" for a thread nobody has
     * opened before and the state that answer clears would otherwise take this with it.
     */
    private val preview: ThreadPreview? = null,
    /**
     * 临时显示被屏蔽内容.
     *
     * A floor the site marked blocked is downloaded and cached like any other, so revealing is a
     * change of view and not of content — no re-fetch, and switching it back re-collapses what is
     * already on screen.
     */
    showBlockedContent: StateFlow<Boolean> = MutableStateFlow(false),
    /** Where this thread was left off last time, and where this read's own place is written. */
    private val readingPositions: ReadingPositionStore = NoReadingPositions,
    /** 追踪新回复; absent in a test that does not care, which also hides the menu item. */
    private val trackedThreads: TrackedThreadStore? = null,
    /**
     * Cached public account information for the opening post and the replies actually on screen.
     */
    private val profiles: ProfileRepository? = null,
    private val clock: AppClock = AppClock.System,
) : ViewModel() {
    private val sourceState =
        MutableStateFlow(
            PostDetailUiState(
                postId = postId,
                title = preview?.title.orEmpty(),
                isAwarded = preview?.isAwarded == true,
                preview = preview,
                replyCount = preview?.replyCount,
                lastFloor = preview?.replyCount,
            ),
        )
    private val _uiState = MutableStateFlow(sourceState.value)
    val uiState: StateFlow<PostDetailUiState> = _uiState.asStateFlow()

    /** Publish the filtered projection in the same turn as flags, without another content store. */
    private inline fun updateState(transform: (PostDetailUiState) -> PostDetailUiState) {
        sourceState.update(transform)
        _uiState.value = sourceState.value.readingView()
    }

    private var loadJob: Job? = null
    private var replyCountJob: Job? = null
    private var authorProfileJob: Job? = null
    private var fetchedAuthorUid: Long? = null
    private val replyAuthorJobs = mutableMapOf<Long, Job>()
    private val replyAuthorRetryAfter = mutableMapOf<Long, Long>()
    private var authorScanJob: Job? = null
    private var authorScanGeneration = 0
    private var unfilteredPosition: ReadingPosition? = null

    /** Where the reader is *now*, reported by the screen as it scrolls. See [recordReadingPosition]. */
    private val readingPosition = MutableStateFlow<ReadingPosition?>(null)

    init {
        // Reconcile session provenance before collecting Room. A restored detail destination can be
        // the first entry composed after process death, so relying on the list ViewModel to do this
        // would allow an authenticated snapshot to flash on a signed-out cold start.
        viewModelScope.launch {
            repository.reconcileSession(
                isSignedIn = session.value.isSignedIn,
                fingerprint = session.value.fingerprint,
            )
            // Asked here, and before the collection below, because that collection marks the thread
            // read and moves the very baseline this compares against. Read afterwards it would
            // answer "no new replies" for the thread whose badge the reader had just tapped.
            openThread(seenReplies = repository.seenRepliesIfUnread(postId))
            repository.thread(postId).collect { thread ->
                if (thread == null) {
                    authorScanGeneration++
                    authorScanJob?.cancel()
                    replyCountJob?.cancel()
                    // Logout deletes the Room row while this Navigation 3 entry can remain alive in a
                    // background tab. Clear the mirrored UI state as well or the composable would keep
                    // rendering the last authenticated snapshot after its owner was removed.
                    //
                    // Everything cleared here is *content*, and [pendingScroll] deliberately is not:
                    // null is also what a thread nobody has cached looks like, which is what every
                    // thread opened from a notification looks like for the length of its first fetch.
                    // Clearing the floor here threw away the one thing that read was opened for,
                    // before the floors it named could possibly have arrived.
                    fetchedAuthorUid = null
                    authorProfileJob?.cancel()
                    replyAuthorJobs.values.forEach { it.cancel() }
                    replyAuthorJobs.clear()
                    replyAuthorRetryAfter.clear()
                    updateState {
                        it.copy(
                            title = preview?.title.orEmpty(),
                            body = null,
                            comments = emptyList(),
                            commentPages = emptyList(),
                            firstLoadedPage = 1,
                            lastLoadedPage = 1,
                            totalPages = 1,
                            hasNextPage = false,
                            collected = null,
                            collectionCount = null,
                            isAwarded = preview?.isAwarded == true,
                            authorLevel = null,
                            authorJoinedDays = null,
                            replyAuthors = emptyMap(),
                            onlyAuthor = false,
                            isScanningAuthor = false,
                            authorScanError = null,
                            replyCount = preview?.replyCount,
                            lastFloor = preview?.replyCount,
                        )
                    }
                    return@collect
                }

                val replyUids = thread.comments.mapNotNull { it.authorUid }.toSet()
                replyAuthorRetryAfter.keys.retainAll(replyUids)
                updateState { state ->
                    state.copy(
                        title = thread.title,
                        body = thread.body,
                        comments = thread.comments,
                        replyAuthors = state.replyAuthors.filterKeys { it in replyUids },
                        commentPages = thread.commentPages,
                        firstLoadedPage = thread.firstLoadedPage,
                        lastLoadedPage = thread.lastLoadedPage,
                        totalPages = thread.totalPages,
                        hasNextPage = thread.hasNextPage,
                        replyCount = thread.lastFloorNumberIfComplete() ?: state.replyCount,
                        lastFloor = thread.lastFloorNumberIfComplete() ?: state.lastFloor,
                        collected = thread.collected,
                        collectionCount = thread.collectionCount,
                        // Unknown collapses to false here, unlike [collected]: an unlit star and an
                        // absent star are two different things to a reader, but an absent badge is
                        // the only thing "we have not been told" can honestly look like.
                        isAwarded = thread.isAwarded == true,
                    )
                }
                loadAuthorProfileIfNeeded(thread.body?.authorUid)

                // "Read" means content actually reached the screen — which is exactly this emission,
                // and the only signal that covers all the ways it can happen: a fresh fetch, a cache
                // hit that skipped the network, or a cached thread read in aeroplane mode.
                //
                // Marking read on *opening* instead looked equivalent and was not: opening an
                // uncached post offline shows nothing but an error, and the thread still ended up
                // dimmed in the list as though it had been read.
                if (!sourceState.value.onlyAuthor) repository.markThreadRead(postId)
                // Reading the last page is being told about every reply on it, so a followed thread
                // must not announce those replies later. Only the last page: floor #10 on page 1 of
                // forty says nothing about how many replies the thread has.
                if (!sourceState.value.onlyAuthor) thread.lastFloorNumberIfComplete()?.let { trackedThreads?.advance(postId, it) }
            }
        }

        trackedThreads
            ?.isTracked(postId)
            ?.onEach { tracked -> updateState { it.copy(tracked = tracked) } }
            ?.launchIn(viewModelScope)

        showBlockedContent
            .onEach { show -> updateState { it.copy(showBlockedContent = show) } }
            .launchIn(viewModelScope)

        // The reply editor is the only thing here that needs an account, and it needs to know before
        // it opens rather than after a failed publish.
        session
            .onEach { sessionState -> updateState { it.copy(isSignedIn = sessionState.isSignedIn) } }
            .launchIn(viewModelScope)

        // Coming back from the WebView signed in, or with a challenge cleared, is the one case where a
        // thread that was "fresh" a second ago is worth re-fetching: a locked thread has content now.
        // `drop(1)` skips the cookies we started with — a cold start is not a session change.
        session
            .distinctUntilChangedBy { it.generation }
            .drop(1)
            .onEach { sessionState ->
                val cleared =
                    repository.reconcileSession(
                        isSignedIn = sessionState.isSignedIn,
                        fingerprint = sessionState.fingerprint,
                    )
                if (sessionState.isSignedIn && !cleared) repository.invalidateCaches()
                refresh()
                loadAuthorProfileIfNeeded(sourceState.value.body?.authorUid)
            }.launchIn(viewModelScope)

        // The stored place is read *before* this read starts writing over it, and then held for the
        // whole visit. That ordering is the feature: the thread opens at its top, so the first thing
        // this read would record is page 1, and a resume offer recomputed from the store would be
        // gone by the time the reader could reach for it.
        viewModelScope.launch {
            val resume = readingPositions.readingPosition(postId)
            updateState { it.copy(resumePosition = resume) }
            readingPosition
                .filterNotNull()
                .distinctUntilChanged()
                // Scrolling past a floor moves the position; writing every one of them would put a
                // database write on each frame of a fling. The reader only has to stop somewhere.
                .debounce(POSITION_WRITE_DELAY_MILLIS)
                .collect { position -> readingPositions.setReadingPosition(postId, position) }
        }
    }

    private fun loadAuthorProfileIfNeeded(uid: Long?) {
        val repo = profiles ?: return
        if (uid == null || uid <= 0 || uid == fetchedAuthorUid) return
        fetchedAuthorUid = uid
        authorProfileJob?.cancel()
        authorProfileJob =
            viewModelScope.launch {
                runCatchingExceptCancellation { repo.authorInfo(uid) }
                    .onSuccess { profile ->
                        if (profile == null) {
                            fetchedAuthorUid = null
                            return@onSuccess
                        }
                        updateState {
                            it.copy(
                                authorLevel = profile.level,
                                authorJoinedDays = joinedDays(profile.createdAt, clock.nowMillis()),
                            )
                        }
                    }.onFailure {
                        fetchedAuthorUid = null
                    }
            }
    }

    /** Called after scrolling settles; lazy precomposition is deliberately not a reason to fetch. */
    fun loadVisibleAuthors(uids: Set<Long>) {
        val repo = profiles ?: return
        val visible = uids.intersect(uiState.value.comments.mapNotNull { it.authorUid }.toSet()).filter { it > 0 }.toSet()
        (replyAuthorJobs.keys - visible).forEach { uid -> replyAuthorJobs.remove(uid)?.cancel() }
        visible.forEach { uid ->
            if (sourceState.value.replyAuthors.containsKey(uid) || replyAuthorJobs[uid]?.isActive == true) return@forEach
            if (clock.nowMillis() < (replyAuthorRetryAfter[uid] ?: Long.MIN_VALUE)) return@forEach
            replyAuthorJobs[uid] = viewModelScope.launch {
                val info = runCatchingExceptCancellation { repo.authorInfo(uid) }.getOrNull()
                if (info == null) {
                    replyAuthorRetryAfter[uid] = clock.nowMillis() + AUTHOR_RETRY_MILLIS
                } else if (sourceState.value.comments.any { it.authorUid == uid }) {
                    replyAuthorRetryAfter.remove(uid)
                    updateState { state ->
                        val authors = state.replyAuthors.toMutableMap()
                        authors.remove(uid)
                        authors[uid] = ReplyAuthorInfo(info.level, joinedDays(info.createdAt, clock.nowMillis()))
                        while (authors.size > MAX_REPLY_AUTHORS) authors.remove(authors.keys.first())
                        state.copy(replyAuthors = authors)
                    }
                }
                while (replyAuthorRetryAfter.size > MAX_REPLY_AUTHORS) replyAuthorRetryAfter.remove(replyAuthorRetryAfter.keys.first())
            }
        }
    }

    /** The reader needs this even when history, collections or an external link supplied no preview. */
    fun loadReplyCount() {
        val state = sourceState.value
        if (!state.hasContent || state.readerLastFloor() != null || replyCountJob?.isActive == true) return
        val pages = state.totalPages
        replyCountJob = viewModelScope.launch {
            val count = runCatchingExceptCancellation { repository.threadReplyCount(postId) }.getOrNull() ?: return@launch
            updateState { current ->
                if (current.hasContent && current.totalPages == pages && current.readerLastFloor() == null) {
                    current.copy(replyCount = count, lastFloor = count)
                } else {
                    current
                }
            }
        }
    }

    /**
     * Decides where this read starts and whether it begins with a request.
     *
     * @param seenReplies how many replies the last read accounted for, when the feed says more have
     *   arrived since — see [PostRepository.seenRepliesIfUnread]. It overrides the cache window: the
     *   badge the reader tapped names content the cached copy provably does not have.
     */
    private suspend fun openThread(seenReplies: Int?) {
        val hasUnreadReplies = seenReplies != null
        // Zero seen is a thread opened before any reply existed: every floor is new, so there is no
        // line to draw between old and new, and the top is already where they start.
        val firstNewFloor = seenReplies?.takeIf { it > 0 }?.plus(1)
        updateState { it.copy(firstNewFloor = firstNewFloor) }
        // Opening on a notification's floor, or on the page a `/post-703863-4` link named, starts
        // the thread there. Page 1 is only the default, not where every read begins — and a thread
        // the reader has been through before opens where they have not, unless something named a
        // place of its own.
        val named = initialFloor != null || initialPage != null
        val floor = initialFloor ?: firstNewFloor?.takeIf { !named }?.let { "#$it" }
        val start = startPage(floor)
        if (floor != null || start > 1) {
            updateState { it.copy(pendingScroll = PendingScroll(start, floor)) }
        }
        // Even a fresh page can predate the reply named by a notification. Refresh once when that
        // floor is missing; collection must not keep retrying a reply that may have been deleted.
        val cached = repository.cachedPages(postId)
        if (
            cached == null ||
            start !in cached ||
            hasUnreadReplies ||
            !repository.isThreadFresh(postId) ||
            !isFloorCached(floor)
        ) {
            load(page = start, replacesWindow = true, scrollTo = sourceState.value.pendingScroll)
        }
    }

    private suspend fun isFloorCached(floor: String?): Boolean {
        val number = NodeSeekSite.parseFloorNumber(floor) ?: return true
        val thread = repository.thread(postId).first() ?: return false
        return NodeSeekSite.parseFloorNumber(thread.body?.floor) == number ||
            thread.comments.any { NodeSeekSite.parseFloorNumber(it.floor) == number }
    }

    /** Where this thread opens: the floor's page, the link's page, or the top. */
    private fun startPage(floor: String? = initialFloor): Int =
        NodeSeekSite
            .parseFloorNumber(floor)
            ?.let(NodeSeekSite::pageOfFloor)
            ?: initialPage?.coerceAtLeast(1)
            ?: 1

    fun refresh() {
        if (sourceState.value.onlyAuthor) {
            scanAuthorPages(refresh = true)
        } else {
            load(page = sourceState.value.firstLoadedPage, replacesWindow = true)
        }
    }

    /** Filtering is a projection of Room, including pages fetched by the cancellable scan. */
    fun toggleOnlyAuthor() {
        if (sourceState.value.onlyAuthor) {
            leaveAuthorFilter()
            val position = unfilteredPosition ?: ReadingPosition(1, null)
            updateState { it.copy(pendingScroll = PendingScroll(position.page, position.floor)) }
        } else {
            unfilteredPosition = readingPosition.value ?: ReadingPosition(sourceState.value.firstLoadedPage, null)
            loadJob?.cancel()
            updateState {
                it.copy(
                    onlyAuthor = true,
                    isLoading = false,
                    isAppending = false,
                    isPrepending = false,
                    isRefreshingTail = false,
                    pendingScroll = PendingScroll(1),
                )
            }
            scanAuthorPages(refresh = false)
        }
    }

    private fun leaveAuthorFilter() {
        authorScanGeneration++
        authorScanJob?.cancel()
        updateState { it.copy(onlyAuthor = false, isScanningAuthor = false, authorScanError = null, isRefreshing = false) }
    }

    private fun scanAuthorPages(refresh: Boolean) {
        val generation = ++authorScanGeneration
        authorScanJob?.cancel()
        updateState { it.copy(isScanningAuthor = true, authorScanError = null) }
        authorScanJob = viewModelScope.launch {
            runCatchingExceptCancellation {
                val cached = repository.cachedPages(postId) ?: (1..1)
                var first = cached.first
                var last = cached.last
                var total = sourceState.value.totalPages
                updateState { it.copy(authorScanChecked = last - first + 1, authorScanTotal = total) }
                suspend fun fetch(page: Int) {
                    repository.extendThread(postId, page)
                    first = minOf(first, page)
                    last = maxOf(last, page)
                    total = repository.thread(postId).first { it != null && page in it.firstLoadedPage..it.lastLoadedPage }!!.totalPages
                    updateState { it.copy(authorScanChecked = last - first + 1, authorScanTotal = total) }
                }
                // Extend adjacent pages so the normal reading window and reaction updates keep one source of truth.
                if (refresh) for (page in cached) fetch(page)
                while (first > 1) fetch(first - 1)
                while (last < total) fetch(last + 1)
            }.onFailure { error ->
                if (generation == authorScanGeneration) updateState { it.copy(authorScanError = error.toSiteError()) }
            }
            if (generation == authorScanGeneration) updateState { it.copy(isScanningAuthor = false, isRefreshing = false) }
        }
    }

    /**
     * [refresh], with the pull indicator owning the feedback instead of the bar under the app bar.
     *
     * Same load, different announcement: the reader's finger is already on the indicator, so also
     * drawing the jump-style progress bar would show one gesture as two loads.
     */
    fun pullRefresh() {
        updateState { it.copy(isRefreshing = true) }
        refresh()
    }

    /**
     * Re-fetches [page] as the whole window.
     *
     * The 刷新 menu item, aimed at the page on screen rather than the window's first: a reader deep
     * in a thread is asking for the newest state of what they are looking at — at the thread's end,
     * the replies that arrived since — not to be carried back to where the window began.
     */
    fun refreshPage(page: Int) {
        if (sourceState.value.onlyAuthor) refresh() else load(page = page.coerceAtLeast(1), replacesWindow = true)
    }

    /**
     * Comments are paginated on the site but read as one thread on a phone, so the next page is
     * appended to the same list rather than replacing it. The append itself happens in the database.
     */
    fun loadNextPage() {
        val state = sourceState.value
        if (state.onlyAuthor) return
        if (state.isLoading || state.isAppending || state.isPrepending || state.isRefreshingTail || !state.hasNextPage) return
        load(page = state.lastLoadedPage + 1, replacesWindow = false)
    }

    /** Joins the preceding page onto this read without replacing the floors already on screen. */
    fun loadPreviousPage() {
        val state = sourceState.value
        if (state.onlyAuthor) return
        if (state.isLoading || state.isAppending || state.isPrepending || state.isRefreshingTail) return
        if (!state.hasContent || state.firstLoadedPage <= 1) return
        load(page = state.firstLoadedPage - 1, replacesWindow = false)
    }

    /**
     * Re-reads the last loaded page, for the pull past the end of a thread that has no next page.
     *
     * Where [loadNextPage] stops, this takes over: the floors posted since the page was fetched are
     * on that same page until it fills, so the gesture that used to do nothing at the foot of a
     * thread now brings them in. Stored as an extend rather than a refresh so the pages above it
     * survive — the reader is at the bottom of a scroll they built by reading, and replacing the
     * window would pull it out from under them. If the new replies started a page of their own, the
     * refetched last page says so and the ordinary append picks it up from there.
     */
    fun refreshTail() {
        val state = sourceState.value
        if (state.onlyAuthor) return
        if (state.isLoading || state.isAppending || state.isPrepending || state.isRefreshingTail) return
        if (state.hasNextPage || !state.hasContent) return
        updateState { it.copy(isRefreshingTail = true) }
        load(page = state.lastLoadedPage, replacesWindow = false, tail = true)
    }

    /**
     * Brings [page] onto the screen, fetching it when it is not one of the loaded ones.
     *
     * The site serves any page directly, so a jump is one request: page 12 is fetched as page 12 and
     * becomes what the screen shows. It used to be reached by fetching pages 2 through 12 in turn, to
     * keep the loaded pages a prefix of the thread — which on a long thread meant dozens of requests
     * and, in practice, a control that never arrived anywhere it had not already been.
     *
     * Only the pages either side of the loaded slice extend it. Those are the ones a reader reaches
     * by continuing to read, and joining them on keeps the thread one uninterrupted scroll.
     */
    fun loadPage(page: Int) = bringIntoView(page, floor = null)

    /**
     * Opens [url] on this screen when it links to this same thread, and says whether it did.
     *
     * A link back to the thread being read — most often the author's signature pointing at their own
     * post — used to push a second copy of this destination onto the stack. The copy looked exactly
     * like the screen under it, so the tap seemed to do nothing, and Back then took one press per
     * tap before anything changed. Answered here instead as the jump the link asks for: the page it
     * names, which for a bare `/post-<id>-1` is the top of the thread.
     */
    fun openLinkInPlace(url: String): Boolean {
        val route = NodeSeekSite.parseInternalRoute(url) as? NodeSeekSite.InternalRoute.Post ?: return false
        if (route.postId != postId) return false
        if (sourceState.value.onlyAuthor) leaveAuthorFilter()
        loadPage(route.page)
        return true
    }

    /**
     * Fetches a page next to the loaded ones and leaves the reader exactly where they are.
     *
     * The difference from [loadPage] is the landing, not the fetch: a page adjoining the window is the
     * rest of this read rather than a place to be put down at, so the screen scrolls into the new
     * floors itself instead of being snapped onto them the moment they arrive. Refuses anything that
     * does not adjoin — a real jump has to go through [loadPage], which is allowed to replace the
     * window.
     */
    fun extendToPage(page: Int) {
        val state = sourceState.value
        if (state.onlyAuthor) return
        if (state.isLoading || state.isAppending || state.isPrepending || state.isRefreshingTail) return
        if (page !in 1..state.totalPages) return
        if (page != state.lastLoadedPage + 1 && page != state.firstLoadedPage - 1) return
        load(page = page, replacesWindow = false)
    }

    /**
     * Scrolls to [floor], loading the page it lives on when it is not on screen.
     *
     * The site names a floor without saying where it is — a notification carries `floor_id`, a quote
     * reference an `#4` anchor — so the page is computed from the floor number.
     */
    fun jumpToFloor(floor: String) {
        val number = NodeSeekSite.parseFloorNumber(floor) ?: return
        if (sourceState.value.onlyAuthor) leaveAuthorFilter()
        bringIntoView(NodeSeekSite.pageOfFloor(number), floor = floor)
    }

    /**
     * Shows the floor a just-published reply landed on.
     *
     * Not [jumpToFloor], for one reason: the floor is seconds old, so the page it lives on — almost
     * always the loaded window's own tail — is stale by exactly that reply, and [bringIntoView]
     * would see the page as already loaded and scroll into a copy the floor is not in. The page is
     * re-fetched even when it is on screen, and the scroll rides the fetch so the landing is the new
     * floor itself. Without this the editor closed, the window quietly refreshed its *first* page,
     * and on a multi-page thread nothing on screen showed the reply the reader had just written.
     *
     * A null [floor] is the site declining to name one (`redirectHash` missing or unreadable); the
     * old whole-window refresh is the honest fallback, since a guessed floor would scroll to
     * somebody else's reply.
     */
    fun showPublishedReply(floor: Int?) {
        if (sourceState.value.onlyAuthor) leaveAuthorFilter()
        if (floor == null) {
            refresh()
            return
        }
        val state = sourceState.value
        val page = NodeSeekSite.pageOfFloor(floor)
        val scroll = PendingScroll(page, "#$floor")
        when {
            // The reply's page is the loaded tail: re-read it in place, keeping the pages above —
            // the reader is at the bottom of a scroll they built, same rule as [refreshTail].
            page == state.lastLoadedPage -> load(page = page, replacesWindow = false, scrollTo = scroll, tail = true)

            // The reply started a fresh page just past the window: append it, ordinary reading order.
            page == state.lastLoadedPage + 1 -> load(page = page, replacesWindow = false, scrollTo = scroll)

            // Anywhere else — a window the reader had jumped away from — the reply's page becomes
            // the window, exactly as a jump to it would.
            else -> load(page = page, replacesWindow = true, scrollTo = scroll)
        }
    }

    /**
     * Returns to where this thread was left off, exactly — the floor when one was recorded, the page
     * otherwise. A no-op when nothing was: the control that offers this hides itself in that case.
     */
    fun resumeReading() {
        if (sourceState.value.onlyAuthor) leaveAuthorFilter()
        val state = sourceState.value
        val resume = state.resumePosition ?: return
        val lastPage = state.totalPages.coerceAtLeast(1)
        // A thread can lose pages between visits — floors get deleted and everything after them moves
        // up. The last page is then the closest thing left to where the reader was, and the floor
        // they stopped on is certainly not on it.
        if (resume.page > lastPage) bringIntoView(lastPage, floor = null) else bringIntoView(resume.page, resume.floor)
    }

    /**
     * Reports where the reader is, so the next visit can offer to come back to it.
     *
     * Called freely as the list scrolls; the write behind it is debounced, and an unchanged position
     * costs nothing.
     */
    fun recordReadingPosition(
        page: Int,
        floor: String?,
    ) {
        if (sourceState.value.onlyAuthor) return
        readingPosition.value = ReadingPosition(page, floor)
    }

    private fun bringIntoView(
        page: Int,
        floor: String?,
    ) {
        val state = sourceState.value
        val target = page.coerceAtLeast(1)
        if (state.onlyAuthor) {
            if (target <= state.readingView().totalPages) updateState { it.copy(pendingScroll = PendingScroll(target, floor)) }
            return
        }
        if (target > state.totalPages) return
        val scroll = PendingScroll(target, floor)
        if (target in state.firstLoadedPage..state.lastLoadedPage) {
            updateState { it.copy(pendingScroll = scroll) }
            return
        }
        // Deliberately *not* guarded on [PostDetailUiState.isAppending]. The page control lives in the
        // bar at the foot of the list, which is exactly where auto-append is running, so refusing a
        // jump mid-append refused most of them — silently, since nothing on screen says why. A jump is
        // the reader overriding the append, and [load] cancels the request it overrides.
        val adjoins = target == state.firstLoadedPage - 1 || target == state.lastLoadedPage + 1
        load(page = target, replacesWindow = !adjoins, scrollTo = scroll)
    }

    /** The screen has scrolled to [PostDetailUiState.pendingScroll]; stop asking it to. */
    fun onScrollHandled() {
        updateState { it.copy(pendingScroll = null) }
    }

    /**
     * Spends one mark on a floor.
     *
     * The new count is never written here: [PostRepository.react] puts it through Room and it arrives
     * by the same observation as every other change to the thread. All this holds is which floor is
     * mid-flight, so the row can show it and a second tap cannot double-spend.
     *
     * The guard against re-reacting is deliberately kept as well as the site's: theirs answers
     * "已经进行过加鸡腿操作" *after* a round trip, and a reader whose first tap was slow should not be
     * able to send a second in the meantime.
     */
    fun react(
        commentId: Long,
        action: ReactionAction,
    ) {
        if (sourceState.value.pendingReaction != null) return
        updateState { it.copy(pendingReaction = PendingReaction(commentId, action), reactionFailure = null) }
        viewModelScope.launch {
            runCatchingExceptCancellation { repository.react(postId, commentId, action) }
                .onSuccess { updateState { it.copy(pendingReaction = null) } }
                .onFailure { throwable ->
                    updateState {
                        it.copy(
                            pendingReaction = null,
                            reactionFailure =
                            ReactionFailure(
                                error = throwable.toSiteError(),
                                // The site's own sentence when it sent one — "鸡腿不足" says more
                                // than any wording of ours could from a status code.
                                detail = (throwable as? SiteException)?.detail,
                            ),
                        )
                    }
                }
        }
    }

    /** The failure has been shown; stop holding it. */
    fun onReactionFailureShown() {
        updateState { it.copy(reactionFailure = null) }
    }

    /**
     * Collects the thread, or takes it out of the collection.
     *
     * A no-op while [PostDetailUiState.collected] is null: no fetched page has said which way the
     * toggle points, and sending "add" on a guess would silently un-collect a thread the reader had
     * already saved.
     *
     * Deliberately not optimistic, unlike most toggles. The star's truth lives in Room and arrives by
     * the same observation as the rest of the thread; flipping it locally first means two writers for
     * one value, and the loser is whichever the reader was actually looking at. [collectPending]
     * greys the star for the round trip instead, which says the same thing without lying about it.
     */
    fun toggleCollect() {
        val state = sourceState.value
        val current = state.collected ?: return
        if (state.collectPending) return
        updateState { it.copy(collectPending = true, collectFailure = null) }
        viewModelScope.launch {
            runCatchingExceptCancellation { repository.setCollected(postId, !current) }
                .onSuccess { updateState { it.copy(collectPending = false) } }
                .onFailure { throwable ->
                    updateState {
                        it.copy(
                            collectPending = false,
                            collectFailure =
                            ReactionFailure(
                                error = throwable.toSiteError(),
                                detail = (throwable as? SiteException)?.detail,
                            ),
                        )
                    }
                }
        }
    }

    /**
     * 追踪新回复 / 取消追踪. The count recorded is the thread's last floor when the last page is on
     * screen, and unknown otherwise — the next poll then takes whatever it sees as the baseline.
     */
    fun toggleTrack() {
        val store = trackedThreads ?: return
        val state = sourceState.value
        viewModelScope.launch {
            if (state.tracked == true) {
                store.untrack(postId)
                return@launch
            }
            val complete = state.hasContent && !state.hasNextPage && state.lastLoadedPage >= state.totalPages
            val known = if (complete) lastFloorNumber(state.comments) else null
            val added =
                store.track(
                    postId = postId,
                    title = state.title,
                    commentCount = known,
                    authorName = state.body?.authorName ?: state.preview?.authorName,
                    categoryTitle = state.body?.categoryTitle ?: state.preview?.categoryTitle,
                    categorySlug = state.preview?.categorySlug,
                )
            updateState { it.copy(trackMessage = if (added) TrackMessage.TRACKED else TrackMessage.FULL) }
        }
    }

    fun onTrackMessageShown() {
        updateState { it.copy(trackMessage = null) }
    }

    /** The collection failure has been shown; stop holding it. */
    fun onCollectFailureShown() {
        updateState { it.copy(collectFailure = null) }
    }

    /**
     * Loads today's free 加鸡腿 allowance so the confirmation can say whether this one is free.
     *
     * Called when the reader opens that confirmation, not on entering the thread: it is one request
     * per thread read that only ever changes a sentence, and most reads never open the dialog.
     */
    fun loadFreeChickenLegs() {
        if (sourceState.value.freeChickenLegs != null) return
        viewModelScope.launch {
            val quota = runCatchingExceptCancellation { repository.freeChickenLegs() }.getOrNull()
            updateState { it.copy(freeChickenLegs = quota) }
        }
    }

    /**
     * @param replacesWindow true when [page] becomes the whole of the cached thread — a first read, a
     *   retry, or a jump — and false when it joins the pages already loaded.
     */
    private fun load(
        page: Int,
        replacesWindow: Boolean,
        scrollTo: PendingScroll? = null,
        tail: Boolean = false,
    ) {
        loadJob?.cancel()
        val prepending = !replacesWindow && !tail && page < sourceState.value.firstLoadedPage
        // A tail refresh announces itself through its own indicator, the way a pull-to-refresh does,
        // so neither the top progress bar nor the append spinner should claim the same load.
        updateState {
            it.copy(
                isLoading = replacesWindow && !tail,
                isAppending = !replacesWindow && !tail && !prepending,
                isPrepending = prepending,
                error = null,
            )
        }
        loadJob =
            viewModelScope.launch {
                runCatchingExceptCancellation {
                    if (replacesWindow) {
                        repository.refreshThread(postId, page)
                    } else {
                        repository.extendThread(postId, page)
                    }
                    if (scrollTo != null) {
                        // Room can publish the refreshed page after the request returns. Keep the
                        // loading gate closed until the screen state holds that snapshot, or a
                        // same-page notification can consume its jump against the old replies.
                        val loaded = repository.thread(postId).first()
                        if (loaded != null) {
                            sourceState.first { state ->
                                state.body == loaded.body &&
                                    state.comments == loaded.comments &&
                                    state.commentPages == loaded.commentPages &&
                                    state.firstLoadedPage == loaded.firstLoadedPage &&
                                    state.lastLoadedPage == loaded.lastLoadedPage
                            }
                        }
                    }
                }.onSuccess {
                    // Neither content nor the read mark is applied here: both follow from the Room
                    // observation above, so there is exactly one path by which either can happen.
                    updateState {
                        it.copy(
                            isLoading = false,
                            isAppending = false,
                            isPrepending = false,
                            isRefreshing = false,
                            isRefreshingTail = false,
                            pendingScroll = scrollTo ?: it.pendingScroll,
                        )
                    }
                }.onFailure { throwable ->
                    updateState {
                        it.copy(
                            isLoading = false,
                            isAppending = false,
                            isPrepending = false,
                            isRefreshing = false,
                            isRefreshingTail = false,
                            error = throwable.toSiteError(),
                        )
                    }
                }
            }
    }

    fun postUrl(): String = NodeSeekSite.BASE_URL + NodeSeekSite.postPath(postId, sourceState.value.lastLoadedPage)

    companion object {
        fun factory(
            container: AppContainer,
            postId: Long,
            initialFloor: String? = null,
            initialPage: Int? = null,
            preview: ThreadPreview? = null,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    PostDetailViewModel(
                        postId,
                        container.postRepository,
                        container.sessionRepository.state,
                        initialFloor,
                        initialPage,
                        preview,
                        container.settingsRepository.showBlockedContent,
                        container.readingPositionStore,
                        container.trackedThreadStore,
                        container.profileRepository,
                        container.clock,
                    )
                }
            }

        /**
         * How long the reader has to stop scrolling before their place is written.
         *
         * Long enough that a fling through twenty floors is one write rather than twenty, short
         * enough that leaving the thread the moment you stop still records where you stopped.
         */
        private const val POSITION_WRITE_DELAY_MILLIS = 500L
        private const val AUTHOR_RETRY_MILLIS = 60_000L
        private const val MAX_REPLY_AUTHORS = 256
    }
}

data class PostDetailUiState(
    /** The thread on screen. Zero only in the default state, which is never rendered with content. */
    val postId: Long = 0,
    val title: String = "",
    /**
     * What the list that opened this thread said about it, or null when nothing opened it from a
     * list. Drawn wherever [body] cannot yet — see [io.github.nodyssey.PostDetailKey.preview].
     */
    val preview: ThreadPreview? = null,
    val body: PostContent? = null,
    val comments: List<PostContent> = emptyList(),
    /** The site page each comment came from, index-aligned with [comments]. */
    val commentPages: List<Int> = emptyList(),
    /** The first site page held in [comments] — not always 1, since a jump loads its page alone. */
    val firstLoadedPage: Int = 1,
    /** The last site page held in [comments]. */
    val lastLoadedPage: Int = 1,
    val totalPages: Int = 1,
    val hasNextPage: Boolean = false,
    val isLoading: Boolean = false,
    val isAppending: Boolean = false,
    /** An earlier page is joining the window; announced at the top rather than at the thread's foot. */
    val isPrepending: Boolean = false,
    /** The load is a pull-to-refresh; its own indicator shows it, so no other progress bar should. */
    val isRefreshing: Boolean = false,
    /** The same, for the pull past the foot of a thread that has no next page. See [PostDetailViewModel.refreshTail]. */
    val isRefreshingTail: Boolean = false,
    val isSignedIn: Boolean = false,
    /** 临时显示被屏蔽内容 — draws the blocked floors instead of collapsing them. */
    val showBlockedContent: Boolean = false,
    /** Where the screen should scroll once the content it names has arrived in [comments]. */
    val pendingScroll: PendingScroll? = null,
    /**
     * The first floor this visit has not seen before — 以下是新回复 is drawn above it — or null when
     * nothing here is new. Taken once, before this read moves the baseline, and held for the visit.
     */
    val firstNewFloor: Int? = null,
    /**
     * Where this thread was left off on a previous visit, or null when it was never read.
     *
     * Read once when the screen opens and then held for the visit, so it keeps meaning "last time"
     * rather than sliding along with the scroll it is offered against.
     */
    val resumePosition: ReadingPosition? = null,
    val error: SiteError? = null,
    /** The floor whose reaction is in flight, so its row can show it and refuse a second tap. */
    val pendingReaction: PendingReaction? = null,
    /** A refused reaction, held until the screen has shown it once. */
    val reactionFailure: ReactionFailure? = null,
    /** Today's free 加鸡腿 allowance; null until [PostDetailViewModel.loadFreeChickenLegs] answers. */
    val freeChickenLegs: FreeChickenLegs? = null,
    /**
     * Whether this account collects the thread; null means no fetched page has said.
     *
     * Null is why the star can be absent rather than merely unlit — the site answers this question
     * only inside a post page, so before one arrives there is nothing honest to draw.
     */
    val collected: Boolean? = null,
    val collectionCount: Int? = null,
    /** 推荐阅读 — see [io.github.nodyssey.model.PostDetail.isAwarded] for what a page can say about it. */
    val isAwarded: Boolean = false,
    /** A collection toggle is in flight, so the star refuses a second tap until it lands. */
    val collectPending: Boolean = false,
    /** A refused collection toggle, held until the screen has shown it once. */
    val collectFailure: ReactionFailure? = null,
    /** Whether 追踪新回复 is on for this thread; null where this build cannot follow threads. */
    val tracked: Boolean? = null,
    /** What the last 追踪新回复 tap came to, held until the screen has said so once. */
    val trackMessage: TrackMessage? = null,
    /** The original poster's rank from `/api/account/getInfo/{uid}`, when known. */
    val authorLevel: Int? = null,
    /** Whole days since the original poster registered, when known. */
    val authorJoinedDays: Int? = null,
    /** Keyed by UID so repeated authors share the same answer without changing reply order. */
    val replyAuthors: Map<Long, ReplyAuthorInfo> = emptyMap(),
    val replyCount: Int? = null,
    /** Original thread floor extent, retained when only-author replaces the reply count. */
    val lastFloor: Int? = null,
    val onlyAuthor: Boolean = false,
    val isScanningAuthor: Boolean = false,
    val authorScanChecked: Int = 0,
    val authorScanTotal: Int = 1,
    val authorScanError: SiteError? = null,
    /** Original site pages survive the filtered view's own pagination, for editing. */
    val commentSourcePages: List<Int> = emptyList(),
) {
    /**
     * Whether there is a thread on screen to read and act on.
     *
     * Deliberately not [body] alone. NodeSeek renders the opening post on page 1 only, so a thread
     * opened straight onto a later page — every notification about a floor past the tenth — has
     * floors and no body until page 1 is fetched. Gating the screen's furniture on the body left
     * that read with no page bar, which is the one control that could have gone back for it.
     */
    val hasContent: Boolean get() = body != null || comments.isNotEmpty() || onlyAuthor
}

data class ReplyAuthorInfo(val level: Int?, val joinedDays: Int?)

/** The counter must never turn original floor 121 into filtered reply 3, or invent missing totals. */
internal fun PostDetailUiState.readerLastFloor(): Int? {
    val loaded = lastFloorNumber(comments)
    val known = lastFloor ?: replyCount.takeUnless { onlyAuthor }
    return when {
        known != null -> maxOf(known, loaded)
        !onlyAuthor && !hasNextPage && lastLoadedPage >= totalPages -> loaded
        else -> null
    }
}

/**
 * The site's DOM can put later floors ahead of earlier ones. Order the reading view numerically,
 * including cached replies, while carrying each reply's source page through the same permutation.
 */
internal fun PostDetailUiState.readingView(): PostDetailUiState {
    val ordered = comments.indices.sortedBy { NodeSeekSite.parseFloorNumber(comments[it].floor) ?: Int.MAX_VALUE }
    val unique =
        ordered.distinctBy { original ->
            val comment = comments[original]
            comment.commentId?.let { "comment:$it" }
                ?: comment.floor?.let { "floor:$it" }
                // A reply with neither id nor floor cannot be compared safely with another one.
                ?: "position:$original"
        }
    if (!onlyAuthor) {
        if (unique.withIndex().all { (index, original) -> index == original }) return this
        return copy(
            comments = unique.map(comments::get),
            commentPages = unique.map { commentPages.getOrNull(it) ?: firstLoadedPage },
        )
    }
    val matching = unique.filter { comments[it].isOriginalPoster || (body?.authorUid != null && comments[it].authorUid == body.authorUid) }
    val pages = ((matching.size + NodeSeekSite.COMMENTS_PER_PAGE - 1) / NodeSeekSite.COMMENTS_PER_PAGE).coerceAtLeast(1)
    return copy(
        comments = matching.map(comments::get),
        commentSourcePages = matching.map { commentPages.getOrNull(it) ?: firstLoadedPage },
        commentPages = matching.indices.map { it / NodeSeekSite.COMMENTS_PER_PAGE + 1 },
        firstLoadedPage = 1,
        lastLoadedPage = pages,
        totalPages = pages,
        hasNextPage = false,
        replyCount = matching.size,
        lastFloor = copy(onlyAuthor = false).readerLastFloor(),
    )
}

enum class TrackMessage { TRACKED, FULL }

/**
 * The highest floor number on the thread's last page, when that page is loaded — the one moment the
 * floors on screen say how many replies the thread has.
 */
private fun ThreadSnapshot.lastFloorNumberIfComplete(): Int? =
    if (hasNextPage || lastLoadedPage < totalPages) null else lastFloorNumber(comments)

/** The highest numbered floor among [comments]; zero for a thread with no replies at all. */
private fun lastFloorNumber(comments: List<PostContent>): Int = comments.mapNotNull { NodeSeekSite.parseFloorNumber(it.floor) }.maxOrNull() ?: 0

data class PendingReaction(
    val commentId: Long,
    val action: ReactionAction,
)

/**
 * A scroll the screen still owes the reader.
 *
 * [page] is what decides whether the target has arrived — a floor that is not in the list yet is
 * indistinguishable from one that was deleted, but "is this page loaded" is answerable. [floor] is
 * the precise target when the site named one; without it the page's first floor will do.
 */
data class PendingScroll(
    val page: Int,
    val floor: String? = null,
)

/**
 * Why a reaction did not land.
 *
 * [detail] is the site's own sentence and wins when there is one; [error] is the fallback for the
 * failures that never reached the site at all.
 */
data class ReactionFailure(
    val error: SiteError,
    val detail: String?,
)
