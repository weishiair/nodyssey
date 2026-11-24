package io.github.nodyssey.ui.mail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import io.github.nodyssey.data.mail.MailAuthMode
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_refresh
import io.github.nodyssey.ui.resources.mail_address_copied
import io.github.nodyssey.ui.resources.mail_all_mailboxes
import io.github.nodyssey.ui.resources.mail_copy_address
import io.github.nodyssey.ui.resources.mail_create_alias
import io.github.nodyssey.ui.resources.mail_disconnect
import io.github.nodyssey.ui.resources.mail_manage
import io.github.nodyssey.ui.resources.mail_mark_all_read
import io.github.nodyssey.ui.resources.mail_mode_api_key
import io.github.nodyssey.ui.resources.mail_mode_web_session
import io.github.nodyssey.ui.resources.mail_poll_api_hint
import io.github.nodyssey.ui.resources.mail_poll_web_hint
import io.github.nodyssey.ui.resources.mail_replace_key
import io.github.nodyssey.ui.resources.mail_select
import io.github.nodyssey.ui.resources.mail_selected_count
import io.github.nodyssey.ui.resources.mail_switch_web_session
import io.github.nodyssey.ui.resources.mail_tags
import io.github.nodyssey.ui.resources.mail_use_key
import io.github.plaza.designsys.component.PlazaIcon
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.rememberClipboardCopy
import org.jetbrains.compose.resources.stringResource

internal enum class MailInboxMenu { Actions, Management, Tags }

@Composable
internal fun MailInboxOptions(
    menu: MailInboxMenu?,
    setMenu: (MailInboxMenu?) -> Unit,
    state: MailInboxUiState,
    controls: MailInboxControls?,
    onMarkAllRead: () -> Unit,
    onRefresh: () -> Unit,
    onCreateAlias: () -> Unit,
    onAuthorize: () -> Unit,
    onUseKey: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val dismiss = { setMenu(null) }
    val busy = state.markingAllRead || state.changingMessages || state.connecting
    DropdownMenu(expanded = menu == MailInboxMenu.Actions || menu == MailInboxMenu.Management, onDismissRequest = dismiss) {
        if (menu == MailInboxMenu.Management) {
            state.authMode?.let { mode ->
                val apiKey = mode == MailAuthMode.API_KEY
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(stringResource(if (apiKey) Res.string.mail_mode_api_key else Res.string.mail_mode_web_session))
                            Text(
                                stringResource(if (apiKey) Res.string.mail_poll_api_hint else Res.string.mail_poll_web_hint),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                    enabled = false,
                    onClick = {},
                )
            }
            if (controls != null) {
                DropdownMenuItem(text = { Text(stringResource(Res.string.mail_create_alias)) }, enabled = !busy, onClick = {
                    dismiss()
                    onCreateAlias()
                })
            }
            if (state.authMode == MailAuthMode.API_KEY) {
                DropdownMenuItem(text = { Text(stringResource(Res.string.mail_switch_web_session)) }, enabled = !busy, onClick = {
                    dismiss()
                    onAuthorize()
                })
            }
            val keyLabel = if (state.authMode == MailAuthMode.API_KEY) Res.string.mail_replace_key else Res.string.mail_use_key
            DropdownMenuItem(text = { Text(stringResource(keyLabel)) }, enabled = !busy, onClick = {
                dismiss()
                onUseKey()
            })
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.mail_disconnect), color = MaterialTheme.colorScheme.error) },
                enabled = !busy,
                onClick = {
                    dismiss()
                    onDisconnect()
                },
            )
        } else {
            if (state.mailboxes.isNotEmpty() && state.filter.isInbox && !state.unreadOnly && !state.selectionMode) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.mail_mark_all_read)) },
                    enabled = !busy && !state.loading && !state.loadingMore && state.messages.isNotEmpty(),
                    onClick = {
                        dismiss()
                        onMarkAllRead()
                    },
                )
            }
            if (controls != null && state.mailboxes.isNotEmpty() && !state.filter.isSent) {
                DropdownMenuItem(
                    text = {
                        Text(
                            if (state.selectionMode) {
                                stringResource(Res.string.mail_selected_count, state.selectedIds.size)
                            } else {
                                stringResource(Res.string.mail_select)
                            },
                        )
                    },
                    enabled = !busy,
                    onClick = {
                        dismiss()
                        controls.toggleSelection()
                    },
                )
                DropdownMenuItem(text = { Text(stringResource(Res.string.mail_tags)) }, enabled = !busy, onClick = {
                    controls.tags.load()
                    setMenu(MailInboxMenu.Tags)
                })
            }
            DropdownMenuItem(text = { Text(stringResource(Res.string.action_refresh)) }, enabled = !state.loading && !busy, onClick = {
                dismiss()
                onRefresh()
            })
            DropdownMenuItem(text = { Text(stringResource(Res.string.mail_manage)) }, onClick = { setMenu(MailInboxMenu.Management) })
        }
    }
    if (controls != null) {
        MailTagFilter(state, controls.tags, expanded = menu == MailInboxMenu.Tags, dismissMenu = dismiss) {
            controls.changeFilter(state.filter.copy(tagId = it))
        }
    }
}

@Composable
internal fun MailboxPicker(state: MailInboxUiState, onSelectMailbox: (Long?) -> Unit) {
    var expanded by remember(state.generation) { mutableStateOf(false) }
    val copyAddress = rememberClipboardCopy()
    val copyLabel = stringResource(Res.string.mail_copy_address)
    val copiedMessage = stringResource(Res.string.mail_address_copied)
    val allMailboxes = stringResource(Res.string.mail_all_mailboxes)
    val selectedMailbox = state.mailboxes.firstOrNull { it.id == state.selectedMailboxId }
    val enabled = state.mailboxes.isNotEmpty() && !state.markingAllRead && !state.changingMessages
    MailScopeButton(selectedMailbox?.address ?: allMailboxes, enabled) { expanded = true }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(allMailboxes) },
            enabled = enabled,
            trailingIcon = { if (state.selectedMailboxId == null) PlazaIcon(Icons.Default.Check, contentDescription = null) },
            onClick = {
                expanded = false
                onSelectMailbox(null)
            },
        )
        state.mailboxes.forEach { mailbox ->
            DropdownMenuItem(
                text = { Text(mailbox.address) },
                enabled = enabled,
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (mailbox.id == state.selectedMailboxId) PlazaIcon(Icons.Default.Check, contentDescription = null)
                        IconButton(onClick = {
                            copyAddress(copyLabel, mailbox.address, copiedMessage)
                            expanded = false
                        }) {
                            PlazaIcon(PlazaIcons.ContentCopy, contentDescription = copyLabel)
                        }
                    }
                },
                onClick = {
                    expanded = false
                    onSelectMailbox(mailbox.id)
                },
            )
        }
    }
}
