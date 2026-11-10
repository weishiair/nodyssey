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
import io.github.nodyssey.data.mail.MailMessage
import io.github.nodyssey.data.mail.MailPreview
import io.github.nodyssey.data.mail.MailRepository
import io.github.nodyssey.data.mail.Mailbox
import io.github.nodyssey.di.AppContainer
import io.github.plaza.core.AppClock
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.runCatchingExceptCancellation
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
    private var defaultMailboxJob: Job? = null
    private var markAllReadJob: Job? = null
    private var authorizationGeneration: Long? = null
    private var authorizationJob: Job? = null
    private var authorizationSerial = 0L
    val uiState = combine(state, repository.readStates) { inbox, reads ->
        if (reads.generation != generation) {
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
                state.map { MailInboxReadiness(it.generation, it.selectedMailboxId, it.loading || it.loadingMore) }.distinctUntilChanged(),
            ) { snapshot, _ -> snapshot }.collectLatest { snapshot ->
                val current = state.value
                if (snapshot == null || snapshot.generation != generation || snapshot.mailboxId != current.selectedMailboxId ||
                    current.loading || current.loadingMore
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
                    if (generation != snapshot.generation || it.selectedMailboxId != snapshot.mailboxId || it.loading || it.loadingMore) {
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
        startAuthorization(website = true)
    }

    private fun startAuthorization(website: Boolean) {
        if (state.value.connecting || state.value.authorizing || authorizationJob?.isActive == true) return
        apiKey.clearText()
        val serial = ++authorizationSerial
        state.update {
            it.copy(
                connecting = true,
                error = null,
                challengeVerificationAvailable = false,
                verifyingChallenge = false,
            )
        }
        authorizationJob = viewModelScope.launch {
            try {
                runCatchingExceptCancellation {
                    val attempt = repository.beginAuthorization()
                    currentCoroutineContext().ensureActive()
                    authorizationGeneration = attempt
                    state.update { it.copy(authorizing = website, connecting = !website, error = null) }
                    if (!website) repository.authorize(attempt)
                }.onFailure { error ->
                    val mailError = error.mailError()
                    if (serial == authorizationSerial) {
                        state.update {
                            it.copy(
                                error = mailError,
                                challengeVerificationAvailable = !website && mailError == MailError.CHALLENGE,
                                verifyingChallenge = false,
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
                    state.update { it.copy(connecting = false, authorizing = false) }
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
            state.update { it.copy(verifyingChallenge = true) }
        }
    }

    fun retryAfterChallengeVerification() {
        if (!state.value.verifyingChallenge && !state.value.challengeVerificationAvailable) return
        state.update { it.copy(verifyingChallenge = false) }
        authorize()
    }

    fun requestDisconnect() = state.update { it.copy(confirmingDisconnect = true) }

    fun dismissDisconnect() = state.update { it.copy(confirmingDisconnect = false) }

    fun disconnect() {
        cancelAuthorization()
        loadJob?.cancel()
        defaultMailboxJob?.cancel()
        markAllReadJob?.cancel()
        apiKey.clearText()
        viewModelScope.launch {
            runCatchingExceptCancellation { repository.disconnect() }
                .onFailure { error -> state.update { it.copy(error = error.mailError()) } }
        }
    }

    fun refresh() {
        if (!state.value.connected) return
        loadJob?.cancel()
        val expected = generation
        state.update { it.copy(loading = true, loadingMore = false, error = null) }
        loadJob = viewModelScope.launch {
            runCatchingExceptCancellation {
                val mailboxes = repository.mailboxes(expected)
                val defaultMailboxId = repository.defaultMailboxId(expected)
                val selected = state.value.selectedMailboxId?.takeIf { id -> mailboxes.any { it.id == id } }
                    ?: defaultMailboxId?.takeIf { id -> mailboxes.any { it.id == id } }
                    ?: mailboxes.firstOrNull()?.id
                if (selected != defaultMailboxId) repository.setDefaultMailboxId(expected, selected)
                currentCoroutineContext().ensureActive()
                if (expected != generation) return@runCatchingExceptCancellation
                state.update { it.copy(mailboxes = mailboxes, selectedMailboxId = selected) }
                val page = selected?.let { repository.inbox(expected, it) }
                val messages = page?.messages.orEmpty().distinctBy(MailMessage::id)
                val previews = withContext(dispatchers.default) { messages.associate { mail -> mail.id to MailPreview.from(mail) } }
                currentCoroutineContext().ensureActive()
                if (expected == generation && state.value.selectedMailboxId == selected) {
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
                if (expected == generation) state.update { it.copy(loading = false, error = error.mailError()) }
            }
        }
    }

    fun selectMailbox(id: Long) {
        if (state.value.markingAllRead) return
        if (id == state.value.selectedMailboxId || state.value.mailboxes.none { it.id == id }) return
        loadJob?.cancel()
        state.update {
            it.copy(selectedMailboxId = id, messages = emptyList(), previews = emptyMap(), nextOffset = null, loadingMore = false)
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

    fun loadMore() {
        if (!state.value.loading && !state.value.loadingMore && state.value.nextOffset != null) loadPage(append = true)
    }

    fun markAllRead() {
        val current = state.value
        val mailboxId = current.selectedMailboxId ?: return
        if (!current.connected || current.markingAllRead || current.loading || current.loadingMore) return
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
        val mailboxId = state.value.selectedMailboxId ?: return
        val offset = if (append) state.value.nextOffset ?: return else 0
        state.update { it.copy(loading = !append, loadingMore = append, error = null) }
        loadJob = viewModelScope.launch {
            runCatchingExceptCancellation { repository.inbox(expected, mailboxId, offset) }
                .onSuccess { page ->
                    val messages = ((if (append) state.value.messages else emptyList()) + page.messages).distinctBy(MailMessage::id)
                    val previews = withContext(dispatchers.default) { messages.associate { mail -> mail.id to MailPreview.from(mail) } }
                    currentCoroutineContext().ensureActive()
                    if (expected == generation && state.value.selectedMailboxId == mailboxId) {
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
                    if (expected == generation && state.value.selectedMailboxId == mailboxId) {
                        state.update {
                            it.copy(loading = false, loadingMore = false, error = error.mailError())
                        }
                    }
                }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { MailInboxViewModel(container.mailRepository, container.dispatchers, container.clock) }
        }
    }
}

private data class MailInboxReadiness(val generation: Long, val mailboxId: Long?, val loading: Boolean)

internal fun Throwable.mailError(): MailError = (this as? MailException)?.error ?: MailError.NETWORK
