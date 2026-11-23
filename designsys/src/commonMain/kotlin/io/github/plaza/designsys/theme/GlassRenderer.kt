package io.github.plaza.designsys.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/** The material renderer, separate from the shared glass theme's colours and reading styles. */
internal enum class GlassRenderer {
    Backdrop,
    Native,
    Opaque,
}

internal expect val platformGlassRenderer: GlassRenderer

/** Whether this platform's own toolkit draws bars, floating controls, menus and sheets. */
internal expect val platformNativeControls: Boolean

/**
 * Whether UIKit draws the bars, floating controls, menus and sheets: on every supported iOS, each in
 * the look that system gives them. Liquid Glass ([nativeGlassAvailable]) is iOS 26's material on
 * top of these controls, not the condition for them. 墨水屏 keeps the Compose forms, which draw without blur or motion.
 */
val nativeControls: Boolean
    @Composable
    @ReadOnlyComposable
    get() = platformNativeControls && !LocalEinkMode.current

/** Only the Compose renderer captures content and draws its own highlights and refraction. */
val PlazaLayers.backdropGlass: Boolean
    get() = frostedGlass && platformGlassRenderer == GlassRenderer.Backdrop

/** Whether UIKit can render Liquid Glass on the current system. */
val nativeGlassAvailable: Boolean
    get() = platformGlassRenderer == GlassRenderer.Native

/** Whether 毛玻璃质感 is the reader's choice here; where UIKit draws Liquid Glass the system controls always do. */
val frostedGlassConfigurable: Boolean
    get() = !nativeGlassAvailable

/** The 毛玻璃质感 actually in force: always on where the system draws glass, never on 墨水屏. */
fun frostedGlassInForce(setting: Boolean, einkMode: Boolean): Boolean =
    frostedGlassInForce(setting, einkMode, nativeGlassAvailable)

internal fun frostedGlassInForce(setting: Boolean, einkMode: Boolean, nativeGlass: Boolean): Boolean =
    (setting || nativeGlass) && !einkMode
