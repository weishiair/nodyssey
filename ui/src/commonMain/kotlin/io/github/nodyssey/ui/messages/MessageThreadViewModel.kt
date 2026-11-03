package io.github.nodyssey.ui.messages

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.DirectMessage
import io.github.nodyssey.data.MessageConversation
import io.github.nodyssey.data.MessageDraft
import io.github.nodyssey.data.MessageDraftBubble
import io.github.nodyssey.data.MessageDraftHandle
import io.github.nodyssey.data.MessageDraftStore
import io.github.nodyssey.data.MessageFailure
import io.github.nodyssey.data.MessageRecovery
import io.github.nodyssey.data.MessageRepository
import io.github.nodyssey.data.MessageThread
import io.github.nodyssey.data.NotificationCategory
import io.github.nodyssey.data.NotificationRepository
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.composer.ImageAttachment
import io.github.nodyssey.data.composer.ImageUploadQueue
import io.github.nodyssey.data.composer.ImageUploader
import io.github.nodyssey.data.composer.PickedImage
import io.github.nodyssey.data.composer.UploadStatus
import io.github.nodyssey.data.session.SessionRepository
import io.github.nodyssey.data.settings.ComposerSurface
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.composer.EditorActions
import io.github.nodyssey.ui.postlist.toSiteError
import io.github.nodyssey.ui.space.joinedDays
import io.github.plaza.core.AppClock
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import io.github.plaza.core.runCatchingExceptCancellation
import io.github.plaza.designsys.editor.EditorAction
import io.github.plaza.designsys.editor.ToolbarLayout
import io.github.plaza.designsys.editor.editFromViewModel
import io.github.plaza.designsys.editor.insertBlockAtCaret
import io.github.plaza.designsys.editor.removeBlock
import io.github.plaza.designsys.editor.toolbarLayout
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

/**
 * Board 7f.
 *
 * The delivery states are the app's, not the site's: NodeSeek posts a message and re-renders the
 * page, so there is nothing to observe between "typed" and "there". Keeping the optimistic bubble in
 * the list — rather than clearing the field and waiting — is what makes a failed send recoverable
 * without the user having retyped anything.
 */
class MessageThreadViewModel(
    private val repository: MessageRepository,
    private val notifications: NotificationRepository,
    private val session: SessionRepository,
    private val clock: AppClock,
    uploader: ImageUploader,
    uid: Long,
    userName: String,
    /** Null in tests; the bar then shows [EditorActions.Message] and offers no wrench. */
    private val settings: SettingsRepository? = null,
    /**
     * Fallback for `level` and `joinedDays` when `message/with/{uid}` omits `created_at` (or when a
     * new conversation has no `talkTo` object yet).
     */
    private val profiles: ProfileRepository? = null,
    private val drafts: MessageDraftStore? = settings?.messageDrafts,
) : ViewModel() {
    // The avatar is known from the uid alone — it is the URL the thread itself answers with — so the
    // header draws the picture from the first frame. Left for the fetch, it drew the initial first and
    // swapped the picture in when the history arrived, even with the picture already cached.
    private val _uiState =
        MutableStateFlow(
            MessageThreadUiState(
                uid = uid,
                userName = userName,
                avatarUrl = NodeSeekSite.avatarUrl(uid),
                isRestoringDraft = drafts != null,
                isSignedIn = session.state.value.isSignedIn,
            ),
        )
    val uiState: StateFlow<MessageThreadUiState> = _uiState.asStateFlow()

    /** The composer's text and selection, held here for the same reason the post editor's is. */
    val draftState = TextFieldState()

    /**
     * The same queue the two composers use, and whichever image host 图床设置 has selected behind it.
     *
     * There is nothing message-shaped about an image here: `message/send` carries one `content`
     * string, so an image in a private message is `![](…)` spliced into that string exactly the way
     * it is spliced into a topic's body.
     */
    private val uploads = ImageUploadQueue(viewModelScope, uploader)
    private var loadJob: Job? = null
    private var pollJob: Job? = null
    private val historyMutex = Mutex()
    private var historyRevision = 0L
    private val sendJobs = mutableMapOf<String, Job>()
    private var draftHandle: MessageDraftHandle? = null
    private var activeFingerprint = session.state.value.fingerprint

    init {
        refresh()
        observeDrafts()
        // The text lives in a state object the ViewModel cannot make a StateFlow out of, so the one
        // derived bit it needs is mirrored across. Whether that is *enough* to send is the UiState's
        // question, because an upload still in flight also has a say.
        snapshotFlow { draftState.text.isNotBlank() }
            .onEach { hasText -> _uiState.update { it.copy(hasDraftText = hasText) } }
            .launchIn(viewModelScope)
        uploads.attachments
            .onEach { attachments -> _uiState.update { it.copy(attachments = attachments) } }
            .launchIn(viewModelScope)
        // At the caret, on a line of its own, as in the composers — see [insertBlockAtCaret].
        uploads.uploaded
            .onEach { attachment ->
                val markdown = attachment.markdown ?: return@onEach
                draftState.editFromViewModel { insertBlockAtCaret(markdown) }
            }.launchIn(viewModelScope)
        settings?.settings
            ?.map { it.messageToolbarActions }
            ?.distinctUntilChanged()
            ?.onEach { stored ->
                _uiState.update { it.copy(toolbar = toolbarLayout(stored, EditorActions.Message)) }
            }?.launchIn(viewModelScope)
    }

    private fun observeDrafts() {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            session.state.distinctUntilChangedBy { it.isSignedIn to it.fingerprint }.collectLatest { account ->
                val renewedSession = account.isSignedIn && account.fingerprint != activeFingerprint
                if (!account.isSignedIn || account.fingerprint != activeFingerprint) {
                    val resumePolling = pollJob?.isActive == true
                    stopPolling()
                    activeFingerprint = account.fingerprint
                    historyRevision++
                    loadJob?.cancel()
                    sendJobs.values.toList().forEach { it.cancel() }
                    sendJobs.clear()
                    draftHandle = null
                    draftState.clearText()
                    uploads.clear()
                    _uiState.update {
                        it.copy(
                            messages = emptyList(),
                            quotes = emptyList(),
                            hasDraftText = false,
                            error = if (account.isSignedIn) null else SiteError.LoginRequired,
                            syncError = null,
                            isLoading = false,
                            isSignedIn = account.isSignedIn,
                            isRestoringDraft = account.isSignedIn && drafts != null,
                            draftSaveFailed = false,
                        )
                    }
                    if (account.isSignedIn) {
                        refresh()
                        if (resumePolling) startPolling()
                    }
                }
                if (!account.isSignedIn || drafts == null) return@collectLatest
                val accountUid = runCatchingExceptCancellation {
                    profiles?.observeProfile(account.fingerprint)?.first()?.uid ?: profiles?.profile()?.uid
                }.getOrNull()?.takeIf { it > 0 }
                if (!session.state.value.isSignedIn || session.state.value.fingerprint != account.fingerprint) return@collectLatest
                val handle = runCatchingExceptCancellation { drafts.open(account.fingerprint, _uiState.value.uid, accountUid) }
                    .getOrElse {
                        _uiState.update { it.copy(isRestoringDraft = false, draftSaveFailed = true) }
                        return@collectLatest
                    }
                draftHandle = handle
                if (!session.state.value.isSignedIn || session.state.value.fingerprint != account.fingerprint) return@collectLatest
                val saved = handle.initial
                val enteredWhileLoading = draftState.text.toString()
                val currentSelection = draftState.selection
                draftState.editFromViewModel {
                    replace(0, length, saved.text + enteredWhileLoading)
                    selection = if (enteredWhileLoading.isEmpty()) {
                        TextRange(saved.selectionStart.coerceIn(0, length), saved.selectionEnd.coerceIn(0, length))
                    } else {
                        TextRange(saved.text.length + currentSelection.start, saved.text.length + currentSelection.end)
                    }
                }
                _uiState.update {
                    it.copy(
                        quotes = (saved.quotes.map { quote -> quote.toBubble(pending = false) } + it.quotes).distinctBy { quote -> quote.id },
                        messages = it.messages + saved.pending.map { pending ->
                            val restored = pending.toBubble(pending = true)
                            if (renewedSession && accountUid != null && restored.failure?.recovery == MessageRecovery.SIGN_IN) {
                                restored.copy(failure = MessageFailure(), failureReason = null)
                            } else {
                                restored
                            }
                        },
                        hasDraftText = draftState.text.isNotBlank(),
                        userName = it.userName.ifBlank { saved.userName },
                        isRestoringDraft = false,
                        syncError = if (saved.pending.isNotEmpty()) it.error ?: it.syncError else it.syncError,
                        error = if (saved.pending.isNotEmpty()) null else it.error,
                    )
                }
                saved.pending.forEach { scheduleRetryAvailability(it.failure) }
                try {
                    // StateFlow carries quotes and pending sends; snapshotFlow carries the editor's
                    // text/selection. Neither alone observes all the things a draft must keep.
                    combine(
                        snapshotFlow { Triple(draftState.text.toString(), draftState.selection.start, draftState.selection.end) },
                        uiState.map { it.copy(messages = it.messages.filter { message -> message.status != SendStatus.SENT }) }
                            .distinctUntilChanged(),
                    ) { _, _ -> draftSnapshot() }.distinctUntilChanged().collectLatest { draft ->
                        delay(AUTOSAVE_DELAY_MILLIS)
                        persistDraft(handle, draft)
                    }
                } finally {
                    // viewModelScope is cancelled when Back removes the entry. Finish the last disk
                    // write, including an edit made just before Back, using coroutines' cleanup API.
                    // The handle remains scoped to its old cookies, even after a session change.
                    // Logout invalidates it in the store before publishing signed-out state.
                    withContext(NonCancellable) { persistDraft(handle, draftSnapshot()) }
                }
            }
        }
    }

    private fun draftSnapshot(): MessageDraft {
        val state = _uiState.value
        return MessageDraft(
            userName = state.userName,
            text = draftState.text.toString(),
            selectionStart = draftState.selection.start,
            selectionEnd = draftState.selection.end,
            quotes = state.quotes.map(MessageBubble::toDraftBubble),
            pending = state.messages.filter { it.status != SendStatus.SENT }.map(MessageBubble::toDraftBubble),
        )
    }

    private suspend fun persistDraft(handle: MessageDraftHandle, draft: MessageDraft) {
        if (!session.state.value.isSignedIn) return
        runCatchingExceptCancellation { drafts?.save(handle, draft.copy(savedAtMillis = clock.nowMillis())) }
            .onSuccess { _uiState.update { it.copy(draftSaveFailed = false) } }
            .onFailure { _uiState.update { it.copy(draftSaveFailed = true) } }
    }

    fun addImages(images: List<PickedImage>) = uploads.enqueue(images)

    fun retryUpload(attachment: ImageAttachment) = uploads.retry(attachment.id)

    fun retryFailedUploads() = uploads.retryFailed()

    /** Dismissing a finished upload also takes its Markdown out of the draft it was written into. */
    fun removeAttachment(attachment: ImageAttachment) {
        val removed = uploads.remove(attachment.id) ?: return
        val markdown = removed.markdown ?: return
        draftState.editFromViewModel { removeBlock(markdown) }
    }

    /** Writes the strip through on every edit in the wrench panel; see `ToolbarCustomizeSheet`. */
    fun setToolbar(actions: List<EditorAction>) {
        val repository = settings ?: return
        viewModelScope.launch {
            repository.setComposerToolbar(ComposerSurface.MESSAGE, actions.map(EditorAction::name))
        }
    }

    /** Clears the stored arrangement, which is what makes the defaults come back. */
    fun resetToolbar() {
        val repository = settings ?: return
        viewModelScope.launch { repository.setComposerToolbar(ComposerSurface.MESSAGE, emptyList()) }
    }

    fun refresh() {
        if (!session.state.value.isSignedIn) {
            _uiState.update { it.copy(error = SiteError.LoginRequired) }
            return
        }
        historyRevision++
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                runCatchingExceptCancellation { fetchHistory() }
                    .onSuccess { thread ->
                        _uiState.update { it.copy(isLoading = false) }
                        if (thread == null) return@onSuccess
                        val resolvedName = applyHistory(thread)
                        val current = _uiState.value
                        if (
                            profiles != null &&
                            current.uid > 0 &&
                            resolvedName != MessageConversation.SYSTEM_NAME &&
                            (current.level == null || current.joinedDays == null)
                        ) {
                            runCatchingExceptCancellation { profiles.profile(current.uid) }
                                .onSuccess { profile ->
                                    _uiState.update { state ->
                                        state.copy(
                                            level = state.level ?: profile.rank,
                                            joinedDays =
                                            state.joinedDays ?: joinedDays(profile.createdAt, clock.nowMillis()),
                                        )
                                    }
                                }
                        }
                    }.onFailure { throwable ->
                        _uiState.update {
                            if (it.messages.isEmpty()) {
                                it.copy(isLoading = false, error = throwable.toSiteError())
                            } else {
                                it.copy(isLoading = false, syncError = throwable.toSiteError())
                            }
                        }
                    }
            }
    }

    /**
     * Foreground polling follows nodyssey-wa 331028a. Reads and sends are coordinated below;
     * failures back off without replacing a conversation the reader can already use.
     */
    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob =
            viewModelScope.launch {
                var interval = POLL_INTERVAL_MILLIS
                var failures = 0
                while (session.state.value.isSignedIn) {
                    if (loadJob?.isActive != true && !isSending()) {
                        val result = runCatchingExceptCancellation { fetchHistory()?.let(::applyHistory) }
                        if (result.isSuccess) {
                            failures = 0
                            interval = POLL_INTERVAL_MILLIS
                        } else {
                            failures++
                            val error = result.exceptionOrNull()!!.toSiteError()
                            if (failures >= 2 || error is SiteError.Cloudflare || error == SiteError.LoginRequired || error == SiteError.SessionUnrecognised) {
                                _uiState.update { it.copy(syncError = error) }
                            }
                            interval = (interval * 2).coerceAtMost(MAX_POLL_INTERVAL_MILLIS)
                        }
                    }
                    delay(interval)
                }
            }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun isSending() = _uiState.value.messages.any { it.status == SendStatus.SENDING }

    /**
     * Serializes manual and automatic reads. A snapshot started before a send, retry or explicit
     * refresh must not erase a message accepted while that request was in flight. Checking only
     * SENDING misses a send that both started and finished before the history came back.
     */
    private suspend fun fetchHistory(): MessageThread? =
        historyMutex.withLock {
            val sessionAtStart = session.state.value
            if (!sessionAtStart.isSignedIn || isSending()) return@withLock null
            val revision = historyRevision
            repository.thread(_uiState.value.uid).takeIf {
                revision == historyRevision && !isSending() && session.state.value == sessionAtStart
            }
        }

    private fun applyHistory(thread: MessageThread): String {
        val now = clock.nowMillis()
        val resolvedName = thread.userName.ifBlank { _uiState.value.userName }
        _uiState.update { state ->
            state.copy(
                messages =
                thread.messages.distinctBy { it.id }.map(::delivered) +
                    state.messages.filter { it.status != SendStatus.SENT }.map { pending ->
                        if (pending.failure?.recovery in setOf(MessageRecovery.VERIFY, MessageRecovery.SIGN_IN)) {
                            pending.copy(failure = MessageFailure(), failureReason = null)
                        } else {
                            pending
                        }
                    },
                userName = resolvedName,
                avatarUrl = thread.avatarUrl ?: state.avatarUrl,
                level = thread.level ?: state.level,
                joinedDays = joinedDays(thread.createdAt, now) ?: state.joinedDays,
                error = null,
                syncError = null,
                nowMillis = now,
            )
        }
        markRead(thread.unreadIds)
        return resolvedName
    }

    /**
     * Tells the server the conversation has been read, then re-reads the badge.
     *
     * Fetching the history is not what clears it — the site posts the ids back and only then does
     * `unread-count` drop, which is why the 私信 badge used to outlive reading the message.
     */
    private fun markRead(ids: List<Long>) {
        if (ids.isEmpty()) return
        notifications.noteRead(NotificationCategory.MESSAGES, ids.size)
        viewModelScope.launch {
            runCatchingExceptCancellation {
                repository.markRead(ids)
                notifications.refreshCounts()
            }
        }
    }

    /**
     * Holds a bubble over the message bar as a quote card (3e), to go out as a blockquote ahead of
     * whatever is typed.
     *
     * The site has no quote button in a conversation — `message/send` carries one `content` string
     * and nothing that points at an earlier message — so this is the app's own, written the way the
     * reply editor's 引用 writes one. Without the floor header: that header names an author and links
     * a floor, and in a thread with exactly two people in it, it would only be restating one of them.
     *
     * State rather than text in the draft, which is what this used to be: 3e draws the quotation as a
     * card above the field, and a card is the one thing that can say whose words they are without
     * putting a `>` block into a one-line pill. What text had going for it is kept where it mattered —
     * quoting a second message adds a second card instead of replacing the first, and each card has
     * its own ✕ — and what is given up is typing *between* two quotations: they now go out together,
     * in the order they were picked, ahead of the reply.
     */
    fun quote(message: MessageBubble) {
        _uiState.update { state ->
            state.copy(quotes = state.quotes.filterNot { it.id == message.id } + message)
        }
    }

    /** The ✕ on a quote card. */
    fun removeQuote(id: String) {
        _uiState.update { state -> state.copy(quotes = state.quotes.filterNot { it.id == id }) }
    }

    fun send() {
        if (!session.state.value.isSignedIn || session.state.value.fingerprint != activeFingerprint) return
        val typed = draftState.text.toString().trim()
        val state = _uiState.value
        if (typed.isEmpty() || !state.isSignedIn || state.isRestoringDraft || state.attachments.any { it.status == UploadStatus.UPLOADING || it.status == UploadStatus.WAITING }) return
        val content = _uiState.value.quotes.joinToString(separator = "") { it.content.toQuoteBlock() } + typed
        val bubble =
            MessageBubble(
                id = "pending-${Uuid.random()}",
                isMine = true,
                content = content,
                isMarkdown = true,
                sentAtMillis = clock.nowMillis(),
                sentAtText = null,
                status = SendStatus.SENDING,
            )
        draftState.clearText()
        // The Markdown went out inside `content`; the queue's cells were only ever the progress
        // report for getting it there, and leaving them up would attach them to the next message.
        uploads.clear()
        _uiState.update {
            it.copy(
                messages = it.messages + bubble,
                quotes = emptyList(),
                nowMillis = clock.nowMillis(),
                scrollToLatestRequest = it.scrollToLatestRequest + 1,
            )
        }
        deliver(bubble)
    }

    fun retry(id: String) {
        if (!session.state.value.isSignedIn || session.state.value.fingerprint != activeFingerprint) return
        val bubble = _uiState.value.messages.firstOrNull { it.id == id } ?: return
        if (bubble.status != SendStatus.FAILED) return
        if (!_uiState.value.isSignedIn) return
        val failure = bubble.failure
        if (failure != null && (failure.recovery !in setOf(MessageRecovery.RETRY, MessageRecovery.WAIT) || clock.nowMillis() < failure.retryAtMillis)) return
        replace(id) { it.copy(status = SendStatus.SENDING, failure = null, failureReason = null) }
        deliver(bubble)
    }

    fun dismissFailed(id: String) {
        _uiState.update { state -> state.copy(messages = state.messages.filterNot { it.id == id && it.status == SendStatus.FAILED }) }
    }

    private fun scheduleRetryAvailability(failure: MessageFailure?) {
        if (failure?.recovery != MessageRecovery.WAIT) return
        viewModelScope.launch {
            delay((failure.retryAtMillis - clock.nowMillis()).coerceAtLeast(0L))
            _uiState.update { it.copy(nowMillis = clock.nowMillis()) }
        }
    }

    private fun deliver(bubble: MessageBubble) {
        historyRevision++
        val account = session.state.value
        sendJobs[bubble.id] = viewModelScope.launch {
            draftHandle?.let { persistDraft(it, draftSnapshot()) }
            if (!session.state.value.isSignedIn || session.state.value.fingerprint != account.fingerprint) return@launch
            runCatchingExceptCancellation {
                repository.send(
                    uid = _uiState.value.uid,
                    content = bubble.content,
                    // Always. The site has an MD On/Off switch, and this composer does not offer it:
                    // everything it writes — a quote card's `>` block, an image, every formatting
                    // key — is Markdown, and MD off would deliver all of it as literal markup.
                    markdown = true,
                )
            }.onSuccess { accepted ->
                if (!session.state.value.isSignedIn || session.state.value.fingerprint != account.fingerprint) return@onSuccess
                historyRevision++
                replace(bubble.id) {
                    // Whatever the echo says, this bubble is ours: an ack without a sender must not
                    // send the message we just wrote over to the other side of the thread.
                    accepted?.let(::delivered)?.copy(id = bubble.id, isMine = true)
                        ?: it.copy(status = SendStatus.SENT)
                }
            }.onFailure { throwable ->
                if (!session.state.value.isSignedIn || session.state.value.fingerprint != account.fingerprint) return@onFailure
                historyRevision++
                val failure = messageFailure(throwable, clock.nowMillis())
                replace(bubble.id) {
                    it.copy(
                        status = SendStatus.FAILED,
                        // "对方已屏蔽你" is not something retrying will fix, and it is the only way
                        // the user finds that out — a bare 发送失败 invites tapping 重试 forever.
                        failureReason = (throwable as? SiteException)?.detail,
                        failure = failure,
                    )
                }
                scheduleRetryAvailability(failure)
            }
            sendJobs.remove(bubble.id)
        }
    }

    private fun replace(
        id: String,
        transform: (MessageBubble) -> MessageBubble,
    ) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { if (it.id == id) transform(it) else it },
                nowMillis = clock.nowMillis(),
            )
        }
    }

    private fun delivered(message: DirectMessage) =
        MessageBubble(
            id = message.id,
            isMine = message.isMine,
            content = message.content,
            isMarkdown = message.isMarkdown,
            sentAtMillis = message.sentAtMillis,
            sentAtText = message.sentAtText,
            status = SendStatus.SENT,
        )

    companion object {
        private const val POLL_INTERVAL_MILLIS = 10_000L
        private const val MAX_POLL_INTERVAL_MILLIS = 60_000L
        private const val AUTOSAVE_DELAY_MILLIS = 750L

        fun factory(
            container: AppContainer,
            uid: Long,
            userName: String,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    MessageThreadViewModel(
                        repository = container.messageRepository,
                        notifications = container.notificationRepository,
                        session = container.sessionRepository,
                        clock = container.clock,
                        uploader = container.imageUploader,
                        uid = uid,
                        userName = userName,
                        settings = container.settingsRepository,
                        profiles = container.profileRepository,
                    )
                }
            }
    }
}

internal fun messageFailure(throwable: Throwable, nowMillis: Long): MessageFailure {
    val error = throwable.toSiteError()
    val detail = (throwable as? SiteException)?.detail?.takeIf(String::isNotBlank)
    return when {
        error is SiteError.Cloudflare -> MessageFailure(MessageRecovery.VERIFY, detail, verificationUrl = error.url)

        error == SiteError.LoginRequired || error == SiteError.SessionUnrecognised -> MessageFailure(MessageRecovery.SIGN_IN, detail)

        // JsonApi does not expose Retry-After. Keep a conservative local cooldown until that
        // contract carries the server's delay, rather than offering a repeat request immediately.
        error == SiteError.RateLimited || error == SiteError.Http(429) -> MessageFailure(MessageRecovery.WAIT, detail, retryAtMillis = nowMillis + 60_000L)

        error == SiteError.Unknown && detail != null -> MessageFailure(MessageRecovery.NONE, detail)

        error is SiteError.LevelRequired || error == SiteError.PrivatePost || (error is SiteError.Http && error.statusCode in 400..499) -> MessageFailure(MessageRecovery.NONE, detail)

        else -> MessageFailure(MessageRecovery.RETRY, detail)
    }
}

private fun MessageBubble.toDraftBubble() = MessageDraftBubble(
    id = id,
    isMine = isMine,
    content = content,
    isMarkdown = isMarkdown,
    sentAtMillis = sentAtMillis,
    sentAtText = sentAtText,
    failure = failure ?: failureReason?.let { MessageFailure(MessageRecovery.NONE, it) },
    wasSending = status == SendStatus.SENDING,
)

private fun MessageDraftBubble.toBubble(pending: Boolean): MessageBubble {
    val restoredFailure = if (wasSending) MessageFailure(uncertain = true) else failure
    return MessageBubble(
        id = id,
        isMine = isMine,
        content = content,
        isMarkdown = isMarkdown,
        sentAtMillis = sentAtMillis,
        sentAtText = sentAtText,
        status = if (pending) SendStatus.FAILED else SendStatus.SENT,
        failureReason = restoredFailure?.detail,
        failure = restoredFailure,
    )
}

enum class SendStatus {
    SENT,
    SENDING,
    FAILED,
}

data class MessageBubble(
    val id: String,
    val isMine: Boolean,
    val content: String,
    /** The site records this per message, so an old plain message stays plain. */
    val isMarkdown: Boolean,
    val sentAtMillis: Long?,
    val sentAtText: String?,
    val status: SendStatus,
    /** The server's own words for a rejection, when it gave any. */
    val failureReason: String? = null,
    val failure: MessageFailure? = null,
)

/**
 * One message as a Markdown blockquote, with a blank line after it.
 *
 * Every line takes its own `>` — a blank line in the middle would otherwise end the quotation and
 * leave the rest of the message as the user's own words. The trailing blank line is what keeps the
 * reply that follows out of the quotation, and what lets the next 引用 land as its own block.
 */
private fun String.toQuoteBlock(): String = "> " + trim().replace("\n", "\n> ") + "\n\n"

data class MessageThreadUiState(
    val uid: Long,
    val userName: String,
    val avatarUrl: String? = null,
    val level: Int? = null,
    val joinedDays: Int? = null,
    val messages: List<MessageBubble> = emptyList(),
    val isLoading: Boolean = false,
    val error: SiteError? = null,
    val syncError: SiteError? = null,
    val draftSaveFailed: Boolean = false,
    val isRestoringDraft: Boolean = false,
    val isSignedIn: Boolean = true,
    /** Mirrored out of [MessageThreadViewModel.draftState]; the text itself is not UiState. */
    val hasDraftText: Boolean = false,
    val attachments: List<ImageAttachment> = emptyList(),
    /** Bubbles picked with 引用, oldest first; they go out as blockquotes ahead of the draft. */
    val quotes: List<MessageBubble> = emptyList(),
    /** The formatting strip's keys and the wrench panel's pool. Defaults until settings arrive. */
    val toolbar: ToolbarLayout = toolbarLayout(emptyList(), EditorActions.Message),
    val nowMillis: Long = 0L,
    /** Only an explicit send asks the reader to leave older messages for the newest bubble. */
    val scrollToLatestRequest: Long = 0L,
) {
    /**
     * Sending an image that has not finished uploading would send its placeholder.
     *
     * The same guard the two composers put on 发布, for the same reason: the Markdown carries a URL
     * the upload has not produced yet, and a message is gone the moment it is sent.
     */
    val canSend: Boolean
        get() = isSignedIn && !isRestoringDraft && hasDraftText &&
            attachments.none { it.status == UploadStatus.UPLOADING || it.status == UploadStatus.WAITING }
}
