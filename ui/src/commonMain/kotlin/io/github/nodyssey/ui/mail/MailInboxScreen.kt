package io.github.nodyssey.ui.mail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.data.mail.MailError
import io.github.nodyssey.data.mail.MailMessage
import io.github.nodyssey.data.session.SessionRepository
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.action_cancel
import io.github.nodyssey.ui.resources.action_refresh
import io.github.nodyssey.ui.resources.action_retry
import io.github.nodyssey.ui.resources.mail_api_key
import io.github.nodyssey.ui.resources.mail_authorize
import io.github.nodyssey.ui.resources.mail_authorize_hint
import io.github.nodyssey.ui.resources.mail_choose_mailbox
import io.github.nodyssey.ui.resources.mail_connect
import io.github.nodyssey.ui.resources.mail_connect_hint
import io.github.nodyssey.ui.resources.mail_connect_title
import io.github.nodyssey.ui.resources.mail_connecting
import io.github.nodyssey.ui.resources.mail_create_key
import io.github.nodyssey.ui.resources.mail_disconnect
import io.github.nodyssey.ui.resources.mail_disconnect_hint
import io.github.nodyssey.ui.resources.mail_empty
import io.github.nodyssey.ui.resources.mail_inbox
import io.github.nodyssey.ui.resources.mail_load_more
import io.github.nodyssey.ui.resources.mail_loading
import io.github.nodyssey.ui.resources.mail_no_mailboxes
import io.github.nodyssey.ui.resources.mail_no_subject
import io.github.nodyssey.ui.resources.mail_open_website
import io.github.nodyssey.ui.resources.mail_reauthorize
import io.github.nodyssey.ui.resources.mail_scopes_hint
import io.github.nodyssey.ui.resources.mail_title
import io.github.nodyssey.ui.resources.mail_unread
import io.github.nodyssey.ui.resources.mail_use_key
import io.github.plaza.core.net.UserAgent
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaBackHandler
import io.github.plaza.designsys.component.PlazaFieldDefaults
import io.github.plaza.designsys.component.PlazaSpinner
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.stringResource

@Composable
fun MailInboxRoute(
    viewModel: MailInboxViewModel,
    onBack: () -> Unit,
    onOpenMail: (MailMessage) -> Unit,
    onOpenWebsite: (String) -> Unit,
    session: SessionRepository,
    userAgent: UserAgent,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.authorizing) {
        PlazaBackHandler { viewModel.cancelAuthorization() }
        MailSignInRoute(
            session = session,
            userAgent = userAgent,
            onToken = viewModel::completeAuthorization,
            onClose = viewModel::cancelAuthorization,
            onOpenExternal = onOpenWebsite,
        )
        return
    }
    MailInboxScreen(
        state = state,
        apiKey = viewModel.apiKey,
        onBack = onBack,
        onConnect = viewModel::connect,
        onDisconnect = viewModel::requestDisconnect,
        onConfirmDisconnect = viewModel::disconnect,
        onDismissDisconnect = viewModel::dismissDisconnect,
        onRefresh = { if (state.connected) viewModel.refresh() else viewModel.initialize() },
        onSelectMailbox = viewModel::selectMailbox,
        onLoadMore = viewModel::loadMore,
        onOpenMail = onOpenMail,
        onOpenWebsite = onOpenWebsite,
        onAuthorize = viewModel::authorize,
    )
}

@Composable
fun MailInboxScreen(
    state: MailInboxUiState,
    apiKey: TextFieldState,
    onBack: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onConfirmDisconnect: () -> Unit,
    onDismissDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onSelectMailbox: (Long) -> Unit,
    onLoadMore: () -> Unit,
    onOpenMail: (MailMessage) -> Unit,
    onOpenWebsite: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAuthorize: () -> Unit = {},
) {
    val appBarState = rememberOneHandAppBarState()
    var choosingMailbox by remember { mutableStateOf(false) }
    var usingKey by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        topBar = {
            OneHandTopAppBar(
                title = stringResource(Res.string.mail_title),
                state = appBarState,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.action_back))
                    }
                },
                actions = {
                    if (state.connected) {
                        IconButton(onClick = onRefresh, enabled = !state.loading) {
                            Icon(Icons.Default.Refresh, stringResource(Res.string.action_refresh))
                        }
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = onRefresh,
            enabled = state.connected && !state.loadingMore,
            modifier = Modifier.padding(padding).fillMaxSize().readableWidth(),
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                if (state.initializing) {
                    item { PlazaSpinner(Modifier.padding(LayerPageGutter)) }
                } else if (!state.connected) {
                    item {
                        Column(
                            Modifier.padding(LayerPageGutter),
                            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                        ) {
                            Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(Res.string.mail_connect_title), style = MaterialTheme.typography.headlineSmall)
                            Text(stringResource(Res.string.mail_authorize_hint), style = MaterialTheme.typography.bodyLarge)
                            Button(onClick = onAuthorize, enabled = !state.connecting) {
                                Text(stringResource(Res.string.mail_authorize))
                            }
                            TextButton(onClick = { usingKey = !usingKey }) { Text(stringResource(Res.string.mail_use_key)) }
                            if (usingKey) {
                                Text(stringResource(Res.string.mail_connect_hint), style = MaterialTheme.typography.bodyLarge)
                                Text(stringResource(Res.string.mail_scopes_hint), style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = { onOpenWebsite("https://seek.li/manage/support?tab=developer") }) {
                                    Text(stringResource(Res.string.mail_create_key))
                                }
                                OutlinedSecureTextField(
                                    state = apiKey,
                                    label = { Text(stringResource(Res.string.mail_api_key)) },
                                    textObfuscationMode = TextObfuscationMode.Hidden,
                                    enabled = !state.connecting,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = PlazaFieldDefaults.shape,
                                    colors = PlazaFieldDefaults.colors(),
                                )
                                Button(onClick = onConnect, enabled = !state.connecting && apiKey.text.isNotBlank()) {
                                    Text(stringResource(if (state.connecting) Res.string.mail_connecting else Res.string.mail_connect))
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Column(Modifier.padding(horizontal = LayerPageGutter)) {
                            Text(stringResource(Res.string.mail_inbox), style = MaterialTheme.typography.titleLarge)
                            Row {
                                Column(Modifier.weight(1f)) {
                                    TextButton(onClick = { choosingMailbox = true }) {
                                        Text(
                                            state.mailboxes.firstOrNull { it.id == state.selectedMailboxId }?.address
                                                ?: stringResource(Res.string.mail_choose_mailbox),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                    DropdownMenu(expanded = choosingMailbox, onDismissRequest = { choosingMailbox = false }) {
                                        state.mailboxes.forEach { mailbox ->
                                            DropdownMenuItem(
                                                text = { Text(mailbox.address) },
                                                onClick = {
                                                    choosingMailbox = false
                                                    onSelectMailbox(mailbox.id)
                                                },
                                            )
                                        }
                                    }
                                }
                                TextButton(onClick = onDisconnect) { Text(stringResource(Res.string.mail_disconnect)) }
                            }
                        }
                    }
                    if (!state.loading && state.error == null && state.messages.isEmpty()) {
                        item {
                            Text(
                                stringResource(if (state.mailboxes.isEmpty()) Res.string.mail_no_mailboxes else Res.string.mail_empty),
                                Modifier.padding(LayerPageGutter),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (state.mailboxes.isEmpty()) {
                                TextButton(onClick = { onOpenWebsite("https://seek.li/") }) {
                                    Text(stringResource(Res.string.mail_open_website))
                                }
                            }
                        }
                    }
                    items(state.messages, key = MailMessage::id) { mail ->
                        ListItem(
                            onClick = { onOpenMail(mail) },
                            overlineContent = { Text(mail.sender, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(mailTimestamp(mail.receivedAt), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            trailingContent = {
                                if (!mail.isRead) {
                                    Text(
                                        stringResource(Res.string.mail_unread),
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            },
                        ) {
                            Text(
                                mail.subject?.takeIf(String::isNotBlank) ?: stringResource(Res.string.mail_no_subject),
                                fontWeight = if (mail.isRead) FontWeight.Normal else FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        HorizontalDivider(Modifier.padding(horizontal = LayerPageGutter))
                    }
                    if (state.nextOffset != null) {
                        item {
                            TextButton(onClick = onLoadMore, enabled = !state.loading && !state.loadingMore) {
                                Text(stringResource(if (state.loadingMore) Res.string.mail_loading else Res.string.mail_load_more))
                            }
                        }
                    }
                }
                state.error?.let { error ->
                    item {
                        Column(Modifier.padding(LayerPageGutter)) {
                            Text(stringResource(error.messageRes()), color = MaterialTheme.colorScheme.error)
                            if (error == MailError.CHALLENGE) {
                                TextButton(onClick = onAuthorize) { Text(stringResource(Res.string.mail_reauthorize)) }
                            }
                            if (state.connected || (error == MailError.STORAGE && !state.connecting)) {
                                TextButton(onClick = onRefresh) {
                                    Text(stringResource(Res.string.action_retry))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (state.confirmingDisconnect) {
        AlertDialog(
            onDismissRequest = onDismissDisconnect,
            title = { Text(stringResource(Res.string.mail_disconnect)) },
            text = { Text(stringResource(Res.string.mail_disconnect_hint)) },
            confirmButton = { TextButton(onClick = onConfirmDisconnect) { Text(stringResource(Res.string.mail_disconnect)) } },
            dismissButton = { TextButton(onClick = onDismissDisconnect) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}
