@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.plaza.designsys.component

import platform.UIKit.UIAlertController
import platform.UIKit.UIViewController
import platform.UIKit.transitionCoordinator

/** A nested sheet may present from another sheet, but must wait for an alert to close first. */
internal fun UIViewController.modalPresenter(): UIViewController? {
    var candidate = this
    while (true) {
        if (candidate.view.window == null || candidate.isBeingPresented() || candidate.isBeingDismissed() || candidate.transitionCoordinator != null) return null
        val presented = candidate.presentedViewController ?: break
        candidate = presented
    }
    return candidate.takeUnless { it is UIAlertController }
}
