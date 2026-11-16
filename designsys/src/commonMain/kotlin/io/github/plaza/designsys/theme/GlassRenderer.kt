package io.github.plaza.designsys.theme

/** The material renderer, separate from the shared glass theme's colours and reading styles. */
internal enum class GlassRenderer {
    Backdrop,
    Native,
    Opaque,
}

internal expect val platformGlassRenderer: GlassRenderer

internal expect val platformSupportsNativeChrome: Boolean

/** Only the Compose renderer captures content and draws its own highlights and refraction. */
val PlazaLayers.backdropGlass: Boolean
    get() = frostedGlass && platformGlassRenderer == GlassRenderer.Backdrop

/** iOS 26+ draws floating controls with UIKit, while keeping the common theme's subdued colours. */
val PlazaLayers.nativeGlass: Boolean
    get() = frostedGlass && platformGlassRenderer == GlassRenderer.Native

/** iOS 16+ uses native UIKit navigation bars and menus outside 墨水屏模式, regardless of glass. */
val PlazaLayers.nativeChrome: Boolean
    get() = platformSupportsNativeChrome && cardBorder == null
