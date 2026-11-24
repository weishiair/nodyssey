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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.data.mail.MailAuthMode
import io.github.nodyssey.data.mail.MailError
import io.github.nodyssey.data.mail.MailMessage
import io.github.nodyssey.data.session.SessionRepository
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_cancel
import io.github.nodyssey.ui.resources.action_close
import io.github.nodyssey.ui.resources.action_retry
import io.github.nodyssey.ui.resources.action_search
import io.github.nodyssey.ui.resources.mail_api_key
import io.github.nodyssey.ui.resources.mail_api_key_switch_hint
import io.github.nodyssey.ui.resources.mail_api_key_switch_title
import io.github.nodyssey.ui.resources.mail_authorize
import io.github.nodyssey.ui.resources.mail_authorize_hint
import io.github.nodyssey.ui.resources.mail_authorize_website
import io.github.nodyssey.ui.resources.mail_change_failed
import io.github.nodyssey.ui.resources.mail_compose
import io.github.nodyssey.ui.resources.mail_connect
import io.github.nodyssey.ui.resources.mail_connect_hint
import io.github.nodyssey.ui.resources.mail_connect_title
import io.github.nodyssey.ui.resources.mail_connecting
import io.github.nodyssey.ui.resources.mail_create_alias
import io.github.nodyssey.ui.resources.mail_create_key
import io.github.nodyssey.ui.resources.mail_disconnect
import io.github.nodyssey.ui.resources.mail_disconnect_hint
import io.github.nodyssey.ui.resources.mail_empty
import io.github.nodyssey.ui.resources.mail_load_more
import io.github.nodyssey.ui.resources.mail_loading
import io.github.nodyssey.ui.resources.mail_mark_all_read_done
import io.github.nodyssey.ui.resources.mail_mark_all_read_failed
import io.github.nodyssey.ui.resources.mail_marking_all_read
import io.github.nodyssey.ui.resources.mail_no_mailboxes
import io.github.nodyssey.ui.resources.mail_no_matches
import io.github.nodyssey.ui.resources.mail_open_website
import io.github.nodyssey.ui.resources.mail_options
import io.github.nodyssey.ui.resources.mail_replace_key
import io.github.nodyssey.ui.resources.mail_scopes_hint
import io.github.nodyssey.ui.resources.mail_title
import io.github.nodyssey.ui.resources.mail_use_key
import io.github.nodyssey.ui.resources.mail_verify_and_retry
import io.github.plaza.core.net.UserAgent
import io.github.plaza.designsys.component.AdaptiveAlertDialog
import io.github.plaza.designsys.component.AdaptivePullToRefreshBox
import io.github.plaza.designsys.component.AdaptiveTopBar
import io.github.plaza.designsys.component.GlassAction
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.PlazaBackHandler
import io.github.plaza.designsys.component.PlazaFieldDefaults
import io.github.plaza.designsys.component.PlazaIcon
import io.github.plaza.designsys.component.PlazaSnackbarHost
import io.github.plaza.designsys.component.PlazaSpinner
import io.github.plaza.designsys.component.TopBarAction
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.component.rememberPlatformHaptics
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import kotlinx.coroutines.launch
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
    var sentMail by remember(state.generation) { mutableStateOf<MailMessage?>(null) }
    val sent = sentMail.takeIf { state.connected }
    if (sent != null) {
        PlazaBackHandler { sentMail = null }
        MailDetailScreen(
            state = MailDetailUiState(loading = false, message = sent, body = sent.bodyText.orEmpty()),
            onBack = { sentMail = null },
            onRetry = {},
            onToggleRead = {},
            onOpenWebsite = onOpenWebsite,
            sent = true,
        )
        return
    }
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
    val mailboxError = state.mailboxActionError?.let { stringResource(Res.string.mail_change_failed, stringResource(it.messageRes())) }
    LaunchedEffect(mailboxError) {
        if (mailboxError != null) {
            snackbarHostState.showSnackbar(mailboxError)
            viewModel.dismissMailboxActionError()
        }
    }
    if (state.authorizing) {
        PlazaBackHandler { viewModel.cancelAuthorization() }
        MailSignInRoute(
            session = session,
            userAgent = userAgent,
            onToken = viewModel::completeAuthorization,
            onClose = viewModel::cancelAuthorization,
            onOpenExternal = onOpenWebsite,
            onChallengePassed = if (state.verifyingChallenge) viewModel::retryAfterChallengeVerification else null,
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
        onOpenMail = { if (state.filter.isSent) sentMail = it else onOpenMail(it) },
        onOpenWebsite = onOpenWebsite,
        onAuthorize = viewModel::authorize,
        onAuthorizeWebsite = viewModel::authorizeOnWebsite,
        onVerifyChallenge = viewModel::openChallengeVerification,
        onCancelAuthorization = viewModel::cancelAuthorization,
        snackbarHostState = snackbarHostState,
        controls = MailInboxControls(
            changeFilter = viewModel::changeFilter,
            toggleUnread = viewModel::toggleUnreadOnly,
            loadFolders = viewModel::loadFolders,
            toggleSelection = viewModel::toggleSelection,
            toggleSelected = viewModel::toggleSelected,
            selectVisible = viewModel::selectVisible,
            markSelectedRead = viewModel::markSelectedRead,
            moveSelected = viewModel::moveSelected,
            toggleFlag = viewModel::toggleFlag,
            checkAlias = viewModel::checkAlias,
            createAlias = viewModel::createAlias,
            resetAlias = viewModel::resetAlias,
            sendMail = viewModel::sendMail,
            resetCompose = viewModel::resetCompose,
            tags = MailTagControls(
                load = viewModel::loadTags,
                create = viewModel::createTag,
                clearCreated = viewModel::clearCreatedTag,
                setSelected = viewModel::setSelectedTag,
            ),
        ),
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
    onSelectMailbox: (Long?) -> Unit,
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
    controls: MailInboxControls? = null,
) {
    val appBarState = rememberOneHandAppBarState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val visibleMessages = state.visibleMessages
    var searchRequested by rememberSaveable(state.generation) { mutableStateOf(false) }
    val searchVisible = state.connected && (searchRequested || state.filter.search.isNotBlank())
    val closeSearch = {
        searchRequested = false
        controls?.changeFilter?.invoke(state.filter.copy(search = ""))
        Unit
    }
    var creatingAlias by remember(state.generation) { mutableStateOf(false) }
    var writing by remember(state.generation) { mutableStateOf(false) }
    if (writing && controls != null) {
        MailComposeDialog(
            state = state.compose,
            mailboxes = state.mailboxes.filterNot { it.sendDisabled },
            selectedMailboxId = state.selectedMailboxId,
            onSend = controls.sendMail,
            onDismiss = {
                writing = false
                controls.resetCompose()
            },
        )
    }
    if (creatingAlias && controls != null) {
        MailAliasDialog(
            state = state.alias,
            onCheck = controls.checkAlias,
            onCreate = controls.createAlias,
            onDismiss = {
                creatingAlias = false
                controls.resetAlias()
            },
        )
    }
    var menu by remember(state.generation) { mutableStateOf<MailInboxMenu?>(null) }
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
    PlazaBackHandler(enabled = searchVisible && !state.markingAllRead && !state.changingMessages, onBack = closeSearch)
    if (controls != null) PlazaBackHandler(enabled = state.selectionMode, onBack = controls.toggleSelection)
    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        topBar = {
            Box {
                AdaptiveTopBar(
                    title = stringResource(Res.string.mail_title),
                    state = appBarState,
                    onBack = onBack,
                    actions = if (state.connected) {
                        listOfNotNull(
                            controls?.takeIf { state.mailboxes.isNotEmpty() }?.let {
                                TopBarAction(
                                    glass = GlassAction(
                                        label = stringResource(if (searchVisible) Res.string.action_close else Res.string.action_search),
                                        onClick = {
                                            if (searchVisible) {
                                                closeSearch()
                                            } else {
                                                searchRequested = true
                                                scope.launch { listState.scrollToItem(0) }
                                            }
                                        },
                                        symbol = if (searchVisible) "xmark" else "magnifyingglass",
                                        enabled = !state.markingAllRead && !state.changingMessages,
                                    ),
                                    icon = if (searchVisible) Icons.Default.Close else Icons.Default.Search,
                                )
                            },
                            if (controls != null && state.mailboxes.any { !it.sendDisabled }) {
                                TopBarAction(
                                    glass = GlassAction(
                                        label = stringResource(Res.string.mail_compose),
                                        onClick = { writing = true },
                                        symbol = "square.and.pencil",
                                        enabled = !state.changingMessages && !state.connecting,
                                    ),
                                    icon = Icons.Default.Edit,
                                )
                            } else {
                                null
                            },
                            TopBarAction(
                                glass = GlassAction(
                                    label = stringResource(Res.string.mail_options),
                                    onClick = { menu = MailInboxMenu.Actions },
                                    symbol = "ellipsis",
                                ),
                                icon = Icons.Default.MoreVert,
                            ),
                        )
                    } else {
                        emptyList()
                    },
                )
                if (state.connected) {
                    Box(Modifier.align(Alignment.BottomEnd)) {
                        MailInboxOptions(
                            menu = menu,
                            setMenu = { menu = it },
                            state = state,
                            controls = controls,
                            onMarkAllRead = onMarkAllRead,
                            onRefresh = onRefresh,
                            onCreateAlias = { creatingAlias = true },
                            onAuthorize = onAuthorize,
                            onUseKey = {
                                keySwitchGeneration = state.generation
                                switchingToKey = true
                            },
                            onDisconnect = onDisconnect,
                        )
                    }
                }
            }
        },
        snackbarHost = { PlazaSnackbarHost(snackbarHostState) },
    ) { padding ->
        val pressHaptics = rememberPlatformHaptics()
        AdaptivePullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = {
                pressHaptics.refreshTriggered()
                onRefresh()
            },
            enabled = state.connected && !state.loadingMore,
            modifier = Modifier.padding(padding).fillMaxSize().readableWidth(),
        ) {
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                if (state.initializing) {
                    item { PlazaSpinner(Modifier.padding(LayerPageGutter)) }
                } else if (!state.connected) {
                    item {
                        Column(
                            Modifier.padding(LayerPageGutter),
                            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                        ) {
                            PlazaIcon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                        MailListToolbar(
                            state = state,
                            controls = controls?.takeIf { state.mailboxes.isNotEmpty() },
                            searchVisible = searchVisible,
                            mailboxPicker = { MailboxPicker(state, onSelectMailbox) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = LayerPageGutter),
                        )
                        state.tags.firstOrNull { it.id == state.filter.tagId }?.let { tag ->
                            TextButton(onClick = {
                                controls?.tags?.load?.invoke()
                                menu = MailInboxMenu.Tags
                            }, modifier = Modifier.padding(horizontal = LayerPageGutter)) { Text(tag.name) }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                    if (state.markingAllRead) {
                        item {
                            Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                                PlazaSpinner(Modifier.padding(end = Spacing.sm), size = 16.dp, strokeWidth = 2.dp)
                                Text(stringResource(Res.string.mail_marking_all_read), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (!state.loading && state.error == null && visibleMessages.isEmpty() && state.nextOffset == null) {
                        item {
                            Text(
                                stringResource(
                                    when {
                                        state.mailboxes.isEmpty() -> Res.string.mail_no_mailboxes
                                        !state.filter.isInbox || state.unreadOnly -> Res.string.mail_no_matches
                                        else -> Res.string.mail_empty
                                    },
                                ),
                                Modifier.padding(LayerPageGutter),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (state.mailboxes.isEmpty()) {
                                if (controls != null) {
                                    TextButton(onClick = { creatingAlias = true }) {
                                        Text(stringResource(Res.string.mail_create_alias))
                                    }
                                }
                                TextButton(onClick = { onOpenWebsite("https://seek.li/") }) {
                                    Text(stringResource(Res.string.mail_open_website))
                                }
                            }
                        }
                    }
                    itemsIndexed(visibleMessages, key = { _, mail -> mail.id }) { index, mail ->
                        if (index > 0) {
                            HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        MailMessageItem(
                            mail = mail,
                            preview = state.previews[mail.id],
                            nowMillis = state.nowMillis,
                            recipientAddress = mail.toAddress.takeIf(String::isNotBlank)
                                ?: state.mailboxes.firstOrNull { it.id == mail.mailboxId }?.address.orEmpty(),
                            onOpen = {
                                if (state.selectionMode && controls != null) controls.toggleSelected(mail.id) else onOpenMail(mail)
                            },
                            selected = (mail.id in state.selectedIds).takeIf { state.selectionMode },
                            onToggleFlag = controls?.takeUnless { state.filter.isSent }?.let { { it.toggleFlag(mail) } },
                            sent = state.filter.isSent,
                            flagEnabled = !state.changingMessages,
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
        AdaptiveAlertDialog(
            onDismiss = onDismissDisconnect,
            title = stringResource(Res.string.mail_disconnect),
            message = stringResource(Res.string.mail_disconnect_hint),
            confirmLabel = stringResource(Res.string.mail_disconnect),
            onConfirm = onConfirmDisconnect,
            dismissLabel = stringResource(Res.string.action_cancel),
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
