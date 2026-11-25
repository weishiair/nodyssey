package io.github.nodyssey.ui.mail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.nodyssey.data.mail.MailError
import io.github.nodyssey.data.mail.MailMessage
import io.github.nodyssey.data.mail.Mailbox
import io.github.nodyssey.data.mail.OutgoingMail
import io.github.nodyssey.ui.common.PlazaSheet
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_close
import io.github.nodyssey.ui.resources.mail_body
import io.github.nodyssey.ui.resources.mail_compose
import io.github.nodyssey.ui.resources.mail_delivery_draft
import io.github.nodyssey.ui.resources.mail_delivery_failed
import io.github.nodyssey.ui.resources.mail_delivery_queued
import io.github.nodyssey.ui.resources.mail_delivery_scheduled
import io.github.nodyssey.ui.resources.mail_delivery_sent
import io.github.nodyssey.ui.resources.mail_loading
import io.github.nodyssey.ui.resources.mail_recipients
import io.github.nodyssey.ui.resources.mail_recipients_bcc
import io.github.nodyssey.ui.resources.mail_recipients_cc
import io.github.nodyssey.ui.resources.mail_recipients_hint
import io.github.nodyssey.ui.resources.mail_scope_mail_send
import io.github.nodyssey.ui.resources.mail_send
import io.github.nodyssey.ui.resources.mail_send_uncertain
import io.github.nodyssey.ui.resources.mail_sender_choice
import io.github.nodyssey.ui.resources.mail_subject
import io.github.nodyssey.ui.resources.mail_submitted
import io.github.plaza.designsys.component.IconBadge
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.PlazaChipDefaults
import io.github.plaza.designsys.component.PlazaFieldDefaults
import io.github.plaza.designsys.component.PlazaIcon
import io.github.plaza.designsys.component.PlazaSpinner
import io.github.plaza.designsys.theme.Spacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MailComposeDialog(
    state: MailComposeState,
    mailboxes: List<Mailbox>,
    selectedMailboxId: Long?,
    onSend: (OutgoingMail) -> Unit,
    onDismiss: () -> Unit,
) {
    var mailboxId by remember(mailboxes, selectedMailboxId) {
        mutableStateOf(mailboxes.firstOrNull { it.id == selectedMailboxId }?.id ?: mailboxes.firstOrNull()?.id)
    }
    var choosingSender by remember { mutableStateOf(false) }
    var to by remember { mutableStateOf("") }
    var cc by remember { mutableStateOf("") }
    var bcc by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    val sender = mailboxes.firstOrNull { it.id == mailboxId }
    val draft = OutgoingMail(sender?.id ?: 0, recipients(to), subject.trim(), body, recipients(cc), recipients(bcc))
    val colors = MaterialTheme.colorScheme

    PlazaSheet(
        onDismiss = { if (!state.sending) onDismiss() },
        title = stringResource(Res.string.mail_compose),
        action = if (state.submitted) {
            { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_close)) } }
        } else {
            {
                Button(
                    onClick = { onSend(draft) },
                    enabled = !state.sending && draft.isValid,
                    contentPadding = PaddingValues(horizontal = Spacing.lg),
                ) {
                    if (state.sending) {
                        PlazaSpinner(Modifier.size(16.dp))
                    } else {
                        PlazaIcon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.mail_send))
                }
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LayerPageGutter)
                .padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (state.submitted) {
                LayerCard(contentPadding = PaddingValues(Spacing.xl)) {
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
            } else {
                LayerCard(
                    contentPadding = PaddingValues(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Box {
                        AssistChip(
                            onClick = { choosingSender = true },
                            enabled = !state.sending && mailboxes.size > 1,
                            label = { Text(stringResource(Res.string.mail_sender_choice, sender?.address.orEmpty())) },
                            trailingIcon = if (mailboxes.size > 1) {
                                { PlazaIcon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else {
                                null
                            },
                            shape = PlazaChipDefaults.shape,
                            colors = PlazaChipDefaults.assistChipColors(inCard = true),
                            border = PlazaChipDefaults.border(),
                        )
                        DropdownMenu(expanded = choosingSender, onDismissRequest = { choosingSender = false }) {
                            mailboxes.forEach { box ->
                                DropdownMenuItem(
                                    text = { Text(box.address) },
                                    onClick = {
                                        mailboxId = box.id
                                        choosingSender = false
                                    },
                                )
                            }
                        }
                    }
                    MailRecipientField(to, { to = it }, Res.string.mail_recipients, !state.sending, hint = true)
                    MailRecipientField(cc, { cc = it }, Res.string.mail_recipients_cc, !state.sending)
                    MailRecipientField(bcc, { bcc = it }, Res.string.mail_recipients_bcc, !state.sending)
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text(stringResource(Res.string.mail_subject)) },
                        singleLine = true,
                        enabled = !state.sending,
                        shape = PlazaFieldDefaults.shape,
                        colors = PlazaFieldDefaults.colors(inCard = true),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text(stringResource(Res.string.mail_body)) },
                        enabled = !state.sending,
                        minLines = 5,
                        shape = PlazaFieldDefaults.shape,
                        colors = PlazaFieldDefaults.colors(inCard = true),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.sending) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PlazaSpinner(Modifier.size(16.dp))
                            Text(
                                stringResource(Res.string.mail_loading),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                    state.error?.let {
                        val message = when (it) {
                            MailError.PERMISSION -> Res.string.mail_scope_mail_send
                            MailError.NETWORK, MailError.SERVICE, MailError.INVALID_RESPONSE -> Res.string.mail_send_uncertain
                            else -> it.messageRes()
                        }
                        Text(stringResource(message), color = colors.error, style = MaterialTheme.typography.bodySmall)
                    }
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

private fun recipients(input: String): List<String> =
    input.split(',', ';', '，', '；', '\n').map(String::trim).filter(String::isNotEmpty).distinct()

internal fun MailMessage.deliveryLabel(): StringResource? = when (status) {
    "sent" -> Res.string.mail_delivery_sent
    "queued" -> Res.string.mail_delivery_queued
    "scheduled" -> Res.string.mail_delivery_scheduled
    "failed", "permanently_failed" -> Res.string.mail_delivery_failed
    "draft" -> Res.string.mail_delivery_draft
    else -> null
}
