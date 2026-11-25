package io.github.nodyssey.ui.mail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.nodyssey.data.mail.MailError
import io.github.nodyssey.data.mail.isMailAddress
import io.github.nodyssey.ui.common.PlazaSheet
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_close
import io.github.nodyssey.ui.resources.mail_address
import io.github.nodyssey.ui.resources.mail_alias_available
import io.github.nodyssey.ui.resources.mail_alias_created
import io.github.nodyssey.ui.resources.mail_alias_unavailable
import io.github.nodyssey.ui.resources.mail_check_address
import io.github.nodyssey.ui.resources.mail_create_alias
import io.github.nodyssey.ui.resources.mail_loading
import io.github.nodyssey.ui.resources.mail_scope_mailbox_write
import io.github.plaza.designsys.component.IconBadge
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.PlazaFieldDefaults
import io.github.plaza.designsys.component.PlazaIcon
import io.github.plaza.designsys.component.PlazaSpinner
import io.github.plaza.designsys.component.TonalTag
import io.github.plaza.designsys.theme.Spacing
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MailAliasDialog(
    state: MailAliasState,
    onCheck: (String) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var address by remember { mutableStateOf("") }
    val value = address.trim()
    val checked = value == state.checkedAddress
    val available = checked && state.available
    val colors = MaterialTheme.colorScheme

    PlazaSheet(
        onDismiss = { if (!state.busy) onDismiss() },
        title = stringResource(Res.string.mail_create_alias),
        fitNativeSheetContent = true,
        action = if (state.created != null) {
            { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_close)) } }
        } else {
            null
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LayerPageGutter)
                .padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            val created = state.created
            if (created != null) {
                LayerCard(contentPadding = PaddingValues(Spacing.xl)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBadge(colors.primaryContainer, colors.primary, size = 42.dp) {
                            PlazaIcon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(22.dp))
                        }
                        Text(stringResource(Res.string.mail_alias_created, created.address), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else {
                LayerCard(
                    contentPadding = PaddingValues(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text(stringResource(Res.string.mail_address)) },
                        singleLine = true,
                        enabled = !state.busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        shape = PlazaFieldDefaults.shape,
                        colors = PlazaFieldDefaults.colors(inCard = true),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (checked) {
                        TonalTag(
                            text = stringResource(if (available) Res.string.mail_alias_available else Res.string.mail_alias_unavailable),
                            containerColor = if (available) colors.primaryContainer else colors.errorContainer,
                            contentColor = if (available) colors.onPrimaryContainer else colors.onErrorContainer,
                        )
                    }
                    state.error?.let {
                        val message = if (it == MailError.PERMISSION) Res.string.mail_scope_mailbox_write else it.messageRes()
                        Text(stringResource(message), color = colors.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = { if (available) onCreate(value) else onCheck(value) },
                        enabled = !state.busy && isMailAddress(value),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.busy) {
                            PlazaSpinner(Modifier.size(16.dp))
                            Spacer(Modifier.width(Spacing.sm))
                            Text(stringResource(Res.string.mail_loading))
                        } else {
                            Text(stringResource(if (available) Res.string.mail_create_alias else Res.string.mail_check_address))
                        }
                    }
                }
            }
        }
    }
}
