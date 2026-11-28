@file:OptIn(ExperimentalForeignApi::class)

package io.github.nodyssey.ui.composer

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectZero
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIDevice
import platform.UIKit.UILayoutGuide
import platform.UIKit.UIUserInterfaceIdiomPad
import platform.UIKit.UIView

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun Modifier.replySheetKeyboardPadding(): Modifier {
    if (UIDevice.currentDevice.userInterfaceIdiom != UIUserInterfaceIdiomPad) return imePadding()
    val sheet = LocalUIViewController.current.view

    // Compose 1.13.0-alpha01 measures from the top of a floating keyboard to the bottom of
    // the sheet. That can consume the entire editor. UIKit's guide tracks only docked keyboards
    // by default. Use it here until Compose's IME inset handles floating keyboards.
    var bottomPadding by remember(sheet) { mutableStateOf(0.dp) }
    DisposableEffect(sheet) {
        val stopObserving = observeReplyKeyboardInset(sheet, sheet.keyboardLayoutGuide) { bottomPadding = it.dp }
        onDispose { stopObserving() }
    }
    return padding(bottom = bottomPadding)
}

internal fun observeReplyKeyboardInset(
    sheet: UIView,
    guide: UILayoutGuide,
    onInset: (Double) -> Unit,
): () -> Unit {
    val probe = ReplyKeyboardInsetView(onInset)
    probe.userInteractionEnabled = false
    probe.translatesAutoresizingMaskIntoConstraints = false
    sheet.addSubview(probe)
    val constraints =
        listOf(
            probe.leadingAnchor.constraintEqualToAnchor(sheet.leadingAnchor),
            probe.trailingAnchor.constraintEqualToAnchor(sheet.trailingAnchor),
            probe.topAnchor.constraintEqualToAnchor(guide.topAnchor, constant = -PROBE_MIN_HEIGHT),
            probe.bottomAnchor.constraintEqualToAnchor(sheet.bottomAnchor),
        )
    NSLayoutConstraint.activateConstraints(constraints)
    return {
        NSLayoutConstraint.deactivateConstraints(constraints)
        probe.removeFromSuperview()
    }
}

private class ReplyKeyboardInsetView(private val onInset: (Double) -> Unit) : UIView(frame = CGRectZero.readValue()) {
    override fun layoutSubviews() {
        super.layoutSubviews()
        onInset((bounds.useContents { size.height } - PROBE_MIN_HEIGHT).coerceAtLeast(0.0))
    }
}

// A zero-height view can skip layoutSubviews when the keyboard goes away.
private const val PROBE_MIN_HEIGHT = 1.0
