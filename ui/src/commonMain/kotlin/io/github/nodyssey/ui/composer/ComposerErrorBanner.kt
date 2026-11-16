package io.github.nodyssey.ui.composer

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nodyssey.data.composer.UploadFailure
import io.github.nodyssey.ui.common.siteErrorRecovery
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_close
import io.github.nodyssey.ui.resources.action_retry
import io.github.nodyssey.ui.resources.composer_publish_challenge
import io.github.nodyssey.ui.resources.composer_publish_failed
import io.github.nodyssey.ui.resources.composer_publish_http
import io.github.nodyssey.ui.resources.composer_publish_login_required
import io.github.nodyssey.ui.resources.composer_publish_network_failed
import io.github.nodyssey.ui.resources.composer_publish_unknown
import io.github.plaza.core.net.SiteError
import io.github.plaza.designsys.component.InlineBanner
import io.github.plaza.designsys.component.StatusAction
import io.github.plaza.designsys.theme.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * Shared inline publish/upload failure banner for both the full-screen post editor and the reply
 * sheet so recovery actions stay anchored inside the editor even while the IME is visible.
 */
@Composable
internal fun ComposerErrorBanner(
    error: SiteError?,
    detail: String?,
    failedUploads: Int,
    uploadFailure: UploadFailure?,
    uploadErrorDetail: String?,
    onRetryPublish: () -> Unit,
    onRetryUploads: () -> Unit,
    onDismiss: () -> Unit,
    onSignIn: (() -> Unit)?,
    onVerify: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    fallbackErrorText: String? = null,
) {
    val message = when {
        error != null -> composerErrorMessage(error, detail, fallbackErrorText)
        failedUploads > 0 -> uploadFailureText(failedUploads, uploadFailure, uploadErrorDetail)
        else -> return
    }
    val action =
        if (error != null) {
            siteErrorRecovery(
                error = error,
                onVerify = onVerify?.let { verify ->
                    { url: String ->
                        onDismiss()
                        verify(url)
                    }
                },
                onSignIn = onSignIn?.let { signIn ->
                    {
                        onDismiss()
                        signIn()
                    }
                },
                onRetry = {
                    onDismiss()
                    onRetryPublish()
                },
            )
        } else {
            StatusAction(stringResource(Res.string.action_retry), onRetryUploads)
        }
    InlineBanner(
        text = message,
        modifier = modifier,
        action = action?.let {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = it.onClick, contentPadding = PaddingValues(horizontal = Spacing.md)) {
                        Text(it.label, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    if (error != null) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(Res.string.action_close),
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun composerErrorMessage(
    error: SiteError,
    detail: String?,
    fallbackErrorText: String?,
): String {
    val reason = when (error) {
        SiteError.Network -> stringResource(Res.string.composer_publish_network_failed)

        SiteError.LoginRequired -> stringResource(Res.string.composer_publish_login_required)

        is SiteError.Cloudflare -> stringResource(Res.string.composer_publish_challenge)

        is SiteError.Http -> {
            val status = stringResource(Res.string.composer_publish_http, error.statusCode)
            detail?.takeIf { it.isNotBlank() && it != error.toString() }?.let { "$status：$it" } ?: status
        }

        else -> detail?.takeIf { it.isNotBlank() } ?: fallbackErrorText ?: stringResource(Res.string.composer_publish_unknown)
    }
    return stringResource(Res.string.composer_publish_failed, reason)
}
