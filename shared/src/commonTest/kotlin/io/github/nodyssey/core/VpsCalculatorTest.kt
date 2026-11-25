package io.github.nodyssey.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VpsCalculatorTest {
    private val trade = LocalDate(2024, 2, 28)
    private val due = LocalDate(2024, 3, 2)

    private fun quote(
        price: Double? = 60.0,
        rate: Double? = 7.0,
        cycle: VpsBillingCycle = VpsBillingCycle.MONTHLY,
        tradeDate: LocalDate? = trade,
        dueDate: LocalDate? = due,
        premium: Double? = null,
        salePrice: Double? = null,
        edited: VpsQuoteEditedField = VpsQuoteEditedField.PREMIUM,
    ) = VpsCalculator.calculate(price, rate, cycle, tradeDate, dueDate, premium, salePrice, edited)

    @Test
    fun `leap day counts toward the original and converted remaining value`() {
        val result = quote()
        assertEquals(420.0, result.priceCny)
        assertTrue(result.hasValidDates)
        assertFalse(result.isExpired)
        assertEquals(3, result.rawDaysRemaining)
        assertEquals(3, result.remainingDays)
        assertEquals(10, result.progressPercent)
        assertEquals(0.1f, result.progressFraction)
        assertEquals(6.0, result.remainingOriginal)
        assertEquals(42.0, result.remainingCny)
    }

    @Test
    fun `empty dates keep the day count unknown while expired dates retain the signed difference`() {
        for (result in listOf(quote(tradeDate = null), quote(dueDate = null))) {
            assertFalse(result.hasValidDates)
            assertFalse(result.isExpired)
            assertNull(result.rawDaysRemaining)
            assertNull(result.remainingDays)
            assertNull(result.progressPercent)
            assertEquals(0f, result.progressFraction)
            assertNull(result.remainingCny)
        }
        for ((date, days) in listOf(trade to 0, LocalDate(2024, 2, 26) to -2)) {
            val result = quote(dueDate = date)
            assertTrue(result.hasValidDates)
            assertTrue(result.isExpired)
            assertEquals(days, result.rawDaysRemaining)
            assertEquals(0, result.remainingDays)
            assertEquals(0, result.progressPercent)
            assertEquals(0f, result.progressFraction)
            assertEquals(0.0, result.remainingOriginal)
            assertEquals(0.0, result.remainingCny)
        }
    }

    @Test
    fun `percentages keep the last day visible and only the progress bar stops at one cycle`() {
        val lastDay = quote(cycle = VpsBillingCycle.TRIENNIALLY, dueDate = LocalDate(2024, 2, 29))
        assertEquals(1, lastDay.progressPercent)
        assertEquals(0.01f, lastDay.progressFraction)
        val prepaid = quote(dueDate = LocalDate(2024, 4, 13))
        assertEquals(45, prepaid.remainingDays)
        assertEquals(150, prepaid.progressPercent)
        assertEquals(1f, prepaid.progressFraction)
        assertEquals(630.0, prepaid.remainingCny)
    }

    @Test
    fun `last edited field determines both quotes even with a discount or a stale counterpart`() {
        val discount = quote(premium = -2.0, salePrice = 99.0)
        assertEquals(-2.0, discount.effectivePremiumCny)
        assertEquals(40.0, discount.effectiveSalePriceCny)
        val offer = quote(premium = 10.0, salePrice = 39.0, edited = VpsQuoteEditedField.SALE_PRICE)
        assertEquals(-3.0, offer.effectivePremiumCny)
        assertEquals(39.0, offer.effectiveSalePriceCny)
        for (result in listOf(
            quote(premium = null, salePrice = 100.0),
            quote(premium = 4.0, salePrice = Double.NaN, edited = VpsQuoteEditedField.SALE_PRICE),
        )) {
            assertNull(result.effectivePremiumCny)
            assertNull(result.effectiveSalePriceCny)
        }
    }

    @Test
    fun `a missing or invalid rate leaves converted amounts and linked quotes unknown`() {
        for (rate in listOf(null, 0.0, Double.POSITIVE_INFINITY)) {
            val result = quote(rate = rate, premium = 5.0)
            assertEquals(6.0, result.remainingOriginal)
            assertNull(result.remainingCny)
            assertNull(result.priceCny)
            assertNull(result.effectiveSalePriceCny)
        }
    }

    @Test
    fun `a zero renewal price is a known value while an invalid price leaves it unknown`() {
        for (price in listOf(null, -1.0, Double.NaN)) {
            assertNull(quote(price = price).remainingOriginal)
        }
        assertEquals(0.0, quote(price = 0.0).remainingCny)
    }

    @Test
    fun `signed discounts parse while invalid prices and dates stay unset`() {
        assertEquals(-2.5, VpsCalculator.parseSignedDecimal(" -2.50 "))
        assertNull(VpsCalculator.parseNonNegativeDecimal(" -2.50 "))
        assertEquals(0.0, VpsCalculator.parseNonNegativeDecimal(" 0.00 "))
        for (text in listOf("", "abc", "NaN", "Infinity", "1e999")) {
            assertNull(VpsCalculator.parseSignedDecimal(text))
        }
        assertEquals(trade, VpsCalculator.parseIsoDate(VpsCalculator.formatIsoDate(trade)))
        for (text in listOf(null, "", "2023-02-29", "11/02/2024")) assertNull(VpsCalculator.parseIsoDate(text))
    }

    @Test
    fun `fixed decimals carry across nines and expand scientific notation without negative zero`() {
        assertEquals("10.00", VpsCalculator.formatDecimal(9.995))
        assertEquals("-1.01", VpsCalculator.formatDecimal(-1.005))
        assertEquals("0.00", VpsCalculator.formatDecimal(-0.004))
        assertEquals("0.0000", VpsCalculator.formatDecimal(-0.0, 4))
        assertEquals("0.0001", VpsCalculator.formatDecimal(5e-5, 4))
        assertEquals("0.0000", VpsCalculator.formatDecimal(1e-100, 4))
        assertEquals("123000000000000000000.0000", VpsCalculator.formatDecimal(1.23e20, 4))
        assertEquals("0.00", VpsCalculator.formatDecimal(Double.NaN))
        assertEquals("0.00", VpsCalculator.formatDecimal(Double.NEGATIVE_INFINITY))
    }
}
