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

    @Test
    fun `circle matches the Material keyline`() {
        val px = symbolPixels("circle", 72, 72)!!
        val (l, r, t, b) = alphaBounds(px, 72)
        val d = maxOf(r - l + 1, b - t + 1)
        assertTrue(d in 59..61, "circle ink diameter $d, want 60±1")
    }

    @Test
    fun `person variants share one optical size`() {
        val one = inkBounds(symbolPixels("person.fill", 72, 72)!!, 72, 72)
        val two = inkBounds(symbolPixels("person.2.fill", 72, 72)!!, 72, 72)
        val h1 = one[3] - one[2] + 1.0
        val h2 = two[3] - two[2] + 1.0
        assertTrue(kotlin.math.abs(h1 - h2) / h1 < 0.15, "ink heights $h1 vs $h2 differ >15%")
    }

    @Test
    fun `every mapped symbol renders non-empty ink inside the box`() {
        val bad = mutableListOf<String>()
        for (name in SfSymbolByIconName.values.toSet()) {
            val px = symbolPixels(name, 72, 72)
            if (px == null) {
                bad += "$name: no render"
                continue
            }
            val (l, r, t, b) = inkBounds(px, 72, 72)
            if (r < l) {
                bad += "$name: empty"
            } else if (r - l + 1 > 71 || b - t + 1 > 71) {
                bad += "$name: exceeds box"
            }
        }
        assertEquals(emptyList(), bad)
    }

    private fun alphaBounds(pixels: ByteArray, w: Int): IntArray =
        inkBounds(pixels, w, pixels.size / (w * 4))
}
