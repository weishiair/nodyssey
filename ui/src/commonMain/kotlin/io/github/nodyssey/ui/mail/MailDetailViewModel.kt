package io.github.nodyssey.ui.mail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.mail.MailAttachment
import io.github.nodyssey.data.mail.MailError
import io.github.nodyssey.data.mail.MailMessage
import io.github.nodyssey.data.mail.MailRemoteImage
import io.github.nodyssey.data.mail.MailRepository
import io.github.nodyssey.data.mail.MailVerificationCode
import io.github.nodyssey.di.AppContainer
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MailDetailUiState(
    val loading: Boolean = true,
    val expired: Boolean = false,
    val message: MailMessage? = null,
    val body: String = "",
    val verificationCode: String? = null,
    val updatingRead: Boolean = false,
    val attachments: List<MailAttachment> = emptyList(),
    val loadingAttachments: Boolean = false,
    val attachmentError: MailError? = null,
    val remoteImages: Map<String, MailRemoteImage> = emptyMap(),
    val loadingRemoteImages: Boolean = false,
    val remoteImagesFailed: Boolean = false,
    val error: MailError? = null,
)

class MailDetailViewModel(
    private val repository: MailRepository,
    private val mailboxId: Long,
    private val mailId: Long,
) : ViewModel() {
    private val state = MutableStateFlow(MailDetailUiState())
    private var generation: Long? = null
    private var loadJob: Job? = null
    private var readJob: Job? = null
    private var attachmentJob: Job? = null
    private var imageJob: Job? = null
    val uiState = combine(state, repository.readStates) { detail, reads ->
        val mail = detail.message
        if (mail == null || reads.generation != generation) {
            detail
        } else {
            detail.copy(
                message = reads.values[mail.id]?.let { mail.copy(isRead = it) } ?: mail,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, state.value)

    init {
        viewModelScope.launch {
            repository.connection.collect { connection ->
                if (!connection.initialized) return@collect
                if (generation == null && connection.connected && !state.value.expired) {
                    generation = connection.generation
                    reload()
                } else if (!connection.connected || connection.generation != generation) {
                    loadJob?.cancel()
                    readJob?.cancel()
                    attachmentJob?.cancel()
                    imageJob?.cancel()
                    state.value = MailDetailUiState(loading = false, expired = true)
                }
            }
        }
        viewModelScope.launch {
            runCatchingExceptCancellation { repository.initialize() }
                .onFailure { error -> state.update { it.copy(loading = false, error = error.mailError()) } }
        }
    }

    fun reload() {
        val expected = generation ?: return
        if (state.value.expired) return
        loadJob?.cancel()
        attachmentJob?.cancel()
        imageJob?.cancel()
        state.update { it.copy(loading = true, error = null, loadingRemoteImages = false) }
        loadJob = viewModelScope.launch {
            runCatchingExceptCancellation {
                val mail = repository.detail(expected, mailboxId, mailId)
                val body = mail.bodyText.orEmpty()
                if (repository.connection.value.generation == expected) {
                    state.value = MailDetailUiState(
                        loading = false,
                        message = mail,
                        body = body,
                        verificationCode = MailVerificationCode.extract(mail.subject, body),
                    )
                    if (!mail.isRead) setRead(true)
                    if (mail.hasAttachments) loadAttachments()
                }
            }.onFailure { error ->
                if (!state.value.expired) state.update { it.copy(loading = false, error = error.mailError()) }
            }
        }
    }

    fun loadRemoteImages() {
        val expected = generation ?: return
        val detail = state.value
        if (detail.loading || detail.loadingRemoteImages || detail.expired) return
        val urls = detail.message?.formattedBody?.remoteImages.orEmpty().filterNot(detail.remoteImages::containsKey)
        if (urls.isEmpty()) return
        state.update { it.copy(loadingRemoteImages = true, remoteImagesFailed = false) }
        imageJob = viewModelScope.launch {
            val images = runCatchingExceptCancellation { repository.loadRemoteImages(expected, urls) }.getOrDefault(emptyMap())
            if (repository.connection.value.generation == expected) {
                state.update {
                    it.copy(
                        remoteImages = it.remoteImages + images,
                        loadingRemoteImages = false,
                        remoteImagesFailed = images.size < urls.size,
                    )
                }
            }
        }
    }

    fun loadAttachments() {
        val expected = generation ?: return
        if (state.value.message?.hasAttachments != true || state.value.loadingAttachments) return
        state.update { it.copy(loadingAttachments = true, attachmentError = null) }
        attachmentJob = viewModelScope.launch {
            runCatchingExceptCancellation { repository.attachments(expected, mailId) }
                .onSuccess { attachments ->
                    state.update { it.copy(attachments = attachments, loadingAttachments = false) }
                }
                .onFailure { error ->
                    if (canSaveAttachment()) state.update { it.copy(loadingAttachments = false, attachmentError = error.mailError()) }
                }
        }
    }

    fun canSaveAttachment(): Boolean =
        repository.connection.value.let { it.connected && it.generation == generation }

    suspend fun downloadAttachment(attachment: MailAttachment): ByteArray {
        val expected = generation ?: throw io.github.nodyssey.data.mail.MailException(MailError.UNAUTHORIZED)
        return repository.downloadAttachment(expected, mailId, attachment)
    }

    fun toggleRead() {
        val mail = uiState.value.message ?: return
        setRead(!mail.isRead)
    }

    private fun setRead(read: Boolean) {
        val expected = generation ?: return
        val mail = state.value.message ?: return
        if (state.value.updatingRead || state.value.expired) return
        state.update { it.copy(updatingRead = true, error = null) }
        readJob = viewModelScope.launch {
            runCatchingExceptCancellation { repository.markRead(expected, mail, read) }
                .onFailure { error ->
                    if (!state.value.expired) state.update { it.copy(error = error.mailError()) }
                }
            state.update { it.copy(updatingRead = false) }
        }
    }

    companion object {
        fun factory(container: AppContainer, mailboxId: Long, mailId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { MailDetailViewModel(container.mailRepository, mailboxId, mailId) }
        }
    }
}
