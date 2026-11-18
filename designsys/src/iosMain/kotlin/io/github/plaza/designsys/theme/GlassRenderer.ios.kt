@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.plaza.designsys.theme

import io.github.plaza.designsys.nativeglass.plaza_native_glass_available

// Keep old systems on solid controls; a transparent fallback is not Apple's Liquid Glass.
internal actual val platformGlassRenderer: GlassRenderer =
    if (plaza_native_glass_available()) {
        GlassRenderer.Native
    } else {
        GlassRenderer.Opaque
    }
