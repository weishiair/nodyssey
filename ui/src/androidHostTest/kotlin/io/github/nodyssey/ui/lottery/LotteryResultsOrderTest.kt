package io.github.nodyssey.ui.lottery

import io.github.nodyssey.core.LuckyDrawParams
import io.github.nodyssey.data.lottery.LotteryEntry
import io.github.nodyssey.data.lottery.LotteryOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class LotteryResultsOrderTest {
    private fun entry(id: Long, outcome: LotteryOutcome?, checkedAt: Long, read: Boolean = false) = LotteryEntry(
        draw = LuckyDrawParams(id, 1793546010000, 10, 1, false),
        title = "draw $id",
        ownerUid = 7767,
        ownerName = "owner",
        origin = "https://www.nodeseek.com",
        outcome = outcome,
        checkedAtMillis = checkedAt,
        resultRead = read,
    )

    @Test
    fun `older unread wins precede newer losses and read wins with recent results first within each group`() {
        val olderWin = entry(1, LotteryOutcome.WON, 10)
        val newerWin = entry(2, LotteryOutcome.WON, 20)
        val loss = entry(3, LotteryOutcome.LOST, 100)
        val absent = entry(4, LotteryOutcome.NOT_ENTERED, 90)
        val readWin = entry(5, LotteryOutcome.WON, 200, read = true)
        val readLoss = entry(6, LotteryOutcome.LOST, 300, read = true)
        val pending = entry(7, null, 1_000)
        val rows = listOf(readWin, absent, olderWin, pending, readLoss, newerWin, loss)
        assertEquals(listOf(2L, 1L, 3L, 4L, 6L, 5L), lotteryResultsInDisplayOrder(rows).map { it.draw.postId })
        val afterReading = rows.map { if (it.key == newerWin.key) it.copy(resultRead = true) else it }
        assertEquals(listOf(1L, 3L, 4L, 6L, 5L, 2L), lotteryResultsInDisplayOrder(afterReading).map { it.draw.postId })
    }

    @Test
    fun `outcome filters exclude pending and missing participation without losing unread priority`() {
        val rows = listOf(
            entry(1, LotteryOutcome.WON, 300, read = true),
            entry(2, LotteryOutcome.NOT_ENTERED, 200),
            entry(3, LotteryOutcome.LOST, 100),
            entry(4, LotteryOutcome.WON, 10),
            entry(5, null, 1_000),
        )
        assertEquals(listOf(4L, 1L), lotteryResultsInDisplayOrder(rows, LotteryOutcome.WON).map { it.draw.postId })
        assertEquals(listOf(3L), lotteryResultsInDisplayOrder(rows, LotteryOutcome.LOST).map { it.draw.postId })
        assertEquals(listOf(2L), lotteryResultsInDisplayOrder(rows, LotteryOutcome.NOT_ENTERED).map { it.draw.postId })
        assertEquals(listOf(4L, 2L, 3L, 1L), lotteryResultsInDisplayOrder(rows).map { it.draw.postId })
    }
}
