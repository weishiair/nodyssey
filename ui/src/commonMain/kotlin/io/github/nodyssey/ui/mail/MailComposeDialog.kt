package io.github.nodyssey.ui.mail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import io.github.nodyssey.data.mail.MailError
import io.github.nodyssey.data.mail.MailMessage
import io.github.nodyssey.data.mail.Mailbox
import io.github.nodyssey.data.mail.OutgoingMail
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_cancel
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
import io.github.plaza.designsys.theme.Spacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MailComposeDialog(
    state: MailComposeState,
    mailboxes: List<Mailbox>,
    selectedMailboxId: Long?,
    onSend: (OutgoingMail) -> Unit,
    onDismiss: () -> Unit,
) {
    var mailboxId by remember { mutableStateOf(mailboxes.firstOrNull { it.id == selectedMailboxId }?.id ?: mailboxes.firstOrNull()?.id) }
    var choosingSender by remember { mutableStateOf(false) }
    var to by remember { mutableStateOf("") }
    var cc by remember { mutableStateOf("") }
    var bcc by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    val sender = mailboxes.firstOrNull { it.id == mailboxId }
    val draft = OutgoingMail(sender?.id ?: 0, recipients(to), subject.trim(), body, recipients(cc), recipients(bcc))
    AlertDialog(
        onDismissRequest = { if (!state.sending) onDismiss() },
        title = { Text(stringResource(Res.string.mail_compose)) },
        text = {
            if (state.submitted) {
                Text(stringResource(Res.string.mail_submitted))
            } else {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Box {
                        OutlinedButton(onClick = { choosingSender = true }, enabled = !state.sending && mailboxes.size > 1) {
                            Text(stringResource(Res.string.mail_sender_choice, sender?.address.orEmpty()))
                        }
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
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text(stringResource(Res.string.mail_body)) },
                        enabled = !state.sending,
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.sending) Text(stringResource(Res.string.mail_loading))
                    state.error?.let {
                        val message = when (it) {
                            MailError.PERMISSION -> Res.string.mail_scope_mail_send
                            MailError.NETWORK, MailError.SERVICE, MailError.INVALID_RESPONSE -> Res.string.mail_send_uncertain
                            else -> it.messageRes()
                        }
                        Text(stringResource(message), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            if (state.submitted) {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_close)) }
            } else {
                TextButton(onClick = { onSend(draft) }, enabled = !state.sending && draft.isValid) {
                    Text(stringResource(Res.string.mail_send))
                }
            }
        },
        dismissButton = {
            if (!state.submitted) TextButton(onClick = onDismiss, enabled = !state.sending) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
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
