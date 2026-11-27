package io.github.nodyssey.ui.mail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nodyssey.data.mail.MailError
import io.github.nodyssey.data.mail.MailFolder
import io.github.nodyssey.data.mail.MailFolderScope
import io.github.nodyssey.data.mail.MailListFilter
import io.github.nodyssey.data.mail.MailMessage
import io.github.nodyssey.data.mail.MailSystemFolder
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_close
import io.github.nodyssey.ui.resources.mail_folder_sent
import io.github.nodyssey.ui.resources.mail_folder_spam
import io.github.nodyssey.ui.resources.mail_folder_trash
import io.github.nodyssey.ui.resources.mail_folders_unavailable
import io.github.nodyssey.ui.resources.mail_inbox
import io.github.nodyssey.ui.resources.mail_loading
import io.github.nodyssey.ui.resources.mail_mark_selected_read
import io.github.nodyssey.ui.resources.mail_move_selected
import io.github.nodyssey.ui.resources.mail_search_hint
import io.github.nodyssey.ui.resources.mail_tags
import io.github.nodyssey.ui.resources.mail_unread_status
import io.github.plaza.designsys.component.AdaptiveGlassToolbar
import io.github.plaza.designsys.component.GlassAction
import io.github.plaza.designsys.component.GlassMenuAction
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.PlazaChipDefaults
import io.github.plaza.designsys.component.PlazaIcon
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.theme.GlassSurfaceStyle
import io.github.plaza.designsys.theme.LocalLiquidGlassBackdrop
import io.github.plaza.designsys.theme.LocalPlazaDarkTheme
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.backdropGlass
import io.github.plaza.designsys.theme.cardBorderStroke
import io.github.plaza.designsys.theme.floatShadow
import io.github.plaza.designsys.theme.glassChromeSurface
import kotlinx.coroutines.delay
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
    mailboxPicker: @Composable () -> Unit,
    onOpenTags: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var choosingFolder by remember { mutableStateOf(false) }
    val enabled = !state.markingAllRead && !state.changingMessages
    val folderError = state.foldersError?.let {
        stringResource(if (it == MailError.PERMISSION) Res.string.mail_folders_unavailable else it.messageRes())
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = LayerPageGutter, end = LayerPageGutter, top = Spacing.xs, bottom = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (controls != null) {
            Box {
                MailScopeChip(
                    label = state.filter.folder.label(),
                    selected = state.filter.folder != MailSystemFolder.INBOX,
                    enabled = enabled,
                    leadingIcon = PlazaIcons.FolderOpen,
                    onClick = {
                        controls.loadFolders()
                        choosingFolder = true
                    },
                )
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
        Box { mailboxPicker() }
        if (controls != null && !state.filter.isSent) {
            FilterChip(
                selected = state.unreadOnly,
                onClick = controls.toggleUnread,
                enabled = enabled,
                label = { Text(stringResource(Res.string.mail_unread_status), style = MaterialTheme.typography.labelLarge) },
                modifier = Modifier.heightIn(min = PlazaChipDefaults.Height),
                shape = PlazaChipDefaults.shape,
                colors = mailFilterChipColors(),
                border = PlazaChipDefaults.border(state.unreadOnly),
            )
        }
        val activeTag = state.tags.firstOrNull { it.id == state.filter.tagId }
        if (activeTag != null && controls != null) {
            FilterChip(
                selected = true,
                onClick = onOpenTags,
                enabled = enabled,
                leadingIcon = { PlazaIcon(PlazaIcons.Label, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text(activeTag.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingIcon = {
                    PlazaIcon(
                        Icons.Default.Close,
                        contentDescription = stringResource(Res.string.action_close),
                        modifier = Modifier.size(16.dp),
                    )
                },
                modifier = Modifier.heightIn(min = PlazaChipDefaults.Height),
                shape = PlazaChipDefaults.shape,
                colors = PlazaChipDefaults.filterChipColors(),
                border = PlazaChipDefaults.border(selected = true),
            )
        }
    }
}

@Composable
internal fun MailSelectionBottomBar(
    state: MailInboxUiState,
    controls: MailInboxControls,
    modifier: Modifier = Modifier,
) {
    var moving by remember { mutableStateOf(false) }
    val enabled = !state.markingAllRead && !state.changingMessages
    val canChange = enabled && !state.loading && !state.loadingMore && state.selectedIds.isNotEmpty()
    val canMove = canChange && (state.filter.folder == MailSystemFolder.INBOX || state.filter.folder is MailFolder)
    val folderError = state.foldersError?.let {
        stringResource(if (it == MailError.PERMISSION) Res.string.mail_folders_unavailable else it.messageRes())
    }
    val moveTargets = (listOf(MailSystemFolder.INBOX) + state.folders).filterNot { it == state.filter.folder }
    val moveMenu = moveTargets.map { folder ->
        GlassMenuAction(
            label = folder.label(),
            symbol = "folder",
            onClick = { controls.moveSelected((folder as? MailFolder)?.id) },
        )
    }
    val tagBatchMenu = mailTagBatchNativeMenu(state, controls.tags)
    val markReadLabel = stringResource(Res.string.mail_mark_selected_read)
    val moveLabel = stringResource(Res.string.mail_move_selected)
    val tagsLabel = stringResource(Res.string.mail_tags)
    val layers = LocalPlazaLayers.current
    val darkTheme = LocalPlazaDarkTheme.current
    val backdrop = LocalLiquidGlassBackdrop.current
    val shape = CircleShape
    AdaptiveGlassToolbar(
        actions = listOf(
            GlassAction(
                label = markReadLabel,
                title = markReadLabel,
                symbol = "checkmark.circle",
                enabled = canChange,
                onClick = controls.markSelectedRead,
            ),
            GlassAction(
                label = moveLabel,
                title = moveLabel,
                symbol = "folder",
                enabled = canMove,
                menu = moveMenu,
                onClick = { controls.loadFolders() },
            ),
            GlassAction(
                label = tagsLabel,
                title = tagsLabel,
                symbol = "tag",
                enabled = canChange,
                menu = tagBatchMenu,
                onClick = { controls.tags.load() },
            ),
        ),
        modifier = modifier.padding(horizontal = Spacing.md, vertical = Spacing.lg),
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.lg)
                .then(
                    if (layers.backdropGlass) {
                        Modifier.glassChromeSurface(
                            layers = layers,
                            darkTheme = darkTheme,
                            shape = shape,
                            elevated = true,
                            backdrop = backdrop,
                            style = GlassSurfaceStyle.Navigation,
                        )
                    } else {
                        Modifier.floatShadow(shape, layers.shadows)
                    },
                ),
            shape = shape,
            color = if (layers.backdropGlass) Color.Transparent else layers.raised,
            border = if (layers.backdropGlass) null else layers.cardBorderStroke,
        ) {
            Row(
                modifier = Modifier.heightIn(min = 58.dp).padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = controls.markSelectedRead,
                    enabled = canChange,
                    contentPadding = PaddingValues(horizontal = Spacing.md),
                ) {
                    PlazaIcon(PlazaIcons.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        markReadLabel,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                }
                Box {
                    TextButton(
                        onClick = {
                            controls.loadFolders()
                            moving = true
                        },
                        enabled = canMove,
                        contentPadding = PaddingValues(horizontal = Spacing.sm),
                    ) {
                        PlazaIcon(PlazaIcons.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(moveLabel, maxLines = 1)
                    }
                    MailFolderMenu(
                        expanded = moving,
                        dismiss = { moving = false },
                        folders = moveTargets,
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
internal fun MailScopeChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    leadingIcon: ImageVector? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        leadingIcon = leadingIcon?.let { icon ->
            { PlazaIcon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) }
        },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp),
            )
        },
        trailingIcon = {
            PlazaIcon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
        },
        modifier = Modifier.heightIn(min = PlazaChipDefaults.Height),
        shape = PlazaChipDefaults.shape,
        colors = mailFilterChipColors(),
        border = PlazaChipDefaults.border(selected),
    )
}

@Composable
private fun mailFilterChipColors() = PlazaChipDefaults.filterChipColors(
    containerColor = with(LocalPlazaLayers.current) { if (raised == page) inset else raised },
)

@Composable
internal fun MailSearchTopBarField(
    generation: Long,
    query: String,
    enabled: Boolean,
    onSearch: (String) -> Unit,
) {
    var text by remember(generation) { mutableStateOf(query) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(query) { if (query != text) text = query }
    LaunchedEffect(generation, text) {
        if (text == query) return@LaunchedEffect
        delay(350)
        onSearch(text)
    }
    TextField(
        value = text,
        onValueChange = { text = it },
        enabled = enabled,
        singleLine = true,
        placeholder = { Text(stringResource(Res.string.mail_search_hint)) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = {
                    text = ""
                    onSearch("")
                }) {
                    PlazaIcon(Icons.Default.Close, contentDescription = stringResource(Res.string.action_close))
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            onSearch(text)
            keyboard?.hide()
        }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
    )
}

@Composable
private fun MailFolderMenu(
    expanded: Boolean,
    dismiss: () -> Unit,
    folders: List<MailFolderScope>,
    current: MailFolderScope?,
    loading: Boolean,
    error: String?,
    onSelect: (MailFolderScope) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = dismiss, modifier = Modifier.width(200.dp)) {
        folders.forEach { folder ->
            DropdownMenuItem(
                text = { Text(folder.label(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingIcon = if (folder == current) {
                    { PlazaIcon(Icons.Default.Check, contentDescription = null) }
                } else {
                    null
                },
                onClick = {
                    dismiss()
                    onSelect(folder)
                },
            )
        }
        if (loading) {
            DropdownMenuItem(text = { Text(stringResource(Res.string.mail_loading)) }, enabled = false, onClick = {})
        }
        if (error != null) {
            DropdownMenuItem(text = { Text(error) }, enabled = false, onClick = {})
        }
    }
}
