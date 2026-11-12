@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.plaza.designsys.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.window.ComposeUIViewController
import io.github.plaza.designsys.theme.LocalEinkMode
import io.github.plaza.designsys.theme.LocalFrostedGlass
import io.github.plaza.designsys.theme.LocalOneHandMode
import io.github.plaza.designsys.theme.LocalOneHandModeAvailable
import io.github.plaza.designsys.theme.LocalPlazaDarkTheme
import io.github.plaza.designsys.theme.LocalPlazaExtraColors
import io.github.plaza.designsys.theme.LocalPlazaFontScale
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.Spacing
import kotlinx.coroutines.delay
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIColor
import platform.UIKit.UIModalPresentationPageSheet
import platform.UIKit.UIPresentationController
import platform.UIKit.UISheetPresentationControllerDetent
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.modalInPresentation
import platform.UIKit.sheetPresentationController
import platform.darwin.NSObject

@Composable
internal actual fun nativeModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    expandFully: Boolean,
    dismissEnabled: Boolean,
    showDragHandle: Boolean,
    content: @Composable ColumnScope.() -> Unit,
): Boolean {
    val owner = LocalUIViewController.current
    val onDismiss = rememberUpdatedState(onDismissRequest)
    val canDismiss = rememberUpdatedState(dismissEnabled)
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes
    val motion = MaterialTheme.motionScheme
    val darkTheme = LocalPlazaDarkTheme.current
    // Copy app/theme values explicitly. Copying currentCompositionLocalContext would transplant
    // the presenting window's focus, keyboard, lifecycle and insets into this separate controller.
    val values = LocalPlatformPresentationValues.current() + arrayOf(
        LocalPlazaLayers provides LocalPlazaLayers.current,
        LocalContentColor provides colors.onSurface,
        LocalPlazaDarkTheme provides darkTheme,
        LocalPlazaExtraColors provides LocalPlazaExtraColors.current,
        LocalPlazaFontScale provides LocalPlazaFontScale.current,
        LocalEinkMode provides LocalEinkMode.current,
        LocalFrostedGlass provides false,
        LocalOneHandMode provides false,
        LocalOneHandModeAvailable provides false,
        LocalPlatformPresentationValues provides LocalPlatformPresentationValues.current,
        LocalPlatformTopAppBarHost provides null,
    )
    val latestContent = rememberUpdatedState<@Composable () -> Unit> {
        MaterialExpressiveTheme(colorScheme = colors, typography = typography, shapes = shapes, motionScheme = motion) {
            CompositionLocalProvider(*values) {
                Column(modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = Spacing.xl)) { content() }
            }
        }
    }
    val controller = remember(owner) {
        // safeDrawing includes the keyboard. Let that padding own avoidance, rather than also
        // shifting the scene for its focused field (which can otherwise chase the editor's IME padding).
        ComposeUIViewController(configure = { onFocusBehavior = OnFocusBehavior.DoNothing }) { latestContent.value() }.apply {
            modalPresentationStyle = UIModalPresentationPageSheet
        }
    }
    val presentation = remember(owner, controller) {
        PlatformPresentationLifecycle(
            present = { done ->
                checkNotNull(owner.modalPresenter()).presentViewController(controller, animated = true, completion = done)
            },
            dismiss = { done -> controller.dismissViewControllerAnimated(true, completion = done) },
            onSystemDismissed = { onDismiss.value() },
        )
    }
    val delegate = remember(presentation) {
        object : NSObject(), UIAdaptivePresentationControllerDelegateProtocol {
            override fun presentationControllerShouldDismiss(presentationController: UIPresentationController): Boolean = canDismiss.value
            override fun presentationControllerDidDismiss(presentationController: UIPresentationController) = presentation.systemDismissed()
        }
    }
    SideEffect {
        controller.view.backgroundColor = UIColor(red = colors.background.red.toDouble(), green = colors.background.green.toDouble(), blue = colors.background.blue.toDouble(), alpha = 1.0)
        controller.overrideUserInterfaceStyle = if (darkTheme) UIUserInterfaceStyle.UIUserInterfaceStyleDark else UIUserInterfaceStyle.UIUserInterfaceStyleLight
        controller.modalInPresentation = !dismissEnabled
        controller.sheetPresentationController?.apply {
            detents = if (expandFully) {
                listOf(UISheetPresentationControllerDetent.largeDetent())
            } else {
                listOf(
                    UISheetPresentationControllerDetent.mediumDetent(),
                    UISheetPresentationControllerDetent.largeDetent(),
                )
            }
            prefersGrabberVisible = showDragHandle
            this.delegate = delegate
        }
    }
    LaunchedEffect(presentation) {
        // A sheet can be requested by a newly pushed page before its view joins a window.
        while (owner.modalPresenter() == null) delay(16)
        presentation.show()
    }
    DisposableEffect(presentation) { onDispose { presentation.dispose() } }
    return true
}
