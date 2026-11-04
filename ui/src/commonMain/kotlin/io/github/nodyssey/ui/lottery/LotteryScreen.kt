package io.github.nodyssey.ui.lottery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.core.LuckyDraw
import io.github.nodyssey.core.LuckyDrawParams
import io.github.nodyssey.data.lottery.LotteryEntry
import io.github.nodyssey.data.lottery.LotteryFailure
import io.github.nodyssey.data.lottery.LotteryFailureSource
import io.github.nodyssey.data.lottery.LotteryOutcome
import io.github.nodyssey.model.PostContent
import io.github.nodyssey.ui.common.SiteErrorSnackbar
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.lottery_active
import io.github.nodyssey.ui.resources.lottery_busy
import io.github.nodyssey.ui.resources.lottery_cancel
import io.github.nodyssey.ui.resources.lottery_card
import io.github.nodyssey.ui.resources.lottery_check
import io.github.nodyssey.ui.resources.lottery_checked
import io.github.nodyssey.ui.resources.lottery_create
import io.github.nodyssey.ui.resources.lottery_deduplicated
import io.github.nodyssey.ui.resources.lottery_duplicates_allowed
import io.github.nodyssey.ui.resources.lottery_empty
import io.github.nodyssey.ui.resources.lottery_empty_title
import io.github.nodyssey.ui.resources.lottery_enable
import io.github.nodyssey.ui.resources.lottery_failed
import io.github.nodyssey.ui.resources.lottery_failure_network
import io.github.nodyssey.ui.resources.lottery_failure_other
import io.github.nodyssey.ui.resources.lottery_failure_response
import io.github.nodyssey.ui.resources.lottery_failure_service
import io.github.nodyssey.ui.resources.lottery_failure_sign_in
import io.github.nodyssey.ui.resources.lottery_failure_verification
import io.github.nodyssey.ui.resources.lottery_filter_all
import io.github.nodyssey.ui.resources.lottery_filter_empty
import io.github.nodyssey.ui.resources.lottery_filter_empty_hint
import io.github.nodyssey.ui.resources.lottery_filter_not_entered
import io.github.nodyssey.ui.resources.lottery_filter_won
import io.github.nodyssey.ui.resources.lottery_finished
import io.github.nodyssey.ui.resources.lottery_finished_empty
import io.github.nodyssey.ui.resources.lottery_floors
import io.github.nodyssey.ui.resources.lottery_help
import io.github.nodyssey.ui.resources.lottery_hint
import io.github.nodyssey.ui.resources.lottery_limit
import io.github.nodyssey.ui.resources.lottery_lost
import io.github.nodyssey.ui.resources.lottery_mark_all_read
import io.github.nodyssey.ui.resources.lottery_mark_read
import io.github.nodyssey.ui.resources.lottery_menu
import io.github.nodyssey.ui.resources.lottery_not_entered
import io.github.nodyssey.ui.resources.lottery_not_entered_detail
import io.github.nodyssey.ui.resources.lottery_official
import io.github.nodyssey.ui.resources.lottery_ok
import io.github.nodyssey.ui.resources.lottery_owner
import io.github.nodyssey.ui.resources.lottery_pending
import io.github.nodyssey.ui.resources.lottery_prizes
import io.github.nodyssey.ui.resources.lottery_refresh
import io.github.nodyssey.ui.resources.lottery_reminding
import io.github.nodyssey.ui.resources.lottery_remove
import io.github.nodyssey.ui.resources.lottery_sign_in
import io.github.nodyssey.ui.resources.lottery_source_beacon
import io.github.nodyssey.ui.resources.lottery_source_forum
import io.github.nodyssey.ui.resources.lottery_summary
import io.github.nodyssey.ui.resources.lottery_time_label
import io.github.nodyssey.ui.resources.lottery_title
import io.github.nodyssey.ui.resources.lottery_unread_results
import io.github.nodyssey.ui.resources.lottery_verification_required
import io.github.nodyssey.ui.resources.lottery_verify
import io.github.nodyssey.ui.resources.lottery_waiting
import io.github.nodyssey.ui.resources.lottery_won
import io.github.nodyssey.ui.settings.rememberNotificationPermissionRequest
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.PlazaSpinner
import io.github.plaza.designsys.component.TonalTag
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.stringResource

@Composable
fun LotteryPostDraws(
    viewModel: LotteryViewModel,
    body: PostContent,
    postId: Long,
    title: String,
    signedIn: Boolean,
    onSignIn: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    onVerify: (String) -> Unit,
) {
    val draws = remember(body, postId) { viewModel.draws(body, postId) }
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val requestPermission = rememberNotificationPermissionRequest()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(horizontal = LayerPageGutter)) {
        draws.forEach { draw ->
            val entry = entries.firstOrNull { it.key == draw.key }
            LotteryCard(
                draw = draw,
                entry = entry,
                now = now,
                busy = draw.key in pending,
                onAction = {
                    if (entry == null && !signedIn) {
                        onSignIn()
                    } else {
                        if (entry == null && now < draw.drawAtMillis) requestPermission()
                        viewModel.act(draw, title)
                    }
                },
                onOfficial = {
                    entry?.let { viewModel.markResultsRead(listOf(it)) }
                    onOpenBrowser(LuckyDraw.link(draw))
                },
                onVerify = { onVerify(entry?.verificationUrl ?: LuckyDraw.link(draw)) },
                onSignIn = onSignIn,
                onMarkRead = { entry?.let { viewModel.markResultsRead(listOf(it)) } },
            )
        }
        if (draws.isNotEmpty()) LotteryFeedback(viewModel, onSignIn, onVerify)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LotteryRoute(
    viewModel: LotteryViewModel,
    onBack: () -> Unit,
    onOpenThread: (Long) -> Unit,
    onOpenBrowser: (String) -> Unit,
    onSignIn: () -> Unit,
    onVerify: (String) -> Unit,
    onCreate: () -> Unit,
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    var finished by rememberSaveable { mutableStateOf(false) }
    var resultFilter by rememberSaveable { mutableStateOf<LotteryOutcome?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var helpOpen by rememberSaveable { mutableStateOf(false) }
    val appBarState = rememberOneHandAppBarState()
    val activeCount = entries.count { it.outcome == null }
    val unreadCount = entries.count { it.outcome != null && !it.resultRead }
    val visible = if (finished) {
        lotteryResultsInDisplayOrder(entries, resultFilter)
    } else {
        entries.filter { it.outcome == null }.sortedBy { it.draw.drawAtMillis }
    }
    Scaffold(
        modifier = Modifier.nestedScroll(appBarState.nestedScrollConnection),
        topBar = {
            OneHandTopAppBar(
                title = stringResource(Res.string.lottery_title),
                state = appBarState,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.action_back))
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::markAllResultsRead, enabled = unreadCount > 0 && "readAll" !in pending) {
                        Text(stringResource(Res.string.lottery_mark_all_read))
                    }
                    IconButton(onClick = viewModel::refresh, enabled = pending.isEmpty()) {
                        if ("refresh" in pending) {
                            PlazaSpinner(size = 22.dp)
                        } else {
                            Icon(Icons.Default.Refresh, stringResource(Res.string.lottery_refresh))
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, stringResource(Res.string.lottery_menu))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.lottery_create)) },
                                leadingIcon = { Icon(Icons.Default.Add, null) },
                                onClick = {
                                    menuOpen = false
                                    onCreate()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.lottery_help)) },
                                leadingIcon = { Icon(Icons.Default.Info, null) },
                                onClick = {
                                    menuOpen = false
                                    helpOpen = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().readableWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = LayerPageGutter, vertical = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(PlazaIcons.Alarm, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(
                    stringResource(Res.string.lottery_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PrimaryTabRow(selectedTabIndex = if (finished) 1 else 0, containerColor = MaterialTheme.colorScheme.surface) {
                Tab(
                    selected = !finished,
                    onClick = { finished = false },
                    text = { LotteryTabLabel(stringResource(Res.string.lottery_active), activeCount) },
                )
                Tab(
                    selected = finished,
                    onClick = { finished = true },
                    text = { LotteryTabLabel(stringResource(Res.string.lottery_finished), unreadCount) },
                )
            }
            if (finished) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = LayerPageGutter),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    (listOf(null) + LotteryOutcome.entries).forEach { outcome ->
                        FilterChip(
                            selected = resultFilter == outcome,
                            onClick = { resultFilter = outcome },
                            label = {
                                Text(
                                    stringResource(
                                        when (outcome) {
                                            null -> Res.string.lottery_filter_all
                                            LotteryOutcome.WON -> Res.string.lottery_filter_won
                                            LotteryOutcome.LOST -> Res.string.lottery_lost
                                            LotteryOutcome.NOT_ENTERED -> Res.string.lottery_filter_not_entered
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
            }
            LotteryFeedback(viewModel, onSignIn, onVerify)
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                contentPadding = PaddingValues(horizontal = LayerPageGutter, vertical = Spacing.md),
            ) {
                if (visible.isEmpty()) {
                    item {
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(Spacing.xl),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                            ) {
                                Icon(
                                    if (finished) Icons.Default.CheckCircle else PlazaIcons.Alarm,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp),
                                )
                                Text(
                                    stringResource(
                                        when {
                                            finished && resultFilter != null -> Res.string.lottery_filter_empty
                                            finished -> Res.string.lottery_finished_empty
                                            else -> Res.string.lottery_empty_title
                                        },
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    stringResource(if (finished && resultFilter != null) Res.string.lottery_filter_empty_hint else Res.string.lottery_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                items(visible, key = { it.key }) { entry ->
                    LotteryCard(
                        draw = entry.draw,
                        entry = entry,
                        now = now,
                        busy = entry.key in pending || "refresh" in pending,
                        onAction = { viewModel.act(entry.draw, entry.title) },
                        onOfficial = {
                            viewModel.markResultsRead(listOf(entry))
                            onOpenBrowser(LuckyDraw.link(entry.draw))
                        },
                        onVerify = { onVerify(entry.verificationUrl ?: LuckyDraw.link(entry.draw)) },
                        onSignIn = onSignIn,
                        title = entry.title,
                        onOpenThread = {
                            viewModel.markResultsRead(listOf(entry))
                            onOpenThread(entry.draw.postId)
                        },
                        onRemove = { viewModel.remove(entry) },
                        onMarkRead = { viewModel.markResultsRead(listOf(entry)) },
                    )
                }
            }
        }
    }
    if (helpOpen) {
        AlertDialog(
            onDismissRequest = { helpOpen = false },
            icon = { Icon(PlazaIcons.Alarm, null) },
            title = { Text(stringResource(Res.string.lottery_help)) },
            text = { Text(stringResource(Res.string.lottery_hint)) },
            confirmButton = { TextButton(onClick = { helpOpen = false }) { Text(stringResource(Res.string.lottery_ok)) } },
        )
    }
}

@Composable
private fun LotteryTabLabel(label: String, count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        if (count > 0) Badge { Text(count.toString()) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LotteryCard(
    draw: LuckyDrawParams,
    entry: LotteryEntry?,
    now: Long,
    busy: Boolean,
    onAction: () -> Unit,
    onOfficial: () -> Unit,
    onVerify: () -> Unit,
    onSignIn: () -> Unit = {},
    title: String? = null,
    onOpenThread: () -> Unit = {},
    onRemove: (() -> Unit)? = null,
    onMarkRead: (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = colors.surface),
    ) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                BadgedBox(
                    badge = {
                        if (entry?.outcome != null && !entry.resultRead) {
                            val description = stringResource(Res.string.lottery_unread_results)
                            Badge(Modifier.semantics { contentDescription = description })
                        }
                    },
                ) {
                    Surface(shape = MaterialTheme.shapes.medium, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                        Icon(PlazaIcons.Alarm, null, modifier = Modifier.padding(Spacing.sm).size(24.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(stringResource(Res.string.lottery_card), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(Res.string.lottery_prizes, draw.prizeCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                LotteryStatus(entry, draw, now, busy)
                if (onRemove != null) {
                    Box {
                        IconButton(onClick = { menuOpen = true }, enabled = !busy) {
                            Icon(Icons.Default.MoreVert, stringResource(Res.string.lottery_menu))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.lottery_remove)) },
                                onClick = {
                                    menuOpen = false
                                    onRemove()
                                },
                            )
                        }
                    }
                }
            }
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenThread),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(Res.string.lottery_time_label), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Text(LuckyDraw.formatDrawTime(draw.drawAtMillis), style = MaterialTheme.typography.titleMedium)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (entry != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Text(stringResource(Res.string.lottery_owner, entry.ownerName), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
                Text(
                    stringResource(if (draw.dedupeFloors) Res.string.lottery_deduplicated else Res.string.lottery_duplicates_allowed),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            if (entry?.outcome == LotteryOutcome.WON) {
                Surface(shape = MaterialTheme.shapes.medium, color = colors.tertiaryContainer, contentColor = colors.onTertiaryContainer) {
                    Text(
                        stringResource(Res.string.lottery_floors, entry.winningFloors.joinToString(", ")),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                    )
                }
            }
            if (entry?.outcome == LotteryOutcome.NOT_ENTERED) {
                Text(stringResource(Res.string.lottery_not_entered_detail), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (entry?.failure != null) {
                Text(lotteryFailureText(entry), style = MaterialTheme.typography.bodySmall, color = colors.error)
            }
            entry?.checkedAtMillis?.let {
                Text(stringResource(Res.string.lottery_checked, LuckyDraw.formatDrawTime(it)), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            HorizontalDivider(color = colors.outlineVariant)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (entry?.outcome == null) {
                    val needsVerification = entry?.failure == LotteryFailure.VERIFICATION
                    val needsSignIn = entry?.failure == LotteryFailure.SIGN_IN
                    FilledTonalButton(
                        onClick = when {
                            needsVerification -> onVerify
                            needsSignIn -> onSignIn
                            else -> onAction
                        },
                        enabled = !busy,
                    ) {
                        if (busy) {
                            PlazaSpinner(size = 16.dp, strokeWidth = 2.dp)
                            Spacer(Modifier.size(Spacing.sm))
                        }
                        Text(
                            stringResource(
                                when {
                                    busy -> Res.string.lottery_busy
                                    needsVerification -> Res.string.lottery_verify
                                    needsSignIn -> Res.string.lottery_sign_in
                                    now >= draw.drawAtMillis -> Res.string.lottery_check
                                    entry != null -> Res.string.lottery_cancel
                                    else -> Res.string.lottery_enable
                                },
                            ),
                        )
                    }
                }
                TextButton(onClick = onOfficial) { Text(stringResource(Res.string.lottery_official)) }
                if (entry?.outcome != null && !entry.resultRead && onMarkRead != null) {
                    TextButton(onClick = onMarkRead, enabled = !busy) { Text(stringResource(Res.string.lottery_mark_read)) }
                }
            }
        }
    }
}

@Composable
private fun LotteryStatus(entry: LotteryEntry?, draw: LuckyDrawParams, now: Long, busy: Boolean) {
    val colors = MaterialTheme.colorScheme
    val status = when {
        busy -> Res.string.lottery_busy
        entry?.outcome == LotteryOutcome.WON -> Res.string.lottery_won
        entry?.outcome == LotteryOutcome.LOST -> Res.string.lottery_lost
        entry?.outcome == LotteryOutcome.NOT_ENTERED -> Res.string.lottery_not_entered
        entry?.failure == LotteryFailure.VERIFICATION -> Res.string.lottery_verification_required
        entry?.failure != null -> Res.string.lottery_failed
        now >= draw.drawAtMillis -> Res.string.lottery_pending
        entry != null -> Res.string.lottery_reminding
        else -> Res.string.lottery_waiting
    }
    val failed = entry?.failure != null && !busy
    val won = entry?.outcome == LotteryOutcome.WON
    TonalTag(
        text = stringResource(status),
        containerColor = when {
            failed -> colors.errorContainer
            won -> colors.tertiaryContainer
            else -> colors.secondaryContainer
        },
        contentColor = when {
            failed -> colors.onErrorContainer
            won -> colors.onTertiaryContainer
            else -> colors.onSecondaryContainer
        },
    )
}

@Composable
private fun lotteryFailureText(entry: LotteryEntry): String {
    val source = stringResource(if (entry.failureSource == LotteryFailureSource.BEACON) Res.string.lottery_source_beacon else Res.string.lottery_source_forum)
    return when (entry.failure) {
        LotteryFailure.NETWORK -> stringResource(Res.string.lottery_failure_network, source)
        LotteryFailure.VERIFICATION -> stringResource(Res.string.lottery_failure_verification)
        LotteryFailure.SIGN_IN -> stringResource(Res.string.lottery_failure_sign_in)
        LotteryFailure.RESPONSE -> stringResource(Res.string.lottery_failure_response, source)
        LotteryFailure.SERVICE -> stringResource(Res.string.lottery_failure_service, source, entry.failureHttpStatus ?: 0)
        else -> stringResource(Res.string.lottery_failure_other)
    }
}

@Composable
private fun LotteryFeedback(viewModel: LotteryViewModel, onSignIn: () -> Unit, onVerify: (String) -> Unit) {
    val error by viewModel.error.collectAsStateWithLifecycle()
    val limit by viewModel.limitReached.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    SiteErrorSnackbar(error, snackbar, viewModel::clearError, onSignIn = onSignIn, onVerify = onVerify)
    SnackbarHost(snackbar)
    if (limit) {
        AlertDialog(
            onDismissRequest = viewModel::clearLimit,
            text = { Text(stringResource(Res.string.lottery_limit)) },
            confirmButton = { TextButton(onClick = viewModel::clearLimit) { Text(stringResource(Res.string.lottery_ok)) } },
        )
    }
}
