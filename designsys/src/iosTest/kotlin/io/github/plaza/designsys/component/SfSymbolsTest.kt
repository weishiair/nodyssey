@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.plaza.designsys.component

import platform.UIKit.UIImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SfSymbolsTest {
    /**
     * A typo'd name fails silently at runtime — the painter just falls back to the Material vector —
     * so the whole map is resolved here, once, against the OS that will be asked for it.
     */
    @Test
    fun `every mapped symbol resolves on the oldest supported iOS`() {
        val unresolved = SfSymbolByIconName.values.distinct().filter { UIImage.systemImageNamed(it) == null }
        assertEquals(emptyList(), unresolved)
    }

    /**
     * A blank or oversized raster would pass the resolve check yet draw nothing — or draw over the
     * keyline — on screen.
     */
    @Test
    fun `the symbol painter rasterises inside its target box`() {
        val bitmap = symbolBitmap("xmark", 60, 60)
        assertNotNull(bitmap, "xmark did not rasterise")
        assertEquals(60, bitmap.width)
        assertEquals(60, bitmap.height)
        val pixels = symbolPixels("xmark", 60, 60)
        assertNotNull(pixels, "xmark did not rasterise")
        assertTrue(pixels.indices.step(4).any { pixels[it + 3].toInt() and 0xff != 0 }, "xmark rasterised to a fully transparent bitmap")

        // An uncentred glyph is a layout bug the non-blank check can't see.
        val (l, r, _, _) = alphaBounds(pixels, 60)
        assertTrue(kotlin.math.abs(l - (60 - r)) <= 1, "xmark is off-centre: left margin $l, right ${60 - r}")

        // A stretched render keeps aspect wrong: the ellipsis reads as a bar, not a square.
        val dots = symbolPixels("ellipsis", 60, 60)
        assertNotNull(dots, "ellipsis did not rasterise")
        val (dl, dr, dt, db) = alphaBounds(dots, 60)
        assertTrue(dr - dl > db - dt, "ellipsis stretched: ${dr - dl}w × ${db - dt}h")
    }

    private fun alphaBounds(pixels: ByteArray, w: Int): IntArray {
        var l = w
        var r = 0
        var t = pixels.size / (w * 4)
        var b = 0
        for (y in 0 until pixels.size / (w * 4)) {
            for (x in 0 until w) {
                if (pixels[(y * w + x) * 4 + 3].toInt() and 0xff != 0) {
                    l = minOf(l, x)
                    r = maxOf(r, x)
                    t = minOf(t, y)
                    b = maxOf(b, y)
                }
            }
        }
        return intArrayOf(l, r, t, b)
    }
}
