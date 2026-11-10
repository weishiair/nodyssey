package io.github.nodyssey.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.nodyssey.ui.common.MediumButton
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.accounts_retry_save
import io.github.nodyssey.ui.resources.accounts_save_failed
import io.github.nodyssey.ui.resources.accounts_title
import io.github.nodyssey.ui.resources.action_close
import io.github.nodyssey.ui.resources.sign_in_submitting
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountSaveFailureScreen(saving: Boolean, onRetry: () -> Unit, onClose: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(Res.string.accounts_title)) },
            navigationIcon = {
                TextButton(onClick = onClose, enabled = !saving) { Text(stringResource(Res.string.action_close)) }
            },
        )
    }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().readableWidth().padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(stringResource(Res.string.accounts_save_failed))
            MediumButton(onClick = onRetry, enabled = !saving) {
                Text(stringResource(if (saving) Res.string.sign_in_submitting else Res.string.accounts_retry_save))
            }
        }
    }
}
