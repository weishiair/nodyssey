package io.github.nodyssey.ui.account

import android.content.ClipData
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import io.github.plaza.core.runCatchingExceptCancellation
import java.io.File

@Composable
internal actual fun rememberExportAccountDiagnostics(): suspend (String) -> Boolean {
    val context = LocalContext.current
    return remember(context) {
        { report ->
            runCatchingExceptCancellation {
                val directory = File(context.cacheDir, "share").apply { mkdirs() }
                val file = File(directory, "nodyssey-account-diagnostics.txt").apply { writeText(report) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri(null, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }.isSuccess
        }
    }
}
