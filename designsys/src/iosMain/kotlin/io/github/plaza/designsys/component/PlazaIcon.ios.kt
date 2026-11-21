@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreGraphics.kCGBitmapByteOrder32Big
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageSymbolConfiguration
import platform.UIKit.UIImageSymbolWeightRegular
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Material's icon grid draws the glyph inside a 20-of-24 keyline, so the symbol's target box is the
 * icon box scaled down the same way and the iOS glyph lands where the Material one would have.
 */
private const val GLYPH_FRACTION = 20f / 24f

private val SYMBOL_WEIGHT = UIImageSymbolWeightRegular

@Composable
internal actual fun rememberSystemSymbolPainter(imageVector: ImageVector): Painter? {
    val name = SfSymbolByIconName[imageVector.name] ?: return null
    return remember(name) {
        if (UIImage.systemImageNamed(name) == null) null else SystemSymbolPainter(name)
    }
}

/**
 * `intrinsicSize` stays unspecified so `Icon` keeps its own 24dp default and honours size modifiers
 * exactly as it does for a vector. The bitmap is a black alpha mask — `Icon`'s tint is a SrcIn
 * colour filter over whatever the painter draws, so the cache needs no colour in its key.
 */
private class SystemSymbolPainter(private val name: String) : Painter() {
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        val w = (size.width * GLYPH_FRACTION).roundToInt()
        val h = (size.height * GLYPH_FRACTION).roundToInt()
        if (w <= 0 || h <= 0) return
        val bitmap = symbolBitmap(name, w, h) ?: return
        drawImage(
            image = bitmap,
            dstOffset =
            IntOffset(
                ((size.width - w) / 2f).roundToInt(),
                ((size.height - h) / 2f).roundToInt(),
            ),
            dstSize = IntSize(w, h),
        )
    }
}

private data class SymbolBitmapKey(val name: String, val w: Int, val h: Int)

// A painter's onDraw runs every frame; the bitmaps themselves are main-thread only, like the
// UIKit rasterisation that produces them.
private const val SYMBOL_CACHE_LIMIT = 256

// Kotlin/Native's LinkedHashMap keeps insertion order but has no removeEldestEntry, so recency is
// a remove-and-reinsert and eviction takes the head — main-thread only, like the rasterisation.
private val symbolBitmapCache = LinkedHashMap<SymbolBitmapKey, ImageBitmap?>()

internal fun symbolBitmap(name: String, w: Int, h: Int): ImageBitmap? {
    val key = SymbolBitmapKey(name, w, h)
    if (symbolBitmapCache.containsKey(key)) {
        val hit = symbolBitmapCache.remove(key)
        symbolBitmapCache[key] = hit
        return hit
    }
    val bitmap = rasterizeSymbol(name, w, h)
    symbolBitmapCache[key] = bitmap
    if (symbolBitmapCache.size > SYMBOL_CACHE_LIMIT) {
        symbolBitmapCache.remove(symbolBitmapCache.keys.first())
    }
    return bitmap
}

/**
 * The whole render: a point size no smaller than the pixel box (re-requested scaled up when the
 * first render comes out under it, so the final draw only ever downscales), the glyph aspect-fitted
 * and centred into a w×h image, and that image copied out as RGBA premultiplied bytes.
 */
internal fun symbolPixels(name: String, w: Int, h: Int): ByteArray? {
    val target = max(w, h).toDouble()
    var image =
        UIImage.systemImageNamed(
            name,
            UIImageSymbolConfiguration.configurationWithPointSize(target, SYMBOL_WEIGHT),
        )
            ?: return null
    val first = image.size.useContents { max(width, height) }
    if (first < target) {
        image =
            UIImage.systemImageNamed(
                name,
                UIImageSymbolConfiguration.configurationWithPointSize(target * target / max(first, 1.0), SYMBOL_WEIGHT),
            )
                ?: return null
    }
    val format =
        UIGraphicsImageRendererFormat.defaultFormat().apply {
            scale = 1.0
            opaque = false
        }
    val rendered =
        UIGraphicsImageRenderer(CGSizeMake(w.toDouble(), h.toDouble()), format)
            .imageWithActions { _ ->
                val dim = image.size.useContents { width to height }
                val fit = min(w / dim.first, h / dim.second)
                val dw = dim.first * fit
                val dh = dim.second * fit
                image.drawInRect(CGRectMake((w - dw) / 2.0, (h - dh) / 2.0, dw, dh))
            }
    val cgImage = rendered.CGImage ?: return null
    return rasterizePixels(cgImage, w, h)
}

private fun rasterizeSymbol(name: String, w: Int, h: Int): ImageBitmap? =
    symbolPixels(name, w, h)?.let {
        Image.makeRaster(
            ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.PREMUL),
            it,
            w * 4,
        )
            .toComposeImageBitmap()
    }

private fun rasterizePixels(cgImage: platform.CoreGraphics.CGImageRef, w: Int, h: Int): ByteArray? {
    val pixels = ByteArray(w * h * 4)
    pixels.usePinned { pinned ->
        val context =
            CGBitmapContextCreate(
                pinned.addressOf(0),
                w.toULong(),
                h.toULong(),
                8uL,
                (w * 4).toULong(),
                CGColorSpaceCreateDeviceRGB(),
                CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value or kCGBitmapByteOrder32Big,
            ) ?: return null
        CGContextDrawImage(context, CGRectMake(0.0, 0.0, w.toDouble(), h.toDouble()), cgImage)
    }
    return pixels
}
