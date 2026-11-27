package io.github.nodyssey.ui.account

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.account_block_title
import io.github.nodyssey.ui.resources.account_blocked_count
import io.github.nodyssey.ui.resources.account_contact_summary
import io.github.nodyssey.ui.resources.account_contact_title
import io.github.nodyssey.ui.resources.account_preferences_summary
import io.github.nodyssey.ui.resources.account_preferences_title
import io.github.nodyssey.ui.resources.account_profile_summary
import io.github.nodyssey.ui.resources.account_profile_title
import io.github.nodyssey.ui.resources.account_security_summary
import io.github.nodyssey.ui.resources.account_security_title
import io.github.nodyssey.ui.resources.account_title
import io.github.nodyssey.ui.resources.action_sign_out
import io.github.nodyssey.ui.settings.SettingsPageLayout
import io.github.nodyssey.ui.settings.SettingsPageScreen
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.settings.SettingsItem
import io.github.plaza.designsys.settings.SettingsPage
import io.github.plaza.designsys.settings.SettingsSection
import io.github.plaza.designsys.theme.PlazaTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun AccountSettingsRoute(
    viewModel: AccountSettingsViewModel,
    onBack: () -> Unit,
    onOpenProfileFields: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenContact: () -> Unit,
    onOpenBlockList: () -> Unit,
    onOpenPreferences: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Signing out unmakes this screen: leaving it on the stack would show the account settings of an
    // account nobody is signed in to.
    LaunchedEffect(state.signedOut) {
        if (state.signedOut) onBack()
    }

    AccountSettingsScreen(
        state = state,
        onBack = onBack,
        onOpenProfileFields = onOpenProfileFields,
        onOpenSecurity = onOpenSecurity,
        onOpenContact = onOpenContact,
        onOpenBlockList = onOpenBlockList,
        onOpenPreferences = onOpenPreferences,
        onSignOut = viewModel::signOut,
        modifier = modifier,
    )
}

/**
 * 账号设置只展示真实的二级页入口。
 *
 * NodeSeek 站点按 hash 将资料、安全等页面内容分成小板块，但 App 中多个小板块会进入
 * 同一个原生页面。主列表因此按 destination 合并，页面内的编辑项不再伪装成多个路由。
 */
@Composable
fun AccountSettingsScreen(
    state: AccountSettingsUiState,
    onBack: () -> Unit,
    onOpenProfileFields: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenContact: () -> Unit,
    onOpenBlockList: () -> Unit,
    onOpenPreferences: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val destinations =
        listOf(
            SettingsItem.Link(
                key = "profile",
                title = stringResource(Res.string.account_profile_title),
                onClick = onOpenProfileFields,
                description = stringResource(Res.string.account_profile_summary),
                icon = Icons.Default.AccountCircle,
            ),
            SettingsItem.Link(
                key = "security",
                title = stringResource(Res.string.account_security_title),
                onClick = onOpenSecurity,
                description = stringResource(Res.string.account_security_summary),
                icon = Icons.Default.Lock,
            ),
            SettingsItem.Link(
                key = "contact",
                title = stringResource(Res.string.account_contact_title),
                onClick = onOpenContact,
                description = stringResource(Res.string.account_contact_summary),
                icon = Icons.Default.Email,
            ),
            SettingsItem.Link(
                key = "blocked-users",
                title = stringResource(Res.string.account_block_title),
                onClick = onOpenBlockList,
                value = state.blockedCount?.let { stringResource(Res.string.account_blocked_count, it) },
                icon = PlazaIcons.Block,
            ),
            SettingsItem.Link(
                key = "preferences",
                title = stringResource(Res.string.account_preferences_title),
                onClick = onOpenPreferences,
                description = stringResource(Res.string.account_preferences_summary),
                icon = Icons.Default.Settings,
            ),
        )
    val signOut =
        SettingsItem.Action(
            key = "sign-out",
            title = stringResource(Res.string.action_sign_out),
            onClick = onSignOut,
            destructive = true,
            icon = Icons.AutoMirrored.Filled.ExitToApp,
        )
    val page =
        SettingsPage(
            title = stringResource(Res.string.account_title),
            sections =
            listOf(
                SettingsSection(key = "account-destinations", items = destinations),
                SettingsSection(key = "account-actions", items = listOf(signOut)),
            ),
        )
    SettingsPageScreen(
        page = page,
        onBack = onBack,
        modifier = modifier,
        layout = SettingsPageLayout.Account,
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun AccountSettingsPreview() {
    PlazaTheme {
        AccountSettingsScreen(
            state =
            AccountSettingsUiState(blockedCount = 3),
            onBack = {},
            onOpenProfileFields = {},
            onOpenSecurity = {},
            onOpenContact = {},
            onOpenBlockList = {},
            onOpenPreferences = {},
            onSignOut = {},
        )
    }
}
