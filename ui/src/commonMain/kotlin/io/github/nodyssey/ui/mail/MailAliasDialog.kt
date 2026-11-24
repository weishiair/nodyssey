package io.github.nodyssey.ui.mail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import io.github.nodyssey.data.mail.MailError
import io.github.nodyssey.data.mail.isMailAddress
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_cancel
import io.github.nodyssey.ui.resources.action_close
import io.github.nodyssey.ui.resources.mail_address
import io.github.nodyssey.ui.resources.mail_alias_available
import io.github.nodyssey.ui.resources.mail_alias_created
import io.github.nodyssey.ui.resources.mail_alias_unavailable
import io.github.nodyssey.ui.resources.mail_check_address
import io.github.nodyssey.ui.resources.mail_create_alias
import io.github.nodyssey.ui.resources.mail_loading
import io.github.nodyssey.ui.resources.mail_scope_mailbox_write
import io.github.plaza.designsys.theme.Spacing
import org.jetbrains.compose.resources.stringResource

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
    AlertDialog(
        onDismissRequest = { if (!state.busy) onDismiss() },
        title = { Text(stringResource(Res.string.mail_create_alias)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                val created = state.created
                if (created != null) {
                    Text(stringResource(Res.string.mail_alias_created, created.address))
                } else {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text(stringResource(Res.string.mail_address)) },
                        singleLine = true,
                        enabled = !state.busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.busy) Text(stringResource(Res.string.mail_loading))
                    if (checked) Text(stringResource(if (available) Res.string.mail_alias_available else Res.string.mail_alias_unavailable))
                    state.error?.let {
                        val message = if (it == MailError.PERMISSION) Res.string.mail_scope_mailbox_write else it.messageRes()
                        Text(stringResource(message), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            if (state.created != null) {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_close)) }
            } else {
                TextButton(
                    onClick = { if (available) onCreate(value) else onCheck(value) },
                    enabled = !state.busy && isMailAddress(value),
                ) {
                    Text(stringResource(if (available) Res.string.mail_create_alias else Res.string.mail_check_address))
                }
            }
        },
        dismissButton = {
            if (state.created == null) TextButton(onClick = onDismiss, enabled = !state.busy) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}
