package io.github.nodyssey.ui.mail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.nodyssey.data.mail.MailError
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_cancel
import io.github.nodyssey.ui.resources.mail_loading
import io.github.nodyssey.ui.resources.mail_scope_tag_read
import io.github.nodyssey.ui.resources.mail_scope_tag_write
import io.github.nodyssey.ui.resources.mail_tag_add
import io.github.nodyssey.ui.resources.mail_tag_create
import io.github.nodyssey.ui.resources.mail_tag_name
import io.github.nodyssey.ui.resources.mail_tag_no_filter
import io.github.nodyssey.ui.resources.mail_tag_remove
import io.github.nodyssey.ui.resources.mail_tags
import io.github.plaza.designsys.component.PlazaIcon
import org.jetbrains.compose.resources.stringResource

data class MailTagControls(
    val load: () -> Unit,
    val create: (String) -> Unit,
    val clearCreated: () -> Unit,
    val setSelected: (Long, Boolean) -> Unit,
)

@Composable
internal fun MailTagFilter(
    state: MailInboxUiState,
    controls: MailTagControls,
    expanded: Boolean,
    dismissMenu: () -> Unit,
    onFilter: (Long?) -> Unit,
) {
    var creating by remember(state.generation) { mutableStateOf(false) }
    val error = state.tagsError?.let {
        stringResource(if (it == MailError.PERMISSION) Res.string.mail_scope_tag_read else it.messageRes())
    }
    DropdownMenu(expanded = expanded, onDismissRequest = dismissMenu) {
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.mail_tag_no_filter)) },
            onClick = {
                dismissMenu()
                onFilter(null)
            },
        )
        state.tags.forEach { tag ->
            DropdownMenuItem(
                text = { Text(tag.name) },
                trailingIcon = if (tag.id == state.filter.tagId) {
                    { PlazaIcon(Icons.Default.Check, contentDescription = null) }
                } else {
                    null
                },
                onClick = {
                    dismissMenu()
                    onFilter(tag.id)
                },
            )
        }
        if (state.loadingTags) TagMenuStatus(stringResource(Res.string.mail_loading))
        if (error != null) TagMenuStatus(error)
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.mail_tag_create)) },
            enabled = !state.loadingTags,
            onClick = {
                dismissMenu()
                controls.clearCreated()
                creating = true
            },
        )
    }
    if (creating) {
        var name by remember { mutableStateOf("") }
        val dismiss = { creating = false }
        LaunchedEffect(state.createdTag) {
            if (state.createdTag != null) {
                dismiss()
                controls.clearCreated()
            }
        }
        AlertDialog(
            onDismissRequest = { if (!state.creatingTag) dismiss() },
            title = { Text(stringResource(Res.string.mail_tag_create)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(Res.string.mail_tag_name)) },
                        singleLine = true,
                        enabled = !state.creatingTag,
                    )
                    state.tagsError?.let {
                        val message = if (it == MailError.PERMISSION) Res.string.mail_scope_tag_write else it.messageRes()
                        Text(stringResource(message), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { controls.create(name) }, enabled = name.isNotBlank() && !state.creatingTag) {
                    Text(stringResource(Res.string.mail_tag_create))
                }
            },
            dismissButton = {
                TextButton(onClick = dismiss, enabled = !state.creatingTag) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }
}

@Composable
internal fun MailTagBatchActions(state: MailInboxUiState, controls: MailTagControls, enabled: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val selected = state.messages.filter { it.id in state.selectedIds }
    Box {
        TextButton(
            enabled = enabled,
            onClick = {
                controls.load()
                expanded = true
            },
        ) {
            Text(stringResource(Res.string.mail_tags))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.tags.forEach { tag ->
                val someTagged = selected.any { mail -> mail.tags.orEmpty().any { it.id == tag.id } }
                if (selected.any { mail -> mail.tags.orEmpty().none { it.id == tag.id } }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.mail_tag_add, tag.name)) },
                        onClick = {
                            expanded = false
                            controls.setSelected(tag.id, true)
                        },
                    )
                }
                if (someTagged) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.mail_tag_remove, tag.name)) },
                        onClick = {
                            expanded = false
                            controls.setSelected(tag.id, false)
                        },
                    )
                }
            }
            if (state.loadingTags) TagMenuStatus(stringResource(Res.string.mail_loading))
            val error = state.tagsError
            if (error != null) {
                TagMenuStatus(stringResource(if (error == MailError.PERMISSION) Res.string.mail_scope_tag_read else error.messageRes()))
            } else if (state.tags.isEmpty() && !state.loadingTags) {
                TagMenuStatus(stringResource(Res.string.mail_tag_create))
            }
        }
    }
}

@Composable
private fun TagMenuStatus(message: String) =
    DropdownMenuItem(text = { Text(message) }, enabled = false, onClick = {})
