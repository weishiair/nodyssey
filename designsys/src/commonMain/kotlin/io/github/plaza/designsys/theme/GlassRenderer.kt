package io.github.plaza.designsys.theme

/** The material renderer, separate from the shared glass theme's colours and reading styles. */
internal enum class GlassRenderer {
    Backdrop,
    Native,
    Opaque,
}

internal expect val platformGlassRenderer: GlassRenderer

/** Only the Compose renderer captures content and draws its own highlights and refraction. */
val PlazaLayers.backdropGlass: Boolean
    get() = frostedGlass && platformGlassRenderer == GlassRenderer.Backdrop

/** iOS 26+ draws floating controls with UIKit, while keeping the common theme's subdued colours. */
val PlazaLayers.nativeGlass: Boolean
    get() = frostedGlass && nativeGlassAvailable

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
