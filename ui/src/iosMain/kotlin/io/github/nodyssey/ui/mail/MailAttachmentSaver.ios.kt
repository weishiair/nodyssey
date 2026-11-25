@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package io.github.nodyssey.ui.mail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import io.github.nodyssey.ui.common.topmostViewController
import io.github.plaza.core.toNSData
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.writeToURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.darwin.NSObject

@Composable
internal actual fun rememberMailAttachmentSaver(
    canSave: () -> Boolean,
    onResult: (Boolean?) -> Unit,
): ((String, ByteArray) -> Boolean)? {
    val result = rememberUpdatedState(onResult)
    val delegate = remember { MailAttachmentPicker { result.value(it) } }
    DisposableEffect(delegate) { onDispose { delegate.cleanUp() } }
    return save@{ name, bytes ->
        if (!canSave()) return@save false
        val presenter = topmostViewController()
        if (presenter == null || !delegate.present(presenter, name, bytes)) {
            result.value(false)
            return@save false
        }
        true
    }
}

private class MailAttachmentPicker(
    private val finished: (Boolean?) -> Unit,
) : NSObject(),
    UIDocumentPickerDelegateProtocol {
    private val files = NSFileManager.defaultManager
    private var directory: NSURL? = null
    private var picker: UIDocumentPickerViewController? = null

    fun present(presenter: platform.UIKit.UIViewController, name: String, bytes: ByteArray): Boolean {
        val temp = NSURL.fileURLWithPath(NSTemporaryDirectory(), isDirectory = true)
        val folder = temp.URLByAppendingPathComponent(NSUUID().UUIDString()) ?: return false
        val file = folder.URLByAppendingPathComponent(name) ?: return false
        if (!files.createDirectoryAtURL(folder, withIntermediateDirectories = false, attributes = null, error = null)) return false
        directory = folder
        if (!bytes.toNSData().writeToURL(file, atomically = true)) {
            cleanUp()
            return false
        }
        val documentPicker = UIDocumentPickerViewController(forExportingURLs = listOf(file), asCopy = true)
        picker = documentPicker
        documentPicker.delegate = this
        presenter.presentViewController(documentPicker, animated = true, completion = null)
        return true
    }

    fun cleanUp() {
        picker?.dismissViewControllerAnimated(false, completion = null)
        picker = null
        directory?.let { files.removeItemAtURL(it, error = null) }
        directory = null
    }

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        picker = null
        cleanUp()
        finished(didPickDocumentsAtURLs.isNotEmpty())
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        picker = null
        cleanUp()
        finished(null)
    }
}
