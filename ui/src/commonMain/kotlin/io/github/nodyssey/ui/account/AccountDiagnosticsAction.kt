package io.github.nodyssey.ui.account

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.accounts_diagnostics
import io.github.nodyssey.ui.resources.accounts_diagnostics_export
import io.github.nodyssey.ui.resources.accounts_diagnostics_export_failed
import io.github.nodyssey.ui.resources.accounts_diagnostics_privacy
import io.github.nodyssey.ui.resources.action_close
import io.github.nodyssey.ui.resources.network_check_copied
import io.github.nodyssey.ui.resources.network_check_copy
import io.github.plaza.designsys.component.rememberClipboardCopy
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AccountDiagnosticsAction(report: String) {
    var expanded by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val export = rememberExportAccountDiagnostics()
    val copy = rememberClipboardCopy()
    val title = stringResource(Res.string.accounts_diagnostics)
    val copied = stringResource(Res.string.network_check_copied)
    TextButton(onClick = { expanded = true }) { Text(title) }
    if (expanded) {
        AlertDialog(
            onDismissRequest = { expanded = false },
            title = { Text(title) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(stringResource(Res.string.accounts_diagnostics_privacy))
                    SelectionContainer {
                        Text(report, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                    if (failed) Text(stringResource(Res.string.accounts_diagnostics_export_failed))
                    TextButton(onClick = { copy(title, report, copied) }) {
                        Text(stringResource(Res.string.network_check_copy))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { scope.launch { failed = !export(report) } }) {
                    Text(stringResource(Res.string.accounts_diagnostics_export))
                }
            },
            dismissButton = {
                TextButton(onClick = { expanded = false }) { Text(stringResource(Res.string.action_close)) }
            },
        )
    }
}

/** Shares a plain text file through the platform sheet; the report has already been redacted. */
@Composable
internal expect fun rememberExportAccountDiagnostics(): suspend (String) -> Boolean
