package io.github.nodyssey.ui.mail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.mail.MailAuthMode
import io.github.nodyssey.data.mail.MailError
import io.github.nodyssey.data.mail.MailMessage
import io.github.nodyssey.data.session.SessionRepository
import io.github.nodyssey.ui.login.WebViewGoal
import io.github.nodyssey.ui.login.WebViewRoute
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.action_cancel
import io.github.nodyssey.ui.resources.action_refresh
import io.github.nodyssey.ui.resources.action_retry
import io.github.nodyssey.ui.resources.mail_api_key
import io.github.nodyssey.ui.resources.mail_api_key_switch_hint
import io.github.nodyssey.ui.resources.mail_api_key_switch_title
import io.github.nodyssey.ui.resources.mail_authorize
import io.github.nodyssey.ui.resources.mail_authorize_hint
import io.github.nodyssey.ui.resources.mail_authorize_website
import io.github.nodyssey.ui.resources.mail_choose_mailbox
import io.github.nodyssey.ui.resources.mail_connect
import io.github.nodyssey.ui.resources.mail_connect_hint
import io.github.nodyssey.ui.resources.mail_connect_title
import io.github.nodyssey.ui.resources.mail_connecting
import io.github.nodyssey.ui.resources.mail_create_key
import io.github.nodyssey.ui.resources.mail_default_mailbox
import io.github.nodyssey.ui.resources.mail_disconnect
import io.github.nodyssey.ui.resources.mail_disconnect_hint
import io.github.nodyssey.ui.resources.mail_empty
import io.github.nodyssey.ui.resources.mail_inbox
import io.github.nodyssey.ui.resources.mail_load_more
import io.github.nodyssey.ui.resources.mail_loading
import io.github.nodyssey.ui.resources.mail_mark_all_read
import io.github.nodyssey.ui.resources.mail_mark_all_read_done
import io.github.nodyssey.ui.resources.mail_mark_all_read_failed
import io.github.nodyssey.ui.resources.mail_marking_all_read
import io.github.nodyssey.ui.resources.mail_mode_api_key
import io.github.nodyssey.ui.resources.mail_mode_web_session
import io.github.nodyssey.ui.resources.mail_no_mailboxes
import io.github.nodyssey.ui.resources.mail_open_website
import io.github.nodyssey.ui.resources.mail_options
import io.github.nodyssey.ui.resources.mail_poll_api_hint
import io.github.nodyssey.ui.resources.mail_poll_web_hint
import io.github.nodyssey.ui.resources.mail_replace_key
import io.github.nodyssey.ui.resources.mail_scopes_hint
import io.github.nodyssey.ui.resources.mail_switch_web_session
import io.github.nodyssey.ui.resources.mail_title
import io.github.nodyssey.ui.resources.mail_use_key
import io.github.nodyssey.ui.resources.mail_verify_and_retry
import io.github.nodyssey.ui.resources.mail_verify_challenge
import io.github.plaza.core.net.UserAgent
import io.github.plaza.designsys.component.GroupCard
import io.github.plaza.designsys.component.GroupedListItem
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaBackHandler
import io.github.plaza.designsys.component.PlazaFieldDefaults
import io.github.plaza.designsys.component.PlazaSpinner
import io.github.plaza.designsys.component.SectionLabel
import io.github.plaza.designsys.component.TonalTag
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
    val snackbarHostState = remember { SnackbarHostState() }
    val allReadMessage = when {
        state.markAllReadError != null -> stringResource(Res.string.mail_mark_all_read_failed, stringResource(state.markAllReadError!!.messageRes()))
        state.allReadComplete -> stringResource(Res.string.mail_mark_all_read_done)
        else -> null
    }
    LaunchedEffect(allReadMessage) {
        if (allReadMessage != null) {
            snackbarHostState.showSnackbar(allReadMessage)
            viewModel.dismissAllReadResult()
        }
    }
    if (state.verifyingChallenge) {
        WebViewRoute(
            url = NodeSeekSite.BASE_URL,
            title = stringResource(Res.string.mail_verify_challenge),
            goal = WebViewGoal.CHALLENGE,
            session = session,
            userAgent = userAgent,
            onOpenExternal = onOpenWebsite,
            onClose = viewModel::retryAfterChallengeVerification,
        )
        return
    }
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
        onBack = {
            viewModel.cancelAuthorization()
            onBack()
        },
        onConnect = viewModel::connect,
        onDisconnect = viewModel::requestDisconnect,
        onConfirmDisconnect = viewModel::disconnect,
        onDismissDisconnect = viewModel::dismissDisconnect,
        onRefresh = { if (state.connected) viewModel.refresh() else viewModel.initialize() },
        onSelectMailbox = viewModel::selectMailbox,
        onLoadMore = viewModel::loadMore,
        onMarkAllRead = viewModel::markAllRead,
        onOpenMail = onOpenMail,
        onOpenWebsite = onOpenWebsite,
        onAuthorize = viewModel::authorize,
        onAuthorizeWebsite = viewModel::authorizeOnWebsite,
        onVerifyChallenge = {
            if (session.peek().hasClearance) {
                viewModel.retryAfterChallengeVerification()
            } else {
                viewModel.openChallengeVerification()
            }
        },
        onCancelAuthorization = viewModel::cancelAuthorization,
        snackbarHostState = snackbarHostState,
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
    onAuthorizeWebsite: () -> Unit = {},
    onVerifyChallenge: () -> Unit = {},
    onCancelAuthorization: () -> Unit = {},
    onMarkAllRead: () -> Unit = {},
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
) {
    val appBarState = rememberOneHandAppBarState()
    var choosingMailbox by remember { mutableStateOf(false) }
    var showingMailboxActions by remember { mutableStateOf(false) }
    var usingKey by remember { mutableStateOf(false) }
    var switchingToKey by remember { mutableStateOf(false) }
    var keySwitchGeneration by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.authMode, state.connected, state.generation, switchingToKey, keySwitchGeneration) {
        if (switchingToKey && state.connected && state.authMode == MailAuthMode.API_KEY &&
            keySwitchGeneration != null && state.generation != keySwitchGeneration
        ) {
            switchingToKey = false
            keySwitchGeneration = null
            apiKey.clearText()
        }
    }
    PlazaBackHandler(enabled = state.connecting, onBack = onCancelAuthorization)
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                                Text(stringResource(if (state.connecting) Res.string.mail_connecting else Res.string.mail_authorize))
                            }
                            if (state.connecting) {
                                TextButton(onClick = onCancelAuthorization) { Text(stringResource(Res.string.action_cancel)) }
                            }
                            TextButton(onClick = onAuthorizeWebsite, enabled = !state.connecting) {
                                Text(stringResource(Res.string.mail_authorize_website))
                            }
                            TextButton(onClick = { usingKey = !usingKey }, enabled = !state.connecting) { Text(stringResource(Res.string.mail_use_key)) }
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
                        val selectedMailbox = state.mailboxes.firstOrNull { it.id == state.selectedMailboxId }
                        GroupCard(Modifier.padding(horizontal = LayerPageGutter)) {
                            Box(Modifier.fillMaxWidth()) {
                                GroupedListItem(
                                    headlineContent = {
                                        Text(
                                            selectedMailbox?.address ?: stringResource(Res.string.mail_choose_mailbox),
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    supportingContent = {
                                        state.authMode?.let { mode ->
                                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                                TonalTag(
                                                    text = stringResource(if (mode == MailAuthMode.API_KEY) Res.string.mail_mode_api_key else Res.string.mail_mode_web_session),
                                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    icon = Icons.Default.Check,
                                                )
                                                Text(
                                                    stringResource(if (mode == MailAuthMode.API_KEY) Res.string.mail_poll_api_hint else Res.string.mail_poll_web_hint),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    },
                                    first = true,
                                    last = true,
                                    onClick = { if (!state.markingAllRead) choosingMailbox = true },
                                    trailingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                            Box {
                                                IconButton(onClick = { showingMailboxActions = true }, enabled = !state.connecting) {
                                                    Icon(
                                                        Icons.Default.MoreVert,
                                                        contentDescription = stringResource(Res.string.mail_options),
                                                    )
                                                }
                                                DropdownMenu(
                                                    expanded = showingMailboxActions,
                                                    onDismissRequest = { showingMailboxActions = false },
                                                ) {
                                                    if (state.authMode == MailAuthMode.API_KEY) {
                                                        DropdownMenuItem(
                                                            text = { Text(stringResource(Res.string.mail_switch_web_session)) },
                                                            enabled = !state.connecting,
                                                            onClick = {
                                                                showingMailboxActions = false
                                                                onAuthorize()
                                                            },
                                                        )
                                                    }
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(stringResource(if (state.authMode == MailAuthMode.API_KEY) Res.string.mail_replace_key else Res.string.mail_use_key))
                                                        },
                                                        onClick = {
                                                            showingMailboxActions = false
                                                            keySwitchGeneration = state.generation
                                                            switchingToKey = true
                                                        },
                                                    )
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                stringResource(Res.string.mail_disconnect),
                                                                color = MaterialTheme.colorScheme.error,
                                                            )
                                                        },
                                                        onClick = {
                                                            showingMailboxActions = false
                                                            onDisconnect()
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    },
                                )
                                DropdownMenu(expanded = choosingMailbox, onDismissRequest = { choosingMailbox = false }) {
                                    state.mailboxes.forEach { mailbox ->
                                        DropdownMenuItem(
                                            text = { Text(mailbox.address) },
                                            enabled = !state.markingAllRead,
                                            trailingIcon = {
                                                if (mailbox.id == state.selectedMailboxId) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = stringResource(Res.string.mail_default_mailbox),
                                                    )
                                                }
                                            },
                                            onClick = {
                                                choosingMailbox = false
                                                onSelectMailbox(mailbox.id)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (state.connecting && !switchingToKey) {
                        item {
                            Row(
                                Modifier.padding(horizontal = LayerPageGutter),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                PlazaSpinner()
                                Text(stringResource(Res.string.mail_connecting), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                TextButton(onClick = onCancelAuthorization) { Text(stringResource(Res.string.action_cancel)) }
                            }
                        }
                    }
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(end = LayerPageGutter),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SectionLabel(stringResource(Res.string.mail_inbox), modifier = Modifier.weight(1f))
                            if (state.selectedMailboxId != null) {
                                TextButton(
                                    onClick = onMarkAllRead,
                                    enabled = !state.markingAllRead && !state.loading && !state.loadingMore && !state.connecting && state.messages.isNotEmpty(),
                                ) {
                                    if (state.markingAllRead) {
                                        PlazaSpinner(
                                            modifier = Modifier.padding(end = Spacing.sm),
                                            size = 16.dp,
                                            strokeWidth = 2.dp,
                                        )
                                    }
                                    Text(stringResource(if (state.markingAllRead) Res.string.mail_marking_all_read else Res.string.mail_mark_all_read))
                                }
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
                    itemsIndexed(state.messages, key = { _, mail -> mail.id }) { index, mail ->
                        MailMessageItem(
                            mail = mail,
                            preview = state.previews[mail.id],
                            first = index == 0,
                            last = index == state.messages.lastIndex,
                            nowMillis = state.nowMillis,
                            onOpen = { onOpenMail(mail) },
                            modifier = Modifier.padding(horizontal = LayerPageGutter),
                        )
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
                            if (error == MailError.CHALLENGE && state.challengeVerificationAvailable) {
                                TextButton(onClick = onVerifyChallenge, enabled = !state.connecting) {
                                    Text(stringResource(Res.string.mail_verify_and_retry))
                                }
                            } else if (error == MailError.CHALLENGE || error == MailError.WEB_AUTHORIZATION || error == MailError.FORUM_SIGN_IN) {
                                TextButton(onClick = onAuthorizeWebsite, enabled = !state.connecting) {
                                    Text(stringResource(Res.string.mail_authorize_website))
                                }
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
    if (switchingToKey && state.connected) {
        AlertDialog(
            onDismissRequest = {
                if (!state.connecting) {
                    switchingToKey = false
                    apiKey.clearText()
                }
            },
            title = {
                Text(stringResource(if (state.authMode == MailAuthMode.API_KEY) Res.string.mail_replace_key else Res.string.mail_api_key_switch_title))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(stringResource(Res.string.mail_api_key_switch_hint))
                    Text(stringResource(Res.string.mail_scopes_hint), style = MaterialTheme.typography.bodySmall)
                    TextButton(
                        onClick = { onOpenWebsite("https://seek.li/manage/support?tab=developer") },
                        enabled = !state.connecting,
                    ) {
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
                    state.error?.let { error ->
                        Text(stringResource(error.messageRes()), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(onClick = onConnect, enabled = !state.connecting && apiKey.text.isNotBlank()) {
                    Text(
                        stringResource(
                            when {
                                state.connecting -> Res.string.mail_connecting
                                state.authMode == MailAuthMode.API_KEY -> Res.string.mail_replace_key
                                else -> Res.string.mail_connect
                            },
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        switchingToKey = false
                        apiKey.clearText()
                    },
                    enabled = !state.connecting,
                ) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }
}
