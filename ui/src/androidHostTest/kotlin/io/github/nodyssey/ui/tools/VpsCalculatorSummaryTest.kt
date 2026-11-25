package io.github.nodyssey.ui.tools

import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.github.nodyssey.core.VpsCurrency
import io.github.nodyssey.core.VpsQuoteEditedField
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VpsCalculatorSummaryTest {
    @get:Rule val compose = createComposeRule()

    private val state = VpsCalculatorUiState(
        tradeDate = LocalDate(2026, 11, 24),
        dueDate = LocalDate(2026, 12, 12),
        priceText = "39",
        rateText = "6.6793",
    )

    @Test
    fun `clipboard markdown matches the image summary for the selected currency`() = runTest {
        val quotes = listOf(state, state.copy(currency = VpsCurrency.CNY, dueDate = null))
        lateinit var summaries: List<String>
        compose.setContent { summaries = quotes.map { vpsSummary(it).markdown } }
        compose.waitForIdle()
        assertEquals(summaries, quotes.map { buildVpsMarkdown(it) })
    }

    @Test
    fun `foreign renewal includes conversion and adds the premium to the remaining value`() {
        lateinit var summary: VpsSummary
        compose.setContent { summary = vpsSummary(state.copy(premiumText = "1000")) }
        compose.runOnIdle {
            assertEquals(
                """
                ### VPS 剩余价值估算

                | 项目 | 明细 |
                | --- | --- |
                | 续费金额 | 39 USD / 年付（约 ¥260.49） |
                | 到期日期 | 2026-12-12（交易日 2026-11-24，剩余 18 天） |
                | 参考汇率 | 1 USD ≈ 6.6793 CNY |
                | 剩余价值 | **¥12.85**（约 1.92 USD） |
                | 交易总价 | **¥1012.85**（溢价 +¥1000.00） |
                """.trimIndent(),
                summary.markdown,
            )
            assertEquals("¥1012.85（溢价 +¥1000.00）", summary.rows.last().detail)
        }
    }

    @Test
    fun `CNY sale below remaining value omits conversions and reports the discount`() {
        val sale = state.copy(currency = VpsCurrency.CNY, priceText = "365", rateText = "1", salePriceText = "10", lastEditedQuote = VpsQuoteEditedField.SALE_PRICE)
        lateinit var summary: VpsSummary
        compose.setContent { summary = vpsSummary(sale) }
        compose.runOnIdle {
            assertEquals(
                """
                ### VPS 剩余价值估算

                | 项目 | 明细 |
                | --- | --- |
                | 续费金额 | ¥365 / 年付 |
                | 到期日期 | 2026-12-12（交易日 2026-11-24，剩余 18 天） |
                | 剩余价值 | **¥18.00** |
                | 交易总价 | **¥10.00**（折价 -¥8.00） |
                """.trimIndent(),
                summary.markdown,
            )
            assertEquals(listOf("续费金额", "到期日期", "剩余价值", "交易总价"), summary.rows.map { it.label })
            assertEquals("¥10.00（折价 -¥8.00）", summary.rows.last().detail)
        }
    }

    @Test
    fun `unset zero and sub-cent differences are reported at value`() {
        lateinit var summaries: List<VpsSummary>
        compose.setContent {
            summaries = listOf(
                vpsSummary(state),
                vpsSummary(state.copy(premiumText = "0")),
                vpsSummary(state.copy(premiumText = "-0.001")),
            )
        }
        compose.runOnIdle {
            for (summary in summaries) {
                assertEquals("| 交易总价 | **¥12.85**（原价） |", summary.markdown.lineSequence().last())
                assertEquals("¥12.85（原价）", summary.rows.last().detail)
            }
        }
    }

    @Test
    fun `missing expiry leaves both the date and days unknown`() {
        lateinit var summary: VpsSummary
        compose.setContent { summary = vpsSummary(state.copy(dueDate = null)) }
        compose.runOnIdle { assertEquals("--（交易日 2026-11-24，剩余 --）", summary.rows.single { it.label == "到期日期" }.detail) }
    }
}
