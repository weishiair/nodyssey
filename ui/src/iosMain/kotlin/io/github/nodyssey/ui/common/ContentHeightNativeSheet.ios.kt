package io.github.nodyssey.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UISheetPresentationControllerDetent
import platform.UIKit.sheetPresentationController

@OptIn(ExperimentalComposeUiApi::class, ExperimentalForeignApi::class)
@Composable
internal actual fun ContentHeightNativeSheet(content: @Composable () -> Unit) {
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
