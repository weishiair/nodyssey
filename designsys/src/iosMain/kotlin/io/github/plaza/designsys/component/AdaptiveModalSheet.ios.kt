@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import io.github.plaza.designsys.theme.LocalPlazaLayers
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIPresentationController
import platform.UIKit.UISheetPresentationControllerDetent
import platform.UIKit.UIViewController
import platform.darwin.NSObject

private class SheetHostController : UIViewController(nibName = null, bundle = null) {
    var onDismiss: (() -> Unit)? = null

    private val delegate =
        object : NSObject(), UIAdaptivePresentationControllerDelegateProtocol {
            override fun presentationControllerDidDismiss(presentationController: UIPresentationController) {
                onDismiss?.invoke()
            }
        }

    init {
        modalPresentationStyle = platform.UIKit.UIModalPresentationPageSheet
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        presentationController?.delegate = delegate
        val sheet = sheetPresentationController
        if (sheet != null) {
            sheet.detents =
                listOf(
                    UISheetPresentationControllerDetent.mediumDetent(),
                    UISheetPresentationControllerDetent.largeDetent(),
                )
            sheet.prefersGrabberVisible = true
            sheet.prefersScrollingExpandsWhenScrolledToEdge = true
        }
    }
}

@Composable
actual fun AdaptiveModalSheet(
    onDismiss: () -> Unit,
    fallback: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val layers = LocalPlazaLayers.current
    if (!layers.nativeGlass) {
        fallback()
        return
    }
    val captured = currentCompositionLocalContext
    val latestContent by rememberUpdatedState(content)
    val latestDismiss by rememberUpdatedState(onDismiss)
    val host = remember { SheetHostController() }
    DisposableEffect(Unit) {
        val holder = NSMutableReference()
        host.onDismiss = { latestDismiss() }
        val compose =
            ComposeUIViewController {
                CompositionLocalProvider(captured) { latestContent() }
            }
        host.addChildViewController(compose)
        compose.view.translatesAutoresizingMaskIntoConstraints = false
        host.view.addSubview(compose.view)
        compose.didMoveToParentViewController(host)
        compose.view.backgroundColor = UIColor.clearColor
        // Find the top-most presented controller.
        var top = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (top?.presentedViewController != null) {
            top = top.presentedViewController
        }
        top?.presentViewController(host, animated = true, completion = null)
        onDispose {
            host.dismissViewControllerAnimated(true, completion = null)
            compose.removeFromParentViewController()
            compose.view.removeFromSuperview()
        }
    }
}

private class NSMutableReference
