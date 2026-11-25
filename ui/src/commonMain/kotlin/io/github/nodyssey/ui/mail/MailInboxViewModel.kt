package io.github.nodyssey.ui.mail

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.mail.MailAuthMode
import io.github.nodyssey.data.mail.MailError
import io.github.nodyssey.data.mail.MailException
import io.github.nodyssey.data.mail.MailFolder
import io.github.nodyssey.data.mail.MailListFilter
import io.github.nodyssey.data.mail.MailMessage
import io.github.nodyssey.data.mail.MailPreview
import io.github.nodyssey.data.mail.MailRepository
import io.github.nodyssey.data.mail.MailTag
import io.github.nodyssey.data.mail.Mailbox
import io.github.nodyssey.data.mail.isMailAddress
import io.github.nodyssey.di.AppContainer
import io.github.plaza.core.AppClock
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MailInboxUiState(
    val initializing: Boolean = true,
    val connected: Boolean = false,
    val generation: Long = 0,
    val authMode: MailAuthMode? = null,
    val connecting: Boolean = false,
    val authorizing: Boolean = false,
    val mailboxes: List<Mailbox> = emptyList(),
    val selectedMailboxId: Long? = null,
    val messages: List<MailMessage> = emptyList(),
    val previews: Map<Long, MailPreview> = emptyMap(),
    val nowMillis: Long = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val markingAllRead: Boolean = false,
    val allReadComplete: Boolean = false,
    val markAllReadError: MailError? = null,
    val nextOffset: Int? = null,
    val error: MailError? = null,
    val confirmingDisconnect: Boolean = false,
    val challengeVerificationAvailable: Boolean = false,
    val verifyingChallenge: Boolean = false,
    val filter: MailListFilter = MailListFilter(),
    val unreadOnly: Boolean = false,
    val folders: List<MailFolder> = emptyList(),
    val loadingFolders: Boolean = false,
    val foldersError: MailError? = null,
    val selectionMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val changingMessages: Boolean = false,
    val mailboxActionError: MailError? = null,
    val alias: MailAliasState = MailAliasState(),
    val tags: List<MailTag> = emptyList(),
    val loadingTags: Boolean = false,
    val creatingTag: Boolean = false,
    val tagsError: MailError? = null,
    val createdTag: MailTag? = null,
) {
    val visibleMessages: List<MailMessage> get() = if (unreadOnly) messages.filterNot(MailMessage::isRead) else messages
}

data class MailAliasState(
    val checkedAddress: String? = null,
    val available: Boolean = false,
    val busy: Boolean = false,
    val created: Mailbox? = null,
    val error: MailError? = null,
)

class MailInboxViewModel(
    private val repository: MailRepository,
    private val dispatchers: AppDispatchers = AppDispatchers(),
    private val clock: AppClock = AppClock.System,
) : ViewModel() {
    // Deliberately not rememberSaveable/SavedStateHandle: a credential must not enter a back stack.
    val apiKey = TextFieldState()
    private val state = MutableStateFlow(MailInboxUiState())
    private var generation = 0L
    private var loadJob: Job? = null
    private var loadSerial = 0L
    private var defaultMailboxJob: Job? = null
    private var markAllReadJob: Job? = null
    private var foldersJob: Job? = null
    private var mailboxActionJob: Job? = null
    private var aliasJob: Job? = null
    private var tagsJob: Job? = null
    private var authorizationGeneration: Long? = null
    private var authorizationJob: Job? = null
    private var authorizationSerial = 0L
    val uiState = combine(state, repository.readStates) { inbox, reads ->
        if (reads.generation != generation || inbox.filter.isSent) {
            inbox
        } else {
            inbox.copy(
                messages = inbox.messages.map { mail ->
                    reads.values[mail.id]?.let { mail.copy(isRead = it) } ?: mail
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, state.value)

    init {
        viewModelScope.launch {
            repository.connection.collect { connection ->
                if (connection.generation == generation && connection.connected == state.value.connected &&
                    connection.initialized == !state.value.initializing
                ) {
                    return@collect
                }
                if (generation != connection.generation) {
                    defaultMailboxJob?.cancel()
                    markAllReadJob?.cancel()
                    foldersJob?.cancel()
                    mailboxActionJob?.cancel()
                    aliasJob?.cancel()
                    tagsJob?.cancel()
                }
                generation = connection.generation
                loadJob?.cancel()
                val sameAuthorization = authorizationGeneration == connection.generation && !connection.connected
                state.value = MailInboxUiState(
                    initializing = !connection.initialized,
                    connected = connection.connected,
                    generation = connection.generation,
                    authMode = connection.authMode,
                    connecting = !connection.connected && state.value.connecting &&
                        (authorizationGeneration == null || sameAuthorization),
                    authorizing = state.value.authorizing && sameAuthorization,
                    challengeVerificationAvailable = state.value.challengeVerificationAvailable && sameAuthorization,
                    verifyingChallenge = state.value.verifyingChallenge && sameAuthorization,
                    error = connection.error,
                )
                if (authorizationGeneration != connection.generation || connection.connected) {
                    authorizationGeneration = null
                }
                if (connection.connected) refresh()
            }
        }
        viewModelScope.launch {
            combine(
                repository.inboxUpdates,
                state.map {
                    MailInboxReadiness(
                        it.generation,
                        it.selectedMailboxId,
                        it.loading || it.loadingMore || it.changingMessages || !it.filter.isInbox,
                    )
                }.distinctUntilChanged(),
            ) { snapshot, _ -> snapshot }.collectLatest { snapshot ->
                val current = state.value
                if (snapshot == null || snapshot.generation != generation || snapshot.mailboxId != current.selectedMailboxId ||
                    current.loading || current.loadingMore || current.changingMessages || !current.filter.isInbox
                ) {
                    return@collectLatest
                }
                // Keep already-loaded older pages while replacing the fresh head of the inbox.
                val oldest = snapshot.page.messages.minOfOrNull(MailMessage::id)
                val tail = if (current.messages.size > 20 && snapshot.page.nextOffset != null && oldest != null) {
                    current.messages.filter { it.id < oldest }
                } else {
                    emptyList()
                }
                val messages = (snapshot.page.messages + tail).distinctBy(MailMessage::id).take(snapshot.page.total)
                val previews = withContext(dispatchers.default) { messages.associate { it.id to MailPreview.from(it) } }
                currentCoroutineContext().ensureActive()
                state.update {
                    if (generation != snapshot.generation || it.selectedMailboxId != snapshot.mailboxId || it.loading || it.loadingMore || it.changingMessages || !it.filter.isInbox) {
                        it
                    } else {
                        it.copy(
                            messages = messages,
                            previews = previews,
                            nowMillis = clock.nowMillis(),
                            nextOffset = messages.size.takeIf { size -> size < snapshot.page.total && snapshot.page.nextOffset != null },
                            error = null,
                        )
                    }
                }
            }
        }
        initialize()
    }

    fun initialize() {
        viewModelScope.launch {
            runCatchingExceptCancellation { repository.initialize() }.onFailure { error ->
                state.update { it.copy(initializing = false, error = error.mailError()) }
            }
        }
    }

    fun connect() {
        if (state.value.connecting) return
        val key = apiKey.text.toString()
        apiKey.clearText()
        state.update { it.copy(connecting = true, error = null) }
        val serial = ++authorizationSerial
        authorizationJob = viewModelScope.launch {
            try {
                runCatchingExceptCancellation { repository.connect(key) }
                    .onFailure { error -> state.update { it.copy(error = error.mailError()) } }
            } finally {
                if (serial == authorizationSerial) state.update { it.copy(connecting = false) }
            }
        }
    }

    fun authorize() {
        startAuthorization(website = !repository.supportsNativeAuthorization)
    }

    fun authorizeOnWebsite() {
        startAuthorization(website = true, recoveringChallenge = state.value.challengeVerificationAvailable)
    }

    private fun startAuthorization(website: Boolean, recoveringChallenge: Boolean = false, automaticallyVerifyChallenge: Boolean = true) {
        if (state.value.connecting || state.value.authorizing || authorizationJob?.isActive == true) return
        apiKey.clearText()
        val serial = ++authorizationSerial
        state.update {
            it.copy(
                connecting = true,
                error = null,
                challengeVerificationAvailable = recoveringChallenge,
                verifyingChallenge = recoveringChallenge,
            )
        }
        authorizationJob = viewModelScope.launch {
            try {
                runCatchingExceptCancellation {
                    val attempt = repository.beginAuthorization()
                    currentCoroutineContext().ensureActive()
                    authorizationGeneration = attempt
                    state.update {
                        it.copy(
                            authorizing = website,
                            connecting = !website,
                            error = null,
                            challengeVerificationAvailable = recoveringChallenge,
                            verifyingChallenge = recoveringChallenge,
                        )
                    }
                    if (!website) repository.authorize(attempt)
                }.onFailure { error ->
                    val mailError = error.mailError()
                    if (serial == authorizationSerial) {
                        val challenge = !website && mailError == MailError.CHALLENGE
                        state.update {
                            it.copy(
                                error = mailError,
                                connecting = false,
                                // Use the provider's real web flow: the forum home page need not
                                // trigger the rule that refused the authorization request.
                                authorizing = challenge && automaticallyVerifyChallenge,
                                challengeVerificationAvailable = challenge,
                                verifyingChallenge = challenge && automaticallyVerifyChallenge,
                            )
                        }
                    }
                }
            } finally {
                if (serial == authorizationSerial && !state.value.authorizing) {
                    authorizationGeneration = null
                    state.update { it.copy(connecting = false) }
                }
            }
        }
    }

    fun completeAuthorization(token: String) {
        val attempt = authorizationGeneration ?: return
        if (!state.value.authorizing || state.value.connecting) return
        state.update { it.copy(connecting = true) }
        authorizationJob = viewModelScope.launch {
            try {
                runCatchingExceptCancellation { repository.completeAuthorization(attempt, token) }
                    .onFailure { error -> state.update { it.copy(error = error.mailError()) } }
            } finally {
                if (authorizationGeneration == attempt) {
                    authorizationGeneration = null
                    state.update { it.copy(connecting = false, authorizing = false, verifyingChallenge = false) }
                }
            }
        }
    }

    fun cancelAuthorization() {
        val attempt = authorizationGeneration
        ++authorizationSerial
        authorizationGeneration = null
        authorizationJob?.cancel()
        state.update { it.copy(authorizing = false, connecting = false, verifyingChallenge = false) }
        if (attempt == null) return
        viewModelScope.launch {
            runCatchingExceptCancellation { repository.cancelAuthorization(attempt) }
                .onFailure { error -> state.update { it.copy(error = error.mailError()) } }
        }
    }

    fun openChallengeVerification() {
        if (state.value.challengeVerificationAvailable && !state.value.connecting) {
            startAuthorization(website = true, recoveringChallenge = true)
        }
    }

    fun retryAfterChallengeVerification() {
        val current = state.value
        if (!current.verifyingChallenge || !current.authorizing || current.connecting) return
        state.update { it.copy(authorizing = false, verifyingChallenge = false) }
        // A renewed cookie is a reason to try once, not proof the endpoint will accept it.
        // A second challenge stays on the inbox until the reader explicitly opens verification.
        startAuthorization(website = false, automaticallyVerifyChallenge = false)
    }

    fun requestDisconnect() = state.update { it.copy(confirmingDisconnect = true) }

    fun dismissDisconnect() = state.update { it.copy(confirmingDisconnect = false) }

    fun disconnect() {
        cancelAuthorization()
        loadJob?.cancel()
        defaultMailboxJob?.cancel()
        markAllReadJob?.cancel()
        foldersJob?.cancel()
        mailboxActionJob?.cancel()
        aliasJob?.cancel()
        tagsJob?.cancel()
        apiKey.clearText()
        viewModelScope.launch {
            runCatchingExceptCancellation { repository.disconnect() }
                .onFailure { error -> state.update { it.copy(error = error.mailError()) } }
        }
    }

    fun refresh() {
        if (!state.value.connected || state.value.changingMessages) return
        loadJob?.cancel()
        val serial = ++loadSerial
        val expected = generation
        val filter = state.value.filter
        state.update { it.copy(loading = true, loadingMore = false, error = null) }
        loadJob = viewModelScope.launch {
            runCatchingExceptCancellation {
                val mailboxes = repository.mailboxes(expected)
                val selected = state.value.selectedMailboxId?.takeIf { id -> mailboxes.any { it.id == id } }
                if (selected != repository.defaultMailboxId(expected)) repository.setDefaultMailboxId(expected, selected)
                currentCoroutineContext().ensureActive()
                if (expected != generation || serial != loadSerial) return@runCatchingExceptCancellation
                state.update { it.copy(mailboxes = mailboxes, selectedMailboxId = selected) }
                val page = if (mailboxes.isEmpty()) null else requestPage(expected, selected, 0, filter)
                val messages = page?.messages.orEmpty().distinctBy(MailMessage::id)
                val previews = withContext(dispatchers.default) { messages.associate { mail -> mail.id to MailPreview.from(mail) } }
                currentCoroutineContext().ensureActive()
                if (expected == generation && serial == loadSerial && state.value.selectedMailboxId == selected) {
                    state.update {
                        it.copy(
                            mailboxes = mailboxes,
                            selectedMailboxId = selected,
                            messages = messages,
                            previews = previews,
                            nowMillis = clock.nowMillis(),
                            nextOffset = page?.nextOffset,
                            loading = false,
                        )
                    }
                }
            }.onFailure { error ->
                if (expected == generation && serial == loadSerial) state.update { it.copy(loading = false, error = error.mailError()) }
            }
        }
    }

    fun selectMailbox(id: Long?) {
        if (state.value.markingAllRead || state.value.changingMessages) return
        if (id == state.value.selectedMailboxId || (id != null && state.value.mailboxes.none { it.id == id })) return
        loadJob?.cancel()
        state.update {
            it.copy(
                selectedMailboxId = id,
                messages = emptyList(),
                previews = emptyMap(),
                nextOffset = null,
                loadingMore = false,
                allReadComplete = false,
                markAllReadError = null,
                selectionMode = false,
                selectedIds = emptySet(),
            )
        }
        val expected = generation
        defaultMailboxJob?.cancel()
        defaultMailboxJob = viewModelScope.launch {
            runCatchingExceptCancellation { repository.setDefaultMailboxId(expected, id) }
                .onFailure { error ->
                    if (expected == generation && state.value.selectedMailboxId == id) {
                        state.update { it.copy(error = error.mailError()) }
                    }
                }
        }
        loadPage(append = false)
    }

    fun changeFilter(filter: MailListFilter) {
        val current = state.value
        val normalized = filter.copy(search = filter.search.trim(), tagId = filter.tagId.takeUnless { filter.isSent })
        if (!current.connected || current.markingAllRead || current.changingMessages || current.filter == normalized) return
        loadJob?.cancel()
        state.update {
            it.copy(
                filter = normalized,
                unreadOnly = it.unreadOnly && !normalized.isSent,
                messages = emptyList(),
                previews = emptyMap(),
                nextOffset = null,
                selectionMode = false,
                selectedIds = emptySet(),
            )
        }
        loadPage(append = false)
    }

    fun toggleUnreadOnly() {
        if (state.value.changingMessages || state.value.filter.isSent) return
        state.update { it.copy(unreadOnly = !it.unreadOnly, selectionMode = false, selectedIds = emptySet()) }
    }

    fun loadFolders() {
        if (state.value.loadingFolders) return
        val expected = generation
        state.update { it.copy(loadingFolders = true, foldersError = null) }
        foldersJob = viewModelScope.launch {
            try {
                runCatchingExceptCancellation { repository.folders(expected) }
                    .onSuccess { folders -> state.update { it.copy(folders = folders) } }
                    .onFailure { error -> state.update { it.copy(foldersError = error.mailError()) } }
            } finally {
                if (generation == expected) state.update { it.copy(loadingFolders = false) }
            }
        }
    }

    fun toggleSelection() {
        if (state.value.changingMessages || state.value.filter.isSent) return
        state.update { it.copy(selectionMode = !it.selectionMode, selectedIds = emptySet()) }
    }

    fun toggleSelected(id: Long) {
        state.update {
            if (it.changingMessages || !it.selectionMode || it.messages.none { mail -> mail.id == id }) return@update it
            it.copy(selectedIds = if (id in it.selectedIds) it.selectedIds - id else it.selectedIds + id)
        }
    }

    fun selectVisible() {
        val visible = uiState.value.visibleMessages.mapTo(mutableSetOf(), MailMessage::id)
        state.update {
            if (it.changingMessages || !it.selectionMode) return@update it
            val selected = if (it.selectedIds.containsAll(visible)) it.selectedIds - visible else it.selectedIds + visible
            it.copy(selectedIds = selected)
        }
    }

    fun markSelectedRead() = changeSelected { expected, mail -> repository.markRead(expected, mail, true) }

    fun moveSelected(folderId: Long?) = changeSelected(reload = true) { expected, mail ->
        repository.move(expected, mail.id, folderId)
    }

    fun toggleFlag(mail: MailMessage) {
        if (state.value.changingMessages || state.value.filter.isSent) return
        val expected = generation
        state.update { it.copy(changingMessages = true) }
        mailboxActionJob = viewModelScope.launch {
            try {
                runCatchingExceptCancellation { repository.flag(expected, mail.id, !mail.isFlagged) }
                    .onSuccess {
                        state.update { inbox ->
                            inbox.copy(messages = inbox.messages.map { if (it.id == mail.id) it.copy(isFlagged = !mail.isFlagged) else it })
                        }
                    }
                    .onFailure { reportMailboxActionError(expected, it) }
            } finally {
                if (generation == expected) state.update { it.copy(changingMessages = false) }
            }
        }
    }

    private fun changeSelected(reload: Boolean = false, change: suspend (Long, MailMessage) -> Unit) {
        val current = state.value
        if (current.filter.isSent || current.changingMessages || current.markingAllRead || current.loading || current.loadingMore) return
        val targets = current.messages.filter { it.id in current.selectedIds }
        if (targets.isEmpty()) return
        val expected = generation
        state.update { it.copy(changingMessages = true) }
        mailboxActionJob = viewModelScope.launch {
            var changed = false
            try {
                for (mail in targets) {
                    change(expected, mail)
                    changed = true
                    state.update { it.copy(selectedIds = it.selectedIds - mail.id) }
                }
                state.update { it.copy(selectionMode = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                reportMailboxActionError(expected, error)
            } finally {
                if (generation == expected) {
                    if (reload && changed && currentCoroutineContext().isActive) loadPage(append = false)
                    state.update { it.copy(changingMessages = false) }
                }
            }
        }
    }

    fun refreshSent() {
        val current = state.value
        if (current.connected && current.filter.isSent && !current.loading && !current.loadingMore) loadPage(append = false)
    }

    fun loadTags() {
        if (state.value.loadingTags || state.value.creatingTag) return
        val expected = generation
        state.update { it.copy(loadingTags = true, tagsError = null) }
        tagsJob = viewModelScope.launch {
            val result = runCatchingExceptCancellation { repository.tags(expected) }
            if (generation == expected) {
                state.update { inbox ->
                    result.fold(
                        onSuccess = { inbox.copy(tags = it, loadingTags = false) },
                        onFailure = { inbox.copy(loadingTags = false, tagsError = it.mailError()) },
                    )
                }
            }
        }
    }

    fun createTag(name: String) {
        val value = name.trim()
        if (value.isEmpty() || state.value.creatingTag || state.value.loadingTags) return
        val expected = generation
        state.update { it.copy(creatingTag = true, tagsError = null, createdTag = null) }
        tagsJob = viewModelScope.launch {
            val result = runCatchingExceptCancellation { repository.createTag(expected, value) }
            if (generation == expected) {
                state.update { inbox ->
                    result.fold(
                        onSuccess = { inbox.copy(creatingTag = false, tags = (inbox.tags + it).distinctBy(MailTag::id), createdTag = it) },
                        onFailure = { inbox.copy(creatingTag = false, tagsError = it.mailError()) },
                    )
                }
            }
        }
    }

    fun clearCreatedTag() = state.update { it.copy(createdTag = null) }

    fun setSelectedTag(tagId: Long, attach: Boolean) = changeSelected(reload = true) { expected, mail ->
        val tags = mail.tags
        if (tags == null || tags.any { it.id == tagId } != attach) repository.setTag(expected, mail.id, tagId, attach)
    }

    fun resetAlias() {
        if (state.value.alias.busy) return
        state.update { it.copy(alias = MailAliasState()) }
    }

    fun checkAlias(address: String) {
        val value = address.trim()
        if (!isMailAddress(value) || state.value.alias.busy) return
        val expected = generation
        state.update { it.copy(alias = MailAliasState(busy = true)) }
        aliasJob = viewModelScope.launch {
            val result = runCatchingExceptCancellation { repository.checkAddress(expected, value) }
            if (generation == expected) {
                state.update {
                    it.copy(
                        alias = result.fold(
                            onSuccess = { available -> MailAliasState(checkedAddress = value, available = available) },
                            onFailure = { error -> MailAliasState(error = error.mailError()) },
                        ),
                    )
                }
            }
        }
    }

    fun createAlias(address: String) {
        val value = address.trim()
        val alias = state.value.alias
        if (alias.busy || !alias.available || alias.checkedAddress != value) return
        val expected = generation
        state.update { it.copy(alias = alias.copy(busy = true, error = null)) }
        aliasJob = viewModelScope.launch {
            runCatchingExceptCancellation { repository.createMailbox(expected, value) }.fold(
                onSuccess = { mailbox ->
                    if (generation == expected) {
                        val previouslyEmpty = state.value.mailboxes.isEmpty()
                        state.update {
                            it.copy(
                                mailboxes = (it.mailboxes + mailbox).distinctBy(Mailbox::id),
                                alias = MailAliasState(created = mailbox),
                            )
                        }
                        if (previouslyEmpty) loadPage(append = false)
                    }
                },
                onFailure = { error ->
                    if (generation == expected) state.update { it.copy(alias = MailAliasState(error = error.mailError())) }
                },
            )
        }
    }

    fun dismissMailboxActionError() = state.update { it.copy(mailboxActionError = null) }

    private fun reportMailboxActionError(expected: Long, error: Throwable) {
        if (expected == generation) state.update { it.copy(mailboxActionError = error.mailError()) }
    }

    fun loadMore() {
        if (!state.value.loading && !state.value.loadingMore && state.value.nextOffset != null) loadPage(append = true)
    }

    fun markAllRead() {
        val current = state.value
        val mailboxId = current.selectedMailboxId
        if (!current.connected || !current.filter.isInbox || current.unreadOnly || current.markingAllRead || current.changingMessages || current.loading || current.loadingMore) return
        val expected = generation
        state.update { it.copy(markingAllRead = true, allReadComplete = false, markAllReadError = null) }
        markAllReadJob = viewModelScope.launch {
            try {
                runCatchingExceptCancellation { repository.markAllRead(expected, mailboxId) }
                    .onSuccess {
                        if (generation == expected) state.update { it.copy(allReadComplete = true) }
                    }
                    .onFailure { error ->
                        if (generation == expected) state.update { it.copy(markAllReadError = error.mailError()) }
                    }
            } finally {
                if (generation == expected) state.update { it.copy(markingAllRead = false) }
            }
        }
    }

    fun dismissAllReadResult() {
        state.update { it.copy(allReadComplete = false, markAllReadError = null) }
    }

    private fun loadPage(append: Boolean) {
        val expected = generation
        val mailboxId = state.value.selectedMailboxId
        val filter = state.value.filter
        val serial = ++loadSerial
        val offset = if (append) state.value.nextOffset ?: return else 0
        state.update { it.copy(loading = !append, loadingMore = append, error = null) }
        loadJob = viewModelScope.launch {
            runCatchingExceptCancellation { requestPage(expected, mailboxId, offset, filter) }
                .onSuccess { page ->
                    val messages = ((if (append) state.value.messages else emptyList()) + page.messages).distinctBy(MailMessage::id)
                    val previews = withContext(dispatchers.default) { messages.associate { mail -> mail.id to MailPreview.from(mail) } }
                    currentCoroutineContext().ensureActive()
                    if (expected == generation && serial == loadSerial && state.value.selectedMailboxId == mailboxId) {
                        state.update {
                            it.copy(
                                messages = messages,
                                previews = previews,
                                nowMillis = clock.nowMillis(),
                                nextOffset = page.nextOffset,
                                loading = false,
                                loadingMore = false,
                            )
                        }
                    }
                }.onFailure { error ->
                    if (expected == generation && serial == loadSerial && state.value.selectedMailboxId == mailboxId) {
                        state.update {
                            it.copy(loading = false, loadingMore = false, error = error.mailError())
                        }
                    }
                }
        }
    }

    private suspend fun requestPage(generation: Long, mailboxId: Long?, offset: Int, filter: MailListFilter) =
        if (filter.isInbox) repository.inbox(generation, mailboxId, offset) else repository.list(generation, mailboxId, offset, filter)

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { MailInboxViewModel(container.mailRepository, container.dispatchers, container.clock) }
        }
    }
}

private data class MailInboxReadiness(val generation: Long, val mailboxId: Long?, val loading: Boolean)

internal fun Throwable.mailError(): MailError = (this as? MailException)?.error ?: MailError.NETWORK
