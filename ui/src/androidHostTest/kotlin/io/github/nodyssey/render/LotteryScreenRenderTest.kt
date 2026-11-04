package io.github.nodyssey.render

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.core.LuckyDrawParams
import io.github.nodyssey.data.lottery.LotteryEntry
import io.github.nodyssey.data.lottery.LotteryFailure
import io.github.nodyssey.data.lottery.LotteryFailureSource
import io.github.nodyssey.data.lottery.LotteryOutcome
import io.github.nodyssey.data.lottery.LotteryRepository
import io.github.nodyssey.ui.lottery.LotteryCard
import io.github.nodyssey.ui.lottery.LotteryRoute
import io.github.nodyssey.ui.lottery.LotteryViewModel
import io.github.plaza.core.AppClock
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Review artifacts only: no goldens, layout assertions, or label assertions. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1000dp")
class LotteryScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val viewModels = ViewModelStore()
    private val now = 1_793_607_000_000L
    private val future = LuckyDrawParams(1024765, now + 3_600_000, 10, 1, true)
    private val upcoming = LotteryEntry(future, "抽奖 台湾 HINET 家宽", 7767, "keyzal", "https://www.nodeseek.com")
    private val won = upcoming.copy(
        draw = future.copy(drawAtMillis = now - 3_600_000),
        outcome = LotteryOutcome.WON,
        winningFloors = listOf(14),
    )

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @After
    fun clearViewModels() = viewModels.clear()

    private fun model(rows: List<LotteryEntry> = listOf(upcoming, won)): LotteryViewModel {
        val repository = object : LotteryRepository {
            override val entries = flowOf(rows)
            override suspend fun watch(draw: LuckyDrawParams, title: String) = Unit
            override suspend fun remove(key: String) = Unit
            override suspend fun refreshDue(key: String?, force: Boolean) = Unit
            override suspend fun markResultsRead(entries: List<LotteryEntry>) = Unit
            override suspend fun synchronize() = Unit
        }
        return ViewModelProvider.create(
            viewModels,
            viewModelFactory { initializer { LotteryViewModel(repository, AppClock { now }) } },
        )[LotteryViewModel::class]
    }

    @Test
    fun `tracking list on a phone`() {
        val viewModel = model()
        composeRule.setContent {
            PlazaTheme {
                LotteryRoute(viewModel, {}, {}, {}, {}, {}, {})
            }
        }
        composeRule.onRoot().captureRender("lottery-tracking-phone")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun `reported draw with a retryable connection failure`() {
        val entry = LotteryEntry(
            LuckyDrawParams(1022241, 1793592000000, 10, 1, true),
            "[IF.TD] 新机器上线/清退旧机器补偿/抽奖三合一 庆祝万圣节通知 已开",
            39801,
            "weishiair",
            "https://www.nodeseek.com",
            failure = LotteryFailure.NETWORK,
            failureSource = LotteryFailureSource.BEACON,
        )
        val viewModel = model(listOf(entry))
        composeRule.setContent {
            PlazaTheme { LotteryRoute(viewModel, {}, {}, {}, {}, {}, {}) }
        }
        composeRule.onRoot().captureRender("lottery-retry-phone")
    }

    @Test
    @Config(qualifiers = "w800dp-h900dp")
    fun `checked list on a tablet in dark`() {
        val viewModel = model()
        composeRule.setContent {
            PlazaTheme(darkTheme = true) {
                LotteryRoute(viewModel, {}, {}, {}, {}, {}, {})
            }
        }
        composeRule.onNodeWithText("已查结果").performClick()
        composeRule.onRoot().captureRender("lottery-checked-tablet-dark")
    }

    @Test
    fun `unread winning results on a phone`() {
        val lost = won.copy(draw = won.draw.copy(postId = 1022241), title = "较新的未中奖结果", outcome = LotteryOutcome.LOST, checkedAtMillis = now)
        val viewModel = model(listOf(upcoming, lost, won.copy(checkedAtMillis = now - 3_600_000)))
        composeRule.setContent {
            PlazaTheme { LotteryRoute(viewModel, {}, {}, {}, {}, {}, {}) }
        }
        composeRule.onNodeWithText("已查结果").performClick()
        composeRule.onRoot().captureRender("lottery-unread-winners-phone")
        composeRule.onNodeWithText("已中奖").performClick()
        composeRule.onRoot().captureRender("lottery-filter-won-phone")
    }

    @Test
    fun `draw cards before and after the result`() {
        composeRule.setContent {
            PlazaTheme {
                Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    LotteryCard(future, null, now, false, {}, {}, {})
                    LotteryCard(future, upcoming, now, false, {}, {}, {})
                    LotteryCard(won.draw, won, now, false, {}, {}, {})
                }
            }
        }
        composeRule.onRoot().captureRender("lottery-post-cards")
    }
}
