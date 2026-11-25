package io.github.nodyssey.ui.mail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.data.mail.MailDraft
import io.github.nodyssey.data.mail.MailError
import io.github.nodyssey.data.mail.MailMessage
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_close
import io.github.nodyssey.ui.resources.action_retry
import io.github.nodyssey.ui.resources.mail_body
import io.github.nodyssey.ui.resources.mail_compose
import io.github.nodyssey.ui.resources.mail_compose_blank
import io.github.nodyssey.ui.resources.mail_compose_connection_expired
import io.github.nodyssey.ui.resources.mail_compose_no_sender
import io.github.nodyssey.ui.resources.mail_delivery_draft
import io.github.nodyssey.ui.resources.mail_delivery_failed
import io.github.nodyssey.ui.resources.mail_delivery_queued
import io.github.nodyssey.ui.resources.mail_delivery_scheduled
import io.github.nodyssey.ui.resources.mail_delivery_sent
import io.github.nodyssey.ui.resources.mail_draft_save
import io.github.nodyssey.ui.resources.mail_draft_storage_error
import io.github.nodyssey.ui.resources.mail_folder_drafts
import io.github.nodyssey.ui.resources.mail_options
import io.github.nodyssey.ui.resources.mail_recipients
import io.github.nodyssey.ui.resources.mail_recipients_bcc
import io.github.nodyssey.ui.resources.mail_recipients_cc
import io.github.nodyssey.ui.resources.mail_recipients_hint
import io.github.nodyssey.ui.resources.mail_send
import io.github.nodyssey.ui.resources.mail_send_http_rejected
import io.github.nodyssey.ui.resources.mail_send_uncertain
import io.github.nodyssey.ui.resources.mail_sender_choice
import io.github.nodyssey.ui.resources.mail_sending
import io.github.nodyssey.ui.resources.mail_subject
import io.github.nodyssey.ui.resources.mail_submitted
import io.github.plaza.designsys.component.IconBadge
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.PlazaBackHandler
import io.github.plaza.designsys.component.PlazaChipDefaults
import io.github.plaza.designsys.component.PlazaFieldDefaults
import io.github.plaza.designsys.component.PlazaIcon
import io.github.plaza.designsys.component.PlazaSpinner
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.paddingWithKeyboard
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MailComposeRoute(
    viewModel: MailComposeViewModel,
    onClose: () -> Unit,
    onOpenDrafts: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.nextAction) {
        when (state.nextAction) {
            MailComposeAction.CLOSE -> onClose()

            MailComposeAction.OPEN_DRAFTS -> {
                viewModel.consumeAction()
                onOpenDrafts()
            }

            MailComposeAction.START_BLANK, null -> Unit
        }
    }
    MailComposeScreen(
        state,
        viewModel::edit,
        viewModel::send,
        viewModel::loadMailboxes,
        viewModel::saveAndClose,
        viewModel::openDrafts,
        viewModel::startBlank,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MailComposeScreen(
    state: MailComposeState,
    onEdit: (MailDraft) -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onOpenDrafts: () -> Unit,
    onStartBlank: () -> Unit,
) {
    val draft = state.draft
    val sender = state.sender
    val colors = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    val scroll = rememberScrollState()
    var choosingSender by remember { mutableStateOf(false) }
    var showingOptions by remember { mutableStateOf(false) }
    val hasDraft = draft.hasText && !state.expired && !state.submitted
    val locked = state.sending || state.savingDraft || state.nextAction != null
    val close = {
        if (!locked) {
            focus.clearFocus()
            onClose()
        }
    }

    PlazaBackHandler(enabled = locked || hasDraft, onBack = close)

    LaunchedEffect(state.error, state.submitted) {
        if (state.error != null || state.submitted) scroll.scrollTo(0)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.mail_compose)) },
                navigationIcon = {
                    IconButton(onClick = close, enabled = !locked) {
                        PlazaIcon(Icons.Default.Close, contentDescription = stringResource(Res.string.action_close))
                    }
                },
                actions = {
                    if (!state.loading && !state.expired && !state.submitted) {
                        Box {
                            IconButton(onClick = { showingOptions = true }, enabled = !locked) {
                                PlazaIcon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.mail_options))
                            }
                            DropdownMenu(expanded = showingOptions, onDismissRequest = { showingOptions = false }) {
                                if (hasDraft) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.mail_compose_blank)) },
                                        onClick = {
                                            showingOptions = false
                                            focus.clearFocus()
                                            onStartBlank()
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.mail_folder_drafts)) },
                                    onClick = {
                                        showingOptions = false
                                        focus.clearFocus()
                                        onOpenDrafts()
                                    },
                                )
                            }
                        }
                    }
                    if (sender != null && !state.submitted && !state.expired) {
                        // Keep Send in the same Compose surface as the focused editor while the keyboard is open.
                        Button(
                            onClick = {
                                focus.clearFocus()
                                onSend()
                            },
                            enabled = state.canSend,
                            modifier = Modifier.padding(end = Spacing.md),
                        ) {
                            if (state.sending) {
                                PlazaSpinner(Modifier.size(16.dp))
                            } else {
                                PlazaIcon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Text(stringResource(Res.string.mail_send), modifier = Modifier.padding(start = Spacing.sm))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().paddingWithKeyboard(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .readableWidth()
                    .verticalScroll(scroll)
                    .padding(horizontal = LayerPageGutter)
                    .padding(top = Spacing.md, bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                if (state.draftStorageError) {
                    Text(stringResource(Res.string.mail_draft_storage_error), color = colors.error)
                }
                when {
                    state.loading -> PlazaSpinner(Modifier.align(Alignment.CenterHorizontally))

                    state.expired -> LayerCard(contentPadding = PaddingValues(Spacing.lg)) {
                        Text(stringResource(Res.string.mail_compose_connection_expired))
                    }

                    state.submitted -> LayerCard(contentPadding = PaddingValues(Spacing.xl)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconBadge(colors.primaryContainer, colors.primary, size = 42.dp) {
                                PlazaIcon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(22.dp))
                            }
                            Text(stringResource(Res.string.mail_submitted), style = MaterialTheme.typography.bodyLarge)
                        }
                    }

                    sender == null -> LayerCard(contentPadding = PaddingValues(Spacing.lg)) {
                        val error = state.error
                        Text(stringResource(error?.messageRes() ?: Res.string.mail_compose_no_sender))
                        if (error != null) {
                            TextButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
                        }
                    }

                    else -> {
                        val error = state.error
                        if (error != null) {
                            LayerCard(contentPadding = PaddingValues(Spacing.lg)) {
                                val status = state.responseStatus
                                val message = when {
                                    status != null && status >= 500 -> stringResource(Res.string.mail_send_uncertain)

                                    status != null -> stringResource(Res.string.mail_send_http_rejected, status)

                                    state.rejection != null -> stringResource(error.messageRes())

                                    error in listOf(MailError.NETWORK, MailError.SERVICE, MailError.INVALID_RESPONSE) ->
                                        stringResource(Res.string.mail_send_uncertain)

                                    else -> stringResource(error.messageRes())
                                }
                                Text(message, color = colors.error, style = MaterialTheme.typography.bodyMedium)
                                state.rejection?.let { Text(it, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                        if (state.sending) {
                            Text(stringResource(Res.string.mail_sending), style = MaterialTheme.typography.bodyMedium)
                        }
                        LayerCard(
                            contentPadding = PaddingValues(Spacing.lg),
                            verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            Box {
                                val hasChoices = state.mailboxes.size > 1
                                AssistChip(
                                    onClick = { choosingSender = true },
                                    enabled = !locked && hasChoices,
                                    label = {
                                        Text(
                                            stringResource(Res.string.mail_sender_choice, sender.address),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    trailingIcon = if (hasChoices) {
                                        { PlazaIcon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                    } else {
                                        null
                                    },
                                    shape = PlazaChipDefaults.shape,
                                    colors = PlazaChipDefaults.assistChipColors(inCard = true),
                                    border = PlazaChipDefaults.border(),
                                )
                                DropdownMenu(expanded = choosingSender, onDismissRequest = { choosingSender = false }) {
                                    state.mailboxes.forEach { mailbox ->
                                        DropdownMenuItem(
                                            text = { Text(mailbox.address) },
                                            onClick = {
                                                onEdit(draft.copy(mailboxId = mailbox.id))
                                                choosingSender = false
                                            },
                                        )
                                    }
                                }
                            }
                            MailRecipientField(draft.to, { onEdit(draft.copy(to = it)) }, Res.string.mail_recipients, !locked, hint = true)
                            MailRecipientField(draft.cc, { onEdit(draft.copy(cc = it)) }, Res.string.mail_recipients_cc, !locked)
                            MailRecipientField(draft.bcc, { onEdit(draft.copy(bcc = it)) }, Res.string.mail_recipients_bcc, !locked)
                            OutlinedTextField(
                                value = draft.subject,
                                onValueChange = { onEdit(draft.copy(subject = it)) },
                                label = { Text(stringResource(Res.string.mail_subject)) },
                                singleLine = true,
                                enabled = !locked,
                                shape = PlazaFieldDefaults.shape,
                                colors = PlazaFieldDefaults.colors(inCard = true),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = draft.body,
                                onValueChange = { onEdit(draft.copy(body = it)) },
                                label = { Text(stringResource(Res.string.mail_body)) },
                                enabled = !locked,
                                minLines = 8,
                                shape = PlazaFieldDefaults.shape,
                                colors = PlazaFieldDefaults.colors(inCard = true),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                if (hasDraft) {
                    TextButton(onClick = close, enabled = !locked) { Text(stringResource(Res.string.mail_draft_save)) }
                }
            }
        }
    }
}

@Composable
private fun MailRecipientField(
    value: String,
    onChange: (String) -> Unit,
    label: StringResource,
    enabled: Boolean,
    hint: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        supportingText = if (hint) {
            { Text(stringResource(Res.string.mail_recipients_hint)) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        singleLine = true,
        enabled = enabled,
        shape = PlazaFieldDefaults.shape,
        colors = PlazaFieldDefaults.colors(inCard = true),
        modifier = Modifier.fillMaxWidth(),
    )
}

internal fun MailMessage.deliveryLabel(): StringResource? = when (status) {
    "sent" -> Res.string.mail_delivery_sent
    "queued" -> Res.string.mail_delivery_queued
    "scheduled" -> Res.string.mail_delivery_scheduled
    "failed", "permanently_failed" -> Res.string.mail_delivery_failed
    "draft" -> Res.string.mail_delivery_draft
    else -> null
}
