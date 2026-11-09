package io.github.nodyssey.ui.mail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nodyssey.data.mail.MailMessage
import io.github.nodyssey.data.mail.MailPreview
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.mail_code_copied
import io.github.nodyssey.ui.resources.mail_copy_code
import io.github.nodyssey.ui.resources.mail_no_subject
import io.github.nodyssey.ui.resources.mail_verification_code
import io.github.plaza.core.TimeFormat
import io.github.plaza.designsys.component.GroupCard
import io.github.plaza.designsys.component.GroupedListItem
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.rememberClipboardCopy
import io.github.plaza.designsys.theme.Spacing
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

@Composable
internal fun MailMessageItem(
    mail: MailMessage,
    preview: MailPreview?,
    first: Boolean,
    last: Boolean,
    nowMillis: Long,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GroupedListItem(
        first = first,
        last = last,
        modifier = modifier,
        onClick = onOpen,
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.md),
        headlineContent = {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (!mail.isRead) Badge()
                Text(
                    mail.sender,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (mail.isRead) FontWeight.Medium else FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    compactMailTimestamp(mail.receivedAt, nowMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    mail.subject?.takeIf(String::isNotBlank) ?: stringResource(Res.string.mail_no_subject),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (mail.isRead) FontWeight.Normal else FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!preview?.snippet.isNullOrBlank()) {
                    Text(
                        preview.snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                preview?.verificationCode?.let { MailCodeChip(it) }
            }
        },
    )
}

@Composable
private fun MailCodeChip(code: String) {
    val copy = rememberClipboardCopy()
    val copied = stringResource(Res.string.mail_code_copied)
    AssistChip(
        onClick = { copy("verification-code", code, copied) },
        label = {
            Text(
                code,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = stringResource(Res.string.mail_verification_code), Modifier.size(16.dp)) },
        trailingIcon = { Icon(PlazaIcons.ContentCopy, contentDescription = stringResource(Res.string.mail_copy_code), Modifier.size(16.dp)) },
    )
}

@Composable
internal fun MailVerificationCodeCard(code: String) {
    val copy = rememberClipboardCopy()
    val copied = stringResource(Res.string.mail_code_copied)
    GroupCard {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                stringResource(Res.string.mail_verification_code),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    code,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                FilledTonalIconButton(onClick = { copy("verification-code", code, copied) }) {
                    Icon(PlazaIcons.ContentCopy, stringResource(Res.string.mail_copy_code))
                }
            }
        }
    }
}

internal fun compactMailTimestamp(raw: String?, nowMillis: Long): String {
    val millis = TimeFormat.parseTimestamp(raw) ?: return raw.orEmpty()
    val zone = TimeZone.currentSystemDefault()
    val time = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone)
    val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(zone).date
    return when {
        time.date == today -> "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
        time.year == today.year -> "${time.month.ordinal + 1}/${time.day}"
        else -> "${time.year}/${time.month.ordinal + 1}/${time.day}"
    }
}
