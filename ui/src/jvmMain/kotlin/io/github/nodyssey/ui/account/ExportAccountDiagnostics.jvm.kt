package io.github.nodyssey.ui.account

import androidx.compose.runtime.Composable

/** Desktop has no platform share sheet; the dialog still offers an explicit clipboard copy. */
@Composable
internal actual fun rememberExportAccountDiagnostics(): suspend (String) -> Boolean = { false }
