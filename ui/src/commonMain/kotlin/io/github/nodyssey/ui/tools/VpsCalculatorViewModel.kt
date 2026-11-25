package io.github.nodyssey.ui.tools

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.core.VpsBillingCycle
import io.github.nodyssey.core.VpsCalculationResult
import io.github.nodyssey.core.VpsCalculator
import io.github.nodyssey.core.VpsCalculatorDraft
import io.github.nodyssey.core.VpsCurrency
import io.github.nodyssey.core.VpsQuoteEditedField
import io.github.nodyssey.data.calculator.ExchangeRateResult
import io.github.nodyssey.data.calculator.VpsCalculatorRepository
import io.github.nodyssey.data.calculator.VpsCalculatorSnapshot
import io.github.nodyssey.di.AppContainer
import io.github.plaza.core.AppClock
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

enum class VpsRateNotice { FETCH_FAILED, RATE_LIMITED }

@Immutable
data class VpsCalculatorUiState(
    val tradeDate: LocalDate,
    val priceText: String = "",
    val currency: VpsCurrency = VpsCurrency.USD,
    val cycle: VpsBillingCycle = VpsBillingCycle.ANNUALLY,
    val dueDate: LocalDate? = null,
    val rateText: String = "",
    val isLoadingRate: Boolean = false,
    val rateFetchedAtMillis: Long? = null,
    val rateFromCache: Boolean = false,
    val rateNotice: VpsRateNotice? = null,
    val premiumText: String = "",
    val salePriceText: String = "",
    val lastEditedQuote: VpsQuoteEditedField = VpsQuoteEditedField.PREMIUM,
    val draftReady: Boolean = false,
) {
    val result: VpsCalculationResult = VpsCalculator.calculate(
        price = VpsCalculator.parseNonNegativeDecimal(priceText),
        exchangeRate = VpsCalculator.parseNonNegativeDecimal(rateText),
        cycle = cycle,
        tradeDate = tradeDate,
        dueDate = dueDate,
        premium = VpsCalculator.parseSignedDecimal(premiumText),
        salePrice = VpsCalculator.parseNonNegativeDecimal(salePriceText),
        lastEditedQuote = lastEditedQuote,
    )

    internal fun syncQuote(): VpsCalculatorUiState =
        when (lastEditedQuote) {
            VpsQuoteEditedField.PREMIUM -> copy(salePriceText = result.effectiveSalePriceCny?.let { VpsCalculator.formatDecimal(it) }.orEmpty())
            VpsQuoteEditedField.SALE_PRICE -> copy(premiumText = result.effectivePremiumCny?.let { VpsCalculator.formatDecimal(it) }.orEmpty())
        }
}

class VpsCalculatorViewModel(
    private val repository: VpsCalculatorRepository,
    private val clock: AppClock,
    private val dispatchers: AppDispatchers,
) : ViewModel() {
    private fun today() = Instant.fromEpochMilliseconds(clock.nowMillis()).toLocalDateTime(TimeZone.currentSystemDefault()).date

    private var customRate = ""
    private val state = MutableStateFlow(repository.cachedSnapshot()?.toUiState(today()) ?: VpsCalculatorUiState(tradeDate = today()))
    val uiState: StateFlow<VpsCalculatorUiState> = state.asStateFlow()
    private var rateJob: Job? = null
    private var saveJob: Job? = null
    private var pendingDraft: VpsCalculatorDraft? = null

    init {
        if (state.value.draftReady) {
            refreshMissingRate()
        } else {
            viewModelScope.launch {
                val snapshot = withContext(dispatchers.io) { repository.loadSnapshot() }
                state.value = snapshot.toUiState(state.value.tradeDate)
                refreshMissingRate()
            }
        }
    }

    private fun refreshMissingRate() {
        if (!state.value.currency.isBaseCny && state.value.rateText.isBlank()) refreshRate(forceRefresh = false)
    }

    private fun VpsCalculatorSnapshot.toUiState(tradeDate: LocalDate): VpsCalculatorUiState {
        val currency = VpsCurrency.fromCode(draft.currencyCode)
        customRate = draft.customRateText.takeIf {
            !currency.isBaseCny && draft.customRateCurrencyCode == currency.code && it.isNotBlank()
        }.orEmpty()
        val rate = cachedRate
        return VpsCalculatorUiState(
            tradeDate = tradeDate,
            priceText = draft.priceText,
            currency = currency,
            cycle = VpsBillingCycle.fromDays(draft.cycleDays),
            dueDate = VpsCalculator.parseIsoDate(draft.dueDateIso),
            rateText = when {
                currency.isBaseCny -> BASE_RATE
                rate != null -> VpsCalculator.formatDecimal(rate.rate, 4)
                else -> customRate
            },
            rateFetchedAtMillis = cachedRate?.fetchedAtMillis,
            rateFromCache = cachedRate?.fromCache == true,
            premiumText = draft.premiumText,
            salePriceText = draft.salePriceText,
            lastEditedQuote = draft.lastEditedQuote,
            draftReady = true,
        ).syncQuote()
    }

    fun setPrice(value: String) = update(state.value.copy(priceText = value))
    fun setCycle(value: VpsBillingCycle) = update(state.value.copy(cycle = value), debounce = false)
    fun setTradeDate(value: LocalDate) = update(state.value.copy(tradeDate = value), debounce = false)
    fun setDueDate(value: LocalDate?) = update(state.value.copy(dueDate = value), debounce = false)
    fun clearDueDate() = setDueDate(null)

    fun setPremium(value: String) = update(state.value.copy(premiumText = value, lastEditedQuote = VpsQuoteEditedField.PREMIUM))
    fun setSalePrice(value: String) = update(state.value.copy(salePriceText = value, lastEditedQuote = VpsQuoteEditedField.SALE_PRICE))

    fun setRate(value: String) {
        if (state.value.currency.isBaseCny) return
        rateJob?.cancel()
        customRate = value
        update(state.value.copy(rateText = value, rateFetchedAtMillis = null, rateFromCache = false, rateNotice = null, isLoadingRate = false))
    }

    fun setCurrency(currency: VpsCurrency) {
        if (state.value.currency == currency) return
        rateJob?.cancel()
        customRate = ""
        update(
            state.value.copy(
                currency = currency,
                rateText = if (currency.isBaseCny) BASE_RATE else "",
                rateFetchedAtMillis = null,
                rateFromCache = false,
                rateNotice = null,
                isLoadingRate = false,
            ),
            debounce = false,
        )
        if (!currency.isBaseCny) refreshRate(forceRefresh = false)
    }

    fun refreshRate(forceRefresh: Boolean = true) {
        val currency = state.value.currency
        if (currency.isBaseCny) return
        rateJob?.cancel()
        state.value = state.value.copy(isLoadingRate = true, rateNotice = null)
        rateJob = viewModelScope.launch {
            val response = runCatchingExceptCancellation {
                withContext(dispatchers.io) { repository.getExchangeRate(currency, forceRefresh) }
            }.getOrElse { ExchangeRateResult.Failure(null, null) }
            val current = state.value.copy(isLoadingRate = false)
            when (response) {
                is ExchangeRateResult.Success -> {
                    customRate = ""
                    update(
                        current.copy(
                            rateText = VpsCalculator.formatDecimal(response.rate, 4),
                            rateFetchedAtMillis = response.fetchedAtMillis,
                            rateFromCache = response.fromCache,
                        ),
                        debounce = false,
                    )
                }

                is ExchangeRateResult.Failure -> showRateNotice(current, VpsRateNotice.FETCH_FAILED, response.cachedRate, response.cachedAtMillis)

                is ExchangeRateResult.RateLimited -> showRateNotice(current, VpsRateNotice.RATE_LIMITED, response.cachedRate, response.cachedAtMillis)
            }
        }
    }

    private fun showRateNotice(current: VpsCalculatorUiState, notice: VpsRateNotice, cached: Double?, fetchedAt: Long?) {
        val updated = if (current.rateText.isBlank() && cached != null) {
            current.copy(rateText = VpsCalculator.formatDecimal(cached, 4), rateFromCache = true, rateFetchedAtMillis = fetchedAt)
        } else {
            current
        }
        state.value = updated.copy(rateNotice = notice).syncQuote()
    }

    fun consumeRateNotice() {
        state.value = state.value.copy(rateNotice = null)
    }

    fun resetInputs() = update(
        state.value.copy(
            priceText = "",
            tradeDate = today(),
            dueDate = null,
            premiumText = "",
            salePriceText = "",
            lastEditedQuote = VpsQuoteEditedField.PREMIUM,
        ),
        debounce = false,
    )

    private fun update(next: VpsCalculatorUiState, debounce: Boolean = true) {
        val synced = next.syncQuote()
        state.value = synced
        val draft = VpsCalculatorDraft(
            priceText = synced.priceText,
            currencyCode = synced.currency.code,
            cycleDays = synced.cycle.days,
            dueDateIso = synced.dueDate?.let(VpsCalculator::formatIsoDate),
            customRateText = customRate,
            customRateCurrencyCode = synced.currency.code.takeIf { customRate.isNotBlank() },
            premiumText = synced.premiumText,
            salePriceText = synced.salePriceText,
            lastEditedQuote = synced.lastEditedQuote,
        )
        pendingDraft = draft
        saveJob?.cancel()
        saveJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                if (debounce) delay(300)
            } finally {
                // A newer edit supersedes this one. Leaving the page flushes the final edit.
                if (pendingDraft === draft) {
                    withContext(NonCancellable) { repository.saveDraft(draft) }
                }
            }
        }
    }

    companion object {
        private const val BASE_RATE = "1.0000"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { VpsCalculatorViewModel(requireNotNull(container.vpsCalculatorRepository), container.clock, container.dispatchers) }
        }
    }
}
