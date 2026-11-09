package io.github.nodyssey.ui.mail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.data.mail.MailError
import io.github.nodyssey.data.mail.isSafeMailLink
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.action_retry
import io.github.nodyssey.ui.resources.mail_attachment_hint
import io.github.nodyssey.ui.resources.mail_connection_expired
import io.github.nodyssey.ui.resources.mail_detail
import io.github.nodyssey.ui.resources.mail_error_challenge
import io.github.nodyssey.ui.resources.mail_error_forum_sign_in
import io.github.nodyssey.ui.resources.mail_error_key
import io.github.nodyssey.ui.resources.mail_error_missing
import io.github.nodyssey.ui.resources.mail_error_network
import io.github.nodyssey.ui.resources.mail_error_permission
import io.github.nodyssey.ui.resources.mail_error_rate
import io.github.nodyssey.ui.resources.mail_error_service
import io.github.nodyssey.ui.resources.mail_error_storage
import io.github.nodyssey.ui.resources.mail_error_unauthorized
import io.github.nodyssey.ui.resources.mail_error_web_authorization
import io.github.nodyssey.ui.resources.mail_from
import io.github.nodyssey.ui.resources.mail_mark_read
import io.github.nodyssey.ui.resources.mail_mark_unread
import io.github.nodyssey.ui.resources.mail_no_body
import io.github.nodyssey.ui.resources.mail_no_subject
import io.github.nodyssey.ui.resources.mail_open_website
import io.github.nodyssey.ui.resources.mail_plain_text_hint
import io.github.nodyssey.ui.resources.mail_to
import io.github.plaza.core.TimeFormat
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaSpinner
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun MailDetailRoute(viewModel: MailDetailViewModel, onBack: () -> Unit, onOpenWebsite: (String) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MailDetailScreen(state, onBack, viewModel::reload, viewModel::toggleRead, onOpenWebsite)
}

@Composable
fun MailDetailScreen(
    state: MailDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onToggleRead: () -> Unit,
    onOpenWebsite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appBarState = rememberOneHandAppBarState()
    val linkColor = MaterialTheme.colorScheme.primary
    val body = remember(state.body, state.verificationCode, linkColor, onOpenWebsite) {
        buildAnnotatedString {
            append(state.body)
            state.verificationCode?.let { code ->
                Regex("(?<![a-zA-Z0-9_])${Regex.escape(code)}(?![a-zA-Z0-9_])").findAll(state.body).forEach { match ->
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = linkColor), match.range.first, match.range.last + 1)
                }
            }
            MAIL_LINKS.findAll(state.body).forEach { match ->
                val url = match.value.trimEnd('.', ',', ';', ':', '!', '?')
                if (isSafeMailLink(url)) {
                    addLink(
                        LinkAnnotation.Url(
                            url = url,
                            styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                            linkInteractionListener = { onOpenWebsite(url) },
                        ),
                        start = match.range.first,
                        end = match.range.first + url.length,
                    )
                }
            }
        }
    }
    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        topBar = {
            OneHandTopAppBar(
                title = stringResource(Res.string.mail_detail),
                state = appBarState,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.action_back))
                    }
                },
                actions = {
                    state.message?.let { mail ->
                        TextButton(onClick = onToggleRead, enabled = !state.updatingRead) {
                            Text(stringResource(if (mail.isRead) Res.string.mail_mark_unread else Res.string.mail_mark_read))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().readableWidth().verticalScroll(rememberScrollState())
                .padding(LayerPageGutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            if (state.loading) PlazaSpinner()
            if (state.expired) Text(stringResource(Res.string.mail_connection_expired))
            state.error?.let { error ->
                Text(stringResource(error.messageRes()), color = MaterialTheme.colorScheme.error)
                if (state.message == null) TextButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
            }
            state.message?.let { mail ->
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                        Text(
                            mail.subject?.takeIf(String::isNotBlank) ?: stringResource(Res.string.mail_no_subject),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Column {
                            Text(stringResource(Res.string.mail_from, mail.sender, mail.fromAddress), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(Res.string.mail_to, mail.toAddress), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(mailTimestamp(mail.receivedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        state.verificationCode?.let { MailVerificationCodeCard(it) }
                        HorizontalDivider()
                        if (state.body.isBlank()) {
                            Text(stringResource(Res.string.mail_no_body), style = MaterialTheme.typography.bodyLarge)
                        } else {
                            Text(body, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Text(
                    stringResource(Res.string.mail_plain_text_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (mail.hasAttachments) {
                    Text(stringResource(Res.string.mail_attachment_hint))
                    TextButton(onClick = { onOpenWebsite("https://seek.li/") }) {
                        Text(stringResource(Res.string.mail_open_website))
                    }
                }
            }
        }
    }
}

private val MAIL_LINKS = Regex("(?:https?://|mailto:)[^\\s<>()]+", RegexOption.IGNORE_CASE)

internal fun mailTimestamp(raw: String?): String =
    TimeFormat.parseTimestamp(raw)?.let { TimeFormat.absolute(it) } ?: raw.orEmpty()

internal fun MailError.messageRes(): StringResource = when (this) {
    MailError.INVALID_KEY -> Res.string.mail_error_key
    MailError.UNAUTHORIZED -> Res.string.mail_error_unauthorized
    MailError.PERMISSION -> Res.string.mail_error_permission
    MailError.NOT_FOUND -> Res.string.mail_error_missing
    MailError.RATE_LIMITED -> Res.string.mail_error_rate
    MailError.STORAGE -> Res.string.mail_error_storage
    MailError.INVALID_RESPONSE, MailError.SERVICE -> Res.string.mail_error_service
    MailError.NETWORK -> Res.string.mail_error_network
    MailError.CHALLENGE -> Res.string.mail_error_challenge
    MailError.FORUM_SIGN_IN -> Res.string.mail_error_forum_sign_in
    MailError.WEB_AUTHORIZATION -> Res.string.mail_error_web_authorization
}
