package io.github.nodyssey.ui.mail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.data.mail.MailAttachment
import io.github.nodyssey.data.mail.MailError
import io.github.nodyssey.data.mail.isSafeMailLink
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_retry
import io.github.nodyssey.ui.resources.mail_attachment_hint
import io.github.nodyssey.ui.resources.mail_attachment_saved
import io.github.nodyssey.ui.resources.mail_attachment_too_large
import io.github.nodyssey.ui.resources.mail_attachments
import io.github.nodyssey.ui.resources.mail_connection_expired
import io.github.nodyssey.ui.resources.mail_detail
import io.github.nodyssey.ui.resources.mail_error_challenge
import io.github.nodyssey.ui.resources.mail_error_forum_sign_in
import io.github.nodyssey.ui.resources.mail_error_input
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
import io.github.nodyssey.ui.resources.mail_loading
import io.github.nodyssey.ui.resources.mail_mark_read
import io.github.nodyssey.ui.resources.mail_mark_unread
import io.github.nodyssey.ui.resources.mail_no_body
import io.github.nodyssey.ui.resources.mail_no_subject
import io.github.nodyssey.ui.resources.mail_open_website
import io.github.nodyssey.ui.resources.mail_remote_images_failed
import io.github.nodyssey.ui.resources.mail_remote_images_load
import io.github.nodyssey.ui.resources.mail_remote_images_loading
import io.github.nodyssey.ui.resources.mail_scope_attachment_read
import io.github.nodyssey.ui.resources.mail_to
import io.github.plaza.core.TimeFormat
import io.github.plaza.core.runCatchingExceptCancellation
import io.github.plaza.designsys.component.AdaptiveTopBar
import io.github.plaza.designsys.component.GlassAction
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.PlazaSpinner
import io.github.plaza.designsys.component.TopBarAction
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun MailDetailRoute(viewModel: MailDetailViewModel, onBack: () -> Unit, onOpenWebsite: (String) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<MailError?>(null) }
    val saver = rememberMailAttachmentSaver(viewModel::canSaveAttachment) { result ->
        saving = false
        saved = result == true
        error = MailError.STORAGE.takeIf { result == false }
    }
    MailDetailScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::reload,
        onToggleRead = viewModel::toggleRead,
        onOpenWebsite = onOpenWebsite,
        onLoadRemoteImages = viewModel::loadRemoteImages,
        onRetryAttachments = viewModel::loadAttachments,
        onSaveAttachment = saver?.let { save ->
            { attachment ->
                if (!saving) {
                    saving = true
                    saved = false
                    error = null
                    scope.launch {
                        runCatchingExceptCancellation { viewModel.downloadAttachment(attachment) }
                            .onSuccess { bytes ->
                                if (!viewModel.canSaveAttachment() || !save(attachment.suggestedName, bytes)) saving = false
                            }
                            .onFailure {
                                saving = false
                                error = it.mailError()
                            }
                    }
                }
            }
        },
        savingAttachment = saving,
        attachmentSaved = saved,
        saveError = error,
    )
}

@Composable
fun MailDetailScreen(
    state: MailDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onToggleRead: () -> Unit,
    onOpenWebsite: (String) -> Unit,
    modifier: Modifier = Modifier,
    onLoadRemoteImages: () -> Unit = {},
    onRetryAttachments: () -> Unit = {},
    onSaveAttachment: ((MailAttachment) -> Unit)? = null,
    savingAttachment: Boolean = false,
    attachmentSaved: Boolean = false,
    saveError: MailError? = null,
    sent: Boolean = false,
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
            val readAction = state.message?.takeUnless { sent }?.let { mail ->
                val label = stringResource(if (mail.isRead) Res.string.mail_mark_unread else Res.string.mail_mark_read)
                TopBarAction(
                    glass = GlassAction(label = label, onClick = onToggleRead, enabled = !state.updatingRead),
                    compose = {
                        TextButton(onClick = onToggleRead, enabled = !state.updatingRead) {
                            Text(label)
                        }
                    },
                )
            }
            AdaptiveTopBar(
                title = stringResource(Res.string.mail_detail),
                state = appBarState,
                onBack = onBack,
                actions = listOfNotNull(readAction),
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
                            if (sent) mail.deliveryLabel()?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
                        }
                        state.verificationCode?.let { MailVerificationCodeCard(it) }
                        HorizontalDivider()
                        if (mail.formattedBody == null && state.body.isBlank()) {
                            Text(stringResource(Res.string.mail_no_body), style = MaterialTheme.typography.bodyLarge)
                        } else if (mail.formattedBody == null) {
                            Text(body, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                mail.formattedBody?.let { html ->
                    if (html.remoteImages.any { it !in state.remoteImages }) {
                        Column {
                            TextButton(onClick = onLoadRemoteImages, enabled = !state.loading && !state.loadingRemoteImages) {
                                val label = if (state.loadingRemoteImages) Res.string.mail_remote_images_loading else Res.string.mail_remote_images_load
                                Text(stringResource(label))
                            }
                            if (state.remoteImagesFailed) {
                                Text(
                                    stringResource(Res.string.mail_remote_images_failed),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    MailHtmlReader(html, state.remoteImages, onOpenWebsite, Modifier.fillMaxWidth())
                }
                if (mail.hasAttachments) {
                    Column {
                        Text(stringResource(Res.string.mail_attachments), style = MaterialTheme.typography.titleMedium)
                        if (state.loadingAttachments || savingAttachment) Text(stringResource(Res.string.mail_loading))
                        state.attachmentError?.let { error ->
                            val message = if (error == MailError.PERMISSION) Res.string.mail_scope_attachment_read else error.messageRes()
                            Text(stringResource(message), color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = onRetryAttachments) { Text(stringResource(Res.string.action_retry)) }
                        }
                        if (onSaveAttachment != null) {
                            state.attachments.forEach { attachment ->
                                TextButton(onClick = { onSaveAttachment(attachment) }, enabled = !savingAttachment) {
                                    Text(attachment.suggestedName)
                                }
                            }
                        }
                        saveError?.let { Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error) }
                        if (attachmentSaved) Text(stringResource(Res.string.mail_attachment_saved))
                        if (onSaveAttachment == null || state.attachmentError != null || saveError != null ||
                            (!state.loadingAttachments && state.attachments.isEmpty())
                        ) {
                            Text(stringResource(Res.string.mail_attachment_hint))
                            TextButton(onClick = { onOpenWebsite("https://seek.li/") }) {
                                Text(stringResource(Res.string.mail_open_website))
                            }
                        }
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
    MailError.INVALID_INPUT -> Res.string.mail_error_input
    MailError.ATTACHMENT_TOO_LARGE -> Res.string.mail_attachment_too_large
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
