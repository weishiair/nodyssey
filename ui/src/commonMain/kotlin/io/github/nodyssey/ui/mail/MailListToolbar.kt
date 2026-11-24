package io.github.nodyssey.ui.mail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nodyssey.data.mail.MailError
import io.github.nodyssey.data.mail.MailFolder
import io.github.nodyssey.data.mail.MailFolderScope
import io.github.nodyssey.data.mail.MailListFilter
import io.github.nodyssey.data.mail.MailMessage
import io.github.nodyssey.data.mail.MailSystemFolder
import io.github.nodyssey.data.mail.OutgoingMail
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_close
import io.github.nodyssey.ui.resources.action_search
import io.github.nodyssey.ui.resources.mail_filter_all
import io.github.nodyssey.ui.resources.mail_folder_sent
import io.github.nodyssey.ui.resources.mail_folder_spam
import io.github.nodyssey.ui.resources.mail_folder_trash
import io.github.nodyssey.ui.resources.mail_inbox
import io.github.nodyssey.ui.resources.mail_loading
import io.github.nodyssey.ui.resources.mail_mark_selected_read
import io.github.nodyssey.ui.resources.mail_move_selected
import io.github.nodyssey.ui.resources.mail_scope_folder_read
import io.github.nodyssey.ui.resources.mail_search_hint
import io.github.nodyssey.ui.resources.mail_select_visible
import io.github.nodyssey.ui.resources.mail_selected_count
import io.github.nodyssey.ui.resources.mail_unread_status
import io.github.plaza.designsys.component.PlazaIcon
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.Spacing
import org.jetbrains.compose.resources.stringResource

data class MailInboxControls(
    val changeFilter: (MailListFilter) -> Unit,
    val toggleUnread: () -> Unit,
    val loadFolders: () -> Unit,
    val toggleSelection: () -> Unit,
    val toggleSelected: (Long) -> Unit,
    val selectVisible: () -> Unit,
    val markSelectedRead: () -> Unit,
    val moveSelected: (Long?) -> Unit,
    val toggleFlag: (MailMessage) -> Unit,
    val checkAlias: (String) -> Unit,
    val createAlias: (String) -> Unit,
    val resetAlias: () -> Unit,
    val sendMail: (OutgoingMail) -> Unit,
    val resetCompose: () -> Unit,
    val tags: MailTagControls,
)

@Composable
internal fun MailFolderScope.label(): String = when (this) {
    is MailFolder -> name
    MailSystemFolder.INBOX -> stringResource(Res.string.mail_inbox)
    MailSystemFolder.SPAM -> stringResource(Res.string.mail_folder_spam)
    MailSystemFolder.TRASH -> stringResource(Res.string.mail_folder_trash)
    MailSystemFolder.SENT -> stringResource(Res.string.mail_folder_sent)
}

@Composable
internal fun MailListToolbar(
    state: MailInboxUiState,
    controls: MailInboxControls?,
    searchVisible: Boolean,
    mailboxPicker: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var choosingFolder by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    val enabled = !state.markingAllRead && !state.changingMessages
    val folderError = state.foldersError?.let {
        stringResource(if (it == MailError.PERMISSION) Res.string.mail_scope_folder_read else it.messageRes())
    }
    Column(modifier) {
        if (searchVisible && controls != null) {
            MailSearchField(state.generation, state.filter.search, enabled) { controls.changeFilter(state.filter.copy(search = it)) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (controls != null) {
                Box(Modifier.align(Alignment.CenterVertically)) {
                    MailScopeButton(state.filter.folder.label(), enabled) {
                        controls.loadFolders()
                        choosingFolder = true
                    }
                    MailFolderMenu(
                        expanded = choosingFolder,
                        dismiss = { choosingFolder = false },
                        folders = MailSystemFolder.entries + state.folders,
                        current = state.filter.folder,
                        loading = state.loadingFolders,
                        error = folderError,
                    ) { folder -> controls.changeFilter(state.filter.copy(folder = folder)) }
                }
            }
            Box(Modifier.align(Alignment.CenterVertically)) { mailboxPicker() }
            if (controls != null && !state.filter.isSent) {
                SingleChoiceSegmentedButtonRow(Modifier.align(Alignment.CenterVertically)) {
                    listOf(Res.string.mail_filter_all, Res.string.mail_unread_status).forEachIndexed { index, label ->
                        val selected = state.unreadOnly == (index == 1)
                        SegmentedButton(
                            selected = selected,
                            onClick = { if (!selected) controls.toggleUnread() },
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                            enabled = enabled,
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                            icon = {},
                        ) { Text(stringResource(label)) }
                    }
                }
            }
        }
        if (state.selectionMode && controls != null) {
            val canChange = enabled && !state.loading && !state.loadingMore && state.selectedIds.isNotEmpty()
            FlowRow {
                TextButton(onClick = controls.toggleSelection, enabled = enabled) {
                    Text(stringResource(Res.string.mail_selected_count, state.selectedIds.size))
                }
                TextButton(onClick = controls.selectVisible, enabled = enabled && state.visibleMessages.isNotEmpty()) {
                    Text(stringResource(Res.string.mail_select_visible))
                }
                TextButton(onClick = controls.markSelectedRead, enabled = canChange) {
                    Text(stringResource(Res.string.mail_mark_selected_read))
                }
                Box {
                    TextButton(
                        onClick = {
                            controls.loadFolders()
                            moving = true
                        },
                        enabled = canChange && (state.filter.folder == MailSystemFolder.INBOX || state.filter.folder is MailFolder),
                    ) {
                        Text(stringResource(Res.string.mail_move_selected))
                    }
                    MailFolderMenu(
                        expanded = moving,
                        dismiss = { moving = false },
                        folders = (listOf(MailSystemFolder.INBOX) + state.folders).filterNot { it == state.filter.folder },
                        current = null,
                        loading = state.loadingFolders,
                        error = folderError,
                    ) { folder -> controls.moveSelected((folder as? MailFolder)?.id) }
                }
                MailTagBatchActions(state, controls.tags, canChange)
            }
        }
    }
}

@Composable
internal fun MailScopeButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.widthIn(max = 136.dp)) {
        Text(label, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
        PlazaIcon(Icons.Default.ArrowDropDown, contentDescription = null)
    }
}

@Composable
private fun MailSearchField(generation: Long, query: String, enabled: Boolean, onSearch: (String) -> Unit) {
    var search by rememberSaveable(generation, query) { mutableStateOf(query) }
    val focus = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val submit = {
        onSearch(search)
        focus.clearFocus()
    }
    val fill = LocalPlazaLayers.current.raised
    TextField(
        value = search,
        onValueChange = { search = it },
        placeholder = { Text(stringResource(Res.string.mail_search_hint)) },
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        enabled = enabled,
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = fill,
            unfocusedContainerColor = fill,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { submit() }),
        leadingIcon = {
            IconButton(onClick = submit, enabled = enabled) {
                PlazaIcon(Icons.Default.Search, contentDescription = stringResource(Res.string.action_search))
            }
        },
        trailingIcon = if (search.isNotEmpty() || query.isNotEmpty()) {
            {
                IconButton(onClick = {
                    search = ""
                    onSearch("")
                }, enabled = enabled) {
                    PlazaIcon(Icons.Default.Close, contentDescription = stringResource(Res.string.action_close))
                }
            }
        } else {
            null
        },
    )
    LaunchedEffect(focusRequester) {
        if (query.isEmpty() && enabled) focusRequester.requestFocus()
    }
}

@Composable
private fun MailFolderMenu(
    expanded: Boolean,
    dismiss: () -> Unit,
    folders: List<MailFolderScope>,
    current: MailFolderScope?,
    loading: Boolean,
    error: String?,
    select: (MailFolderScope) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = dismiss) {
        folders.forEach { folder ->
            DropdownMenuItem(
                text = { Text(folder.label()) },
                trailingIcon = if (current == folder) {
                    { PlazaIcon(Icons.Default.Check, contentDescription = null) }
                } else {
                    null
                },
                onClick = {
                    dismiss()
                    select(folder)
                },
            )
        }
        if (error != null) DropdownMenuItem(text = { Text(error) }, enabled = false, onClick = {})
        if (loading) DropdownMenuItem(text = { Text(stringResource(Res.string.mail_loading)) }, enabled = false, onClick = {})
    }
}
