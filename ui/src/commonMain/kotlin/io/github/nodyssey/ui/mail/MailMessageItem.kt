package io.github.nodyssey.ui.mail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
import io.github.plaza.designsys.component.GroupedListItem
import io.github.plaza.designsys.component.IconBadge
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.PlazaIcon
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.TonalTag
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.component.groupedListItemColors
import io.github.plaza.designsys.component.rememberClipboardCopy
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

@OptIn(ExperimentalLayoutApi::class)
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
    first: Boolean = true,
    last: Boolean = true,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val layers = LocalPlazaLayers.current
    val unread = !sent && !mail.isRead
    val readStatus = if (sent) null else stringResource(if (unread) Res.string.mail_unread_status else Res.string.mail_read_status)
    val senderLabel = if (sent) recipientAddress else mail.sender.ifBlank { recipientAddress }
    val avatarSeed = senderLabel.ifBlank { mail.fromAddress }.ifBlank { "?" }
    val rowContainer = if (selected == true) colors.secondaryContainer else layers.card

    GroupedListItem(
        first = first,
        last = last,
        onClick = onOpen,
        onLongClick = onLongClick,
        verticalAlignment = Alignment.Top,
        colors = groupedListItemColors(containerColor = rowContainer),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
        modifier = modifier.fillMaxWidth().semantics {
            if (readStatus != null) stateDescription = readStatus
            if (selected != null) this.selected = selected
        },
        leadingContent = {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                if (selected != null) {
                    Checkbox(checked = selected, onCheckedChange = null)
                } else {
                    UserAvatar(url = null, name = avatarSeed, size = 40.dp)
                    if (unread) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 2.dp, y = (-2).dp)
                                .size(11.dp)
                                .background(rowContainer, CircleShape)
                                .padding(2.dp)
                                .background(colors.primary, CircleShape),
                        )
                    }
                }
            }
        },
        headlineContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = senderLabel,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = compactMailTimestamp(mail.receivedAt, nowMillis),
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (unread) colors.primary else colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        },
        supportingContent = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = mail.subject?.takeIf(String::isNotBlank) ?: stringResource(Res.string.mail_no_subject),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (unread) colors.onSurface else colors.onSurface.copy(alpha = 0.88f),
                        fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (selected == null && onToggleFlag != null) {
                        MailStarButton(
                            flagged = mail.isFlagged,
                            enabled = flagEnabled,
                            onToggle = onToggleFlag,
                        )
                    }
                }
                if (!preview?.snippet.isNullOrBlank()) {
                    Text(
                        text = preview.snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val delivery = if (sent) mail.deliveryLabel()?.let { stringResource(it) } else null
                val tags = mail.tags.orEmpty()
                val hasMeta = delivery != null || tags.isNotEmpty()
                if (hasMeta) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        delivery?.let {
                            TonalTag(
                                text = it,
                                containerColor = colors.primaryContainer,
                                contentColor = colors.onPrimaryContainer,
                            )
                        }
                        tags.forEach { tag ->
                            TonalTag(
                                text = tag.name,
                                containerColor = colors.secondaryContainer,
                                contentColor = colors.onSecondaryContainer,
                            )
                        }
                    }
                }
                if (!sent && selected == null) {
                    preview?.verificationCode?.let { code ->
                        Box(Modifier.padding(top = 4.dp)) {
                            MailCodeChip(code)
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun MailStarButton(
    flagged: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(if (flagged) Res.string.mail_unstar else Res.string.mail_star)
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 28.dp) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(enabled = enabled, role = Role.Checkbox, onClick = onToggle)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            PlazaIcon(
                imageVector = if (flagged) Icons.Default.Star else PlazaIcons.StarBorder,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = if (flagged) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun MailCodeChip(code: String) {
    val copy = rememberClipboardCopy()
    val copied = stringResource(Res.string.mail_code_copied)
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = { copy("verification-code", code, copied) },
        shape = shape,
        color = colors.primaryContainer.copy(alpha = 0.72f),
        contentColor = colors.onPrimaryContainer,
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.16f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PlazaIcon(
                Icons.Default.Lock,
                contentDescription = stringResource(Res.string.mail_verification_code),
                modifier = Modifier.size(14.dp),
                tint = colors.primary,
            )
            Text(
                text = code,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = TABULAR_FIGURES,
                ),
                color = colors.onPrimaryContainer,
            )
            PlazaIcon(
                PlazaIcons.ContentCopy,
                contentDescription = stringResource(Res.string.mail_copy_code),
                modifier = Modifier.size(14.dp),
                tint = colors.onPrimaryContainer.copy(alpha = 0.78f),
            )
        }
    }
}

@Composable
internal fun MailVerificationCodeCard(code: String) {
    val copy = rememberClipboardCopy()
    val copied = stringResource(Res.string.mail_code_copied)
    val colors = MaterialTheme.colorScheme
    LayerCard(
        containerColor = colors.primaryContainer.copy(alpha = 0.65f),
        contentPadding = PaddingValues(Spacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            IconBadge(
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                size = 42.dp,
            ) {
                PlazaIcon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(Res.string.mail_verification_code),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onPrimaryContainer.copy(alpha = 0.8f),
                )
                Text(
                    text = code,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = TABULAR_FIGURES,
                    ),
                    color = colors.onPrimaryContainer,
                )
            }
            FilledTonalButton(
                onClick = { copy("verification-code", code, copied) },
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
            ) {
                PlazaIcon(PlazaIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.mail_copy_code), style = MaterialTheme.typography.labelLarge)
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
