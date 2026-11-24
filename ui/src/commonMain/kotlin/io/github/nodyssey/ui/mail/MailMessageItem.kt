package io.github.nodyssey.ui.mail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import io.github.nodyssey.ui.resources.mail_read_status
import io.github.nodyssey.ui.resources.mail_star
import io.github.nodyssey.ui.resources.mail_unread_status
import io.github.nodyssey.ui.resources.mail_unstar
import io.github.nodyssey.ui.resources.mail_verification_code
import io.github.plaza.core.TimeFormat
import io.github.plaza.designsys.component.GroupCard
import io.github.plaza.designsys.component.PlazaIcon
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
    nowMillis: Long,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    recipientAddress: String = mail.toAddress,
    selected: Boolean? = null,
    onToggleFlag: (() -> Unit)? = null,
    flagEnabled: Boolean = true,
    sent: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val unread = !sent && !mail.isRead
    val readStatus = if (sent) null else stringResource(if (unread) Res.string.mail_unread_status else Res.string.mail_read_status)
    Column(modifier.fillMaxWidth()) {
        ListItem(
            modifier = Modifier.clickable(onClick = onOpen).semantics {
                if (readStatus != null) stateDescription = readStatus
                if (selected != null) this.selected = selected
            },
            colors = ListItemDefaults.colors(containerColor = if (selected == true) colors.secondaryContainer else Color.Transparent),
            leadingContent = if (sent) {
                null
            } else {
                {
                    if (selected != null) {
                        Checkbox(checked = selected, onCheckedChange = null)
                    } else {
                        Box(Modifier.size(6.dp).background(if (unread) colors.primary else Color.Transparent, CircleShape))
                    }
                }
            },
            trailingContent = {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        compactMailTimestamp(mail.receivedAt, nowMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                    if (selected == null && onToggleFlag != null) {
                        IconToggleButton(checked = mail.isFlagged, onCheckedChange = { onToggleFlag() }, enabled = flagEnabled) {
                            PlazaIcon(
                                if (mail.isFlagged) Icons.Default.Star else Icons.Outlined.Star,
                                stringResource(if (mail.isFlagged) Res.string.mail_unstar else Res.string.mail_star),
                                modifier = Modifier.size(18.dp),
                                tint = if (mail.isFlagged) colors.primary else colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            headlineContent = {
                Text(
                    if (sent) recipientAddress else mail.sender.ifBlank { recipientAddress },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        mail.subject?.takeIf(String::isNotBlank) ?: stringResource(Res.string.mail_no_subject),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface,
                        fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!preview?.snippet.isNullOrBlank()) {
                        Text(
                            preview.snippet,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val status = if (sent) mail.deliveryLabel()?.let { stringResource(it) } else null
                    val labels = listOfNotNull(status) + mail.tags.orEmpty().map { it.name }
                    if (labels.isNotEmpty()) {
                        Text(
                            labels.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!sent && selected == null) preview?.verificationCode?.let { MailCodeChip(it) }
                }
            },
        )
    }
}

@Composable
private fun MailCodeChip(code: String) {
    val copy = rememberClipboardCopy()
    val copied = stringResource(Res.string.mail_code_copied)
    val colors = MaterialTheme.colorScheme
    AssistChip(
        onClick = { copy("verification-code", code, copied) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = colors.primaryContainer,
            labelColor = colors.onPrimaryContainer,
            leadingIconContentColor = colors.onPrimaryContainer,
            trailingIconContentColor = colors.onPrimaryContainer,
        ),
        border = null,
        label = {
            Text(
                code,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
        },
        leadingIcon = { PlazaIcon(Icons.Default.Lock, contentDescription = stringResource(Res.string.mail_verification_code), Modifier.size(16.dp)) },
        trailingIcon = { PlazaIcon(PlazaIcons.ContentCopy, contentDescription = stringResource(Res.string.mail_copy_code), Modifier.size(16.dp)) },
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
                    PlazaIcon(PlazaIcons.ContentCopy, stringResource(Res.string.mail_copy_code))
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
