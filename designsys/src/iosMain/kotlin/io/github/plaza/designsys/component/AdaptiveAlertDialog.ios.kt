@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package io.github.plaza.designsys.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.uikit.LocalUIViewController
import io.github.plaza.designsys.theme.LocalPlazaDarkTheme
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyle
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertActionStyleDestructive
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIViewController
import platform.UIKit.transitionCoordinator

@Composable
actual fun AdaptiveAlertDialog(
    title: String,
    message: String?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String?,
    destructive: Boolean,
    prominentConfirm: Boolean,
    icon: ImageVector?,
) {
    val host = LocalUIViewController.current
    val accent = MaterialTheme.colorScheme.primary.toUIColor()
    val dark = LocalPlazaDarkTheme.current
    val confirm = rememberUpdatedState(onConfirm)
    val dismiss = rememberUpdatedState(onDismiss)
    LaunchedEffect(title, message, confirmLabel, dismissLabel, destructive) {
        val alert =
            UIAlertController.alertControllerWithTitle(title, message, UIAlertControllerStyleAlert)
        alert.view.tintColor = accent
        alert.overrideUserInterfaceStyle = interfaceStyle(dark)
        val handled = Handled()
        for (button in alertButtons(confirmLabel, dismissLabel, destructive)) {
            val style: UIAlertActionStyle =
                when (button.role) {
                    AlertButtonRole.Cancel -> UIAlertActionStyleCancel
                    AlertButtonRole.Destructive -> UIAlertActionStyleDestructive
                    AlertButtonRole.Default -> UIAlertActionStyleDefault
                }
            // UIAlertAction retains what its handler captures; the flag and the two State objects own
            // no view, while capturing the alert would cycle the controller back into its own action.
            val action =
                UIAlertAction.actionWithTitle(button.label, style) {
                    handled.value = true
                    if (button.confirms) confirm.value() else dismiss.value()
                }
            alert.addAction(action)
            if (button.preferred) alert.preferredAction = action
        }
        // Presenting a second alert while the previous one is still animating away is refused
        // silently by UIKit, so wait for any in-flight transition on the topmost controller.
        var presenter = topmostPresented(host)
        while (
            presenter.isBeingPresented() ||
            presenter.isBeingDismissed() ||
            presenter.transitionCoordinator != null
        ) {
            delay(16)
            presenter = topmostPresented(host)
        }
        presenter.presentViewController(alert, animated = true, completion = null)
        try {
            awaitCancellation()
        } finally {
            if (!handled.value && alert.presentingViewController != null) {
                alert.dismissViewControllerAnimated(true, completion = null)
            }
        }
    }
}

private class Handled {
    var value = false
}

private fun topmostPresented(host: UIViewController): UIViewController {
    var controller = host
    while (true) {
        controller = controller.presentedViewController ?: return controller
    }
}
