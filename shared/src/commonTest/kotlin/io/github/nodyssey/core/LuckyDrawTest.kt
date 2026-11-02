package io.github.nodyssey.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Official URL parameters, including epoch milliseconds and the inverted duplicate flag. */
class LuckyDrawTest {
    // 2026-07-27 20:00 +08:00
    private val drawAt = 1_785_153_600_000L

    @Test
    fun `builds the link with the five parameters the site's form has`() {
        val link =
            LuckyDraw.link(
                LuckyDrawParams(
                    postId = 286417,
                    drawAtMillis = drawAt,
                    prizeCount = 3,
                    startFloor = 1,
                    dedupeFloors = true,
                ),
            )

        assertEquals(
            "https://www.nodeseek.com/lucky?post=286417&time=1785153600000&count=3&start=1&duplicate=false&mode=view",
            link,
        )
    }

    @Test
    fun `writes dedupe off as duplicate true rather than omitting it`() {
        val link =
            LuckyDraw.link(
                LuckyDrawParams(
                    postId = 1,
                    drawAtMillis = drawAt,
                    prizeCount = 1,
                    startFloor = 0,
                    dedupeFloors = false,
                ),
            )

        assertTrue(link.endsWith("&start=0&duplicate=true&mode=view"))
    }

    /** A zero prize count would produce a draw nobody can win; the builder floors it at one. */
    @Test
    fun `never generates a draw with no prizes`() {
        val link =
            LuckyDraw.link(
                LuckyDrawParams(
                    postId = 9,
                    drawAtMillis = drawAt,
                    prizeCount = 0,
                    startFloor = -4,
                    dedupeFloors = true,
                ),
            )

        assertTrue(link.contains("&count=1&"))
        assertTrue(link.contains("&start=0&"))
    }
}
