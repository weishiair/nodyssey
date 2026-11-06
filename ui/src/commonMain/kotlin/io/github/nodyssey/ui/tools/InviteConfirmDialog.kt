package io.github.nodyssey.ui.tools

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.nodyssey.data.InviteCurrency
import io.github.nodyssey.ui.common.SpendConfirmDialog
import io.github.nodyssey.ui.common.SpendDetail
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.invite_balance_after
import io.github.nodyssey.ui.resources.invite_caution
import io.github.nodyssey.ui.resources.invite_caution_stardust
import io.github.nodyssey.ui.resources.invite_confirm
import io.github.nodyssey.ui.resources.invite_confirm_title
import io.github.nodyssey.ui.resources.invite_confirm_title_stardust
import io.github.nodyssey.ui.resources.invite_cost
import io.github.nodyssey.ui.resources.invite_cost_value
import io.github.nodyssey.ui.resources.invite_cost_value_stardust
import io.github.nodyssey.ui.resources.invite_repeat_purchase_caution
import io.github.nodyssey.ui.resources.invite_short_hint
import io.github.nodyssey.ui.resources.invite_short_hint_stardust
import io.github.nodyssey.ui.resources.invite_short_title
import io.github.nodyssey.ui.resources.invite_short_title_stardust
import io.github.nodyssey.ui.resources.invite_shortfall
import io.github.nodyssey.ui.resources.invite_shortfall_label
import io.github.nodyssey.ui.resources.invite_shortfall_stardust
import io.github.nodyssey.ui.resources.spend_current_balance
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.theme.PlazaTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Buying one invite code, confirmed the way every other irreversible spend is.
 *
 * Two currencies and one shape: what leaves the account, what is left afterwards, and that neither can
 * be undone. The wording is per currency rather than one sentence with a unit spliced into it, because
 * 鸡腿 and 星辰 are earned in different ways — the short-balance hint has to say which one to go and
 * earn, and a shared sentence could only ever name one of them.
 *
 * [cost] comes from the site rather than from a constant here: 1000 鸡腿 and 100 星辰 are what it was
 * selling for on 2026-11-06, and what it sells for tomorrow is its decision.
 */
@Composable
fun InviteConfirmDialog(
    currency: InviteCurrency,
    cost: Int,
    balance: Int?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    previousPurchaseUncertain: Boolean = false,
) {
    val shortfall = balance?.let { (cost - it).takeIf { gap -> gap > 0 } }
    if (balance != null && shortfall != null) {
        SpendConfirmDialog(
            title = stringResource(shortTitle(currency)),
            details =
            listOf(
                SpendDetail(stringResource(Res.string.spend_current_balance), balance.toString()),
                SpendDetail(stringResource(Res.string.invite_cost), stringResource(costValue(currency), cost)),
                SpendDetail(
                    stringResource(Res.string.invite_shortfall_label),
                    shortfall.toString(),
                    separated = true,
                    isError = true,
                ),
            ),
            caution = stringResource(shortHint(currency)),
            confirmLabel = stringResource(Res.string.invite_confirm),
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            icon = null,
            shortfall = stringResource(shortfallLabel(currency), shortfall),
        )
        return
    }
    SpendConfirmDialog(
        title = stringResource(confirmTitle(currency), cost),
        details =
        buildList {
            add(SpendDetail(stringResource(Res.string.invite_cost), stringResource(costValue(currency), cost)))
            balance?.let {
                add(SpendDetail(stringResource(Res.string.spend_current_balance), it.toString()))
                add(
                    SpendDetail(
                        stringResource(Res.string.invite_balance_after),
                        (it - cost).toString(),
                        separated = true,
                    ),
                )
            }
        },
        caution =
        if (previousPurchaseUncertain) {
            stringResource(Res.string.invite_repeat_purchase_caution) + "\n" + stringResource(caution(currency))
        } else {
            stringResource(caution(currency))
        },
        confirmLabel = stringResource(Res.string.invite_confirm),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        icon = PlazaIcons.ConfirmationNumber,
    )
}

private fun confirmTitle(currency: InviteCurrency) =
    when (currency) {
        InviteCurrency.COIN -> Res.string.invite_confirm_title
        InviteCurrency.STARDUST -> Res.string.invite_confirm_title_stardust
    }

private fun costValue(currency: InviteCurrency) =
    when (currency) {
        InviteCurrency.COIN -> Res.string.invite_cost_value
        InviteCurrency.STARDUST -> Res.string.invite_cost_value_stardust
    }

private fun caution(currency: InviteCurrency) =
    when (currency) {
        InviteCurrency.COIN -> Res.string.invite_caution
        InviteCurrency.STARDUST -> Res.string.invite_caution_stardust
    }

private fun shortTitle(currency: InviteCurrency) =
    when (currency) {
        InviteCurrency.COIN -> Res.string.invite_short_title
        InviteCurrency.STARDUST -> Res.string.invite_short_title_stardust
    }

private fun shortfallLabel(currency: InviteCurrency) =
    when (currency) {
        InviteCurrency.COIN -> Res.string.invite_shortfall
        InviteCurrency.STARDUST -> Res.string.invite_shortfall_stardust
    }

private fun shortHint(currency: InviteCurrency) =
    when (currency) {
        InviteCurrency.COIN -> Res.string.invite_short_hint
        InviteCurrency.STARDUST -> Res.string.invite_short_hint_stardust
    }

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun InviteConfirmCoinPreview() {
    PlazaTheme {
        InviteConfirmDialog(
            currency = InviteCurrency.COIN,
            cost = 1000,
            balance = 1_286,
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun InviteConfirmStardustShortPreview() {
    PlazaTheme(darkTheme = true) {
        InviteConfirmDialog(
            currency = InviteCurrency.STARDUST,
            cost = 100,
            balance = 1,
            onConfirm = {},
            onDismiss = {},
        )
    }
}
