package io.github.nodyssey.ui.mail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.data.mail.MailAuthMode
import io.github.nodyssey.data.mail.MailError
import io.github.nodyssey.data.mail.MailMessage
import io.github.nodyssey.data.session.SessionRepository
import io.github.nodyssey.ui.common.PlazaSheet
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_cancel
import io.github.nodyssey.ui.resources.action_close
import io.github.nodyssey.ui.resources.action_retry
import io.github.nodyssey.ui.resources.action_search
import io.github.nodyssey.ui.resources.mail_all_mailboxes
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
import io.github.nodyssey.ui.resources.mail_select_visible
import io.github.nodyssey.ui.resources.mail_selected_count
import io.github.nodyssey.ui.resources.mail_title
import io.github.nodyssey.ui.resources.mail_use_key
import io.github.nodyssey.ui.resources.mail_verify_and_retry
import io.github.plaza.core.net.UserAgent
import io.github.plaza.designsys.component.AdaptiveAlertDialog
import io.github.plaza.designsys.component.AdaptivePullToRefreshBox
import io.github.plaza.designsys.component.AdaptiveTopBar
import io.github.plaza.designsys.component.GlassAction
import io.github.plaza.designsys.component.GroupedListItem
import io.github.plaza.designsys.component.IconBadge
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.PlazaBackHandler
import io.github.plaza.designsys.component.PlazaExtendedFab
import io.github.plaza.designsys.component.PlazaFieldDefaults
import io.github.plaza.designsys.component.PlazaIcon
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.PlazaSnackbarHost
import io.github.plaza.designsys.component.PlazaSpinner
import io.github.plaza.designsys.component.StatusAction
import io.github.plaza.designsys.component.StatusView
import io.github.plaza.designsys.component.TopBarAction
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.component.rememberPlatformHaptics
import io.github.plaza.designsys.theme.LocalLiquidGlassBackdrop
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.StatusShapes
import io.github.plaza.designsys.theme.liquidGlassBackdropSource
import io.github.plaza.designsys.theme.readableWidth
import io.github.plaza.designsys.theme.rememberLiquidGlassBackdrop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun MailInboxRoute(
    viewModel: MailInboxViewModel,
    onBack: () -> Unit,
    onOpenMail: (MailMessage) -> Unit,
    onCompose: (Long, Long?) -> Unit,
    onOpenWebsite: (String) -> Unit,
    session: SessionRepository,
    userAgent: UserAgent,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.refreshSent() }
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
        onCompose = { onCompose(state.generation, state.selectedMailboxId) },
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
            tags = MailTagControls(
                load = viewModel::loadTags,
                create = viewModel::createTag,
                clearCreated = viewModel::clearCreatedTag,
                setSelected = viewModel::setSelectedTag,
            ),
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
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
    onCompose: (() -> Unit)? = null,
) {
    val appBarState = rememberOneHandAppBarState()
    val listState = rememberMailListState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val visibleMessages = state.visibleMessages
    if (state.nextOffset != null && state.connected && !state.loading && !state.loadingMore &&
        state.error == null && !state.selectionMode && !state.markingAllRead && !state.changingMessages
    ) {
        LaunchedEffect(state.generation, state.selectedMailboxId, state.filter, state.unreadOnly, state.nextOffset) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.key == LOAD_MORE_ITEM_KEY } }.first { it }
            onLoadMore()
        }
    }
    var searchRequested by rememberSaveable(state.generation) { mutableStateOf(false) }
    val searchVisible = state.connected && (searchRequested || state.filter.search.isNotBlank())
    val closeSearch = {
        searchRequested = false
        controls?.changeFilter?.invoke(state.filter.copy(search = ""))
        Unit
    }
    var creatingAlias by remember(state.generation) { mutableStateOf(false) }
    var showingTagSheet by remember(state.generation) { mutableStateOf(false) }
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
    if (showingTagSheet && controls != null) {
        MailTagSheet(
            state = state,
            controls = controls.tags,
            onFilter = { controls.changeFilter(state.filter.copy(tagId = it)) },
            onDismiss = { showingTagSheet = false },
        )
    }
    LaunchedEffect(state.selectionMode) {
        if (state.selectionMode && controls != null) {
            controls.loadFolders()
            controls.tags.load()
        }
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

    val oneHanded = !state.selectionMode && !searchVisible
    val mailBackdrop = rememberLiquidGlassBackdrop(LocalPlazaLayers.current.page)
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 40 } }
    val canCompose = state.connected && !state.selectionMode && onCompose != null && state.mailboxes.any { !it.sendDisabled }
    val selectedMailboxAddress = state.mailboxes.firstOrNull { it.id == state.selectedMailboxId }?.address
    val topBarSubtitle = if (state.connected && state.mailboxes.isNotEmpty()) {
        selectedMailboxAddress ?: stringResource(Res.string.mail_all_mailboxes)
    } else {
        null
    }

    Scaffold(
        modifier = modifier.then(if (oneHanded) Modifier.nestedScroll(appBarState.nestedScrollConnection) else Modifier),
        containerColor = LocalPlazaLayers.current.page,
        topBar = {
            when {
                state.selectionMode && controls != null -> {
                    MailSelectionTopBar(
                        count = state.selectedIds.size,
                        enabled = !state.markingAllRead && !state.changingMessages,
                        canSelectVisible = visibleMessages.isNotEmpty(),
                        onClose = controls.toggleSelection,
                        onSelectVisible = controls.selectVisible,
                    )
                }

                searchVisible && controls != null -> {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(
                                onClick = closeSearch,
                                enabled = !state.markingAllRead && !state.changingMessages,
                            ) {
                                PlazaIcon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(Res.string.action_close),
                                )
                            }
                        },
                        title = {
                            MailSearchTopBarField(
                                generation = state.generation,
                                query = state.filter.search,
                                enabled = !state.markingAllRead && !state.changingMessages,
                                onSearch = { controls.changeFilter(state.filter.copy(search = it)) },
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = LocalPlazaLayers.current.page),
                    )
                }

                else -> {
                    val nativeMenu = if (state.connected) {
                        mailInboxNativeMenu(
                            state = state,
                            controls = controls,
                            onMarkAllRead = onMarkAllRead,
                            onRefresh = onRefresh,
                            onOpenTags = {
                                controls?.tags?.load?.invoke()
                                showingTagSheet = true
                            },
                            onCreateAlias = { creatingAlias = true },
                            onAuthorize = onAuthorize,
                            onUseKey = {
                                keySwitchGeneration = state.generation
                                switchingToKey = true
                            },
                            onDisconnect = onDisconnect,
                        )
                    } else {
                        emptyList()
                    }
                    val nativeMenuTitle = if (state.connected) mailInboxMenuTitle(state) else null
                    Box {
                        AdaptiveTopBar(
                            title = stringResource(Res.string.mail_title),
                            subtitle = topBarSubtitle,
                            state = appBarState,
                            onBack = onBack,
                            actions = if (state.connected) {
                                listOfNotNull(
                                    controls?.takeIf { state.mailboxes.isNotEmpty() }?.let {
                                        TopBarAction(
                                            glass = GlassAction(
                                                label = stringResource(Res.string.action_search),
                                                onClick = {
                                                    searchRequested = true
                                                    scope.launch { listState.scrollToItem(0) }
                                                },
                                                symbol = "magnifyingglass",
                                                enabled = !state.markingAllRead && !state.changingMessages,
                                            ),
                                            icon = Icons.Default.Search,
                                        )
                                    },
                                    TopBarAction(
                                        glass = GlassAction(
                                            label = stringResource(Res.string.mail_options),
                                            onClick = { menu = MailInboxMenu.Actions },
                                            symbol = "ellipsis",
                                            menu = nativeMenu,
                                            menuTitle = nativeMenuTitle,
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
                }
            }
        },
        floatingActionButton = {
            if (canCompose) {
                CompositionLocalProvider(LocalLiquidGlassBackdrop provides mailBackdrop) {
                    PlazaExtendedFab(
                        text = stringResource(Res.string.mail_compose),
                        icon = Icons.Default.Edit,
                        nativeSymbol = "square.and.pencil",
                        expanded = fabExpanded,
                        onClick = {
                            if (!state.changingMessages && !state.connecting) onCompose()
                        },
                    )
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        snackbarHost = {
            CompositionLocalProvider(LocalLiquidGlassBackdrop provides mailBackdrop) {
                PlazaSnackbarHost(snackbarHostState)
            }
        },
    ) { padding ->
        val pressHaptics = rememberPlatformHaptics()
        val direction = LocalLayoutDirection.current
        val bottomInset = padding.calculateBottomPadding()
        Box(
            Modifier.fillMaxSize().padding(
                start = padding.calculateStartPadding(direction),
                top = padding.calculateTopPadding(),
                end = padding.calculateEndPadding(direction),
            ).consumeWindowInsets(padding),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .liquidGlassBackdropSource(mailBackdrop)
                    .readableWidth()
                    .align(Alignment.TopCenter),
            ) {
                if (state.connected && !state.initializing && !state.selectionMode) {
                    MailListToolbar(
                        state = state,
                        controls = controls?.takeIf { state.mailboxes.isNotEmpty() },
                        mailboxPicker = { MailboxPicker(state, onSelectMailbox) },
                        onOpenTags = {
                            controls?.tags?.load?.invoke()
                            menu = MailInboxMenu.Tags
                        },
                    )
                }
                AdaptivePullToRefreshBox(
                    isRefreshing = state.loading,
                    onRefresh = {
                        pressHaptics.refreshTriggered()
                        onRefresh()
                    },
                    enabled = state.connected && !state.loadingMore && !state.selectionMode,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    when {
                        state.initializing -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                PlazaSpinner()
                            }
                        }

                        !state.connected -> {
                            MailConnectOnboarding(
                                state = state,
                                apiKey = apiKey,
                                usingKey = usingKey,
                                onToggleKey = { usingKey = !usingKey },
                                onAuthorize = onAuthorize,
                                onAuthorizeWebsite = onAuthorizeWebsite,
                                onCancelAuthorization = onCancelAuthorization,
                                onConnect = onConnect,
                                onOpenWebsite = onOpenWebsite,
                                onVerifyChallenge = onVerifyChallenge,
                                onRefresh = onRefresh,
                                bottomInset = bottomInset,
                            )
                        }

                        visibleMessages.isEmpty() && state.nextOffset == null && !state.loading && state.error == null -> {
                            MailEmptyState(
                                state = state,
                                canCreateAlias = controls != null,
                                onCreateAlias = { creatingAlias = true },
                                onOpenWebsite = onOpenWebsite,
                            )
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = listState,
                                contentPadding = PaddingValues(
                                    start = LayerPageGutter,
                                    end = LayerPageGutter,
                                    top = Spacing.xs,
                                    bottom = bottomInset + if (canCompose || state.selectionMode) FLOATING_CLEARANCE else Spacing.xl,
                                ),
                            ) {
                                if (state.markingAllRead) {
                                    item(key = "marking-all-read") {
                                        LayerCard(
                                            modifier = Modifier.padding(bottom = Spacing.sm),
                                            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                PlazaSpinner(Modifier.size(18.dp))
                                                Text(
                                                    stringResource(Res.string.mail_marking_all_read),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                                state.error?.let { error ->
                                    item(key = "inbox-error") {
                                        MailErrorCard(
                                            error = error,
                                            state = state,
                                            onVerifyChallenge = onVerifyChallenge,
                                            onAuthorizeWebsite = onAuthorizeWebsite,
                                            onRefresh = onRefresh,
                                            modifier = Modifier.padding(bottom = Spacing.sm),
                                        )
                                    }
                                }
                                itemsIndexed(visibleMessages, key = { _, mail -> mail.id }) { index, mail ->
                                    MailMessageItem(
                                        mail = mail,
                                        preview = state.previews[mail.id],
                                        nowMillis = state.nowMillis,
                                        recipientAddress = mail.toAddress.takeIf(String::isNotBlank)
                                            ?: state.mailboxes.firstOrNull { it.id == mail.mailboxId }?.address.orEmpty(),
                                        onOpen = {
                                            if (state.selectionMode && controls != null) {
                                                controls.toggleSelected(mail.id)
                                            } else {
                                                onOpenMail(mail)
                                            }
                                        },
                                        onLongClick = if (controls != null && !state.filter.isSent) {
                                            {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                if (!state.selectionMode) controls.toggleSelection()
                                                controls.toggleSelected(mail.id)
                                            }
                                        } else {
                                            null
                                        },
                                        selected = (mail.id in state.selectedIds).takeIf { state.selectionMode },
                                        onToggleFlag = controls?.takeUnless { state.filter.isSent }?.let { { it.toggleFlag(mail) } },
                                        sent = state.filter.isSent,
                                        flagEnabled = !state.changingMessages,
                                        first = index == 0,
                                        last = index == visibleMessages.lastIndex,
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                                if (state.nextOffset != null) {
                                    item(key = LOAD_MORE_ITEM_KEY) {
                                        Box(
                                            Modifier.fillMaxWidth().padding(top = Spacing.md),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            FilledTonalButton(
                                                onClick = onLoadMore,
                                                enabled = !state.loading && !state.loadingMore,
                                            ) {
                                                if (state.loadingMore) {
                                                    PlazaSpinner(Modifier.size(16.dp))
                                                    Spacer(Modifier.width(Spacing.sm))
                                                }
                                                Text(stringResource(if (state.loadingMore) Res.string.mail_loading else Res.string.mail_load_more))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (state.selectionMode && controls != null) {
                CompositionLocalProvider(LocalLiquidGlassBackdrop provides mailBackdrop) {
                    MailSelectionBottomBar(
                        state = state,
                        controls = controls,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset).readableWidth(),
                    )
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
            destructive = true,
        )
    }
    if (switchingToKey && state.connected) {
        PlazaSheet(
            onDismiss = {
                if (!state.connecting) {
                    switchingToKey = false
                    apiKey.clearText()
                }
            },
            title = stringResource(if (state.authMode == MailAuthMode.API_KEY) Res.string.mail_replace_key else Res.string.mail_api_key_switch_title),
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
                    Text(stringResource(Res.string.mail_api_key_switch_hint), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(Res.string.mail_scopes_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedSecureTextField(
                        state = apiKey,
                        label = { Text(stringResource(Res.string.mail_api_key)) },
                        textObfuscationMode = TextObfuscationMode.Hidden,
                        enabled = !state.connecting,
                        modifier = Modifier.fillMaxWidth(),
                        shape = PlazaFieldDefaults.shape,
                        colors = PlazaFieldDefaults.colors(inCard = true),
                    )
                    state.error?.let { error ->
                        Text(
                            stringResource(error.messageRes()),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { onOpenWebsite("https://seek.li/manage/support?tab=developer") },
                            enabled = !state.connecting,
                        ) {
                            Text(stringResource(Res.string.mail_create_key))
                        }
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
                    }
                }
            }
        }
    }
}

@Composable
private fun MailSelectionTopBar(
    count: Int,
    enabled: Boolean,
    canSelectVisible: Boolean,
    onClose: () -> Unit,
    onSelectVisible: () -> Unit,
) {
    val selectVisibleLabel = stringResource(Res.string.mail_select_visible)
    AdaptiveTopBar(
        title = stringResource(Res.string.mail_selected_count, count),
        onBack = if (enabled) onClose else null,
        closeInsteadOfBack = true,
        containerColor = LocalPlazaLayers.current.page,
        actions = listOf(
            TopBarAction(
                glass = GlassAction(
                    label = selectVisibleLabel,
                    title = selectVisibleLabel,
                    onClick = onSelectVisible,
                    enabled = enabled && canSelectVisible,
                ),
                compose = {
                    TextButton(onClick = onSelectVisible, enabled = enabled && canSelectVisible) {
                        Text(selectVisibleLabel)
                    }
                },
            ),
        ),
    )
}

@Composable
private fun MailConnectOnboarding(
    state: MailInboxUiState,
    apiKey: TextFieldState,
    usingKey: Boolean,
    onToggleKey: () -> Unit,
    onAuthorize: () -> Unit,
    onAuthorizeWebsite: () -> Unit,
    onCancelAuthorization: () -> Unit,
    onConnect: () -> Unit,
    onOpenWebsite: (String) -> Unit,
    onVerifyChallenge: () -> Unit,
    onRefresh: () -> Unit,
    bottomInset: Dp,
) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberMailListState(),
        contentPadding = PaddingValues(
            start = LayerPageGutter,
            end = LayerPageGutter,
            top = Spacing.md,
            bottom = Spacing.md + bottomInset,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item(key = "connect-hero") {
            LayerCard(
                contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    IconBadge(
                        containerColor = colors.primaryContainer,
                        contentColor = colors.primary,
                        size = 56.dp,
                    ) {
                        PlazaIcon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(28.dp))
                    }
                    Text(
                        text = stringResource(Res.string.mail_connect_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(Res.string.mail_authorize_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Button(
                        onClick = onAuthorize,
                        enabled = !state.connecting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.connecting) {
                            PlazaSpinner(Modifier.size(18.dp))
                            Spacer(Modifier.width(Spacing.sm))
                        }
                        Text(stringResource(if (state.connecting) Res.string.mail_connecting else Res.string.mail_authorize))
                    }
                    if (state.connecting) {
                        TextButton(onClick = onCancelAuthorization) {
                            Text(stringResource(Res.string.action_cancel))
                        }
                    }
                }
            }
        }
        state.error?.let { error ->
            item(key = "connect-error") {
                MailErrorCard(
                    error = error,
                    state = state,
                    onVerifyChallenge = onVerifyChallenge,
                    onAuthorizeWebsite = onAuthorizeWebsite,
                    onRefresh = onRefresh,
                )
            }
        }
        item(key = "connect-alternatives") {
            Column {
                GroupedListItem(
                    first = true,
                    last = false,
                    enabled = !state.connecting,
                    onClick = onAuthorizeWebsite,
                    leadingContent = {
                        IconBadge(colors.secondaryContainer, colors.onSecondaryContainer, size = 36.dp) {
                            PlazaIcon(PlazaIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    },
                    headlineContent = { Text(stringResource(Res.string.mail_authorize_website)) },
                    trailingContent = {
                        PlazaIcon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                        )
                    },
                )
                GroupedListItem(
                    first = false,
                    last = true,
                    enabled = !state.connecting,
                    onClick = onToggleKey,
                    leadingContent = {
                        IconBadge(colors.secondaryContainer, colors.onSecondaryContainer, size = 36.dp) {
                            PlazaIcon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    },
                    headlineContent = { Text(stringResource(Res.string.mail_use_key)) },
                    trailingContent = {
                        PlazaIcon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                        )
                    },
                )
            }
        }
        if (usingKey) {
            item(key = "connect-api-key") {
                LayerCard(
                    contentPadding = PaddingValues(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Text(stringResource(Res.string.mail_connect_hint), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(Res.string.mail_scopes_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                    OutlinedSecureTextField(
                        state = apiKey,
                        label = { Text(stringResource(Res.string.mail_api_key)) },
                        textObfuscationMode = TextObfuscationMode.Hidden,
                        enabled = !state.connecting,
                        modifier = Modifier.fillMaxWidth(),
                        shape = PlazaFieldDefaults.shape,
                        colors = PlazaFieldDefaults.colors(inCard = true),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { onOpenWebsite("https://seek.li/manage/support?tab=developer") }) {
                            Text(stringResource(Res.string.mail_create_key))
                        }
                        Button(onClick = onConnect, enabled = !state.connecting && apiKey.text.isNotBlank()) {
                            Text(stringResource(if (state.connecting) Res.string.mail_connecting else Res.string.mail_connect))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MailEmptyState(
    state: MailInboxUiState,
    canCreateAlias: Boolean,
    onCreateAlias: () -> Unit,
    onOpenWebsite: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val titleRes = when {
        state.mailboxes.isEmpty() -> Res.string.mail_no_mailboxes
        !state.filter.isInbox || state.unreadOnly -> Res.string.mail_no_matches
        else -> Res.string.mail_empty
    }
    StatusView(
        icon = Icons.Default.Email,
        shape = StatusShapes.Empty,
        containerColor = colors.secondaryContainer,
        iconColor = colors.onSecondaryContainer,
        title = stringResource(titleRes),
        primaryAction = if (state.mailboxes.isEmpty() && canCreateAlias) {
            StatusAction(stringResource(Res.string.mail_create_alias), onClick = onCreateAlias)
        } else {
            null
        },
        secondaryAction = if (state.mailboxes.isEmpty()) {
            StatusAction(stringResource(Res.string.mail_open_website), onClick = { onOpenWebsite("https://seek.li/") })
        } else {
            null
        },
    )
}

@Composable
private fun MailErrorCard(
    error: MailError,
    state: MailInboxUiState,
    onVerifyChallenge: () -> Unit,
    onAuthorizeWebsite: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    LayerCard(
        modifier = modifier,
        containerColor = colors.errorContainer.copy(alpha = 0.55f),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(error.messageRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onErrorContainer,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (error == MailError.CHALLENGE && state.challengeVerificationAvailable) {
                FilledTonalButton(onClick = onVerifyChallenge, enabled = !state.connecting) {
                    Text(stringResource(Res.string.mail_verify_and_retry))
                }
            } else if (error == MailError.CHALLENGE || error == MailError.WEB_AUTHORIZATION || error == MailError.FORUM_SIGN_IN) {
                FilledTonalButton(onClick = onAuthorizeWebsite, enabled = !state.connecting) {
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

private val FLOATING_CLEARANCE = 96.dp
private const val LOAD_MORE_ITEM_KEY = "load-more"
