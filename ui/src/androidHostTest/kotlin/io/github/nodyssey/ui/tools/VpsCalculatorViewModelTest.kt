package io.github.nodyssey.ui.tools

import io.github.nodyssey.core.VpsBillingCycle
import io.github.nodyssey.core.VpsCalculatorDraft
import io.github.nodyssey.core.VpsCurrency
import io.github.nodyssey.core.VpsQuoteEditedField
import io.github.nodyssey.data.calculator.ExchangeRateResult
import io.github.nodyssey.data.calculator.VpsCalculatorRepository
import io.github.nodyssey.data.calculator.VpsCalculatorSnapshot
import io.github.nodyssey.ui.ViewModels
import io.github.plaza.core.AppClock
import io.github.plaza.core.AppDispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class VpsCalculatorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModels()
    private val today = LocalDate(2026, 3, 1)
    private val expiry = LocalDate(2026, 3, 11)
    private val clock = AppClock { today.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() }
    private val repository = FakeVpsCalculatorRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        models.clear(dispatcher.scheduler)
        Dispatchers.resetMain()
    }

    private fun open(source: VpsCalculatorRepository = repository) = models.track(VpsCalculatorViewModel(source, clock, AppDispatchers(dispatcher, dispatcher, dispatcher)))

    private val snapshot = VpsCalculatorSnapshot(
        VpsCalculatorDraft(priceText = "39"),
        ExchangeRateResult.Success(6.6793, 1234L, true),
    )

    @Test
    fun `memory snapshot is ready before any coroutine runs`() = runTest(dispatcher) {
        val source = object : VpsCalculatorRepository by repository {
            override fun cachedSnapshot() = snapshot
            override suspend fun loadSnapshot(): VpsCalculatorSnapshot = error("Unexpected cold load")
        }
        val state = open(source).uiState.value
        assertTrue(state.draftReady)
        assertEquals("39", state.priceText)
        assertEquals("6.6793", state.rateText)
        runCurrent()
        assertTrue(repository.requests.isEmpty())
    }

    @Test
    fun `cold snapshot restores the draft and cached rate in one emission`() = runTest(dispatcher) {
        var loads = 0
        val source = object : VpsCalculatorRepository by repository {
            override suspend fun loadSnapshot(): VpsCalculatorSnapshot {
                loads++
                return snapshot
            }
        }
        val vm = open(source)
        val states = mutableListOf<VpsCalculatorUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.toList(states) }
        runCurrent()
        assertEquals(1, loads)
        assertEquals(listOf(false, true), states.map { it.draftReady })
        assertTrue(states.none { it.isLoadingRate })
        assertEquals("39", states.last().priceText)
        assertEquals("6.6793", states.last().rateText)
        assertEquals(1234L, states.last().rateFetchedAtMillis)
        assertTrue(states.last().rateFromCache)
        assertTrue(repository.requests.isEmpty())
    }

    @Test
    fun `new draft starts without expiry and requests the USD rate without charging a refresh`() = runTest(dispatcher) {
        val vm = open()
        runCurrent()
        assertEquals(today, vm.uiState.value.tradeDate)
        assertNull(vm.uiState.value.dueDate)
        assertFalse(vm.uiState.value.result.hasValidDates)
        assertEquals(listOf(VpsCurrency.USD to false), repository.requests)
        assertEquals("7.0000", vm.uiState.value.rateText)
    }

    @Test
    fun `active quote survives changes to price rate cycle and dates and the draft records the inputs`() = runTest(dispatcher) {
        val vm = open()
        runCurrent()
        vm.setPrice("365")
        vm.setDueDate(expiry)
        assertEquals(expiry.toString(), repository.draft.value.dueDateIso)
        assertEquals("365", repository.draft.value.priceText)
        assertEquals(70.0, requireNotNull(vm.uiState.value.result.remainingCny), 0.00001)

        vm.setPremium("5")
        assertEquals("75.00", vm.uiState.value.salePriceText)
        vm.setPremium("-5")
        assertEquals("65.00", vm.uiState.value.salePriceText)
        vm.setPrice("730")
        assertEquals("135.00", vm.uiState.value.salePriceText)
        vm.setSalePrice("125")
        assertEquals("-15.00", vm.uiState.value.premiumText)
        vm.setRate("5")
        assertEquals("25.00", vm.uiState.value.premiumText)
        vm.setTradeDate(LocalDate(2026, 3, 6))
        assertEquals("75.00", vm.uiState.value.premiumText)
        vm.setCycle(VpsBillingCycle.BIENNIALLY)
        assertEquals("100.00", vm.uiState.value.premiumText)
        vm.setSalePrice("-10")
        assertEquals("", vm.uiState.value.premiumText)
        vm.setPremium("-")
        assertEquals("", vm.uiState.value.salePriceText)
        vm.setSalePrice("")
        assertEquals("", vm.uiState.value.premiumText)
    }

    @Test
    fun `currency switch discards an outstanding response and CNY stays local`() = runTest(dispatcher) {
        val waiting = CompletableDeferred<ExchangeRateResult>()
        repository.respond = { waiting.await() }
        val vm = open()
        runCurrent()
        assertTrue(vm.uiState.value.isLoadingRate)
        vm.setCurrency(VpsCurrency.CNY)
        vm.refreshRate()
        waiting.complete(ExchangeRateResult.Success(3.4, 1234L, false))
        runCurrent()
        assertEquals("1.0000", vm.uiState.value.rateText)
        assertFalse(vm.uiState.value.isLoadingRate)
        assertNull(vm.uiState.value.rateFetchedAtMillis)
        repository.respond = { ExchangeRateResult.Success(8.12345, 1234L, true) }
        vm.setCurrency(VpsCurrency.EUR)
        runCurrent()
        assertEquals(listOf(VpsCurrency.USD to false, VpsCurrency.EUR to false), repository.requests)
        assertEquals("8.12345", vm.uiState.value.rateText)
        assertTrue(vm.uiState.value.rateFromCache)
    }

    @Test
    fun `manual refresh preserves user input and falls back to cached rate when blank`() = runTest(dispatcher) {
        val vm = open()
        runCurrent()
        vm.setRate("7.1")
        repository.respond = { ExchangeRateResult.RateLimited(6.5, 5678L) }
        vm.refreshRate()
        runCurrent()
        assertEquals("7.1", vm.uiState.value.rateText)
        assertEquals(VpsRateNotice.RATE_LIMITED, vm.uiState.value.rateNotice)
        vm.consumeRateNotice()
        assertNull(vm.uiState.value.rateNotice)

        repository.respond = { ExchangeRateResult.Failure(6.5, 5678L) }
        vm.refreshRate()
        runCurrent()
        assertEquals(VpsRateNotice.FETCH_FAILED, vm.uiState.value.rateNotice)
        assertEquals("7.1", vm.uiState.value.rateText)
        vm.setRate("")
        vm.refreshRate()
        runCurrent()
        assertEquals("6.5000", vm.uiState.value.rateText)
        assertTrue(vm.uiState.value.rateFromCache)
        assertEquals(5678L, vm.uiState.value.rateFetchedAtMillis)
        assertEquals(VpsCurrency.USD to true, repository.requests.last())
    }

    @Test
    fun `reopening a draft retains its dates and quote and reset clears the saved dates`() = runTest(dispatcher) {
        val vm = open()
        runCurrent()
        val trade = LocalDate(2026, 3, 6)
        vm.setCycle(VpsBillingCycle.MONTHLY)
        vm.setTradeDate(trade)
        vm.setDueDate(expiry)
        vm.setRate("6")
        vm.setPrice("30")
        vm.setSalePrice("55")
        advanceUntilIdle()
        val restored = open()
        runCurrent()
        val state = restored.uiState.value
        assertEquals(expiry, state.dueDate)
        assertEquals(trade, state.tradeDate)
        assertEquals("30", state.priceText)
        assertEquals(VpsCurrency.USD, state.currency)
        assertEquals(VpsBillingCycle.MONTHLY, state.cycle)
        assertEquals(VpsQuoteEditedField.SALE_PRICE, state.lastEditedQuote)
        assertEquals("55", state.salePriceText)
        assertEquals("25.00", state.premiumText)
        assertEquals("6", state.rateText)
        assertEquals(1, repository.requests.size)
        restored.setTradeDate(expiry)
        restored.resetInputs()
        assertEquals(today, restored.uiState.value.tradeDate)
        assertNull(repository.draft.value.dueDateIso)
        assertNull(repository.draft.value.tradeDateIso)
        assertEquals("", repository.draft.value.priceText)
        assertEquals("6", restored.uiState.value.rateText)
    }

    @Test
    fun `converted amounts use the full automatic rate until the user edits it`() = runTest(dispatcher) {
        repository.respond = { ExchangeRateResult.Success(0.004850123456, 1234L, false) }
        val vm = open()
        runCurrent()
        vm.setCurrency(VpsCurrency.KRW)
        vm.setPrice("10000000")
        runCurrent()

        val state = vm.uiState.value
        assertEquals(48501.23456, requireNotNull(state.result.priceCny), 0.00001)
        vm.setRate("0.005")
        assertEquals(50000.0, requireNotNull(vm.uiState.value.result.priceCny), 0.00001)
    }

    @Test
    fun `a failed draft write keeps the edit and the next edit retries the write`() = runTest(dispatcher) {
        var writable = false
        val source = object : VpsCalculatorRepository by repository {
            override suspend fun saveDraft(draft: VpsCalculatorDraft) {
                if (!writable) throw IOException("No space left on device")
                repository.saveDraft(draft)
            }
        }
        val vm = open(source)
        runCurrent()
        vm.setPrice("30")
        advanceUntilIdle()
        assertEquals("30", vm.uiState.value.priceText)
        assertTrue(vm.uiState.value.draftSaveFailed)

        writable = true
        vm.setPrice("31")
        advanceUntilIdle()
        assertEquals("31", repository.draft.value.priceText)
        assertFalse(vm.uiState.value.draftSaveFailed)
    }

    @Test
    fun `leaving during the typing delay flushes only the latest input`() = runTest(dispatcher) {
        val vm = open()
        runCurrent()
        vm.setPrice("3")
        vm.setPrice("30")
        runCurrent()
        assertEquals("", repository.draft.value.priceText)
        models.clear(dispatcher.scheduler)
        assertEquals("30", repository.draft.value.priceText)
    }
}

internal class FakeVpsCalculatorRepository : VpsCalculatorRepository {
    override val draft = MutableStateFlow(VpsCalculatorDraft())
    val requests = mutableListOf<Pair<VpsCurrency, Boolean>>()
    var respond: suspend () -> ExchangeRateResult = { ExchangeRateResult.Success(7.0, 1234L, false) }

    override suspend fun saveDraft(draft: VpsCalculatorDraft) {
        this.draft.value = draft
    }

    override suspend fun getExchangeRate(currency: VpsCurrency, forceRefresh: Boolean): ExchangeRateResult {
        requests += currency to forceRefresh
        return respond()
    }
}
