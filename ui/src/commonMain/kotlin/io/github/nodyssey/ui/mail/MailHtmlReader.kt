package io.github.nodyssey.ui.mail

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import io.github.nodyssey.data.mail.MailHtmlBody
import io.github.nodyssey.data.mail.MailRemoteImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal expect fun MailHtmlReader(
    body: MailHtmlBody,
    images: Map<String, MailRemoteImage>,
    onOpenWebsite: (String) -> Unit,
    modifier: Modifier,
)

@Composable
internal fun MailHtmlBody.rememberDocument(images: Map<String, MailRemoteImage>, includeStyles: Boolean = true): String? {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return key(this) {
        produceState<String?>(null, images, dark, includeStyles) {
            value = withContext(Dispatchers.Default) { document(images, dark, includeStyles) }
        }.value
    }
}
