package io.github.nodyssey.ui.mail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.mail.MailRepository
import io.github.nodyssey.data.mail.SavedMailDraft
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.action_cancel
import io.github.nodyssey.ui.resources.action_retry
import io.github.nodyssey.ui.resources.composer_restore_discard
import io.github.nodyssey.ui.resources.mail_compose_connection_expired
import io.github.nodyssey.ui.resources.mail_compose_discard
import io.github.nodyssey.ui.resources.mail_draft_storage_error
import io.github.nodyssey.ui.resources.mail_drafts_empty
import io.github.nodyssey.ui.resources.mail_folder_drafts
import io.github.nodyssey.ui.resources.mail_no_subject
import io.github.plaza.core.runCatchingExceptCancellation
import io.github.plaza.designsys.component.AdaptiveAlertDialog
import io.github.plaza.designsys.component.GroupedListItem
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.PlazaIcon
import io.github.plaza.designsys.component.PlazaSpinner
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

internal data class MailDraftsState(
    val drafts: List<SavedMailDraft> = emptyList(),
    val loading: Boolean = true,
    val deleting: Boolean = false,
    val expired: Boolean = false,
    val error: Boolean = false,
)

internal class MailDraftsViewModel(private val repository: MailRepository, private val generation: Long) : ViewModel() {
    private val state = MutableStateFlow(MailDraftsState())
    private val reload = MutableStateFlow(0L)
    val uiState = state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.connection, repository.draftRevision, reload) { connection, _, _ -> connection }
                .collectLatest { connection ->
                    if (connection.initialized && (!connection.connected || connection.generation != generation)) {
                        state.value = MailDraftsState(expired = true, loading = false)
                    } else {
                        runCatchingExceptCancellation {
                            repository.initialize()
                            repository.drafts(generation)
                        }.onSuccess { drafts ->
                            state.update { it.copy(drafts = drafts, loading = false, error = false) }
                        }.onFailure {
                            state.update { it.copy(loading = false, error = true) }
                        }
                    }
                }
        }
    }

    fun retry() {
        state.update { it.copy(loading = true, error = false) }
        reload.update { it + 1 }
    }

    fun delete(id: String) {
        if (state.value.deleting || state.value.expired) return
        state.update { it.copy(deleting = true, error = false) }
        viewModelScope.launch {
            val success = runCatchingExceptCancellation { repository.deleteDraft(generation, id) }.isSuccess
            state.update {
                if (it.expired) it else it.copy(deleting = false, error = !success)
            }
        }
    }

    companion object {
        fun factory(repository: MailRepository, generation: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { MailDraftsViewModel(repository, generation) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MailDraftsRoute(viewModel: MailDraftsViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var discardId by remember { mutableStateOf<String?>(null) }
    discardId?.takeUnless { state.expired }?.let { id ->
        AdaptiveAlertDialog(
            title = stringResource(Res.string.mail_compose_discard),
            message = null,
            confirmLabel = stringResource(Res.string.composer_restore_discard),
            dismissLabel = stringResource(Res.string.action_cancel),
            onConfirm = {
                discardId = null
                viewModel.delete(id)
            },
            onDismiss = { discardId = null },
            destructive = true,
        )
    }
    Scaffold(
        containerColor = LocalPlazaLayers.current.page,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.mail_folder_drafts)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        PlazaIcon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).readableWidth(),
            state = rememberMailListState(),
            contentPadding = PaddingValues(horizontal = LayerPageGutter, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            when {
                state.loading -> item { PlazaSpinner() }

                state.expired -> item { Text(stringResource(Res.string.mail_compose_connection_expired)) }

                state.error -> item {
                    Text(stringResource(Res.string.mail_draft_storage_error))
                    TextButton(onClick = viewModel::retry) { Text(stringResource(Res.string.action_retry)) }
                }

                state.drafts.isEmpty() -> item { Text(stringResource(Res.string.mail_drafts_empty)) }
            }
            itemsIndexed(state.drafts, key = { _, saved -> saved.id }) { index, saved ->
                GroupedListItem(
                    first = index == 0,
                    last = index == state.drafts.lastIndex,
                    enabled = !state.deleting,
                    onClick = { onOpen(saved.id) },
                    headlineContent = {
                        Text(
                            saved.draft.subject.ifBlank { stringResource(Res.string.mail_no_subject) },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = {
                        Text(saved.draft.to.ifBlank { saved.draft.body }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    trailingContent = {
                        IconButton(onClick = { discardId = saved.id }, enabled = !state.deleting) {
                            PlazaIcon(Icons.Default.Delete, contentDescription = stringResource(Res.string.composer_restore_discard))
                        }
                    },
                )
            }
        }
    }
}
