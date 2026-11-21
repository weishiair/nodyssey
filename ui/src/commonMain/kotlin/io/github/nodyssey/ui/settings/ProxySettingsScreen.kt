package io.github.nodyssey.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.data.proxy.ProxyConfigProblem
import io.github.nodyssey.data.proxy.ProxyConnectionFailure
import io.github.nodyssey.data.proxy.ProxyScope
import io.github.nodyssey.data.proxy.ProxyType
import io.github.nodyssey.ui.account.AccountMessageSnackbar
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.proxy_advanced_hint
import io.github.nodyssey.ui.resources.proxy_advanced_title
import io.github.nodyssey.ui.resources.proxy_credential_hint
import io.github.nodyssey.ui.resources.proxy_host_label
import io.github.nodyssey.ui.resources.proxy_host_placeholder
import io.github.nodyssey.ui.resources.proxy_host_required
import io.github.nodyssey.ui.resources.proxy_master_hint
import io.github.nodyssey.ui.resources.proxy_master_title
import io.github.nodyssey.ui.resources.proxy_password_label
import io.github.nodyssey.ui.resources.proxy_port_invalid
import io.github.nodyssey.ui.resources.proxy_port_label
import io.github.nodyssey.ui.resources.proxy_port_placeholder
import io.github.nodyssey.ui.resources.proxy_save
import io.github.nodyssey.ui.resources.proxy_scope_hint
import io.github.nodyssey.ui.resources.proxy_scope_title
import io.github.nodyssey.ui.resources.proxy_test
import io.github.nodyssey.ui.resources.proxy_test_failure_connection
import io.github.nodyssey.ui.resources.proxy_test_failure_dns
import io.github.nodyssey.ui.resources.proxy_test_failure_other
import io.github.nodyssey.ui.resources.proxy_test_failure_socks_auth
import io.github.nodyssey.ui.resources.proxy_test_failure_timeout
import io.github.nodyssey.ui.resources.proxy_test_failure_tls
import io.github.nodyssey.ui.resources.proxy_title
import io.github.nodyssey.ui.resources.proxy_type_http
import io.github.nodyssey.ui.resources.proxy_type_socks
import io.github.nodyssey.ui.resources.proxy_type_title
import io.github.nodyssey.ui.resources.proxy_username_label
import io.github.nodyssey.ui.resources.proxy_webview_hint
import io.github.plaza.designsys.component.ChoiceSegments
import io.github.plaza.designsys.component.GroupedListItemSwitch
import io.github.plaza.designsys.component.GroupedRow
import io.github.plaza.designsys.component.InlineBanner
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaIcon
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.PlazaSnackbarHost
import io.github.plaza.designsys.component.SectionNotes
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.settings.AdaptiveSettingsPage
import io.github.plaza.designsys.settings.SettingsChoicePresentation
import io.github.plaza.designsys.settings.SettingsItem
import io.github.plaza.designsys.settings.SettingsKeyboard
import io.github.plaza.designsys.settings.SettingsOption
import io.github.plaza.designsys.settings.SettingsPage
import io.github.plaza.designsys.settings.SettingsSection
import io.github.plaza.designsys.settings.SettingsSectionStyle
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProxySettingsRoute(
    viewModel: ProxySettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    AccountMessageSnackbar(
        message = state.message,
        snackbarHostState = snackbarHostState,
        onShown = viewModel::consumeMessage,
    )

    ProxySettingsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onEnabledChange = viewModel::setEnabled,
        onTypeChange = viewModel::setType,
        onForumOnlyChange = viewModel::setForumOnly,
        onHostChange = viewModel::updateHost,
        onPortChange = viewModel::updatePort,
        onUsernameChange = viewModel::updateUsername,
        onPasswordChange = viewModel::updatePassword,
        onSave = viewModel::save,
        onTest = viewModel::test,
        modifier = modifier,
    )
}

/**
 * 代理 — an HTTP or SOCKS5 proxy for the app's own requests.
 *
 * Everything below the master switch is dimmed and inert while it is off, the same
 * f4-style treatment [NotificationSettingsScreen] uses — there is nothing to configure until there is
 * something to turn on.
 */
@Composable
fun ProxySettingsScreen(
    state: ProxySettingsUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onTypeChange: (ProxyType) -> Unit,
    onForumOnlyChange: (Boolean) -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appBarState = rememberOneHandAppBarState()
    AdaptiveSettingsPage(
        page = proxySettingsPage(
            state = state,
            onEnabledChange = onEnabledChange,
            onTypeChange = onTypeChange,
            onForumOnlyChange = onForumOnlyChange,
            onHostChange = onHostChange,
            onPortChange = onPortChange,
            onUsernameChange = onUsernameChange,
            onPasswordChange = onPasswordChange,
            onSave = onSave,
            onTest = onTest,
        ),
        onBack = onBack,
        modifier = modifier,
    ) {
        Scaffold(
            modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
            snackbarHost = { PlazaSnackbarHost(snackbarHostState) },
            topBar = {
                OneHandTopAppBar(
                    title = stringResource(Res.string.proxy_title),
                    state = appBarState,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            PlazaIcon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.action_back),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .readableWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(SettingsPagePadding),
                verticalArrangement = Arrangement.spacedBy(SettingsItemGap),
            ) {
                SettingsGroup {
                    GroupedRow(
                        icon = PlazaIcons.VpnLock,
                        title = stringResource(Res.string.proxy_master_title),
                        subtitle = stringResource(Res.string.proxy_master_hint),
                        first = true,
                        last = true,
                        checked = state.enabled,
                        onCheckedChange = onEnabledChange,
                        trailing = { GroupedListItemSwitch(checked = state.enabled) },
                    )
                }

                // No alpha over the block: every control in it takes `enabled` and dims itself.
                Column(verticalArrangement = Arrangement.spacedBy(SettingsItemGap)) {
                    // The type and the four fields are one card: they are one proxy, and a type split
                    // from the address it applies to read as two settings.
                    LayerCard(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        val choices = listOf(
                            ProxyType.HTTP to stringResource(Res.string.proxy_type_http),
                            ProxyType.SOCKS to stringResource(Res.string.proxy_type_socks),
                        )
                        val typeTitle = stringResource(Res.string.proxy_type_title)
                        ChoiceSegments(
                            labels = choices.map { it.second },
                            selectedIndex = choices.indexOfFirst { it.first == state.type },
                            onSelect = { onTypeChange(choices[it].first) },
                            enabled = state.enabled,
                            // The card drops 代理类型's visible heading — the two segments name
                            // themselves — so the group carries it for a screen reader instead.
                            modifier = Modifier.semantics { contentDescription = typeTitle },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            ProxyField(
                                value = state.hostInput,
                                onValueChange = onHostChange,
                                labelRes = Res.string.proxy_host_label,
                                placeholderRes = Res.string.proxy_host_placeholder,
                                errorRes = Res.string.proxy_host_required,
                                isError = state.problem == ProxyConfigProblem.MISSING_HOST,
                                enabled = state.enabled,
                                keyboardType = KeyboardType.Uri,
                                modifier = Modifier.weight(2f),
                            )
                            ProxyField(
                                value = state.portInput,
                                onValueChange = onPortChange,
                                labelRes = Res.string.proxy_port_label,
                                placeholderRes = Res.string.proxy_port_placeholder,
                                errorRes = Res.string.proxy_port_invalid,
                                isError = state.problem == ProxyConfigProblem.INVALID_PORT,
                                enabled = state.enabled,
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        ProxyField(
                            value = state.usernameInput,
                            onValueChange = onUsernameChange,
                            labelRes = Res.string.proxy_username_label,
                            placeholderRes = Res.string.proxy_credential_hint,
                            enabled = state.enabled,
                            keyboardType = KeyboardType.Ascii,
                        )
                        ProxyField(
                            value = state.passwordInput,
                            onValueChange = onPasswordChange,
                            labelRes = Res.string.proxy_password_label,
                            placeholderRes = Res.string.proxy_credential_hint,
                            enabled = state.enabled,
                            keyboardType = KeyboardType.Password,
                            obscure = true,
                        )
                    }

                    SettingsGroup {
                        GroupedRow(
                            icon = PlazaIcons.Forum,
                            title = stringResource(Res.string.proxy_scope_title),
                            subtitle = stringResource(Res.string.proxy_scope_hint),
                            first = true,
                            last = true,
                            enabled = state.enabled,
                            checked = state.scope == ProxyScope.FORUM_ONLY,
                            onCheckedChange = onForumOnlyChange,
                            trailing = {
                                GroupedListItemSwitch(
                                    checked = state.scope == ProxyScope.FORUM_ONLY,
                                    enabled = state.enabled,
                                )
                            },
                        )
                    }

                    state.testFailure?.let { failure ->
                        InlineBanner(text = proxyTestFailureText(failure), icon = PlazaIcons.ErrorCircle, announce = true)
                    }
                    SettingsTestSaveButtons(
                        testLabel = stringResource(Res.string.proxy_test),
                        saveLabel = stringResource(Res.string.proxy_save),
                        testing = state.testing,
                        enabled = state.enabled,
                        onTest = onTest,
                        onSave = onSave,
                    )
                }

                // Outside the dimmed block on purpose: this is what someone reads *before* deciding what
                // to type, and it is the only place the app admits it speaks neither VLESS nor the
                // WebView's network stack.
                SectionNotes(
                    title = stringResource(Res.string.proxy_advanced_title),
                    lines = listOf(stringResource(Res.string.proxy_advanced_hint), stringResource(Res.string.proxy_webview_hint)),
                )
            }
        }
    }
}

/**
 * The same page as a description, for the system-drawn list on iOS; the Compose body above stays the
 * fallback. The type card becomes the section's rows — one proxy's type and address in one group.
 */
@Composable
private fun proxySettingsPage(
    state: ProxySettingsUiState,
    onEnabledChange: (Boolean) -> Unit,
    onTypeChange: (ProxyType) -> Unit,
    onForumOnlyChange: (Boolean) -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
): SettingsPage {
    val types = listOf(ProxyType.HTTP to stringResource(Res.string.proxy_type_http), ProxyType.SOCKS to stringResource(Res.string.proxy_type_socks))
    return SettingsPage(
        title = stringResource(Res.string.proxy_title),
        sections = listOfNotNull(
            SettingsSection(
                key = "master",
                style = SettingsSectionStyle.Prominent,
                items = listOf(
                    SettingsItem.Toggle(
                        key = "enabled",
                        title = stringResource(Res.string.proxy_master_title),
                        checked = state.enabled,
                        onCheckedChange = onEnabledChange,
                        description = stringResource(Res.string.proxy_master_hint),
                    ),
                ),
            ),
            SettingsSection(
                key = "config",
                items = listOf(
                    SettingsItem.Choice(
                        key = "type",
                        title = stringResource(Res.string.proxy_type_title),
                        options = types.map { SettingsOption(it.second) },
                        selectedIndex = types.indexOfFirst { it.first == state.type }.coerceAtLeast(0),
                        onSelect = { onTypeChange(types[it].first) },
                        presentation = SettingsChoicePresentation.Segmented,
                        enabled = state.enabled,
                    ),
                    SettingsItem.Text(
                        key = "host",
                        title = stringResource(Res.string.proxy_host_label),
                        value = state.hostInput,
                        onValueChange = onHostChange,
                        placeholder = stringResource(Res.string.proxy_host_placeholder),
                        keyboard = SettingsKeyboard.Uri,
                        errorMessage = if (state.problem == ProxyConfigProblem.MISSING_HOST) stringResource(Res.string.proxy_host_required) else null,
                        enabled = state.enabled,
                    ),
                    SettingsItem.Text(
                        key = "port",
                        title = stringResource(Res.string.proxy_port_label),
                        value = state.portInput,
                        onValueChange = onPortChange,
                        placeholder = stringResource(Res.string.proxy_port_placeholder),
                        keyboard = SettingsKeyboard.Number,
                        errorMessage = if (state.problem == ProxyConfigProblem.INVALID_PORT) stringResource(Res.string.proxy_port_invalid) else null,
                        enabled = state.enabled,
                    ),
                    SettingsItem.Text(
                        key = "username",
                        title = stringResource(Res.string.proxy_username_label),
                        value = state.usernameInput,
                        onValueChange = onUsernameChange,
                        placeholder = stringResource(Res.string.proxy_credential_hint),
                        enabled = state.enabled,
                    ),
                    SettingsItem.Text(
                        key = "password",
                        title = stringResource(Res.string.proxy_password_label),
                        value = state.passwordInput,
                        onValueChange = onPasswordChange,
                        placeholder = stringResource(Res.string.proxy_credential_hint),
                        secure = true,
                        enabled = state.enabled,
                    ),
                ),
            ),
            SettingsSection(
                key = "scope",
                items = listOf(
                    SettingsItem.Toggle(
                        key = "forum_only",
                        title = stringResource(Res.string.proxy_scope_title),
                        checked = state.scope == ProxyScope.FORUM_ONLY,
                        onCheckedChange = onForumOnlyChange,
                        description = stringResource(Res.string.proxy_scope_hint),
                        enabled = state.enabled,
                    ),
                ),
            ),
            state.testFailure?.let {
                SettingsSection(
                    key = "test_result",
                    items = listOf(
                        SettingsItem.Info(
                            key = "failure",
                            title = proxyTestFailureText(it),
                        ),
                    ),
                )
            },
            SettingsSection(
                key = "actions",
                items = listOf(
                    SettingsItem.Action(
                        key = "test",
                        title = stringResource(Res.string.proxy_test),
                        onClick = onTest,
                        loading = state.testing,
                        enabled = state.enabled,
                    ),
                    SettingsItem.Action(
                        key = "save",
                        title = stringResource(Res.string.proxy_save),
                        onClick = onSave,
                        enabled = state.enabled,
                    ),
                ),
            ),
            SettingsSection(
                key = "advanced",
                header = stringResource(Res.string.proxy_advanced_title),
                footer = stringResource(Res.string.proxy_advanced_hint) + "\n" + stringResource(Res.string.proxy_webview_hint),
            ),
        ),
    )
}

@Composable
private fun proxyTestFailureText(failure: ProxyConnectionFailure): String {
    val messageRes =
        when (failure.kind) {
            ProxyConnectionFailure.Kind.DNS -> Res.string.proxy_test_failure_dns
            ProxyConnectionFailure.Kind.TIMEOUT -> Res.string.proxy_test_failure_timeout
            ProxyConnectionFailure.Kind.CONNECTION -> Res.string.proxy_test_failure_connection
            ProxyConnectionFailure.Kind.SOCKS_AUTHENTICATION -> Res.string.proxy_test_failure_socks_auth
            ProxyConnectionFailure.Kind.TLS -> Res.string.proxy_test_failure_tls
            ProxyConnectionFailure.Kind.OTHER -> Res.string.proxy_test_failure_other
        }
    return stringResource(messageRes, failure.exceptionName)
}

@Composable
private fun ProxyField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: StringResource,
    placeholderRes: StringResource,
    modifier: Modifier = Modifier,
    errorRes: StringResource? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    obscure: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    SettingsTextField(
        value = value,
        onValueChange = onValueChange,
        label = stringResource(labelRes),
        placeholder = stringResource(placeholderRes),
        modifier = modifier,
        enabled = enabled,
        isError = isError,
        supportingText = if (isError && errorRes != null) stringResource(errorRes) else null,
        keyboardType = keyboardType,
        visualTransformation = if (obscure) PasswordVisualTransformation() else VisualTransformation.None,
        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun ProxySettingsPreview() {
    PlazaTheme {
        ProxySettingsScreen(
            state = ProxySettingsUiState(enabled = true, hostInput = "192.168.1.1", portInput = "1080"),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onEnabledChange = {},
            onTypeChange = {},
            onForumOnlyChange = {},
            onHostChange = {},
            onPortChange = {},
            onUsernameChange = {},
            onPasswordChange = {},
            onSave = {},
            onTest = {},
        )
    }
}
