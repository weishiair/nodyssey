package io.github.nodyssey.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.data.OfflineUsage
import io.github.nodyssey.data.attendance.AutoAttendanceFailure
import io.github.nodyssey.data.attendance.AutoAttendanceState
import io.github.nodyssey.data.session.AccountFailure
import io.github.nodyssey.data.session.AccountsState
import io.github.nodyssey.data.session.SavedAccount
import io.github.nodyssey.ui.common.MediumButton
import io.github.nodyssey.ui.common.MediumButtonStyle
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.accounts_add
import io.github.nodyssey.ui.resources.accounts_cancel_add
import io.github.nodyssey.ui.resources.accounts_current
import io.github.nodyssey.ui.resources.accounts_empty
import io.github.nodyssey.ui.resources.accounts_expired
import io.github.nodyssey.ui.resources.accounts_failure_expired
import io.github.nodyssey.ui.resources.accounts_failure_network
import io.github.nodyssey.ui.resources.accounts_failure_storage
import io.github.nodyssey.ui.resources.accounts_failure_unknown
import io.github.nodyssey.ui.resources.accounts_failure_verify
import io.github.nodyssey.ui.resources.accounts_failure_webview
import io.github.nodyssey.ui.resources.accounts_hint
import io.github.nodyssey.ui.resources.accounts_offline_warning
import io.github.nodyssey.ui.resources.accounts_remove
import io.github.nodyssey.ui.resources.accounts_remove_body
import io.github.nodyssey.ui.resources.accounts_remove_offline_warning
import io.github.nodyssey.ui.resources.accounts_retry_save
import io.github.nodyssey.ui.resources.accounts_switch
import io.github.nodyssey.ui.resources.accounts_title
import io.github.nodyssey.ui.resources.accounts_uid
import io.github.nodyssey.ui.resources.action_cancel
import io.github.nodyssey.ui.resources.auto_attendance_done
import io.github.nodyssey.ui.resources.auto_attendance_failed
import io.github.nodyssey.ui.resources.auto_attendance_running
import io.github.nodyssey.ui.resources.auto_attendance_sign_in
import io.github.nodyssey.ui.resources.auto_attendance_title
import io.github.nodyssey.ui.resources.auto_attendance_verify
import io.github.nodyssey.ui.settings.SettingsItemGap
import io.github.nodyssey.ui.settings.SettingsPagePadding
import io.github.plaza.designsys.component.AdaptiveAlertDialog
import io.github.plaza.designsys.component.AdaptiveTopBar
import io.github.plaza.designsys.component.GroupCard
import io.github.plaza.designsys.component.GroupedListItem
import io.github.plaza.designsys.component.GroupedListItemSwitch
import io.github.plaza.designsys.component.GroupedRow
import io.github.plaza.designsys.component.PlazaIcon
import io.github.plaza.designsys.component.PlazaSnackbarHost
import io.github.plaza.designsys.component.PlazaSpinner
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.component.groupedListItemColors
import io.github.plaza.designsys.component.rememberPlatformHaptics
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.stringResource

@Composable
fun AccountsRoute(viewModel: AccountsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val usage by viewModel.usage.collectAsStateWithLifecycle(OfflineUsage())
    val attendanceStates by viewModel.attendanceStates.collectAsStateWithLifecycle()
    // NavDisplay also composes this entry behind sign-in for predictive back. Only the
    // owning back stack may decide that an add was abandoned (see MainNavigation).
    LaunchedEffect(Unit) {
        if (state.failure == null) viewModel.refresh()
    }
    AccountsScreen(state, usage.posts > 0, onBack, viewModel::add, viewModel::switch, viewModel::remove, viewModel::cancelAdd, viewModel::dismissFailure, viewModel::setAttendanceEnabled, attendanceStates, viewModel::refresh)
}

private data class AccountAction(val account: SavedAccount? = null, val remove: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    state: AccountsState,
    hasOfflineDownloads: Boolean,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onSwitch: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onCancelAdd: () -> Unit,
    onFailureShown: () -> Unit,
    onAttendanceEnabled: (Long, Boolean) -> Unit = { _, _ -> },
    attendanceStates: Map<Long, AutoAttendanceState> = emptyMap(),
    onRetrySave: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    var confirmation by remember { mutableStateOf<AccountAction?>(null) }
    val pressHaptics = rememberPlatformHaptics()
    var switchingUid by remember { mutableStateOf<Long?>(null) }
    val actionsEnabled = !state.busy && switchingUid == null
    LaunchedEffect(state.busy, state.activeUid, state.failure) {
        if (!state.busy && (state.activeUid == switchingUid || state.failure != null)) switchingUid = null
    }
    val failure = state.failure?.let {
        stringResource(
            when (it) {
                AccountFailure.EXPIRED -> Res.string.accounts_failure_expired
                AccountFailure.VERIFY -> Res.string.accounts_failure_verify
                AccountFailure.NETWORK -> Res.string.accounts_failure_network
                AccountFailure.STORAGE -> Res.string.accounts_failure_storage
                AccountFailure.UNSUPPORTED -> Res.string.accounts_failure_webview
                AccountFailure.UNKNOWN -> Res.string.accounts_failure_unknown
            },
        )
    }
    LaunchedEffect(failure) {
        if (failure != null) {
            snackbar.showSnackbar(failure)
            onFailureShown()
        }
    }
    val perform: (AccountAction) -> Unit = { action ->
        val account = action.account
        when {
            action.remove && account != null -> onRemove(account.uid)

            account == null || account.needsSignIn -> onAdd()

            else -> {
                switchingUid = account.uid
                pressHaptics.toggle()
                onSwitch(account.uid)
            }
        }
    }
    val request: (AccountAction) -> Unit = { action ->
        if (!state.busy && switchingUid == null) {
            if (action.remove || hasOfflineDownloads) confirmation = action else perform(action)
        }
    }
    Scaffold(
        containerColor = LocalPlazaLayers.current.page,
        topBar = {
            AdaptiveTopBar(
                title = stringResource(Res.string.accounts_title),
                onBack = onBack,
            )
        },
        snackbarHost = { PlazaSnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().readableWidth(),
            contentPadding = SettingsPagePadding,
            verticalArrangement = Arrangement.spacedBy(SettingsItemGap),
        ) {
            item {
                Text(
                    stringResource(Res.string.accounts_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                )
            }
            if (state.accounts.isEmpty()) item { Text(stringResource(Res.string.accounts_empty)) }
            if (state.adding) {
                item {
                    TextButton(onClick = onRetrySave, enabled = actionsEnabled) { Text(stringResource(Res.string.accounts_retry_save)) }
                }
            }
            items(state.accounts, key = { it.uid }) { account ->
                val current = account.uid == state.activeUid
                val switching = switchingUid == account.uid
                val attendance = attendanceStates[account.uid]
                val status = if (account.attendance.enabled && attendance != null) {
                    when {
                        attendance.running -> Res.string.auto_attendance_running
                        attendance.completed -> Res.string.auto_attendance_done
                        attendance.failure == AutoAttendanceFailure.SIGN_IN -> Res.string.auto_attendance_sign_in
                        attendance.failure == AutoAttendanceFailure.VERIFICATION -> Res.string.auto_attendance_verify
                        attendance.failure != null -> Res.string.auto_attendance_failed
                        else -> null
                    }
                } else {
                    null
                }
                GroupCard {
                    GroupedListItem(
                        first = true,
                        last = false,
                        selected = current,
                        enabled = actionsEnabled,
                        onClick = if (current && !account.needsSignIn) null else ({ request(AccountAction(account)) }),
                        colors = groupedListItemColors(
                            containerColor = if (switching) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent,
                        ),
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                Text(account.name, Modifier.weight(1f))
                                if (current) Text(stringResource(Res.string.accounts_current), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        },
                        supportingContent = {
                            Column {
                                Text(stringResource(Res.string.accounts_uid, account.uid))
                                if (account.needsSignIn) Text(stringResource(Res.string.accounts_expired), color = MaterialTheme.colorScheme.error)
                            }
                        },
                        leadingContent = { UserAvatar(account.avatarUrl, account.name, size = 40.dp) },
                        trailingContent = {
                            if (switching) {
                                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                    PlazaSpinner(size = 18.dp, strokeWidth = 2.dp)
                                }
                            } else {
                                IconButton(onClick = { request(AccountAction(account, remove = true)) }, enabled = actionsEnabled) {
                                    PlazaIcon(Icons.Default.Delete, stringResource(Res.string.accounts_remove), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                    )
                    GroupedRow(
                        title = stringResource(Res.string.auto_attendance_title),
                        subtitle = status?.let { stringResource(it) },
                        last = true,
                        enabled = actionsEnabled,
                        checked = account.attendance.enabled,
                        onCheckedChange = { onAttendanceEnabled(account.uid, it) },
                        trailing = { GroupedListItemSwitch(account.attendance.enabled, enabled = actionsEnabled) },
                    )
                }
            }
            item {
                MediumButton(onClick = { request(AccountAction()) }, style = MediumButtonStyle.Tonal, enabled = actionsEnabled, icon = Icons.Default.Add, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.accounts_add))
                }
            }
            if (state.adding) item { TextButton(onClick = onCancelAdd, enabled = actionsEnabled) { Text(stringResource(Res.string.accounts_cancel_add)) } }
        }
    }
    confirmation?.let { action ->
        val removesCurrent = action.remove && action.account?.uid == state.activeUid
        val title = stringResource(if (action.remove) Res.string.accounts_remove else Res.string.accounts_switch)
        val removal = if (action.remove) stringResource(Res.string.accounts_remove_body, action.account?.name.orEmpty()) else null
        val warning = if (hasOfflineDownloads && (!action.remove || removesCurrent)) {
            stringResource(if (action.remove) Res.string.accounts_remove_offline_warning else Res.string.accounts_offline_warning)
        } else {
            null
        }
        AdaptiveAlertDialog(
            title = title,
            message = listOfNotNull(removal, warning).joinToString("\n\n").ifEmpty { null },
            confirmLabel = title,
            onConfirm = {
                confirmation = null
                if (!state.busy && switchingUid == null) perform(action)
            },
            onDismiss = { confirmation = null },
            dismissLabel = stringResource(Res.string.action_cancel),
        )
    }
}
