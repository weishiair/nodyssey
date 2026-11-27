@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class, ExperimentalComposeUiApi::class)

package io.github.plaza.designsys.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.LocalTonalElevationEnabled
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import io.github.plaza.designsys.richtext.LocalImageLongPress
import io.github.plaza.designsys.richtext.LocalRichTextAppearance
import io.github.plaza.designsys.richtext.LocalStickerSizing
import io.github.plaza.designsys.theme.LocalEinkMode
import io.github.plaza.designsys.theme.LocalFrostedGlass
import io.github.plaza.designsys.theme.LocalLiquidGlassBackdrop
import io.github.plaza.designsys.theme.LocalOneHandMode
import io.github.plaza.designsys.theme.LocalOneHandModeAvailable
import io.github.plaza.designsys.theme.LocalPlazaDarkTheme
import io.github.plaza.designsys.theme.LocalPlazaExtraColors
import io.github.plaza.designsys.theme.LocalPlazaFontScale
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.LocalReadableWidthSpec
import io.github.plaza.designsys.theme.nativeControls
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIModalPresentationPageSheet
import platform.UIKit.UIPresentationController
import platform.UIKit.UISheetPresentationControllerDetent
import platform.UIKit.UIViewController
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
    containerColor: Color?,
    fitContent: Boolean,
    content: @Composable () -> Unit,
) {
    if (!nativeControls) {
        fallback()
        return
    }
    // Forward only theme and app-environment locals rather than currentCompositionLocalContext:
    // capturing the entire context overwrites the sheet's own LocalUIViewController,
    // LocalSoftwareKeyboardController, LocalFocusManager, LocalWindowInfo and UIKit interop host.
    val colorScheme = MaterialTheme.colorScheme
    val motionScheme = MaterialTheme.motionScheme
    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes
    val layers = LocalPlazaLayers.current
    val extraColors = LocalPlazaExtraColors.current
    val dark = LocalPlazaDarkTheme.current
    val fontScale = LocalPlazaFontScale.current
    val oneHandMode = LocalOneHandMode.current
    val oneHandModeAvailable = LocalOneHandModeAvailable.current
    val einkMode = LocalEinkMode.current
    val frostedGlass = LocalFrostedGlass.current
    val tonalElevation = LocalTonalElevationEnabled.current
    val rippleConfig = LocalRippleConfiguration.current
    val backdrop = LocalLiquidGlassBackdrop.current
    val linkPrefetcher = LocalLinkPrefetcher.current
    val imageLongPress = LocalImageLongPress.current
    val richTextAppearance = LocalRichTextAppearance.current
    val stickerSizing = LocalStickerSizing.current
    val readableWidthSpec = LocalReadableWidthSpec.current
    val uriHandler = LocalUriHandler.current
    val sheetColor = containerColor ?: layers.page

    val latestContent by rememberUpdatedState(content)
    val latestDismiss by rememberUpdatedState(onDismiss)
    val latestColorScheme by rememberUpdatedState(colorScheme)
    val latestMotionScheme by rememberUpdatedState(motionScheme)
    val latestTypography by rememberUpdatedState(typography)
    val latestShapes by rememberUpdatedState(shapes)
    val latestLayers by rememberUpdatedState(layers)
    val latestExtraColors by rememberUpdatedState(extraColors)
    val latestDark by rememberUpdatedState(dark)
    val latestFontScale by rememberUpdatedState(fontScale)
    val latestOneHandMode by rememberUpdatedState(oneHandMode)
    val latestOneHandModeAvailable by rememberUpdatedState(oneHandModeAvailable)
    val latestEinkMode by rememberUpdatedState(einkMode)
    val latestFrostedGlass by rememberUpdatedState(frostedGlass)
    val latestTonalElevation by rememberUpdatedState(tonalElevation)
    val latestRippleConfig by rememberUpdatedState(rippleConfig)
    val latestBackdrop by rememberUpdatedState(backdrop)
    val latestLinkPrefetcher by rememberUpdatedState(linkPrefetcher)
    val latestImageLongPress by rememberUpdatedState(imageLongPress)
    val latestRichTextAppearance by rememberUpdatedState(richTextAppearance)
    val latestStickerSizing by rememberUpdatedState(stickerSizing)
    val latestReadableWidthSpec by rememberUpdatedState(readableWidthSpec)
    val latestUriHandler by rememberUpdatedState(uriHandler)
    val latestSheetColor by rememberUpdatedState(sheetColor)
    val latestFitContent by rememberUpdatedState(fitContent)

    val presenter = LocalUIViewController.current
    // UIKit holds the presentation delegate weakly; remember keeps it alive for the sheet's life.
    val delegate = remember { SheetDismissDelegate() }
    var sheetController by remember { mutableStateOf<UIViewController?>(null) }

    SideEffect {
        sheetController?.let { controller ->
            controller.view.backgroundColor = sheetColor.toUIColor()
            controller.overrideUserInterfaceStyle = interfaceStyle(dark)
        }
    }

    DisposableEffect(Unit) {
        delegate.onDismiss = { latestDismiss() }
        val sheet =
            ComposeUIViewController(
                configure = {
                    // Sheets with imePadding handle keyboard insets themselves; UISheetPresentationController
                    // also expands to largeDetent when a field inside becomes first responder.
                    onFocusBehavior = OnFocusBehavior.DoNothing
                },
            ) {
                MaterialExpressiveTheme(
                    colorScheme = latestColorScheme,
                    motionScheme = latestMotionScheme,
                    typography = latestTypography,
                    shapes = latestShapes,
                ) {
                    CompositionLocalProvider(
                        LocalPlazaLayers provides latestLayers,
                        LocalPlazaExtraColors provides latestExtraColors,
                        LocalPlazaDarkTheme provides latestDark,
                        LocalPlazaFontScale provides latestFontScale,
                        LocalOneHandMode provides latestOneHandMode,
                        LocalOneHandModeAvailable provides latestOneHandModeAvailable,
                        LocalEinkMode provides latestEinkMode,
                        LocalFrostedGlass provides latestFrostedGlass,
                        LocalTonalElevationEnabled provides latestTonalElevation,
                        LocalRippleConfiguration provides latestRippleConfig,
                        LocalLiquidGlassBackdrop provides latestBackdrop,
                        LocalLinkPrefetcher provides latestLinkPrefetcher,
                        LocalImageLongPress provides latestImageLongPress,
                        LocalRichTextAppearance provides latestRichTextAppearance,
                        LocalStickerSizing provides latestStickerSizing,
                        LocalReadableWidthSpec provides latestReadableWidthSpec,
                        LocalUriHandler provides latestUriHandler,
                    ) {
                        Surface(
                            color = latestSheetColor,
                            contentColor = latestColorScheme.onSurface,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            // Clear UIKit's prefersGrabberVisible bar (matches PlazaSheetDefaults.DragHandle's 22dp).
                            Box(Modifier.fillMaxSize().padding(top = NATIVE_SHEET_GRABBER_CLEARANCE)) {
                                if (latestFitContent) {
                                    FitSheetToContent { latestContent() }
                                } else {
                                    latestContent()
                                }
                            }
                        }
                    }
                }
            }
        sheet.view.backgroundColor = latestSheetColor.toUIColor()
        sheet.overrideUserInterfaceStyle = interfaceStyle(latestDark)
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
        sheetController = sheet
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
            sheetController = null
            delegate.onDismiss = {}
            if (sheet.presentingViewController != null) {
                sheet.dismissViewControllerAnimated(true, completion = null)
            }
        }
    }
}

/** Fits the presenting sheet to the measured height of short content. */
@Composable
private fun FitSheetToContent(content: @Composable () -> Unit) {
    val sheet = LocalUIViewController.current.sheetPresentationController
    if (sheet == null) {
        content()
        return
    }

    val density = LocalDensity.current.density
    var contentTop by remember { mutableStateOf<Float?>(null) }
    var contentHeight by remember { mutableIntStateOf(0) }
    val height = contentTop?.takeIf { contentHeight > 0 }?.let { (it + contentHeight) / density }
    val latestHeight by rememberUpdatedState(height)
    val detent = remember(sheet) {
        UISheetPresentationControllerDetent.customDetentWithIdentifier("content") { context ->
            // The detent excludes UIKit's bottom safe area. The measured top includes the grabber.
            val measured = latestHeight?.toDouble() ?: 0.0
            measured.coerceAtMost(context?.maximumDetentValue ?: measured)
        }
    }
    LaunchedEffect(sheet, height) {
        if (height == null) return@LaunchedEffect
        if (sheet.selectedDetentIdentifier == detent.identifier) {
            sheet.animateChanges { sheet.invalidateDetents() }
        } else {
            sheet.animateChanges {
                sheet.detents = listOf(detent)
                sheet.selectedDetentIdentifier = detent.identifier
            }
        }
    }

    // Measure the viewport origin and the full content independently. Scroll when the available
    // height is smaller than the content, for example in landscape or at large text sizes.
    Box(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { contentTop = it.positionInRoot().y },
    ) {
        Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().onSizeChanged { contentHeight = it.height }) {
                content()
            }
        }
    }
}

private val NATIVE_SHEET_GRABBER_CLEARANCE = 22.dp
