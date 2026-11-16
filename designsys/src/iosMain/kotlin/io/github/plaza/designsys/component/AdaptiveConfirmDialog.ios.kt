@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import io.github.plaza.designsys.nativeglass.plaza_present_confirm_alert
import io.github.plaza.designsys.theme.LocalPlazaDarkTheme
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.nativeChrome
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

@Composable
actual fun AdaptiveConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructiveConfirm: Boolean,
    destructiveDismiss: Boolean,
    dismissIsCancel: Boolean,
    fallback: @Composable () -> Unit,
) {
    if (!LocalPlazaLayers.current.nativeChrome) {
        fallback()
        return
    }
    val dark = LocalPlazaDarkTheme.current
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    DisposableEffect(
        title,
        message,
        confirmLabel,
        dismissLabel,
        destructiveConfirm,
        destructiveDismiss,
        dismissIsCancel,
        dark,
    ) {
        val host = topmostWindowViewController()
        if (host == null) {
            return@DisposableEffect onDispose {}
        }
        var resolved = false
        val alert = plaza_present_confirm_alert(
            host = host,
            title = title,
            message = message,
            confirmTitle = confirmLabel,
            confirmDestructive = destructiveConfirm,
            dismissTitle = dismissLabel,
            dismissDestructive = destructiveDismiss,
            dismissCancel = dismissIsCancel,
            dark = dark,
            onConfirm = {
                resolved = true
                currentOnConfirm()
            },
            onDismiss = {
                resolved = true
                currentOnDismiss()
            },
        )
        onDispose {
            if (!resolved && alert.presentingViewController != null) {
                alert.dismissViewControllerAnimated(false, completion = null)
            }
        }
    }
}

private fun topmostWindowViewController(): UIViewController? {
    val scene = UIApplication.sharedApplication.connectedScenes.firstOrNull { it is UIWindowScene }
    val window =
        (scene as? UIWindowScene)?.windows?.firstOrNull { window ->
            (window as? UIWindow)?.isKeyWindow() == true
        } as? UIWindow
    var controller = window?.rootViewController ?: return null
    while (true) {
        controller = controller.presentedViewController ?: return controller
    }
}
