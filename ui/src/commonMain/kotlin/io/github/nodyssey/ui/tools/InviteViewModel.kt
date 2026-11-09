package io.github.nodyssey.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.InviteCode
import io.github.nodyssey.data.InviteCurrency
import io.github.nodyssey.data.InvitePricing
import io.github.nodyssey.data.InvitePurchaseResult
import io.github.nodyssey.data.InviteRepository
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.postlist.toSiteError
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What a purchase ended in, held until the reader dismisses it.
 *
 * A lost response leaves a third possibility: the site may have charged the account without the app
 * receiving confirmation. Only an explicit refusal can safely say nothing was bought.
 */
sealed interface InviteOutcome {
    /**
     * Bought.
     *
     * [code] is what the list answered with afterwards — the purchase itself does not return the code,
     * and the site reads it back the same way — or null when the list could not be read in time.
     */
    data class Bought(val currency: InviteCurrency, val code: String?) : InviteOutcome

    /** Refused. [detail] is the site's own sentence when it gave one ("鸡腿数目不足"). */
    data class Refused(val error: SiteError, val detail: String? = null) : InviteOutcome

    data class Unknown(val currency: InviteCurrency) : InviteOutcome
}

data class InviteUiState(
    /** Null until the price list answers, or when it could not be read at all. */
    val pricing: InvitePricing? = null,
    val chickenCount: Int? = null,
    val stardustCount: Int? = null,
    val codes: List<InviteCode> = emptyList(),
    /** How many codes this account has bought, which can exceed the one page [codes] holds. */
    val codesTotal: Int = 0,
    val codesLoaded: Boolean = false,
    val isLoading: Boolean = true,
    /** The currency of the purchase in flight, or null when nothing is. */
    val isBuying: InviteCurrency? = null,
    val error: SiteError? = null,
    val codesError: SiteError? = null,
    /** Survives dismissing the result dialog so another purchase still warns about a repeat spend. */
    val purchaseUncertain: Boolean = false,
    val outcome: InviteOutcome? = null,
) {
    fun balanceOf(currency: InviteCurrency): Int? =
        when (currency) {
            InviteCurrency.COIN -> chickenCount
            InviteCurrency.STARDUST -> stardustCount
        }
}

/**
 * 邀请好友.
 *
 * The screen used to know exactly one number — the chicken balance behind a confirmation that ended by
 * opening the site's own page. It now owns the whole purchase: the two prices the site is selling at,
 * both balances, the confirmation per currency, and the list of codes afterwards. That list is why
 * buying and seeing the result happen in one place: the purchase answers with a balance and nothing
 * else, so the code can only come from re-reading it.
 *
 * The three reads are allowed to fail independently. A missing balance degrades to null and the screen
 * says nothing; prices and codes have independent error messages and recovery actions.
 */
class InviteViewModel(
    private val profileRepository: ProfileRepository,
    private val inviteRepository: InviteRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(InviteUiState())
    val uiState: StateFlow<InviteUiState> = _uiState.asStateFlow()
    private var refreshing = false
    private var pendingPurchase: PendingPurchase? = null

    init {
        refresh()
    }

    fun refresh() {
        if (refreshing || _uiState.value.isBuying != null) return
        refreshing = true
        _uiState.update { it.copy(isLoading = true, error = null, codesError = null) }
        viewModelScope.launch {
            try {
                val pricing = runCatchingExceptCancellation { inviteRepository.pricing() }
                val codes = runCatchingExceptCancellation { inviteRepository.codes() }
                val profile = runCatchingExceptCancellation { profileRepository.profile(refresh = true) }
                val pending = pendingPurchase
                val newCode = pending?.let { newestCode(it.knownCodes, codes.getOrNull()?.codes) }
                if (newCode != null) pendingPurchase = null
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        // A reload that fails keeps what the last good read said: the price is not a
                        // setting, so a stale one is closer to the truth than a screen with no buttons.
                        pricing = pricing.getOrNull() ?: state.pricing,
                        codes = codes.getOrNull()?.codes ?: state.codes,
                        codesTotal = codes.getOrNull()?.total ?: state.codesTotal,
                        codesLoaded = codes.isSuccess || state.codesLoaded,
                        chickenCount = profile.getOrNull()?.chickenCount ?: state.chickenCount,
                        stardustCount = profile.getOrNull()?.starCount ?: state.stardustCount,
                        error = pricing.exceptionOrNull()?.toSiteError(),
                        codesError = codes.exceptionOrNull()?.toSiteError(),
                        purchaseUncertain = state.purchaseUncertain && newCode == null,
                        outcome = if (newCode != null) InviteOutcome.Bought(pending.currency, newCode) else state.outcome,
                    )
                }
            } finally {
                refreshing = false
            }
        }
    }

    /**
     * Buys one code.
     *
     * The balance the answer carries is written back for the currency that paid, and the list is read
     * again for the code — in that order, so a failed second read still leaves the balance right.
     * Ignored while another purchase is in flight: two taps are two codes and two spends.
     */
    fun buy(currency: InviteCurrency) {
        if (_uiState.value.isBuying != null || refreshing) return
        val known = _uiState.value.takeIf { it.codesLoaded }?.codes?.mapTo(mutableSetOf()) { it.code }
        // Written before the launch rather than inside it: a second tap in the same frame would
        // otherwise read a state the first coroutine has not reached yet, and two taps would be two
        // codes and two spends.
        _uiState.update { it.copy(isBuying = currency, outcome = null) }
        viewModelScope.launch {
            val result = runCatchingExceptCancellation { inviteRepository.buy(currency) }.getOrNull()
            if (result is InvitePurchaseResult.Refused) {
                _uiState.update {
                    it.copy(isBuying = null, outcome = InviteOutcome.Refused(result.error, result.detail))
                }
                return@launch
            }
            val bought = result as? InvitePurchaseResult.Bought
            pendingPurchase = PendingPurchase(currency, known)
            // Write the confirmed balance before reading again, so failed reads cannot lose it.
            _uiState.update { state ->
                state.copy(
                    chickenCount = if (currency == InviteCurrency.COIN) bought?.balance ?: state.chickenCount else state.chickenCount,
                    stardustCount = if (currency == InviteCurrency.STARDUST) bought?.balance ?: state.stardustCount else state.stardustCount,
                )
            }
            // A lost response may follow a completed spend. Re-read without ever retrying the POST.
            val codes = runCatchingExceptCancellation { inviteRepository.codes() }
            val profile = runCatchingExceptCancellation { profileRepository.profile(refresh = true) }.getOrNull()
            val newCode = newestCode(known, codes.getOrNull()?.codes)
            if (newCode != null) pendingPurchase = null
            _uiState.update { state ->
                state.copy(
                    isBuying = null,
                    chickenCount =
                    if (currency == InviteCurrency.COIN && bought?.balance != null) {
                        bought.balance
                    } else {
                        profile?.chickenCount ?: state.chickenCount
                    },
                    stardustCount =
                    if (currency == InviteCurrency.STARDUST && bought?.balance != null) {
                        bought.balance
                    } else {
                        profile?.starCount ?: state.stardustCount
                    },
                    codes = codes.getOrNull()?.codes ?: state.codes,
                    codesTotal = codes.getOrNull()?.total ?: state.codesTotal,
                    codesLoaded = codes.isSuccess || state.codesLoaded,
                    codesError = codes.exceptionOrNull()?.toSiteError(),
                    purchaseUncertain = bought == null && newCode == null,
                    outcome =
                    if (bought != null || newCode != null) {
                        InviteOutcome.Bought(currency, newCode)
                    } else {
                        InviteOutcome.Unknown(currency)
                    },
                )
            }
        }
    }

    fun dismissOutcome() = _uiState.update { it.copy(outcome = null) }

    /**
     * The code this purchase created.
     *
     * The list is newest first, so the answer is normally its first row; comparing against what was
     * there before the call is what keeps a stale page from naming somebody's older code as the new one.
     */
    private fun newestCode(known: Set<String>?, after: List<InviteCode>?): String? {
        // A failed initial list read is not an empty account: without a baseline we cannot identify
        // which code this purchase created. A stale list must never name an old code as the new one.
        if (known == null) return null
        return after?.firstOrNull { it.code !in known }?.code
    }

    private data class PendingPurchase(val currency: InviteCurrency, val knownCodes: Set<String>?)

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    InviteViewModel(
                        profileRepository = container.profileRepository,
                        inviteRepository = container.inviteRepository,
                    )
                }
            }
    }
}
