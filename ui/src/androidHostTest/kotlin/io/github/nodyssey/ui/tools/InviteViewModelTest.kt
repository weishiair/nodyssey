package io.github.nodyssey.ui.tools

import io.github.nodyssey.data.InviteCode
import io.github.nodyssey.data.InviteCodePage
import io.github.nodyssey.data.InviteCurrency
import io.github.nodyssey.data.InvitePrice
import io.github.nodyssey.data.InvitePricing
import io.github.nodyssey.data.InvitePurchaseResult
import io.github.nodyssey.data.InviteRepository
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.UserProfile
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The purchase behind 邀请好友, which spends real 鸡腿 and real 星辰.
 *
 * The cases that matter are the ones where a mistake cannot be undone by looking again: which balance
 * the answer moves, whether a refusal is reported as one, and that two taps are one purchase. The last
 * one is not hypothetical — the guard has to be written before the coroutine starts, because a second
 * tap in the same frame would otherwise read a state the first one has not reached yet.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InviteViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        repository: FakeInviteRepository,
        chicken: Int? = 1_165,
        stardust: Int? = 1,
    ) = InviteViewModel(FakeProfileRepository(chicken = chicken, stardust = stardust), repository)

    @Test
    fun `writes back the balance that paid and names the code the list gained`() =
        runTest(dispatcher) {
            val repository =
                FakeInviteRepository(
                    codesPage = Result.success(InviteCodePage(listOf(CODE_OLD), total = 1)),
                    buyResult = Result.success(InvitePurchaseResult.Bought(65)),
                    codesAfterBuy = Result.success(InviteCodePage(listOf(CODE_NEW, CODE_OLD), total = 2)),
                )
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.buy(InviteCurrency.COIN)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(65, state.chickenCount)
            // The other currency did not pay and must not move.
            assertEquals(1, state.stardustCount)
            assertEquals(listOf("NEW-CODE", "OLD-CODE"), state.codes.map { it.code })
            assertEquals(2, state.codesTotal)
            assertEquals(InviteOutcome.Bought(InviteCurrency.COIN, "NEW-CODE"), state.outcome)
            assertNull(state.isBuying)
        }

    @Test
    fun `reports a refusal with the site's sentence and leaves both balances alone`() =
        runTest(dispatcher) {
            val repository =
                FakeInviteRepository(
                    buyResult = Result.success(InvitePurchaseResult.Refused(SiteError.Unknown, "星辰数目不足")),
                )
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.buy(InviteCurrency.STARDUST)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(1, state.stardustCount)
            assertEquals(1_165, state.chickenCount)
            assertEquals(InviteOutcome.Refused(SiteError.Unknown, "星辰数目不足"), state.outcome)
        }

    /**
     * A purchase whose list re-read was not needed to know it happened.
     *
     * The dialog has to say the code is somewhere rather than inventing one or claiming nothing was
     * bought — the balance already moved.
     */
    @Test
    fun `reports a purchase whose code the list could not show`() =
        runTest(dispatcher) {
            val repository =
                FakeInviteRepository(
                    buyResult = Result.success(InvitePurchaseResult.Bought(1_065)),
                    codesAfterBuy = Result.failure(SiteException(SiteError.Network)),
                )
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.buy(InviteCurrency.COIN)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(1_065, state.chickenCount)
            assertEquals(InviteOutcome.Bought(InviteCurrency.COIN, code = null), state.outcome)
        }

    @Test
    fun `ignores a second tap while a purchase is in flight`() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val repository = FakeInviteRepository(buyResult = Result.success(InvitePurchaseResult.Bought(1_065)), gate = gate)
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.buy(InviteCurrency.COIN)
            viewModel.buy(InviteCurrency.COIN)
            advanceUntilIdle()

            assertEquals(1, repository.buys)

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, repository.buys)
            assertEquals(InviteOutcome.Bought(InviteCurrency.COIN, code = null), viewModel.uiState.value.outcome)
        }

    /**
     * A reload that fails keeps the prices the last good read gave.
     *
     * Dropping them would take both buttons away over a network blip, and a price the site set a minute
     * ago is still closer to the truth than a screen with nothing on it.
     */
    @Test
    fun `keeps the last good prices when a reload fails`() =
        runTest(dispatcher) {
            val repository = FakeInviteRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()
            assertEquals(1_000, viewModel.uiState.value.pricing?.coin?.cost)

            repository.pricing = Result.failure(SiteException(SiteError.Network))
            viewModel.refresh()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(1_000, state.pricing?.coin?.cost)
            assertEquals(100, state.pricing?.stardust?.cost)
            assertEquals(SiteError.Network, state.error)
        }

    // Bug: the fallback returned an already used code when the read after purchase was stale.
    @Test
    fun `a stale list never names an old code as the one just bought`() =
        runTest(dispatcher) {
            val repository = FakeInviteRepository(
                codesPage = Result.success(InviteCodePage(listOf(CODE_OLD), total = 1)),
                buyResult = Result.success(InvitePurchaseResult.Bought(65)),
            )
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.buy(InviteCurrency.COIN)
            advanceUntilIdle()

            assertEquals(InviteOutcome.Bought(InviteCurrency.COIN, code = null), viewModel.uiState.value.outcome)
            assertEquals(65, viewModel.uiState.value.chickenCount)
        }

    // Bug: an unsuccessful first list read looked like an empty account and made every code new.
    @Test
    fun `a missing baseline cannot identify an existing code as a new purchase`() =
        runTest(dispatcher) {
            val repository = FakeInviteRepository(
                codesPage = Result.failure(SiteException(SiteError.Network)),
                codesAfterBuy = Result.success(InviteCodePage(listOf(CODE_OLD), total = 1)),
            )
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.buy(InviteCurrency.COIN)
            advanceUntilIdle()

            assertEquals(InviteOutcome.Bought(InviteCurrency.COIN, code = null), viewModel.uiState.value.outcome)
            assertEquals(listOf(CODE_OLD), viewModel.uiState.value.codes)
        }

    // Bug: a response lost after charging the account was reported as a refusal with a retry hint.
    @Test
    fun `a lost purchase response is reconciled with the new code without posting again`() =
        runTest(dispatcher) {
            val repository = FakeInviteRepository(
                codesPage = Result.success(InviteCodePage(listOf(CODE_OLD), total = 1)),
                buyResult = Result.failure(SiteException(SiteError.Network)),
                codesAfterBuy = Result.success(InviteCodePage(listOf(CODE_NEW, CODE_OLD), total = 2)),
            )
            val profile = FakeProfileRepository(chicken = 1_165, stardust = 1)
            val viewModel = InviteViewModel(profile, repository)
            advanceUntilIdle()
            profile.chicken = 165

            viewModel.buy(InviteCurrency.COIN)
            advanceUntilIdle()

            assertEquals(InviteOutcome.Bought(InviteCurrency.COIN, "NEW-CODE"), viewModel.uiState.value.outcome)
            assertEquals(165, viewModel.uiState.value.chickenCount)
            assertFalse(viewModel.uiState.value.purchaseUncertain)
            assertEquals(1, repository.buys)
            assertEquals(listOf(true, true), profile.refreshes)
        }

    @Test
    fun `an unresolved purchase keeps its warning after dismissal and can be checked again`() =
        runTest(dispatcher) {
            val repository = FakeInviteRepository(
                codesPage = Result.success(InviteCodePage(listOf(CODE_OLD), total = 1)),
                buyResult = Result.failure(SiteException(SiteError.Network)),
            )
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.buy(InviteCurrency.COIN)
            advanceUntilIdle()
            assertEquals(InviteOutcome.Unknown(InviteCurrency.COIN), viewModel.uiState.value.outcome)
            viewModel.dismissOutcome()
            viewModel.refresh()
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.purchaseUncertain)
            assertNull(viewModel.uiState.value.outcome)

            repository.codesAfterBuy = Result.success(InviteCodePage(listOf(CODE_NEW, CODE_OLD), total = 2))
            viewModel.refresh()
            advanceUntilIdle()

            assertEquals(InviteOutcome.Bought(InviteCurrency.COIN, "NEW-CODE"), viewModel.uiState.value.outcome)
            assertFalse(viewModel.uiState.value.purchaseUncertain)
            assertEquals(1, repository.buys)
        }

    // Bug: the error after a confirmed spend was discarded, leaving no way to recover its code.
    @Test
    fun `retrying a failed purchase read recovers the code without another purchase`() =
        runTest(dispatcher) {
            val repository = FakeInviteRepository(
                codesPage = Result.success(InviteCodePage(listOf(CODE_OLD), total = 1)),
                buyResult = Result.success(InvitePurchaseResult.Bought(65)),
                codesAfterBuy = Result.failure(SiteException(SiteError.Network)),
            )
            val viewModel = viewModel(repository)
            advanceUntilIdle()
            viewModel.buy(InviteCurrency.COIN)
            advanceUntilIdle()
            assertEquals(SiteError.Network, viewModel.uiState.value.codesError)
            assertEquals(65, viewModel.uiState.value.chickenCount)

            repository.codesAfterBuy = Result.success(InviteCodePage(listOf(CODE_NEW, CODE_OLD), total = 2))
            viewModel.dismissOutcome()
            viewModel.refresh()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.codesError)
            assertEquals(InviteOutcome.Bought(InviteCurrency.COIN, "NEW-CODE"), viewModel.uiState.value.outcome)
            assertEquals(1, repository.buys)
        }

    private companion object {
        val CODE_OLD = InviteCode("OLD-CODE", applied = true, createdAtMillis = 1_784_804_442_000)
        val CODE_NEW = InviteCode("NEW-CODE", applied = false, createdAtMillis = 1_793_936_053_000)
    }
}

internal class FakeInviteRepository(
    var pricing: Result<InvitePricing> =
        Result.success(
            InvitePricing(
                coin = InvitePrice(cost = 1_000, enabled = true),
                stardust = InvitePrice(cost = 100, enabled = true),
            ),
        ),
    var codesPage: Result<InviteCodePage> = Result.success(InviteCodePage(emptyList(), total = 0)),
    var buyResult: Result<InvitePurchaseResult> = Result.success(InvitePurchaseResult.Bought(null)),
    /** What the re-read after a purchase answers; null falls back to [codesPage]. */
    var codesAfterBuy: Result<InviteCodePage>? = null,
    /** Suspends the purchase so a second tap can be tried while it is in flight. */
    var gate: CompletableDeferred<Unit>? = null,
) : InviteRepository {
    var buys = 0
        private set

    private var bought = false

    override suspend fun pricing(): InvitePricing = pricing.getOrThrow()

    override suspend fun codes(page: Int): InviteCodePage =
        (if (bought) codesAfterBuy ?: codesPage else codesPage).getOrThrow()

    override suspend fun buy(currency: InviteCurrency): InvitePurchaseResult {
        buys++
        gate?.await()
        bought = true
        return buyResult.getOrThrow()
    }
}

internal class FakeProfileRepository(
    var chicken: Int?,
    private val stardust: Int?,
) : ProfileRepository {
    override val selfUid = MutableStateFlow<Long?>(9)
    val refreshes = mutableListOf<Boolean>()

    override suspend fun profile(refresh: Boolean): UserProfile =
        UserProfile(
            uid = 9,
            name = "viewer",
            avatarUrl = "",
            rank = 3,
            chickenCount = chicken,
            starCount = stardust,
        ).also { refreshes += refresh }

    override suspend fun profile(uid: Long): UserProfile = profile(refresh = false)
}
