package io.github.nodyssey.core

import androidx.compose.runtime.Immutable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToInt

enum class VpsCurrency(val code: String, val symbol: String) {
    CNY("CNY", "¥"),
    USD("USD", "$"),
    EUR("EUR", "€"),
    GBP("GBP", "£"),
    JPY("JPY", "¥"),
    HKD("HKD", "HK$"),
    AUD("AUD", "A$"),
    SGD("SGD", "S$"),
    KRW("KRW", "₩"),
    TWD("TWD", "NT$"),
    CAD("CAD", "C$"),
    ;

    val isBaseCny: Boolean get() = this == CNY

    companion object {
        fun fromCode(code: String?): VpsCurrency = entries.firstOrNull { it.code == code } ?: USD
    }
}

enum class VpsBillingCycle(val days: Int) {
    MONTHLY(30),
    QUARTERLY(90),
    SEMI_ANNUALLY(180),
    ANNUALLY(365),
    BIENNIALLY(730),
    TRIENNIALLY(1095),
    ;

    companion object {
        fun fromDays(days: Int?): VpsBillingCycle = entries.firstOrNull { it.days == days } ?: ANNUALLY
    }
}

@Serializable
enum class VpsQuoteEditedField { PREMIUM, SALE_PRICE }

@Serializable
data class VpsCalculatorDraft(
    val priceText: String = "",
    val currencyCode: String = VpsCurrency.USD.code,
    val cycleDays: Int = VpsBillingCycle.ANNUALLY.days,
    val dueDateIso: String? = null,
    val tradeDateIso: String? = null,
    val customRateText: String = "",
    val customRateCurrencyCode: String? = null,
    val premiumText: String = "",
    val salePriceText: String = "",
    val lastEditedQuote: VpsQuoteEditedField = VpsQuoteEditedField.PREMIUM,
)

@Immutable
data class VpsCalculationResult(
    val priceCny: Double?,
    val hasValidDates: Boolean,
    val rawDaysRemaining: Int?,
    val remainingDays: Int?,
    val isExpired: Boolean,
    val progressPercent: Int?,
    val progressFraction: Float,
    val remainingOriginal: Double?,
    val remainingCny: Double?,
    val effectivePremiumCny: Double?,
    val effectiveSalePriceCny: Double?,
)

object VpsCalculator {
    fun calculate(
        price: Double?,
        exchangeRate: Double?,
        cycle: VpsBillingCycle,
        tradeDate: LocalDate?,
        dueDate: LocalDate?,
        premium: Double?,
        salePrice: Double?,
        lastEditedQuote: VpsQuoteEditedField,
    ): VpsCalculationResult {
        val validPrice = price?.takeIf { it.isFinite() && it >= 0 }
        val validRate = exchangeRate?.takeIf { it.isFinite() && it > 0 }
        val days = if (tradeDate != null && dueDate != null) tradeDate.daysUntil(dueDate) else null
        val remainingDays = days?.coerceAtLeast(0)
        val progress = when (remainingDays) {
            null -> null
            0 -> 0
            else -> maxOf(1, (remainingDays * 100.0 / cycle.days).roundToInt())
        }
        val remainingOriginal = if (validPrice != null && remainingDays != null) {
            (validPrice / cycle.days * remainingDays).takeIf { it.isFinite() }
        } else {
            null
        }
        val remainingCny = convertToCny(remainingOriginal, validRate)
        val (effectivePremium, effectiveSalePrice) = remainingCny?.let { value ->
            when (lastEditedQuote) {
                VpsQuoteEditedField.PREMIUM -> premium?.takeIf { it.isFinite() }?.let { it to (value + it).takeIf(Double::isFinite) }
                VpsQuoteEditedField.SALE_PRICE -> salePrice?.takeIf { it.isFinite() }?.let { (it - value).takeIf(Double::isFinite) to it }
            }
        } ?: (null to null)
        return VpsCalculationResult(
            priceCny = convertToCny(validPrice, validRate),
            hasValidDates = days != null,
            rawDaysRemaining = days,
            remainingDays = remainingDays,
            isExpired = days != null && days <= 0,
            progressPercent = progress,
            progressFraction = ((progress ?: 0) / 100f).coerceIn(0f, 1f),
            remainingOriginal = remainingOriginal,
            remainingCny = remainingCny,
            effectivePremiumCny = effectivePremium,
            effectiveSalePriceCny = effectiveSalePrice,
        )
    }

    private fun convertToCny(amount: Double?, rate: Double?): Double? {
        if (amount == null || rate == null) return null
        return (amount * rate).takeIf { it.isFinite() }
    }

    fun parseNonNegativeDecimal(text: String): Double? = parseSignedDecimal(text)?.takeIf { it >= 0 }

    fun parseSignedDecimal(text: String): Double? = text.trim().toDoubleOrNull()?.takeIf { it.isFinite() }

    /** Round the decimal digits directly: scaling a Double can lose a cent or overflow a Long. */
    fun formatDecimal(value: Double, decimals: Int = 2): String {
        require(decimals >= 0)
        val magnitude = if (value.isFinite()) abs(value).toString().uppercase() else "0"
        val mantissa = magnitude.substringBefore('E')
        val exponent = magnitude.substringAfter('E', "0").toInt()
        val point = mantissa.substringBefore('.').length + exponent
        val digits = mantissa.replace(".", "")
        val cut = point + decimals
        val kept = cut.coerceAtLeast(0)
        // Keep one leading zero for a carry across the most significant digit.
        val rounded = ("0" + digits.take(kept).padEnd(kept, '0')).toCharArray()
        if (digits.getOrNull(cut) in '5'..'9') {
            var index = rounded.lastIndex
            while (rounded[index] == '9') {
                rounded[index] = '0'
                index--
            }
            rounded[index] = rounded[index] + 1
        }
        val fixed = rounded.concatToString().trimStart('0').padStart(decimals + 1, '0')
        val sign = if (value < 0 && fixed.any { it != '0' }) "-" else ""
        if (decimals == 0) return sign + fixed
        return sign + fixed.dropLast(decimals) + "." + fixed.takeLast(decimals)
    }

    fun formatIsoDate(date: LocalDate): String = date.toString()

    fun parseIsoDate(iso: String?): LocalDate? = iso?.let { LocalDate.Formats.ISO.parseOrNull(it) }
}
