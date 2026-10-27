package io.github.nodyssey.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_close
import io.github.nodyssey.ui.resources.network_check_copied
import io.github.nodyssey.ui.resources.network_check_copy
import io.github.nodyssey.ui.resources.sign_in_diagnostics
import io.github.nodyssey.ui.resources.sign_in_diagnostics_privacy
import io.github.plaza.designsys.component.rememberClipboardCopy
import io.github.plaza.designsys.theme.Spacing
import org.jetbrains.compose.resources.stringResource

/** Available without signing in; unlike the transient snackbar this survives until the next attempt. */
@Composable
internal fun SignInDiagnosticsAction(report: String?) {
    if (report == null) return
    var expanded by remember { mutableStateOf(false) }
    val title = stringResource(Res.string.sign_in_diagnostics)
    val copy = rememberClipboardCopy()
    val copied = stringResource(Res.string.network_check_copied)
    TextButton(onClick = { expanded = true }) { Text(title) }
    if (expanded) {
        AlertDialog(
            onDismissRequest = { expanded = false },
            title = { Text(title) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Text(stringResource(Res.string.sign_in_diagnostics_privacy))
                    SelectionContainer {
                        Text(report, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { copy(title, report, copied) }) {
                    Text(stringResource(Res.string.network_check_copy))
                }
            },
            dismissButton = {
                TextButton(onClick = { expanded = false }) { Text(stringResource(Res.string.action_close)) }
            },
        )
    }
}
