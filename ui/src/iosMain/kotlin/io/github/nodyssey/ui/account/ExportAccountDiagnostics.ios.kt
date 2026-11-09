@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.nodyssey.ui.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.nodyssey.ui.common.topmostViewController
import io.github.plaza.core.runCatchingExceptCancellation
import io.github.plaza.core.toNSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.popoverPresentationController

@Composable
internal actual fun rememberExportAccountDiagnostics(): suspend (String) -> Boolean = remember {
    { report ->
        runCatchingExceptCancellation {
            val file = NSURL.fileURLWithPath(NSTemporaryDirectory() + "nodyssey-account-diagnostics.txt")
            check(report.encodeToByteArray().toNSData().writeToURL(file, atomically = true))
            val host = checkNotNull(topmostViewController())
            val sheet = UIActivityViewController(activityItems = listOf(file), applicationActivities = null)
            sheet.popoverPresentationController?.sourceView = host.view
            host.presentViewController(sheet, animated = true, completion = null)
        }.isSuccess
    }
}
