package io.github.nodyssey.ui.mail

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.data.mail.MailAttachment
import io.github.nodyssey.data.mail.MailError
import io.github.nodyssey.data.mail.isSafeMailLink
import io.github.nodyssey.ui.account.formatBytes
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
import io.github.plaza.designsys.component.GroupedListItem
import io.github.plaza.designsys.component.IconBadge
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.LayerDivider
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.PlazaIcon
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.PlazaSpinner
import io.github.plaza.designsys.component.SectionLabel
import io.github.plaza.designsys.component.TonalTag
import io.github.plaza.designsys.component.TopBarAction
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
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

@OptIn(ExperimentalLayoutApi::class)
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
    val colors = MaterialTheme.colorScheme
    val linkColor = colors.primary
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
        Box(Modifier.padding(padding).fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .readableWidth()
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = LayerPageGutter, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                if (state.loading) {
                    Box(Modifier.fillMaxWidth().padding(vertical = Spacing.xxl), contentAlignment = Alignment.Center) {
                        PlazaSpinner()
                    }
                }
                if (state.expired) {
                    LayerCard(contentPadding = PaddingValues(Spacing.lg)) {
                        Text(stringResource(Res.string.mail_connection_expired), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                state.error?.let { error ->
                    LayerCard(
                        containerColor = colors.errorContainer.copy(alpha = 0.55f),
                        contentPadding = PaddingValues(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(stringResource(error.messageRes()), color = colors.onErrorContainer)
                        if (state.message == null) {
                            FilledTonalButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
                        }
                    }
                }
                state.message?.let { mail ->
                    // Header card: subject, tags, sender avatar & metadata
                    LayerCard(
                        contentPadding = PaddingValues(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        SelectionContainer {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                Text(
                                    text = mail.subject?.takeIf(String::isNotBlank) ?: stringResource(Res.string.mail_no_subject),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                val delivery = if (sent) mail.deliveryLabel()?.let { stringResource(it) } else null
                                val tags = mail.tags.orEmpty()
                                if (delivery != null || tags.isNotEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        delivery?.let {
                                            TonalTag(it, colors.primaryContainer, colors.onPrimaryContainer)
                                        }
                                        tags.forEach { tag ->
                                            TonalTag(tag.name, colors.secondaryContainer, colors.onSecondaryContainer)
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val avatarSeed = mail.sender.ifBlank { mail.fromAddress }.ifBlank { "?" }
                                    UserAvatar(url = null, name = avatarSeed, size = 40.dp)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = stringResource(Res.string.mail_from, mail.sender, mail.fromAddress),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            text = mailTimestamp(mail.receivedAt),
                                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
                                            color = colors.onSurfaceVariant,
                                        )
                                    }
                                }
                                LayerDivider(startInset = 0.dp)
                                Text(
                                    text = stringResource(Res.string.mail_to, mail.toAddress),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    state.verificationCode?.let { MailVerificationCodeCard(it) }

                    // Remote images prompt card (if HTML has pending remote images)
                    mail.formattedBody?.let { html ->
                        if (html.remoteImages.any { it !in state.remoteImages }) {
                            LayerCard(
                                containerColor = colors.secondaryContainer.copy(alpha = 0.65f),
                                contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
                                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    PlazaIcon(
                                        PlazaIcons.Image,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = colors.onSecondaryContainer,
                                    )
                                    val label = if (state.loadingRemoteImages) Res.string.mail_remote_images_loading else Res.string.mail_remote_images_load
                                    Text(
                                        text = stringResource(label),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.onSecondaryContainer,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (state.loadingRemoteImages) {
                                        PlazaSpinner(Modifier.size(18.dp))
                                    } else {
                                        FilledTonalButton(
                                            onClick = onLoadRemoteImages,
                                            enabled = !state.loading && !state.loadingRemoteImages,
                                            contentPadding = PaddingValues(horizontal = Spacing.md),
                                        ) {
                                            Text(stringResource(Res.string.mail_remote_images_load))
                                        }
                                    }
                                }
                                if (state.remoteImagesFailed) {
                                    Text(
                                        text = stringResource(Res.string.mail_remote_images_failed),
                                        color = colors.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }

                    // Body reading card
                    LayerCard(contentPadding = PaddingValues(Spacing.lg)) {
                        val html = mail.formattedBody
                        when {
                            html != null -> {
                                MailHtmlReader(html, state.remoteImages, onOpenWebsite, Modifier.fillMaxWidth())
                            }

                            state.body.isBlank() -> {
                                Text(
                                    text = stringResource(Res.string.mail_no_body),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.onSurfaceVariant,
                                )
                            }

                            else -> {
                                SelectionContainer {
                                    Text(body, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }

                    // Attachments section
                    if (mail.hasAttachments) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            SectionLabel(stringResource(Res.string.mail_attachments))
                            if (state.loadingAttachments) {
                                LayerCard(contentPadding = PaddingValues(Spacing.lg)) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        PlazaSpinner(Modifier.size(18.dp))
                                        Text(
                                            stringResource(Res.string.mail_loading),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colors.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            if (onSaveAttachment != null && state.attachments.isNotEmpty()) {
                                Column {
                                    state.attachments.forEachIndexed { index, attachment ->
                                        val detail = listOfNotNull(
                                            attachment.size.takeIf { it > 0 }?.let(::formatBytes),
                                            attachment.contentType?.takeIf(String::isNotBlank),
                                        ).joinToString(" · ").takeIf(String::isNotEmpty)
                                        GroupedListItem(
                                            first = index == 0,
                                            last = index == state.attachments.lastIndex,
                                            enabled = !savingAttachment,
                                            onClick = { onSaveAttachment(attachment) },
                                            leadingContent = {
                                                IconBadge(colors.secondaryContainer, colors.onSecondaryContainer, size = 38.dp) {
                                                    PlazaIcon(PlazaIcons.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                                }
                                            },
                                            headlineContent = {
                                                Text(
                                                    attachment.suggestedName,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            },
                                            supportingContent = detail?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                                            trailingContent = {
                                                if (savingAttachment) {
                                                    PlazaSpinner(Modifier.size(18.dp))
                                                } else {
                                                    PlazaIcon(
                                                        PlazaIcons.Download,
                                                        contentDescription = null,
                                                        tint = colors.primary,
                                                    )
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                            if (attachmentSaved) {
                                LayerCard(
                                    containerColor = colors.primaryContainer.copy(alpha = 0.5f),
                                    contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        PlazaIcon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = colors.primary,
                                        )
                                        Text(
                                            stringResource(Res.string.mail_attachment_saved),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colors.onPrimaryContainer,
                                        )
                                    }
                                }
                            }
                            state.attachmentError?.let { error ->
                                val message = if (error == MailError.PERMISSION) Res.string.mail_scope_attachment_read else error.messageRes()
                                LayerCard(
                                    containerColor = colors.errorContainer.copy(alpha = 0.55f),
                                    contentPadding = PaddingValues(Spacing.lg),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                                ) {
                                    Text(stringResource(message), color = colors.onErrorContainer)
                                    FilledTonalButton(onClick = onRetryAttachments) { Text(stringResource(Res.string.action_retry)) }
                                }
                            }
                            saveError?.let { error ->
                                LayerCard(
                                    containerColor = colors.errorContainer.copy(alpha = 0.55f),
                                    contentPadding = PaddingValues(Spacing.lg),
                                ) {
                                    Text(stringResource(error.messageRes()), color = colors.onErrorContainer)
                                }
                            }
                            if (onSaveAttachment == null || state.attachmentError != null || saveError != null ||
                                (!state.loadingAttachments && state.attachments.isEmpty())
                            ) {
                                LayerCard(
                                    contentPadding = PaddingValues(Spacing.lg),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                                ) {
                                    Text(
                                        stringResource(Res.string.mail_attachment_hint),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.onSurfaceVariant,
                                    )
                                    FilledTonalButton(onClick = { onOpenWebsite("https://seek.li/") }) {
                                        PlazaIcon(PlazaIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(Res.string.mail_open_website))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.size(Spacing.xl))
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
