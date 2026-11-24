package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable

// Android switches and pull-to-refresh never produced haptics; a no-op keeps that behaviour.
private object NoopPlatformHaptics : PlatformHaptics {
    override fun toggle() {}

    override fun refreshTriggered() {}
}

@Composable
actual fun rememberPlatformHaptics(): PlatformHaptics = NoopPlatformHaptics
