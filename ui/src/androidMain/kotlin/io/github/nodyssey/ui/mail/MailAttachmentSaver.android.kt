package io.github.nodyssey.ui.mail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberMailAttachmentSaver(
    canSave: () -> Boolean,
    onResult: (Boolean?) -> Unit,
): ((String, ByteArray) -> Boolean)? {
    val resolver = LocalContext.current.applicationContext.contentResolver
    val scope = rememberCoroutineScope()
    val allowed = rememberUpdatedState(canSave)
    val result = rememberUpdatedState(onResult)
    val pending = remember { PendingAttachment() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val bytes = pending.bytes
        pending.bytes = null
        if (uri == null || bytes == null || !allowed.value()) {
            result.value(null)
        } else {
            scope.launch {
                val written = withContext(Dispatchers.IO) {
                    runCatching { resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } != null }.getOrDefault(false)
                }
                result.value(written)
            }
        }
    }
    return { name, bytes ->
        pending.bytes = bytes
        runCatching { launcher.launch(name) }.fold(
            onSuccess = { true },
            onFailure = {
                pending.bytes = null
                result.value(false)
                false
            },
        )
    }
}

private class PendingAttachment(var bytes: ByteArray? = null)
