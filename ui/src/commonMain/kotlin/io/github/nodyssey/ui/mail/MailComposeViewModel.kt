package io.github.nodyssey.ui.mail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.mail.MailDraft
import io.github.nodyssey.data.mail.MailError
import io.github.nodyssey.data.mail.MailException
import io.github.nodyssey.data.mail.MailRepository
import io.github.nodyssey.data.mail.Mailbox
import io.github.nodyssey.data.mail.OutgoingMail
import io.github.nodyssey.di.AppContainer
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

internal enum class MailComposeAction { CLOSE, OPEN_DRAFTS, START_BLANK }

internal data class MailComposeState(
    val loading: Boolean = true,
    val expired: Boolean = false,
    val mailboxes: List<Mailbox> = emptyList(),
    val draft: MailDraft = MailDraft(),
    val savingDraft: Boolean = false,
    val nextAction: MailComposeAction? = null,
    val draftStorageError: Boolean = false,
    val sending: Boolean = false,
    val submitted: Boolean = false,
    val error: MailError? = null,
    val responseStatus: Int? = null,
    val rejection: String? = null,
) {
    val sender: Mailbox? get() = mailboxes.firstOrNull { it.id == draft.mailboxId } ?: mailboxes.firstOrNull()
    val canSend: Boolean get() = !loading && !expired && !savingDraft && !sending && !submitted && nextAction == null &&
        sender?.let { draft.outgoing(it).isValid } == true
}

internal class MailComposeViewModel(
    private val repository: MailRepository,
    private val generation: Long,
    selectedMailboxId: Long?,
    private val existingDraftId: String? = null,
) : ViewModel() {
    private val state = MutableStateFlow(MailComposeState(draft = MailDraft(mailboxId = selectedMailboxId)))
    val uiState = state.asStateFlow()
    private var loadJob: Job? = null
    private var sendJob: Job? = null
    private var saveJob: Job? = null
    private var draftLoaded = false
    private var draftId: String? = null

    init {
        viewModelScope.launch {
            repository.connection.collect { connection ->
                if (connection.initialized && (!connection.connected || connection.generation != generation)) {
                    loadJob?.cancel()
                    sendJob?.cancel()
                    saveJob?.cancel()
                    state.value = MailComposeState(loading = false, expired = true)
                }
            }
        }
        loadMailboxes()
    }

    fun loadMailboxes() {
        if (state.value.expired || loadJob?.isActive == true) return
        state.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch {
            runCatchingExceptCancellation {
                repository.initialize()
                if (!draftLoaded) {
                    val drafts = repository.drafts(generation)
                    val saved = if (existingDraftId == null) {
                        drafts.maxByOrNull { it.savedAt }
                    } else {
                        drafts.firstOrNull { it.id == existingDraftId } ?: throw MailException(MailError.NOT_FOUND)
                    }
                    draftLoaded = true
                    if (saved != null) {
                        draftId = saved.id
                        state.update { if (it.expired) it else it.copy(draft = saved.draft) }
                    }
                }
                repository.mailboxes(generation).filterNot(Mailbox::sendDisabled)
            }.onSuccess { mailboxes ->
                state.update { if (it.expired) it else it.copy(loading = false, mailboxes = mailboxes) }
            }.onFailure { failure ->
                state.update { if (it.expired) it else it.copy(loading = false, error = failure.mailError()) }
            }
        }
    }

    fun edit(draft: MailDraft) {
        state.update { if (it.expired || it.sending || it.savingDraft || it.submitted) it else it.copy(draft = draft, draftStorageError = false) }
    }

    fun saveAndClose() = saveThen(MailComposeAction.CLOSE)

    fun openDrafts() = saveThen(MailComposeAction.OPEN_DRAFTS)

    fun startBlank() = saveThen(MailComposeAction.START_BLANK)

    fun consumeAction() {
        state.update { it.copy(nextAction = null) }
    }

    private fun saveThen(action: MailComposeAction) {
        val current = state.value
        if (current.sending || current.savingDraft || current.nextAction != null) return
        if (action != MailComposeAction.CLOSE && (current.loading || current.expired || current.submitted)) return
        if (current.expired || current.submitted || !current.draft.hasText) {
            finish(action)
            return
        }
        val id = draftId ?: Uuid.random().toString().also { draftId = it }
        state.update { it.copy(savingDraft = true, draftStorageError = false) }
        saveJob = viewModelScope.launch {
            val saved = runCatchingExceptCancellation { repository.saveDraft(generation, id, current.draft) }.isSuccess
            if (state.value.expired) return@launch
            if (saved) {
                finish(action)
            } else {
                state.update { it.copy(savingDraft = false, draftStorageError = true) }
            }
        }
    }

    private fun finish(action: MailComposeAction) {
        if (action == MailComposeAction.START_BLANK) {
            draftId = null
            draftLoaded = true
            state.update { current ->
                current.copy(
                    draft = MailDraft(mailboxId = current.sender?.id),
                    savingDraft = false,
                    draftStorageError = false,
                    error = null,
                    responseStatus = null,
                    rejection = null,
                )
            }
        } else {
            state.update { it.copy(savingDraft = false, nextAction = action) }
        }
    }

    fun send() {
        val current = state.value
        if (!current.canSend) return
        val outbound = current.draft.outgoing(current.sender ?: return)
        state.update { it.copy(sending = true, error = null, responseStatus = null, rejection = null) }
        sendJob = viewModelScope.launch {
            val result = runCatchingExceptCancellation { repository.send(generation, outbound) }
            val cleanupFailed = result.isSuccess && draftId?.let { id ->
                runCatchingExceptCancellation { repository.deleteDraft(generation, id) }.isFailure
            } == true
            state.update { composing ->
                if (composing.expired) return@update composing
                result.fold(
                    onSuccess = { composing.copy(draft = MailDraft(), sending = false, submitted = true, draftStorageError = cleanupFailed) },
                    onFailure = { failure ->
                        val mailFailure = failure as? MailException
                        composing.copy(
                            sending = false,
                            error = failure.mailError(),
                            responseStatus = mailFailure?.responseStatus,
                            rejection = mailFailure?.rejection,
                        )
                    },
                )
            }
        }
    }

    companion object {
        fun factory(container: AppContainer, generation: Long, mailboxId: Long?, draftId: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer { MailComposeViewModel(container.mailRepository, generation, mailboxId, draftId) }
        }
    }
}

private fun MailDraft.outgoing(sender: Mailbox) = OutgoingMail(sender.id, recipients(to), subject.trim(), body, recipients(cc), recipients(bcc))

private fun recipients(input: String): List<String> =
    input.split(',', ';', '，', '；', '\n').map(String::trim).filter(String::isNotEmpty).distinct()
