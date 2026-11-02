package io.github.nodyssey.ui.lottery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.core.LuckyDraw
import io.github.nodyssey.core.LuckyDrawParams
import io.github.nodyssey.data.lottery.LotteryEntry
import io.github.nodyssey.data.lottery.LotteryFailure
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
import io.github.nodyssey.ui.resources.lottery_create
import io.github.nodyssey.ui.resources.lottery_empty
import io.github.nodyssey.ui.resources.lottery_enable
import io.github.nodyssey.ui.resources.lottery_finished
import io.github.nodyssey.ui.resources.lottery_floors
import io.github.nodyssey.ui.resources.lottery_hint
import io.github.nodyssey.ui.resources.lottery_limit
import io.github.nodyssey.ui.resources.lottery_lost
import io.github.nodyssey.ui.resources.lottery_not_entered
import io.github.nodyssey.ui.resources.lottery_official
import io.github.nodyssey.ui.resources.lottery_ok
import io.github.nodyssey.ui.resources.lottery_owner
import io.github.nodyssey.ui.resources.lottery_pending
import io.github.nodyssey.ui.resources.lottery_query_failed
import io.github.nodyssey.ui.resources.lottery_refresh
import io.github.nodyssey.ui.resources.lottery_remove
import io.github.nodyssey.ui.resources.lottery_time
import io.github.nodyssey.ui.resources.lottery_title
import io.github.nodyssey.ui.resources.lottery_verify
import io.github.nodyssey.ui.resources.lottery_waiting
import io.github.nodyssey.ui.resources.lottery_won
import io.github.nodyssey.ui.settings.rememberNotificationPermissionRequest
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.OneHandTopAppBar
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
                onOfficial = { onOpenBrowser(LuckyDraw.link(draw)) },
                onVerify = { onVerify(entry?.verificationUrl ?: LuckyDraw.link(draw)) },
            )
        }
        if (draws.isNotEmpty()) LotteryFeedback(viewModel, onSignIn, onVerify)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val appBarState = rememberOneHandAppBarState()
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
                    TextButton(onClick = onCreate) { Text(stringResource(Res.string.lottery_create)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().readableWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            contentPadding = PaddingValues(horizontal = LayerPageGutter, vertical = Spacing.sm),
        ) {
            item {
                Text(stringResource(Res.string.lottery_hint), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilterChip(selected = !finished, onClick = { finished = false }, label = { Text(stringResource(Res.string.lottery_active)) })
                    FilterChip(selected = finished, onClick = { finished = true }, label = { Text(stringResource(Res.string.lottery_finished)) })
                    TextButton(onClick = viewModel::refresh, enabled = "refresh" !in pending) {
                        Text(stringResource(Res.string.lottery_refresh))
                    }
                }
                LotteryFeedback(viewModel, onSignIn, onVerify)
            }
            val visible = entries.filter { (it.outcome != null) == finished }.sortedBy { it.draw.drawAtMillis }
            if (visible.isEmpty()) item { Text(stringResource(Res.string.lottery_empty)) }
            items(visible, key = { it.key }) { entry ->
                Column {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            entry.title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f).clickable { onOpenThread(entry.draw.postId) }.padding(vertical = Spacing.sm),
                        )
                        IconButton(onClick = { viewModel.remove(entry) }, enabled = entry.key !in pending) {
                            Icon(Icons.Default.Close, stringResource(Res.string.lottery_remove))
                        }
                    }
                    LotteryCard(
                        draw = entry.draw,
                        entry = entry,
                        now = now,
                        busy = entry.key in pending,
                        onAction = { viewModel.act(entry.draw, entry.title) },
                        onOfficial = { onOpenBrowser(LuckyDraw.link(entry.draw)) },
                        onVerify = { onVerify(entry.verificationUrl ?: LuckyDraw.link(entry.draw)) },
                    )
                }
            }
        }
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
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(Res.string.lottery_card, draw.prizeCount), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                val status = when (entry?.outcome) {
                    LotteryOutcome.WON -> Res.string.lottery_won
                    LotteryOutcome.LOST -> Res.string.lottery_lost
                    LotteryOutcome.NOT_ENTERED -> Res.string.lottery_not_entered
                    null -> if (now < draw.drawAtMillis) Res.string.lottery_waiting else Res.string.lottery_pending
                }
                Text(stringResource(status), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
            Text(stringResource(Res.string.lottery_time, LuckyDraw.formatDrawTime(draw.drawAtMillis)), style = MaterialTheme.typography.bodySmall)
            if (entry != null) Text(stringResource(Res.string.lottery_owner, entry.ownerName), style = MaterialTheme.typography.bodySmall)
            if (entry?.outcome == LotteryOutcome.WON) {
                Text(stringResource(Res.string.lottery_floors, entry.winningFloors.joinToString(", ")), color = MaterialTheme.colorScheme.primary)
            }
            if (entry?.failure != null) Text(stringResource(Res.string.lottery_query_failed), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (entry?.outcome == null) {
                    Button(onClick = onAction, enabled = !busy) {
                        Text(
                            stringResource(
                                when {
                                    busy -> Res.string.lottery_busy
                                    now >= draw.drawAtMillis -> Res.string.lottery_check
                                    entry != null -> Res.string.lottery_cancel
                                    else -> Res.string.lottery_enable
                                },
                            ),
                        )
                    }
                }
                if (entry?.failure == LotteryFailure.VERIFICATION) {
                    TextButton(onClick = onVerify) { Text(stringResource(Res.string.lottery_verify)) }
                }
                TextButton(onClick = onOfficial) { Text(stringResource(Res.string.lottery_official)) }
            }
        }
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
