package io.github.nodyssey.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.ForumNotification
import io.github.nodyssey.data.MessageConversation
import io.github.nodyssey.data.MessageRepository
import io.github.nodyssey.data.NotificationCategory
import io.github.nodyssey.data.NotificationCounts
import io.github.nodyssey.data.NotificationRepository
import io.github.nodyssey.data.NotificationTab
import io.github.nodyssey.data.SearchRepository
import io.github.nodyssey.data.UserSearchResult
import io.github.nodyssey.data.session.SessionRepository
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.postlist.toSiteError
import io.github.plaza.core.AppClock
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NotificationsViewModel(
    private val repository: NotificationRepository,
    private val messages: MessageRepository,
    private val search: SearchRepository,
    private val session: SessionRepository,
    private val clock: AppClock,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(NotificationsUiState(isSignedIn = session.state.value.isSignedIn))
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var messagePollJob: Job? = null
    private val refreshMutex = Mutex()
    private var lastRefreshMillis: Long? = null
    private var pickerJob: Job? = null

    init {
        session.state
            .distinctUntilChangedBy { it.generation }
            .onEach { value ->
                val wasPolling = messagePollJob?.isActive == true
                loadJob?.cancel()
                stopMessagePolling()
                lastRefreshMillis = null
                _uiState.update {
                    if (value.isSignedIn) it.copy(isSignedIn = true) else NotificationsUiState()
                }
                // Signing out has to empty the badge at the source too, or the counts the previous
                // account was carrying stay in the repository waiting to be re-emitted.
                if (value.isSignedIn) refresh() else repository.clearCounts()
                if (wasPolling && value.isSignedIn) startMessagePolling()
            }.launchIn(viewModelScope)
        // The badges are the repository's, not this screen's: a conversation read on board 7f has to
        // move them too, and that happens while this view model is doing nothing at all.
        repository.counts
            .onEach { counts -> _uiState.update { it.copy(counts = counts) } }
            .launchIn(viewModelScope)
    }

    /**
     * Which group the tabs and 全部已读 are about.
     *
     * It no longer empties the other group's list: the two are pages the reader swipes between, so
     * the one being left is still on screen for the length of the gesture, and the one arriving has
     * to arrive with its rows rather than after them.
     */
    fun selectTab(tab: NotificationTab) {
        if (_uiState.value.selectedTab == tab) return
        if (tab != NotificationTab.MESSAGES) stopMessagePolling()
        _uiState.update { it.copy(selectedTab = tab) }
        refresh()
    }

    /**
     * Loads the counts and both groups.
     *
     * Both, rather than whichever tab is selected: the groups are pages the reader swipes between,
     * and a page that arrives empty and fills in a moment later is the flicker the swipe exists to
     * avoid. They are two endpoints — 私信 is not a list of notifications, it is board 7e's
     * conversation list — so they go out together and are awaited together.
     *
     * A failure belongs to the group whose request failed. One endpoint walled off behind Cloudflare
     * must not replace the other group's rows with an error screen about a list that loaded fine.
     */
    fun refresh() {
        if (!session.state.value.isSignedIn) return
        val resumeMessagePolling = messagePollJob?.isActive == true
        stopMessagePolling()
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                refreshMutex.withLock {
                    _uiState.update { it.copy(isLoading = true, errors = emptyMap()) }
                    try {
                        load(includeInteractions = true)
                    } finally {
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }
            }
        if (resumeMessagePolling) startMessagePolling()
    }

    /** Only the visible private-message list polls; the route stops it on pause or navigation. */
    fun startMessagePolling() {
        if (messagePollJob?.isActive == true) return
        if (!session.state.value.isSignedIn || _uiState.value.selectedTab != NotificationTab.MESSAGES) return
        messagePollJob =
            viewModelScope.launch {
                var interval = MESSAGE_POLL_INTERVAL_MILLIS
                while (session.state.value.isSignedIn && _uiState.value.selectedTab == NotificationTab.MESSAGES) {
                    // Entry and tab selection already refresh. Wait before the first poll so those
                    // requests are not immediately repeated when the lifecycle resumes.
                    delay(interval)
                    if (loadJob?.isActive == true) continue
                    val succeeded = refreshMutex.withLock { load(includeInteractions = false) }
                    interval =
                        if (succeeded) MESSAGE_POLL_INTERVAL_MILLIS else (interval * 2).coerceAtMost(MAX_MESSAGE_POLL_INTERVAL_MILLIS)
                }
            }
    }

    fun stopMessagePolling() {
        messagePollJob?.cancel()
        messagePollJob = null
    }

    /** Shared serialization prevents a slow automatic response from undoing a manual refresh. */
    private suspend fun load(includeInteractions: Boolean): Boolean =
        coroutineScope {
            val sessionAtStart = session.state.value
            if (!sessionAtStart.isSignedIn) return@coroutineScope false
            // Counts publish through the repository's flow; copying their result here would race
            // the overlap correction made by the merged interaction load.
            val counts = async { runCatchingExceptCancellation { repository.refreshCounts() } }
            val interactions =
                if (includeInteractions) async { runCatchingExceptCancellation { repository.interactions() } } else null
            val conversations = async { runCatchingExceptCancellation { messages.conversations() } }
            val loadedCounts = counts.await()
            val loadedItems = interactions?.await()
            val loadedConversations = conversations.await()
            if (session.state.value != sessionAtStart) return@coroutineScope false
            val succeeded = loadedCounts.isSuccess && loadedConversations.isSuccess && loadedItems?.isFailure != true
            // A failed badge request must expire even a recent successful refresh. Message-only
            // polls cannot extend the freshness of the interaction list they did not fetch.
            if (!succeeded) {
                lastRefreshMillis = null
            } else if (includeInteractions) {
                lastRefreshMillis = clock.nowMillis()
            }
            _uiState.update { state ->
                state.copy(
                    items = loadedItems?.getOrElse { state.items } ?: state.items,
                    conversations = loadedConversations.getOrElse { state.conversations },
                    errors =
                    buildMap {
                        if (!includeInteractions) putAll(state.errors - NotificationTab.MESSAGES)
                        loadedItems?.exceptionOrNull()?.let {
                            put(NotificationTab.INTERACTIONS, it.toSiteError())
                        }
                        loadedConversations.exceptionOrNull()?.let {
                            put(NotificationTab.MESSAGES, it.toSiteError())
                        }
                    },
                    nowMillis = if (loadedConversations.isSuccess || loadedItems?.isSuccess == true) clock.nowMillis() else state.nowMillis,
                )
            }
            succeeded
        }

    /**
     * [refresh], unless what is on screen is recent enough to stand.
     *
     * Called every time the screen comes back into view — the tab bar, Back from a thread, the app
     * returning to the foreground. This view model lives at the navigation root and survives all of
     * those, so nothing recreates it and init's one refresh was the only load a session ever ran.
     * The throttle is what keeps tab-hopping from turning into a request per tap; only a successful
     * load stamps [lastRefreshMillis], so a failed one retries on the next return
     * instead of waiting out the interval.
     */
    fun refreshIfStale() {
        if (loadJob?.isActive == true) return
        val lastRefresh = lastRefreshMillis
        if (lastRefresh != null && clock.nowMillis() - lastRefresh < REFRESH_THROTTLE_MILLIS) return
        refresh()
    }

    /**
     * Clears the group, optimistically and then for real.
     *
     * The local update lands first because the button should not feel like a network round trip; the
     * request that follows is what makes it survive a refresh. A failure re-reads the server rather
     * than restoring the badges by hand — whatever it says is the truth either way.
     */
    fun markAllRead() {
        val tab = _uiState.value.selectedTab
        val state = _uiState.value
        // Only the groups the button belongs to. Zeroing the whole `NotificationCounts` used to wipe
        // every badge, so 全部已读 on 通知 also claimed the unread 私信 had been read.
        tab.categories.forEach { category ->
            repository.noteRead(category = category, count = state.counts.forCategory(category))
        }
        _uiState.update {
            it.copy(
                items = it.items.map(ForumNotification::read),
                conversations = it.conversations.map { row -> row.copy(unreadCount = 0) },
            )
        }
        viewModelScope.launch {
            runCatchingExceptCancellation {
                if (tab == NotificationTab.MESSAGES) {
                    messages.markAllRead()
                    repository.refreshCounts()
                } else {
                    // Both endpoints: the site has no "all groups" call, and the list on screen is
                    // the two of them.
                    tab.categories.forEach { repository.markAllRead(it) }
                }
            }.onFailure { refresh() }
        }
    }

    /**
     * Opening a row is a read, on the server as well as on screen.
     *
     * Only greying the row out locally was the whole of bug 1: the badge is the count endpoint's
     * answer, the count endpoint had never been told, and so the number that sent the user here
     * survived being acted on — through a refresh, and through a restart.
     */
    fun markOpened(id: String) {
        val item = _uiState.value.items.firstOrNull { it.id == id } ?: return
        _uiState.update { state ->
            state.copy(items = state.items.map { if (it.id == id) it.read() else it })
        }
        if (!item.isUnread) return
        viewModelScope.launch {
            // Every group the row arrived in, not just the one whose sentence it is showing: a
            // folded row is two unread rows on the server.
            runCatchingExceptCancellation { repository.markViewed(item.sources) }
        }
    }

    /**
     * The badge half of opening a conversation; board 7f posts the message ids once it has them.
     *
     * The list row knows how many are unread but not which, because `message/list` carries one row
     * per conversation — so this moves the badge now and the thread settles it against the server a
     * moment later.
     */
    fun markConversationOpened(uid: Long) {
        val conversation = _uiState.value.conversations.firstOrNull { it.uid == uid } ?: return
        repository.noteRead(NotificationCategory.MESSAGES, conversation.unreadCount)
        _uiState.update { state ->
            state.copy(
                conversations =
                state.conversations.map { if (it.uid == uid) it.copy(unreadCount = 0) else it },
            )
        }
    }

    fun showNewConversation() {
        _uiState.update { it.copy(newConversation = NewConversationState(isVisible = true)) }
    }

    fun dismissNewConversation() {
        pickerJob?.cancel()
        _uiState.update { it.copy(newConversation = NewConversationState()) }
    }

    fun updateNewConversationQuery(value: String) {
        _uiState.update { it.copy(newConversation = it.newConversation.copy(query = value)) }
    }

    fun searchRecipients() {
        val query = _uiState.value.newConversation.query.trim()
        if (query.isEmpty()) return
        pickerJob?.cancel()
        pickerJob =
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        newConversation =
                        it.newConversation.copy(isSearching = true, error = null, results = emptyList()),
                    )
                }
                runCatchingExceptCancellation { search.searchUsers(query) }
                    .onSuccess { users ->
                        _uiState.update {
                            it.copy(
                                newConversation =
                                it.newConversation.copy(isSearching = false, results = users),
                            )
                        }
                    }.onFailure { throwable ->
                        _uiState.update {
                            it.copy(
                                newConversation =
                                it.newConversation.copy(
                                    isSearching = false,
                                    error = throwable.toSiteError(),
                                ),
                            )
                        }
                    }
            }
    }

    companion object {
        /** How recent "recent enough" is; see [refreshIfStale]. */
        private const val REFRESH_THROTTLE_MILLIS = 30_000L
        private const val MESSAGE_POLL_INTERVAL_MILLIS = 10_000L
        private const val MAX_MESSAGE_POLL_INTERVAL_MILLIS = 60_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    NotificationsViewModel(
                        repository = container.notificationRepository,
                        messages = container.messageRepository,
                        search = container.searchRepository,
                        session = container.sessionRepository,
                        clock = container.clock,
                    )
                }
            }
    }
}

/**
 * The 新建私信 picker (board 7e's FAB).
 *
 * Marked "App 增强" on the board: the site has no way to start a conversation with someone you have
 * never talked to, so this reuses the member-search endpoint to pick a recipient.
 */
data class NewConversationState(
    val isVisible: Boolean = false,
    val query: String = "",
    val results: List<UserSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val error: SiteError? = null,
)

data class NotificationsUiState(
    val isSignedIn: Boolean = false,
    val selectedTab: NotificationTab = NotificationTab.INTERACTIONS,
    val counts: NotificationCounts = NotificationCounts(),
    val items: List<ForumNotification> = emptyList(),
    val conversations: List<MessageConversation> = emptyList(),
    val isLoading: Boolean = false,
    /** What each group's last load failed with, if it did. Absent groups loaded, or never ran. */
    val errors: Map<NotificationTab, SiteError> = emptyMap(),
    /** Stamped when the list loaded, so relative labels stay stable across recomposition. */
    val nowMillis: Long = 0L,
    val newConversation: NewConversationState = NewConversationState(),
) {
    val hasUnread: Boolean
        get() = items.any(ForumNotification::isUnread) || conversations.any { it.unreadCount > 0 }

    val isEmpty: Boolean
        get() = isEmptyOf(selectedTab)

    /** The selected group's failure, which is the one the screen's own error state is about. */
    val error: SiteError?
        get() = errors[selectedTab]

    fun isEmptyOf(tab: NotificationTab): Boolean =
        if (tab == NotificationTab.MESSAGES) conversations.isEmpty() else items.isEmpty()
}
