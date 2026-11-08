package io.github.nodyssey.ui.mail

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.mail.MailError
import io.github.nodyssey.data.mail.MailException
import io.github.nodyssey.data.mail.MailMessage
import io.github.nodyssey.data.mail.MailRepository
import io.github.nodyssey.data.mail.Mailbox
import io.github.nodyssey.di.AppContainer
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MailInboxUiState(
    val initializing: Boolean = true,
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val authorizing: Boolean = false,
    val mailboxes: List<Mailbox> = emptyList(),
    val selectedMailboxId: Long? = null,
    val messages: List<MailMessage> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val nextOffset: Int? = null,
    val error: MailError? = null,
    val confirmingDisconnect: Boolean = false,
)

class MailInboxViewModel(private val repository: MailRepository) : ViewModel() {
    // Deliberately not rememberSaveable/SavedStateHandle: a credential must not enter a back stack.
    val apiKey = TextFieldState()
    private val state = MutableStateFlow(MailInboxUiState())
    private var generation = 0L
    private var loadJob: Job? = null
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
                generation = connection.generation
                loadJob?.cancel()
                val sameAuthorization = authorizationGeneration == connection.generation && !connection.connected
                state.value = MailInboxUiState(
                    initializing = !connection.initialized,
                    connected = connection.connected,
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
        state.update { it.copy(connecting = true, error = null) }
        authorizationJob = viewModelScope.launch {
            try {
                runCatchingExceptCancellation {
                    val attempt = repository.beginAuthorization()
                    currentCoroutineContext().ensureActive()
                    authorizationGeneration = attempt
                    state.update { it.copy(authorizing = website, connecting = !website, error = null) }
                    if (!website) repository.authorize(attempt)
                }.onFailure { error ->
                    if (serial == authorizationSerial) state.update { it.copy(error = error.mailError()) }
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
        state.update { it.copy(authorizing = false, connecting = false) }
        if (attempt == null) return
        viewModelScope.launch {
            runCatchingExceptCancellation { repository.cancelAuthorization(attempt) }
                .onFailure { error -> state.update { it.copy(error = error.mailError()) } }
        }
    }

    fun requestDisconnect() = state.update { it.copy(confirmingDisconnect = true) }

    fun dismissDisconnect() = state.update { it.copy(confirmingDisconnect = false) }

    fun disconnect() {
        cancelAuthorization()
        loadJob?.cancel()
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
                val selected = state.value.selectedMailboxId?.takeIf { id -> mailboxes.any { it.id == id } }
                    ?: mailboxes.firstOrNull()?.id
                currentCoroutineContext().ensureActive()
                if (expected != generation) return@runCatchingExceptCancellation
                state.update { it.copy(mailboxes = mailboxes, selectedMailboxId = selected) }
                val page = selected?.let { repository.inbox(expected, it) }
                currentCoroutineContext().ensureActive()
                if (expected == generation && state.value.selectedMailboxId == selected) {
                    state.update {
                        it.copy(
                            mailboxes = mailboxes,
                            selectedMailboxId = selected,
                            messages = page?.messages.orEmpty().distinctBy(MailMessage::id),
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
        if (id == state.value.selectedMailboxId || state.value.mailboxes.none { it.id == id }) return
        loadJob?.cancel()
        state.update { it.copy(selectedMailboxId = id, messages = emptyList(), nextOffset = null, loadingMore = false) }
        loadPage(append = false)
    }

    fun loadMore() {
        if (!state.value.loading && !state.value.loadingMore && state.value.nextOffset != null) loadPage(append = true)
    }

    private fun loadPage(append: Boolean) {
        val expected = generation
        val mailboxId = state.value.selectedMailboxId ?: return
        val offset = if (append) state.value.nextOffset ?: return else 0
        state.update { it.copy(loading = !append, loadingMore = append, error = null) }
        loadJob = viewModelScope.launch {
            runCatchingExceptCancellation { repository.inbox(expected, mailboxId, offset) }
                .onSuccess { page ->
                    currentCoroutineContext().ensureActive()
                    if (expected == generation && state.value.selectedMailboxId == mailboxId) {
                        state.update {
                            it.copy(
                                messages = ((if (append) it.messages else emptyList()) + page.messages)
                                    .distinctBy(MailMessage::id),
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
            initializer { MailInboxViewModel(container.mailRepository) }
        }
    }
}

internal fun Throwable.mailError(): MailError = (this as? MailException)?.error ?: MailError.NETWORK
