package io.github.nodyssey.ui.lottery

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.core.LuckyDrawParams
import io.github.nodyssey.data.lottery.LotteryEntry
import io.github.nodyssey.data.lottery.LotteryOutcome
import io.github.nodyssey.data.lottery.LotteryRepository
import io.github.plaza.core.AppClock
import io.github.plaza.designsys.theme.PlazaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h700dp")
class LotteryResultReadTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val models = ViewModelStore()
    private val draw = LuckyDrawParams(1024765, 1793546010000, 10, 1, false)
    private val pending = LotteryEntry(draw, "pending", 7767, "owner", "https://www.nodeseek.com")
    private val completed = pending.copy(draw = draw.copy(prizeCount = 1), outcome = LotteryOutcome.LOST, checkedAtMillis = draw.drawAtMillis + 1_000)
    private val rows = MutableStateFlow(listOf(pending, completed))
    private val acknowledged = mutableListOf<String>()
    private val openedThreads = mutableListOf<Long>()
    private var duringRead: () -> Unit = {}

    @After
    fun clearModels() = models.clear()

    private fun show() {
        val repository = object : LotteryRepository {
            override val entries = rows
            override suspend fun watch(draw: LuckyDrawParams, title: String) = Unit
            override suspend fun remove(key: String) = Unit
            override suspend fun refreshDue(key: String?, force: Boolean) = Unit
            override suspend fun synchronize() = Unit
            override suspend fun markResultsRead(entries: List<LotteryEntry>) {
                acknowledged += entries.map { it.key }
                duringRead()
                rows.value = rows.value.map { if (it in entries) it.copy(resultRead = true) else it }
            }
        }
        val model = ViewModelProvider.create(
            models,
            viewModelFactory { initializer { LotteryViewModel(repository, AppClock { draw.drawAtMillis + 2_000 }) } },
        )[LotteryViewModel::class]
        composeRule.setContent {
            PlazaTheme { LotteryRoute(model, {}, { openedThreads += it }, {}, {}, {}, {}) }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `opening one result leaves the others unread even when more arrive in the results tab`() {
        show()
        assertEquals(emptyList<String>(), acknowledged)
        composeRule.onNodeWithText("已查结果").performClick()
        composeRule.waitForIdle()
        assertEquals(emptyList<String>(), acknowledged)
        composeRule.onNodeWithText(completed.title).performClick()
        composeRule.waitUntil { acknowledged == listOf(completed.key) }
        assertEquals(listOf(completed.draw.postId), openedThreads)
        composeRule.runOnIdle {
            rows.value = rows.value.map { if (it.key == pending.key) it.copy(outcome = LotteryOutcome.WON) else it }
        }
        composeRule.waitForIdle()
        assertEquals(listOf(completed.key), acknowledged)
        assertEquals(false, rows.value.first { it.key == pending.key }.resultRead)
        composeRule.onNodeWithText("提醒中").performClick()
        composeRule.onNodeWithText("已查结果").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(completed.key), acknowledged)
    }

    @Test
    fun `mark all read acknowledges completed results without clearing results arriving after the click`() {
        val second = completed.copy(draw = draw.copy(prizeCount = 2), outcome = LotteryOutcome.WON)
        val arriving = completed.copy(draw = draw.copy(prizeCount = 3))
        rows.value += second
        duringRead = { rows.value += arriving }
        show()
        // The top-bar action works from the reminders tab without opening each completed result.
        composeRule.onNodeWithText("全部已读").performClick()
        composeRule.waitUntil { acknowledged.size == 2 }
        assertEquals(listOf(completed.key, second.key), acknowledged)
        composeRule.runOnIdle {
            val results = rows.value.associateBy { it.key }
            assertEquals(true, results.getValue(completed.key).resultRead)
            assertEquals(true, results.getValue(second.key).resultRead)
            assertEquals(false, results.getValue(arriving.key).resultRead)
            assertEquals(false, results.getValue(pending.key).resultRead)
            assertEquals(4, results.size)
        }
    }

    @Test
    fun `mark all read includes results hidden by the selected outcome filter`() {
        val win = completed.copy(draw = draw.copy(prizeCount = 2), outcome = LotteryOutcome.WON)
        val absent = completed.copy(draw = draw.copy(prizeCount = 3), outcome = LotteryOutcome.NOT_ENTERED)
        rows.value += listOf(win, absent)
        show()
        composeRule.onNodeWithText("已查结果").performClick()
        composeRule.onNodeWithText("已中奖").performClick()
        composeRule.onNodeWithText("全部已读").performClick()
        composeRule.waitUntil { acknowledged.size == 3 }
        assertEquals(listOf(completed.key, win.key, absent.key), acknowledged)
        composeRule.runOnIdle {
            assertEquals(true, rows.value.filter { it.outcome != null }.all { it.resultRead })
            assertEquals(false, rows.value.first { it.key == pending.key }.resultRead)
        }
    }
}
