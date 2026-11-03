package io.github.nodyssey.core

import io.github.nodyssey.core.html.Fixtures
import io.github.plaza.core.net.SiteException
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LotteryResultsTest {
    private val draw = LuckyDrawParams(1024765, 1793546010000, 10, 1, false)
    private val seed = LotteryResults.randomness(Fixtures.load("lottery-6603832-beacon.json"), 6603832)
    private val floors = LotteryResults.floors(Fixtures.load("lottery-1024765-floors.json"))

    /** A wrong overflow, PRNG step or pre-filter would silently announce the wrong winners. */
    @Test
    fun `matches all ten winners displayed by the official draw`() {
        assertEquals(6603832L, LotteryResults.round(draw.drawAtMillis))
        assertEquals(
            listOf(14, 15, 35, 58, 63, 69, 76, 92, 96, 117),
            LotteryResults.winners(draw, floors, seed).map { it.floor },
        )
    }

    @Test
    fun `reported draw 1022241 matches the official deduplicated result`() {
        val reported = LuckyDrawParams(1022241, 1793592000000, 10, 1, true)
        val participants = LotteryResults.floors(Fixtures.load("lottery-1022241-floors.json"))
        val randomness = LotteryResults.randomness(Fixtures.load("lottery-6605365-beacon.json"), 6605365)
        val winners = LotteryResults.winners(reported, participants, randomness)
        assertEquals(listOf(20, 54, 61, 65, 76, 87, 101, 115, 140, 142), winners.map { it.floor })
        assertEquals(listOf(48929L, 55077L, 69639L, 1473L, 72836L, 35076L, 72200L, 9180L, 25581L, 68154L), winners.map { it.uid })
    }

    @Test
    fun `deduplication keeps the first eligible floor rather than the first shuffled occurrence`() {
        val repeated = floors.map { if (it.floor in listOf(1, 14, 15)) it.copy(uid = 999) else it }
        val result = LotteryResults.winners(draw.copy(dedupeFloors = true), repeated, seed)
        assertEquals(10, result.size)
        assertEquals(10, result.map { it.uid }.distinct().size)
        assertEquals(false, result.any { it.floor == 14 || it.floor == 15 })
    }

    @Test
    fun `empty participants is a completed empty draw and not an index crash`() {
        assertEquals(emptyList(), LotteryResults.winners(draw, emptyList(), seed))
        assertEquals(emptyList(), LotteryResults.winners(draw.copy(startFloor = 999), floors, seed))
    }

    @Test
    fun `refused floor responses and wrong beacon rounds cannot become a losing result`() {
        assertFailsWith<SiteException> { LotteryResults.floors("""{"success":false,"message":"wait"}""") }
        assertFailsWith<SiteException> { LotteryResults.floors("""{"success":true}""") }
        assertFailsWith<SiteException> { LotteryResults.randomness(Fixtures.load("lottery-6603832-beacon.json"), 6603833) }
        assertFailsWith<SiteException> { LotteryResults.randomness("""{"round":6603832,"randomness":""}""", 6603832) }
    }

    @Test
    fun `parses the real result link and rejects lookalike hosts and incomplete draws`() {
        assertEquals(draw, LuckyDraw.parse(LuckyDraw.link(draw), Site.NODESEEK))
        assertNull(LuckyDraw.parse("https://www.nodeseek.com.evil.test/lucky?post=1024765", Site.NODESEEK))
        assertNull(LuckyDraw.parse(LuckyDraw.link(draw).replace("&count=10", ""), Site.NODESEEK))
        assertNull(LuckyDraw.parse(LuckyDraw.link(draw).replace("&duplicate=true", "&duplicate=maybe"), Site.NODESEEK))
        assertNull(LuckyDraw.parse(LuckyDraw.link(draw).replace("https://", "https://user@"), Site.NODESEEK))
    }

    @Test
    fun `finds folded result links once and excludes another threads draw`() {
        val link = InlineNode.Link("result", LuckyDraw.link(draw))
        val other = InlineNode.Link("other", LuckyDraw.link(draw.copy(postId = 99)))
        val nodes = listOf(
            RichNode.Paragraph(listOf(link)),
            RichNode.Fold("fold", listOf(RichNode.Paragraph(listOf(link, other)))),
        )
        assertEquals(listOf(draw), LuckyDraw.find(nodes, draw.postId, Site.NODESEEK))
    }
}
