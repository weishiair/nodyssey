package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UISelectionFeedbackGenerator

private class IosPlatformHaptics : PlatformHaptics {
    // Prepared once: the first fire is silent without it on a cold generator.
    private val selection =
        UISelectionFeedbackGenerator().apply { prepare() }
    private val impact =
        UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium).apply { prepare() }

    override fun toggle() {
        selection.selectionChanged()
    }

    override fun refreshTriggered() {
        impact.impactOccurred()
    }
}

@Composable
actual fun rememberPlatformHaptics(): PlatformHaptics = remember { IosPlatformHaptics() }
