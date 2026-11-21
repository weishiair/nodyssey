@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class, ExperimentalComposeUiApi::class)

package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.window.ComposeUIViewController
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.nativeGlass
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIModalPresentationPageSheet
import platform.UIKit.UIPresentationController
import platform.UIKit.UISheetPresentationControllerDetent
import platform.UIKit.presentationController
import platform.UIKit.sheetPresentationController
import platform.darwin.NSObject

private class SheetDismissDelegate :
    NSObject(),
    UIAdaptivePresentationControllerDelegateProtocol {
    var onDismiss: () -> Unit = {}

    override fun presentationControllerDidDismiss(presentationController: UIPresentationController) {
        onDismiss()
    }
}

@Composable
actual fun AdaptiveModalSheet(
    onDismiss: () -> Unit,
    fallback: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!LocalPlazaLayers.current.nativeGlass) {
        fallback()
        return
    }
    // Captured so the sheet's own composition still sees the host's theme, strings and locals (R3).
    val captured = currentCompositionLocalContext
    val latestContent by rememberUpdatedState(content)
    val latestDismiss by rememberUpdatedState(onDismiss)
    val presenter = LocalUIViewController.current
    // UIKit holds the presentation delegate weakly; remember keeps it alive for the sheet's life.
    val delegate = remember { SheetDismissDelegate() }
    DisposableEffect(Unit) {
        delegate.onDismiss = { latestDismiss() }
        val sheet =
            ComposeUIViewController {
                CompositionLocalProvider(captured) { latestContent() }
            }
        sheet.modalPresentationStyle = UIModalPresentationPageSheet
        sheet.sheetPresentationController?.let { ctl ->
            ctl.detents =
                listOf(
                    UISheetPresentationControllerDetent.mediumDetent(),
                    UISheetPresentationControllerDetent.largeDetent(),
                )
            ctl.prefersGrabberVisible = true
            ctl.prefersScrollingExpandsWhenScrolledToEdge = true
        }
        var top = presenter
        while (top.presentedViewController != null) {
            top = top.presentedViewController!!
        }
        // presentationController only exists once the presentation starts; the delegate that
        // reports swipe-down dismissal goes on in the completion, when it is there to take it.
        top.presentViewController(sheet, animated = true) {
            sheet.presentationController?.delegate = delegate
        }
        onDispose {
            sheet.dismissViewControllerAnimated(true, completion = null)
        }
    }
}
