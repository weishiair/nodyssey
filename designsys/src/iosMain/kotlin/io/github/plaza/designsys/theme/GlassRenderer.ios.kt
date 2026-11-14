package io.github.plaza.designsys.theme

import platform.UIKit.UIDevice

// Keep old systems on solid controls; a transparent fallback is not Apple's Liquid Glass.
internal actual val platformGlassRenderer: GlassRenderer =
    if (UIDevice.currentDevice.systemVersion.substringBefore('.').toInt() >= 26) {
        GlassRenderer.Native
    } else {
        GlassRenderer.Opaque
    }
