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
import platform.UIKit.UIImageSymbolWeightMedium
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Point size as a fraction of the icon box, calibrated on the simulator so "circle" renders with an
 * ink diameter of exactly 20/24 x the box — Material's keyline. SF Symbols share one optical size
 * per point size, so every symbol lands at its natural size together.
 */
internal const val SYMBOL_POINT_FRACTION = 0.825

private val SYMBOL_WEIGHT = UIImageSymbolWeightMedium

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
        val w = size.width.roundToInt()
        val h = size.height.roundToInt()
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
internal fun symbolPixels(name: String, w: Int, h: Int, pointFraction: Double = SYMBOL_POINT_FRACTION): ByteArray? {
    val pt = min(w, h) * pointFraction
    val image =
        UIImage.systemImageNamed(name, UIImageSymbolConfiguration.configurationWithPointSize(pt, SYMBOL_WEIGHT))
            ?: return null
    val iw = image.size.useContents { width.roundToInt() }
    val ih = image.size.useContents { height.roundToInt() }
    if (iw <= 0 || ih <= 0) return null
    val format =
        UIGraphicsImageRendererFormat.defaultFormat().apply {
            scale = 1.0
            opaque = false
        }
    val rendered =
        UIGraphicsImageRenderer(CGSizeMake(iw.toDouble(), ih.toDouble()), format)
            .imageWithActions { _ -> image.drawInRect(CGRectMake(0.0, 0.0, iw.toDouble(), ih.toDouble())) }
    val cg = rendered.CGImage ?: return null
    val src = rasterizePixels(cg, iw, ih) ?: return null
    val (l, r, t, b) = inkBounds(src, iw, ih)
    if (r < l || b < t) return null
    val inkW = r - l + 1
    val inkH = b - t + 1
    val scale = min(1.0, min((w - 2.0) / inkW, (h - 2.0) / inkH))
    val dw = max(1, (inkW * scale).roundToInt())
    val dh = max(1, (inkH * scale).roundToInt())
    val ink =
        platform.CoreGraphics.CGImageCreateWithImageInRect(
            cg,
            CGRectMake(l.toDouble(), t.toDouble(), inkW.toDouble(), inkH.toDouble()),
        ) ?: return null
    val out = ByteArray(w * h * 4)
    out.usePinned { pinned ->
        val ctx =
            CGBitmapContextCreate(
                pinned.addressOf(0),
                w.toULong(),
                h.toULong(),
                8uL,
                (w * 4).toULong(),
                CGColorSpaceCreateDeviceRGB(),
                CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value or kCGBitmapByteOrder32Big,
            ) ?: return null
        CGContextDrawImage(
            ctx,
            CGRectMake((w - dw) / 2.0, (h - dh) / 2.0, dw.toDouble(), dh.toDouble()),
            ink,
        )
    }
    return out
}

/** Alpha bounding box of an RGBA premultiplied buffer: (left, right, top, bottom) inclusive. */
internal fun inkBounds(px: ByteArray, w: Int, h: Int): IntArray {
    var l = w
    var r = -1
    var t = h
    var b = -1
    for (y in 0 until h) {
        for (x in 0 until w) {
            if (px[(y * w + x) * 4 + 3].toInt() and 0xff != 0) {
                if (x < l) l = x
                if (x > r) r = x
                if (y < t) t = y
                if (y > b) b = y
            }
        }
    }
    return intArrayOf(l, r, t, b)
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
