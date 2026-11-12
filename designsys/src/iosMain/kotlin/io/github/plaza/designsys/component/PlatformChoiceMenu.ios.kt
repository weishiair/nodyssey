@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import io.github.plaza.designsys.resources.Res
import io.github.plaza.designsys.resources.action_cancel
import kotlinx.cinterop.useContents
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertActionStyleDestructive
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleActionSheet
import platform.UIKit.UIPopoverPresentationController
import platform.UIKit.UIPopoverPresentationControllerDelegateProtocol
import platform.UIKit.UIPresentationController
import platform.UIKit.popoverPresentationController
import platform.UIKit.presentationController
import platform.darwin.NSObject

@Composable
actual fun platformChoiceMenu(expanded: Boolean, onDismissRequest: () -> Unit, actions: List<PlatformBarAction>): Boolean {
    if (!expanded) return true
    val owner = LocalUIViewController.current
    val onDismiss = rememberUpdatedState(onDismissRequest)
    val cancel = stringResource(Res.string.action_cancel)
    val latestActions = rememberUpdatedState(actions)
    val alert = remember(owner) {
        UIAlertController.alertControllerWithTitle(null, null, UIAlertControllerStyleActionSheet).apply {
            popoverPresentationController?.apply {
                sourceView = owner.view
                sourceRect = owner.view.bounds.useContents { CGRectMake(size.width / 2, size.height / 2, 1.0, 1.0) }
                permittedArrowDirections = 0uL
            }
        }
    }
    val presentation = remember(alert) {
        PlatformPresentationLifecycle(
            present = { done -> checkNotNull(owner.modalPresenter()).presentViewController(alert, animated = true, completion = done) },
            dismiss = { done -> alert.dismissViewControllerAnimated(true, completion = done) },
            onSystemDismissed = { onDismiss.value() },
        )
    }
    val nativeActions = remember(alert, presentation) {
        actions.mapIndexed { index, operation ->
            UIAlertAction.actionWithTitle(
                if (operation.selected) "✓ ${operation.label}" else operation.label,
                if (operation.destructive) UIAlertActionStyleDestructive else UIAlertActionStyleDefault,
            ) {
                val selected = latestActions.value.getOrNull(index)
                // UIAlertController dismisses itself for a selection. Mark it closed before
                // removing the composition, otherwise disposal starts a second dismissal.
                presentation.systemDismissed()
                selected?.takeIf { it.enabled }?.onClick?.invoke()
            }.also { alert.addAction(it) }
        }.also {
            alert.addAction(UIAlertAction.actionWithTitle(cancel, UIAlertActionStyleCancel) { presentation.systemDismissed() })
        }
    }
    SideEffect {
        nativeActions.forEachIndexed { index, action ->
            val operation = actions.getOrNull(index)
            action.enabled = operation?.enabled == true
        }
    }
    val delegate = remember(presentation) {
        object : NSObject(), UIPopoverPresentationControllerDelegateProtocol {
            override fun presentationControllerDidDismiss(presentationController: UIPresentationController) = presentation.systemDismissed()
            override fun popoverPresentationControllerDidDismissPopover(popoverPresentationController: UIPopoverPresentationController) = presentation.systemDismissed()
        }
    }
    LaunchedEffect(presentation) {
        while (owner.modalPresenter() == null) delay(16)
        alert.presentationController?.delegate = delegate
        alert.popoverPresentationController?.delegate = delegate
        presentation.show()
    }
    DisposableEffect(presentation) { onDispose { presentation.dispose() } }
    return true
}
