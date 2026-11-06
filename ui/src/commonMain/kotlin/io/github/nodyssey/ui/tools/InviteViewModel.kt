package io.github.nodyssey.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.InviteCode
import io.github.nodyssey.data.InviteCurrency
import io.github.nodyssey.data.InvitePricing
import io.github.nodyssey.data.InviteRepository
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.postlist.toSiteError
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What a purchase ended in, held until the reader dismisses it.
 *
 * The two halves are the two things the site can say: it took the money, or it refused and gave a
 * reason. A screen that only had the balanced to look at could not tell "bought and the list has not
 * caught up" from "nothing happened".
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
}

data class InviteUiState(
    /** Null until the price list answers, or when it could not be read at all. */
    val pricing: InvitePricing? = null,
    val chickenCount: Int? = null,
    val stardustCount: Int? = null,
    val codes: List<InviteCode> = emptyList(),
    /** How many codes this account has bought, which can exceed the one page [codes] holds. */
    val codesTotal: Int = 0,
    val isLoading: Boolean = true,
    /** The currency of the purchase in flight, or null when nothing is. */
    val isBuying: InviteCurrency? = null,
    val error: SiteError? = null,
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
 * says nothing; a missing price list is what takes the buttons away, and a missing list is an empty
 * card — neither is worth failing the whole screen over.
 */
class InviteViewModel(
    private val profileRepository: ProfileRepository,
    private val inviteRepository: InviteRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(InviteUiState())
    val uiState: StateFlow<InviteUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val pricing = runCatchingExceptCancellation { inviteRepository.pricing() }
            val codes = runCatchingExceptCancellation { inviteRepository.codes() }
            val profile = runCatchingExceptCancellation { profileRepository.profile() }
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    // A reload that fails keeps what the last good read said: the price is not a
                    // setting, so a stale one is closer to the truth than a screen with no buttons.
                    pricing = pricing.getOrNull() ?: state.pricing,
                    codes = codes.getOrNull()?.codes ?: state.codes,
                    codesTotal = codes.getOrNull()?.total ?: state.codesTotal,
                    chickenCount = profile.getOrNull()?.chickenCount ?: state.chickenCount,
                    stardustCount = profile.getOrNull()?.starCount ?: state.stardustCount,
                    error = pricing.exceptionOrNull()?.toSiteError() ?: codes.exceptionOrNull()?.toSiteError(),
                )
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
        if (_uiState.value.isBuying != null) return
        // Written before the launch rather than inside it: a second tap in the same frame would
        // otherwise read a state the first coroutine has not reached yet, and two taps would be two
        // codes and two spends.
        _uiState.update { it.copy(isBuying = currency, outcome = null) }
        viewModelScope.launch {
            val known = _uiState.value.codes
            val result = runCatchingExceptCancellation { inviteRepository.buy(currency) }
            val balance = result.getOrNull()
            val page =
                if (result.isSuccess) {
                    runCatchingExceptCancellation { inviteRepository.codes() }.getOrNull()
                } else {
                    null
                }
            _uiState.update { state ->
                state.copy(
                    isBuying = null,
                    chickenCount = if (currency == InviteCurrency.COIN) balance ?: state.chickenCount else state.chickenCount,
                    stardustCount = if (currency == InviteCurrency.STARDUST) balance ?: state.stardustCount else state.stardustCount,
                    codes = page?.codes ?: state.codes,
                    codesTotal = page?.total ?: state.codesTotal,
                    outcome =
                    result.fold(
                        onSuccess = { InviteOutcome.Bought(currency, newestCode(known, page?.codes)) },
                        onFailure = {
                            InviteOutcome.Refused(it.toSiteError(), (it as? SiteException)?.detail)
                        },
                    ),
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
    private fun newestCode(known: List<InviteCode>, after: List<InviteCode>?): String? {
        val updated = after ?: return null
        val seen = known.mapTo(mutableSetOf()) { it.code }
        return updated.firstOrNull { it.code !in seen }?.code ?: updated.firstOrNull()?.code
    }

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
