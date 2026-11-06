package io.github.nodyssey.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.data.InviteCode
import io.github.nodyssey.data.InviteCurrency
import io.github.nodyssey.data.InvitePrice
import io.github.nodyssey.data.InvitePricing
import io.github.nodyssey.ui.common.MediumButton
import io.github.nodyssey.ui.common.shortMessage
import io.github.nodyssey.ui.common.siteName
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.action_copy
import io.github.nodyssey.ui.resources.action_refresh
import io.github.nodyssey.ui.resources.action_retry
import io.github.nodyssey.ui.resources.invite_body
import io.github.nodyssey.ui.resources.invite_bought_body
import io.github.nodyssey.ui.resources.invite_bought_title
import io.github.nodyssey.ui.resources.invite_bought_unknown
import io.github.nodyssey.ui.resources.invite_buy_failed_hint
import io.github.nodyssey.ui.resources.invite_buy_unavailable
import io.github.nodyssey.ui.resources.invite_check_result
import io.github.nodyssey.ui.resources.invite_chicken_balance
import io.github.nodyssey.ui.resources.invite_code_applied
import io.github.nodyssey.ui.resources.invite_code_copied
import io.github.nodyssey.ui.resources.invite_code_unused
import io.github.nodyssey.ui.resources.invite_codes_empty
import io.github.nodyssey.ui.resources.invite_codes_title
import io.github.nodyssey.ui.resources.invite_codes_truncated
import io.github.nodyssey.ui.resources.invite_price_coin
import io.github.nodyssey.ui.resources.invite_price_stardust
import io.github.nodyssey.ui.resources.invite_purchase_unknown_body
import io.github.nodyssey.ui.resources.invite_purchase_unknown_title
import io.github.nodyssey.ui.resources.invite_stardust_balance
import io.github.nodyssey.ui.resources.invite_title
import io.github.nodyssey.ui.resources.invite_web_check
import io.github.nodyssey.ui.resources.invite_web_fallback
import io.github.nodyssey.ui.resources.spend_got_it
import io.github.plaza.core.TimeFormat
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.PlazaSpinner
import io.github.plaza.designsys.component.TonalTag
import io.github.plaza.designsys.component.rememberClipboardCopy
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun InviteRoute(
    viewModel: InviteViewModel,
    onBack: () -> Unit,
    onBuyOnSite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    InviteScreen(
        state = state,
        onBack = onBack,
        onBuy = viewModel::buy,
        onRetry = viewModel::refresh,
        onOpenWeb = onBuyOnSite,
        onDismissOutcome = viewModel::dismissOutcome,
        modifier = modifier,
    )
}

/**
 * 邀请好友 — the site's two prices, both balances, and the codes already bought.
 *
 * Nothing here is a guess about the site: 1000 鸡腿 and 100 星辰 are whatever `/api/invite/config`
 * answered, either row disappears when the site withdraws that currency, and the list below is the
 * same `/api/invite/list` its own page draws. The purchase is native now — the confirmation is the only
 * one there is, and the code it produces is shown the moment the list carries it.
 *
 * 在网页中购买 stays as the way out of a purchase this app cannot complete: a refused CSRF token, a
 * challenge, a shape the parser has not seen. It is a text button under the real ones rather than the
 * real ones' replacement, because the app should not hide the better path to make its own look whole.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteScreen(
    state: InviteUiState,
    onBack: () -> Unit,
    onBuy: (InviteCurrency) -> Unit,
    onRetry: () -> Unit,
    onOpenWeb: () -> Unit,
    onDismissOutcome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Saved as the site's own word rather than the enum: `rememberSaveable` has to survive a process
    // death on every platform this module builds for, and a String is the one type all of them keep.
    var confirming by rememberSaveable { mutableStateOf<String?>(null) }
    val copy = rememberClipboardCopy()
    val copied = stringResource(Res.string.invite_code_copied)

    val appBarState = rememberOneHandAppBarState()
    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        topBar = {
            OneHandTopAppBar(
                title = stringResource(Res.string.invite_title),
                state = appBarState,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .readableWidth()
                // Scrollable although the cards fit at the default size: the app bar folds away on a
                // scroll, and the list of codes makes this page long on any account that has bought a
                // few.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LayerPageGutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            PurchaseCard(state = state, onPick = { confirming = it.wireValue }, onRetry = onRetry, onOpenWeb = onOpenWeb)
            CodesCard(state = state, onCopy = { code -> copy(code, code, copied) }, onRetry = onRetry)
        }
    }

    confirming?.let { wire ->
        val currency = InviteCurrency.entries.firstOrNull { it.wireValue == wire }
        val price = currency?.let { state.pricing?.priceFor(it) }
        if (currency != null && price != null) {
            InviteConfirmDialog(
                currency = currency,
                cost = price.cost,
                balance = state.balanceOf(currency),
                previousPurchaseUncertain = state.purchaseUncertain,
                onConfirm = {
                    confirming = null
                    onBuy(currency)
                },
                onDismiss = { confirming = null },
            )
        }
    }

    state.outcome?.let { outcome ->
        OutcomeDialog(
            outcome = outcome,
            onCopy = { code -> copy(code, code, copied) },
            onDismiss = onDismissOutcome,
            onRetry = {
                onDismissOutcome()
                onRetry()
            },
        )
    }
}

@Composable
private fun PurchaseCard(
    state: InviteUiState,
    onPick: (InviteCurrency) -> Unit,
    onRetry: () -> Unit,
    onOpenWeb: () -> Unit,
) {
    LayerCard(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(
                PlazaIcons.Group,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
            Text(
                text = stringResource(Res.string.invite_body, siteName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val pricing = state.pricing
        when {
            pricing != null ->
                if (pricing.sellable.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.invite_buy_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    pricing.sellable.forEach { currency ->
                        val price = pricing.priceFor(currency) ?: return@forEach
                        MediumButton(
                            onClick = { onPick(currency) },
                            icon = PlazaIcons.ConfirmationNumber,
                            enabled = state.isBuying == null && !state.isLoading,
                            busy = state.isBuying == currency,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(priceLabel(currency), price.cost))
                        }
                    }
                }

            state.isLoading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                PlazaSpinner(size = 16.dp, strokeWidth = 2.dp)
            }
        }

        state.error?.let { error ->
            Text(
                text = error.shortMessage(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = onRetry, enabled = !state.isLoading && state.isBuying == null) {
                Text(stringResource(Res.string.action_retry))
            }
        }

        if (state.purchaseUncertain) {
            Text(
                text = stringResource(Res.string.invite_purchase_unknown_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Balances(state = state)

        TextButton(onClick = onOpenWeb, enabled = state.isBuying == null) {
            Text(stringResource(if (state.purchaseUncertain) Res.string.invite_web_check else Res.string.invite_web_fallback))
        }
    }
}

/**
 * Both balances on one line.
 *
 * Shown beside the buttons rather than only inside the confirmation, because "can I afford this" is the
 * first question anyone asks here — and with two currencies, whether it is the 鸡腿 or the 星辰 that is
 * short.
 */
@Composable
private fun Balances(state: InviteUiState) {
    val chicken = state.chickenCount
    val stardust = state.stardustCount
    if (chicken == null && stardust == null) return

    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        chicken?.let {
            Text(
                text = stringResource(Res.string.invite_chicken_balance, it),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        stardust?.let {
            Text(
                text = stringResource(Res.string.invite_stardust_balance, it),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CodesCard(
    state: InviteUiState,
    onCopy: (String) -> Unit,
    onRetry: () -> Unit,
) {
    LayerCard(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.invite_codes_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry, enabled = !state.isLoading && state.isBuying == null) {
                Text(stringResource(if (state.codesError == null) Res.string.action_refresh else Res.string.action_retry))
            }
        }
        state.codesError?.let { error ->
            Text(
                text = error.shortMessage(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        when {
            state.codes.isNotEmpty() -> state.codes.forEachIndexed { index, code ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                CodeRow(code = code, onCopy = onCopy)
            }

            state.isLoading -> Box(Modifier.fillMaxWidth()) { PlazaSpinner() }

            state.codesError == null -> Text(
                text = stringResource(Res.string.invite_codes_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The site pages this list fifty at a time and the screen reads only the first page. Saying so
        // is the difference between a list and a claim about every code the account ever bought.
        if (state.codesTotal > state.codes.size) {
            Text(
                text = stringResource(Res.string.invite_codes_truncated, state.codesTotal, state.codes.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CodeRow(
    code: InviteCode,
    onCopy: (String) -> Unit,
) {
    val applied = code.applied
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = code.code,
                style =
                MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                TonalTag(
                    text = stringResource(
                        if (applied) Res.string.invite_code_applied else Res.string.invite_code_unused,
                    ),
                    containerColor =
                    if (applied) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor =
                    if (applied) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                )
                code.createdAtMillis?.let {
                    Text(
                        text = TimeFormat.absolute(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        IconButton(onClick = { onCopy(code.code) }) {
            Icon(
                PlazaIcons.ContentCopy,
                contentDescription = stringResource(Res.string.action_copy),
            )
        }
    }
}

/**
 * What the purchase did.
 *
 * The code is the point of the whole screen, so a bought one is shown here in full rather than left to
 * be found in the list — and it can be 复制 straight from the dialog. A refusal shows the site's own
 * sentence, which is the only text that says whether 鸡腿 or 星辰 was short.
 */
@Composable
private fun OutcomeDialog(
    outcome: InviteOutcome,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    when (outcome) {
        is InviteOutcome.Bought -> {
            val code = outcome.code
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = { Icon(PlazaIcons.ConfirmationNumber, contentDescription = null) },
                title = { Text(stringResource(Res.string.invite_bought_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        if (code == null) {
                            Text(
                                text = stringResource(Res.string.invite_bought_unknown),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            Text(
                                text = stringResource(Res.string.invite_bought_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Surface(
                                color = LocalPlazaLayers.current.card,
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = code,
                                    style =
                                    MaterialTheme.typography.headlineSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                    modifier = Modifier.padding(Spacing.lg),
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    if (code == null) {
                        Button(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
                    } else {
                        Button(onClick = { onCopy(code) }) { Text(stringResource(Res.string.action_copy)) }
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text(stringResource(Res.string.spend_got_it)) }
                },
            )
        }

        is InviteOutcome.Refused -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text(outcome.error.shortMessage()) },
            text = {
                Text(
                    text = outcome.detail ?: stringResource(Res.string.invite_buy_failed_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(onClick = onDismiss) { Text(stringResource(Res.string.spend_got_it)) }
            },
        )

        is InviteOutcome.Unknown -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text(stringResource(Res.string.invite_purchase_unknown_title)) },
            text = { Text(stringResource(Res.string.invite_purchase_unknown_body)) },
            confirmButton = {
                Button(onClick = onRetry) { Text(stringResource(Res.string.invite_check_result)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.spend_got_it)) }
            },
        )
    }
}

private fun priceLabel(currency: InviteCurrency): StringResource =
    when (currency) {
        InviteCurrency.COIN -> Res.string.invite_price_coin
        InviteCurrency.STARDUST -> Res.string.invite_price_stardust
    }

@Preview(showBackground = true, widthDp = 360, heightDp = 720, name = "9d 邀请好友")
@Composable
private fun InvitePreview() {
    PlazaTheme {
        InviteScreen(
            state =
            InviteUiState(
                pricing = InvitePricing(coin = InvitePrice(1_000, enabled = true), stardust = InvitePrice(100, enabled = true)),
                chickenCount = 344,
                stardustCount = 1,
                codes = listOf(InviteCode("8F3K2Q9M", applied = false, createdAtMillis = 1_784_804_442_000)),
                isLoading = false,
            ),
            onBack = {},
            onBuy = {},
            onRetry = {},
            onOpenWeb = {},
            onDismissOutcome = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720, name = "9d 邀请好友 · dark")
@Composable
private fun InviteDarkPreview() {
    PlazaTheme(darkTheme = true) {
        InviteScreen(
            state =
            InviteUiState(
                pricing = InvitePricing(coin = InvitePrice(1_000, enabled = true), stardust = InvitePrice(100, enabled = true)),
                chickenCount = 1_165,
                stardustCount = 1,
                isLoading = false,
            ),
            onBack = {},
            onBuy = {},
            onRetry = {},
            onOpenWeb = {},
            onDismissOutcome = {},
        )
    }
}
