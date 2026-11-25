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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nodyssey.data.mail.MailError
import io.github.nodyssey.ui.common.PlazaSheet
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.mail_loading
import io.github.nodyssey.ui.resources.mail_scope_tag_read
import io.github.nodyssey.ui.resources.mail_scope_tag_write
import io.github.nodyssey.ui.resources.mail_tag_add
import io.github.nodyssey.ui.resources.mail_tag_create
import io.github.nodyssey.ui.resources.mail_tag_name
import io.github.nodyssey.ui.resources.mail_tag_no_filter
import io.github.nodyssey.ui.resources.mail_tag_remove
import io.github.nodyssey.ui.resources.mail_tags
import io.github.plaza.designsys.component.GlassMenuAction
import io.github.plaza.designsys.component.GroupedListItem
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.PlazaFieldDefaults
import io.github.plaza.designsys.component.PlazaIcon
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.PlazaSpinner
import io.github.plaza.designsys.theme.Spacing
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
            trailingIcon = if (state.filter.tagId == null) {
                { PlazaIcon(Icons.Default.Check, contentDescription = null) }
            } else {
                null
            },
            onClick = {
                dismissMenu()
                onFilter(null)
            },
        )
        state.tags.forEach { tag ->
            DropdownMenuItem(
                text = { Text(tag.name) },
                trailingIcon = if (state.filter.tagId == tag.id) {
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
        MailCreateTagSheet(
            state = state,
            controls = controls,
            onDismiss = { creating = false },
        )
    }
}

/** Native-sheet tag picker used when triggered from iOS's native top-bar `UIMenu`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MailTagSheet(
    state: MailInboxUiState,
    controls: MailTagControls,
    onFilter: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember(state.generation) { mutableStateOf(false) }
    val error = state.tagsError?.let {
        stringResource(if (it == MailError.PERMISSION) Res.string.mail_scope_tag_read else it.messageRes())
    }
    PlazaSheet(
        onDismiss = onDismiss,
        title = stringResource(Res.string.mail_tags),
        fitNativeSheetContent = true,
        action = {
            TextButton(
                onClick = {
                    controls.clearCreated()
                    creating = true
                },
                enabled = !state.loadingTags,
            ) {
                PlazaIcon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(Res.string.mail_tag_create))
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LayerPageGutter)
                .padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (state.loadingTags) {
                LayerCard(contentPadding = PaddingValues(Spacing.lg)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlazaSpinner(Modifier.size(18.dp))
                        Text(stringResource(Res.string.mail_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (error != null) {
                LayerCard(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                    contentPadding = PaddingValues(Spacing.lg),
                ) {
                    Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            Column {
                val totalRows = 1 + state.tags.size
                GroupedListItem(
                    first = true,
                    last = totalRows == 1,
                    selected = state.filter.tagId == null,
                    onClick = {
                        onFilter(null)
                        onDismiss()
                    },
                    headlineContent = { Text(stringResource(Res.string.mail_tag_no_filter)) },
                    trailingContent = if (state.filter.tagId == null) {
                        { PlazaIcon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                )
                state.tags.forEachIndexed { index, tag ->
                    val active = state.filter.tagId == tag.id
                    GroupedListItem(
                        first = false,
                        last = index == state.tags.lastIndex,
                        selected = active,
                        onClick = {
                            onFilter(tag.id)
                            onDismiss()
                        },
                        headlineContent = { Text(tag.name) },
                        trailingContent = if (active) {
                            { PlazaIcon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
    if (creating) {
        MailCreateTagSheet(
            state = state,
            controls = controls,
            onDismiss = { creating = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MailCreateTagSheet(
    state: MailInboxUiState,
    controls: MailTagControls,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    LaunchedEffect(state.createdTag) {
        if (state.createdTag != null) {
            onDismiss()
            controls.clearCreated()
        }
    }
    PlazaSheet(
        onDismiss = { if (!state.creatingTag) onDismiss() },
        title = stringResource(Res.string.mail_tag_create),
        fitNativeSheetContent = true,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LayerPageGutter)
                .padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            LayerCard(
                contentPadding = PaddingValues(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.mail_tag_name)) },
                    singleLine = true,
                    enabled = !state.creatingTag,
                    shape = PlazaFieldDefaults.shape,
                    colors = PlazaFieldDefaults.colors(inCard = true),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.tagsError?.let {
                    val message = if (it == MailError.PERMISSION) Res.string.mail_scope_tag_write else it.messageRes()
                    Text(stringResource(message), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = { controls.create(name) },
                    enabled = name.isNotBlank() && !state.creatingTag,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.creatingTag) {
                        PlazaSpinner(Modifier.size(16.dp))
                        Spacer(Modifier.width(Spacing.sm))
                    }
                    Text(stringResource(Res.string.mail_tag_create))
                }
            }
        }
    }
}

@Composable
internal fun mailTagBatchNativeMenu(
    state: MailInboxUiState,
    controls: MailTagControls,
): List<GlassMenuAction> {
    val selected = state.messages.filter { it.id in state.selectedIds }
    val actions = mutableListOf<GlassMenuAction>()
    state.tags.forEach { tag ->
        val someTagged = selected.any { mail -> mail.tags.orEmpty().any { it.id == tag.id } }
        if (selected.any { mail -> mail.tags.orEmpty().none { it.id == tag.id } }) {
            actions += GlassMenuAction(
                label = stringResource(Res.string.mail_tag_add, tag.name),
                symbol = "plus.circle",
                onClick = { controls.setSelected(tag.id, true) },
            )
        }
        if (someTagged) {
            actions += GlassMenuAction(
                label = stringResource(Res.string.mail_tag_remove, tag.name),
                symbol = "minus.circle",
                destructive = true,
                onClick = { controls.setSelected(tag.id, false) },
            )
        }
    }
    if (actions.isEmpty()) {
        val status = when {
            state.loadingTags -> stringResource(Res.string.mail_loading)

            state.tagsError != null -> stringResource(
                if (state.tagsError == MailError.PERMISSION) Res.string.mail_scope_tag_read else state.tagsError.messageRes(),
            )

            else -> stringResource(Res.string.mail_tag_create)
        }
        actions += GlassMenuAction(label = status, enabled = false, onClick = {})
    }
    return actions
}

@Composable
internal fun MailTagBatchActions(state: MailInboxUiState, controls: MailTagControls, enabled: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val selected = state.messages.filter { it.id in state.selectedIds }
    Box {
        TextButton(
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 8.dp),
            onClick = {
                controls.load()
                expanded = true
            },
        ) {
            PlazaIcon(PlazaIcons.Label, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(Res.string.mail_tags), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
