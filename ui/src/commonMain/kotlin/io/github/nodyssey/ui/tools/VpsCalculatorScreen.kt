package io.github.nodyssey.ui.tools

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.core.VpsBillingCycle
import io.github.nodyssey.core.VpsCalculator
import io.github.nodyssey.core.VpsCurrency
import io.github.nodyssey.ui.common.MediumButton
import io.github.nodyssey.ui.common.MediumButtonStyle
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.action_cancel
import io.github.nodyssey.ui.resources.action_close
import io.github.nodyssey.ui.resources.action_copy
import io.github.nodyssey.ui.resources.action_done
import io.github.nodyssey.ui.resources.action_preview
import io.github.nodyssey.ui.resources.action_refresh
import io.github.nodyssey.ui.resources.network_check_copied
import io.github.nodyssey.ui.resources.post_share_image_failed
import io.github.nodyssey.ui.resources.search_advanced_reset
import io.github.nodyssey.ui.resources.viewer_save_failed
import io.github.nodyssey.ui.resources.viewer_save_no_permission
import io.github.nodyssey.ui.resources.viewer_saved
import io.github.nodyssey.ui.resources.vps_annually
import io.github.nodyssey.ui.resources.vps_aud
import io.github.nodyssey.ui.resources.vps_biennially
import io.github.nodyssey.ui.resources.vps_cached
import io.github.nodyssey.ui.resources.vps_cad
import io.github.nodyssey.ui.resources.vps_clear_due
import io.github.nodyssey.ui.resources.vps_cny
import io.github.nodyssey.ui.resources.vps_copy_text
import io.github.nodyssey.ui.resources.vps_currency
import io.github.nodyssey.ui.resources.vps_cycle
import io.github.nodyssey.ui.resources.vps_days
import io.github.nodyssey.ui.resources.vps_draft_save_failed
import io.github.nodyssey.ui.resources.vps_due_date
import io.github.nodyssey.ui.resources.vps_equivalent
import io.github.nodyssey.ui.resources.vps_eur
import io.github.nodyssey.ui.resources.vps_expired
import io.github.nodyssey.ui.resources.vps_foreign_amount
import io.github.nodyssey.ui.resources.vps_gbp
import io.github.nodyssey.ui.resources.vps_generate
import io.github.nodyssey.ui.resources.vps_hkd
import io.github.nodyssey.ui.resources.vps_jpy
import io.github.nodyssey.ui.resources.vps_krw
import io.github.nodyssey.ui.resources.vps_missing_valuation_inputs
import io.github.nodyssey.ui.resources.vps_monthly
import io.github.nodyssey.ui.resources.vps_percent
import io.github.nodyssey.ui.resources.vps_pick_due
import io.github.nodyssey.ui.resources.vps_premium
import io.github.nodyssey.ui.resources.vps_price
import io.github.nodyssey.ui.resources.vps_quarterly
import io.github.nodyssey.ui.resources.vps_rate
import io.github.nodyssey.ui.resources.vps_rate_attribution
import io.github.nodyssey.ui.resources.vps_rate_failed
import io.github.nodyssey.ui.resources.vps_rate_limited
import io.github.nodyssey.ui.resources.vps_remaining
import io.github.nodyssey.ui.resources.vps_sale_price
import io.github.nodyssey.ui.resources.vps_save_image
import io.github.nodyssey.ui.resources.vps_semi_annually
import io.github.nodyssey.ui.resources.vps_sgd
import io.github.nodyssey.ui.resources.vps_title
import io.github.nodyssey.ui.resources.vps_trade_date
import io.github.nodyssey.ui.resources.vps_triennially
import io.github.nodyssey.ui.resources.vps_twd
import io.github.nodyssey.ui.resources.vps_unknown
import io.github.nodyssey.ui.resources.vps_updated
import io.github.nodyssey.ui.resources.vps_usd
import io.github.nodyssey.ui.viewer.SaveOutcome
import io.github.plaza.core.TimeFormat
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaFieldDefaults
import io.github.plaza.designsys.component.PlazaSnackbarHost
import io.github.plaza.designsys.component.PlazaSpinner
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.component.rememberSilentClipboardCopy
import io.github.plaza.designsys.theme.LocalPlazaExtraColors
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import io.github.plaza.designsys.theme.paddingWithKeyboard
import io.github.plaza.designsys.theme.readableWidth
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun VpsCalculatorRoute(
    viewModel: VpsCalculatorViewModel,
    saveImage: suspend (ImageBitmap) -> SaveOutcome,
    onBack: () -> Unit,
) = VpsCalculatorTheme {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val writeClipboard = rememberSilentClipboardCopy()
    val title = stringResource(Res.string.vps_title)
    val notice = when (state.rateNotice) {
        VpsRateNotice.FETCH_FAILED -> stringResource(Res.string.vps_rate_failed)
        VpsRateNotice.RATE_LIMITED -> stringResource(Res.string.vps_rate_limited)
        null -> null
    }
    LaunchedEffect(notice) {
        if (notice != null) {
            snackbar.showSnackbar(notice)
            viewModel.consumeRateNotice()
        }
    }
    var capture by remember { mutableStateOf<VpsCalculatorUiState?>(null) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    val bar = rememberOneHandAppBarState()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val dismissFocus: () -> Unit = {
        focusManager.clearFocus()
        keyboard?.hide()
    }
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { dismissFocus() }) }) {
        Scaffold(
            modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
            topBar = {
                VpsTopBarTheme {
                    OneHandTopAppBar(
                        title = title,
                        state = bar,
                        navigationIcon = {
                            IconButton(onClick = {
                                dismissFocus()
                                onBack()
                            }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.action_back)) }
                        },
                        actions = {
                            TextButton(
                                onClick = {
                                    dismissFocus()
                                    viewModel.resetInputs()
                                },
                                enabled = state.draftReady,
                            ) { Text(stringResource(Res.string.search_advanced_reset)) }
                        },
                    )
                }
            },
            snackbarHost = { PlazaSnackbarHost(snackbar) },
        ) { padding ->
            if (state.draftReady) {
                Column(
                    Modifier.paddingWithKeyboard(padding).fillMaxSize().readableWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = LayerPageGutter, vertical = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    LayerCard {
                        PriceField(state.priceText, state.currency, state.result.priceCny, viewModel::setPrice, viewModel::setCurrency, dismissFocus)
                        BillingCycleField(state.cycle, viewModel::setCycle, dismissFocus)
                        DateFields(state.tradeDate, state.dueDate, viewModel::setTradeDate, viewModel::setDueDate, viewModel::clearDueDate, dismissFocus)
                        RateField(
                            state.currency,
                            state.rateText,
                            state.isLoadingRate,
                            state.rateFetchedAtMillis,
                            state.rateFromCache,
                            viewModel::setRate,
                            dismissFocus,
                        ) {
                            dismissFocus()
                            viewModel.refreshRate()
                        }
                    }
                    if (state.draftSaveFailed) {
                        Text(
                            stringResource(Res.string.vps_draft_save_failed),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    ResultCard(
                        currency = state.currency,
                        remainingCny = state.result.remainingCny,
                        remainingOriginal = state.result.remainingOriginal,
                        remainingDays = state.result.remainingDays,
                        expired = state.result.isExpired,
                        progressFraction = state.result.progressFraction,
                        progressPercent = state.result.progressPercent,
                        premium = state.result.effectivePremiumCny ?: 0.0,
                        premiumText = state.premiumText,
                        salePriceText = state.salePriceText,
                        setPremium = viewModel::setPremium,
                        setSalePrice = viewModel::setSalePrice,
                        dismissFocus = dismissFocus,
                        onCopy = { text ->
                            dismissFocus()
                            scope.launch {
                                writeClipboard(title, text)
                                snackbar.showSnackbar(getString(Res.string.network_check_copied))
                            }
                        },
                        onCopyMarkdown = {
                            dismissFocus()
                            val snapshot = state
                            scope.launch {
                                writeClipboard(title, buildVpsMarkdown(snapshot))
                                snackbar.showSnackbar(getString(Res.string.network_check_copied))
                            }
                        },
                        onGenerate = {
                            dismissFocus()
                            if (capture == null) capture = state
                        },
                    )
                }
            }
        }
        capture?.let {
            VpsCalculatorShareCapture(vpsSummary(it)) { bitmap ->
                capture = null
                if (bitmap != null) preview = bitmap else scope.launch { snackbar.showSnackbar(getString(Res.string.post_share_image_failed)) }
            }
        }
    }
    preview?.let { bitmap ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(stringResource(Res.string.action_preview)) },
            text = { Image(bitmap, title, Modifier.fillMaxWidth().heightIn(max = 420.dp)) },
            confirmButton = {
                TextButton(onClick = {
                    preview = null
                    scope.launch {
                        val message = when (saveImage(bitmap)) {
                            SaveOutcome.SAVED -> Res.string.viewer_saved
                            SaveOutcome.PERMISSION_DENIED -> Res.string.viewer_save_no_permission
                            SaveOutcome.UNSUPPORTED_OS, SaveOutcome.FAILED -> Res.string.viewer_save_failed
                        }
                        snackbar.showSnackbar(getString(message))
                    }
                }) { Text(stringResource(Res.string.vps_save_image)) }
            },
            dismissButton = { TextButton(onClick = { preview = null }) { Text(stringResource(Res.string.action_close)) } },
        )
    }
}

@Composable
private fun VpsTopBarTheme(content: @Composable () -> Unit) {
    if (vpsCalculatorFontFamily == null) {
        content()
    } else {
        val base = MaterialTheme.typography
        val typography = remember(base) { base.copy(titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Normal)) }
        MaterialTheme(typography = typography, content = content)
    }
}

@Composable
private fun BillingCycleField(selected: VpsBillingCycle, setCycle: (VpsBillingCycle) -> Unit, dismissFocus: () -> Unit) {
    Text(stringResource(Res.string.vps_cycle), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        VpsBillingCycle.entries.forEach { cycle ->
            FilterChip(
                selected = cycle == selected,
                onClick = {
                    dismissFocus()
                    setCycle(cycle)
                },
                label = { Text(stringResource(cycle.label)) },
            )
        }
    }
}

@Composable
private fun ResultCard(
    currency: VpsCurrency,
    remainingCny: Double?,
    remainingOriginal: Double?,
    remainingDays: Int?,
    expired: Boolean,
    progressFraction: Float,
    progressPercent: Int?,
    premium: Double,
    premiumText: String,
    salePriceText: String,
    setPremium: (String) -> Unit,
    setSalePrice: (String) -> Unit,
    dismissFocus: () -> Unit,
    onCopy: (String) -> Unit,
    onCopyMarkdown: () -> Unit,
    onGenerate: () -> Unit,
) {
    LayerCard {
        val amount = remainingCny?.let { VpsCalculator.formatDecimal(it) }
        val copyLabel = stringResource(Res.string.action_copy)
        Text(stringResource(Res.string.vps_remaining), style = MaterialTheme.typography.titleMedium)
        Text(
            amount?.let { cnyAmount(it) } ?: stringResource(Res.string.vps_unknown),
            modifier = if (amount != null) Modifier.clickable(onClickLabel = copyLabel) { onCopy(amount) } else Modifier,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = TABULAR_FIGURES),
        )
        when {
            remainingDays == null -> Text(stringResource(Res.string.vps_pick_due))

            expired -> Text(stringResource(Res.string.vps_expired), color = MaterialTheme.colorScheme.error)

            !currency.isBaseCny && remainingOriginal != null -> {
                val original = stringResource(Res.string.vps_foreign_amount, VpsCalculator.formatDecimal(remainingOriginal), currency.code)
                Text(stringResource(Res.string.vps_equivalent, original))
            }
        }
        if (remainingDays != null && amount == null) {
            Text(stringResource(Res.string.vps_missing_valuation_inputs), style = MaterialTheme.typography.bodySmall)
        }
        VpsProgress(
            remainingDays?.let { stringResource(Res.string.vps_days, it) } ?: stringResource(Res.string.vps_unknown),
            progressPercent?.let { stringResource(Res.string.vps_percent, it) } ?: stringResource(Res.string.vps_unknown),
            progressFraction,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            val ink = when {
                premium < 0 -> LocalPlazaExtraColors.current.success
                premium > 0 -> MaterialTheme.colorScheme.tertiary
                else -> Color.Unspecified
            }
            // The iOS decimal keypad has no minus key. Keep discounts enterable.
            DecimalField(premiumText, setPremium, Res.string.vps_premium, Modifier.weight(1f), dismissFocus, signed = true, color = ink)
            DecimalField(salePriceText, setSalePrice, Res.string.vps_sale_price, Modifier.weight(1f), dismissFocus, placeholder = amount)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            MediumButton(onClick = onCopyMarkdown, modifier = Modifier.weight(1f), style = MediumButtonStyle.Tonal, enabled = amount != null) {
                Text(stringResource(Res.string.vps_copy_text))
            }
            MediumButton(onClick = onGenerate, modifier = Modifier.weight(1f), enabled = amount != null) {
                Text(stringResource(Res.string.vps_generate))
            }
        }
    }
}

@Composable
private fun DecimalField(
    value: String,
    onValueChange: (String) -> Unit,
    label: StringResource,
    modifier: Modifier,
    dismissFocus: () -> Unit,
    signed: Boolean = false,
    color: Color = Color.Unspecified,
    prefix: String? = null,
    suffix: String? = null,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(stringResource(label)) },
        prefix = prefix?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (signed) KeyboardType.Text else KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { dismissFocus() }),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = color, fontFeatureSettings = TABULAR_FIGURES),
        shape = PlazaFieldDefaults.shape,
        colors = PlazaFieldDefaults.colors(inCard = true),
    )
}

@Composable
private fun PriceField(
    priceText: String,
    currency: VpsCurrency,
    priceCny: Double?,
    setPrice: (String) -> Unit,
    setCurrency: (VpsCurrency) -> Unit,
    dismissFocus: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        DecimalField(priceText, setPrice, Res.string.vps_price, Modifier.weight(1f), dismissFocus, prefix = currency.symbol)
        Box {
            OutlinedButton(onClick = {
                dismissFocus()
                expanded = true
            }) {
                Text(currency.code)
                Icon(Icons.Default.ArrowDropDown, stringResource(Res.string.vps_currency))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                VpsCurrency.entries.forEach { unit ->
                    DropdownMenuItem(
                        text = { Text("${unit.code}  ${unit.symbol}  ${stringResource(unit.label)}") },
                        onClick = {
                            expanded = false
                            setCurrency(unit)
                        },
                    )
                }
            }
        }
    }
    if (!currency.isBaseCny) {
        val equivalent = priceCny?.let { cnyAmount(VpsCalculator.formatDecimal(it)) } ?: stringResource(Res.string.vps_unknown)
        Text(stringResource(Res.string.vps_equivalent, equivalent), style = MaterialTheme.typography.bodySmall)
    }
}

private enum class DateField { DUE, TRADE }

@Composable
private fun DateFields(
    tradeDate: LocalDate,
    dueDate: LocalDate?,
    setTrade: (LocalDate) -> Unit,
    setDue: (LocalDate) -> Unit,
    clearDue: () -> Unit,
    dismissFocus: () -> Unit,
) {
    val datePadding = PaddingValues(horizontal = 12.dp, vertical = ButtonDefaults.ContentPadding.calculateTopPadding())
    var editing by rememberSaveable { mutableStateOf<DateField?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Column(Modifier.weight(1f)) {
            Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
                Text(stringResource(Res.string.vps_trade_date), style = MaterialTheme.typography.titleSmall)
            }
            OutlinedButton(
                onClick = {
                    dismissFocus()
                    editing = DateField.TRADE
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = datePadding,
            ) {
                Text(tradeDate.toString(), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(Modifier.weight(1f)) {
            Row(modifier = Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.vps_due_date), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                if (dueDate != null) {
                    IconButton(onClick = {
                        dismissFocus()
                        clearDue()
                    }) { Icon(Icons.Default.Close, stringResource(Res.string.vps_clear_due)) }
                }
            }
            OutlinedButton(
                onClick = {
                    dismissFocus()
                    editing = DateField.DUE
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = datePadding,
            ) {
                Text(
                    dueDate?.toString() ?: stringResource(Res.string.vps_pick_due),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    editing?.let { field ->
        val initial = if (field == DateField.DUE) dueDate else tradeDate
        VpsDatePickerDialog(
            title = stringResource(if (field == DateField.DUE) Res.string.vps_due_date else Res.string.vps_trade_date),
            initialDate = initial,
            fallbackDate = tradeDate,
            confirmText = stringResource(Res.string.action_done),
            cancelText = stringResource(Res.string.action_cancel),
            onDismiss = { editing = null },
            onConfirm = { date ->
                if (field == DateField.DUE) setDue(date) else setTrade(date)
                editing = null
            },
        )
    }
}

@Composable
private fun RateField(
    currency: VpsCurrency,
    rateText: String,
    isLoadingRate: Boolean,
    rateFetchedAtMillis: Long?,
    rateFromCache: Boolean,
    setRate: (String) -> Unit,
    dismissFocus: () -> Unit,
    refresh: () -> Unit,
) {
    if (currency.isBaseCny) {
        Text("1 CNY = 1.0000 CNY", style = MaterialTheme.typography.bodySmall)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        DecimalField(rateText, setRate, Res.string.vps_rate, Modifier.weight(1f), dismissFocus, prefix = "1 ${currency.code} ≈", suffix = "CNY")
        IconButton(onClick = refresh, enabled = !isLoadingRate) {
            if (isLoadingRate) PlazaSpinner(size = 24.dp) else Icon(Icons.Default.Refresh, stringResource(Res.string.action_refresh))
        }
    }
    rateFetchedAtMillis?.let { millis ->
        Text(
            stringResource(if (rateFromCache) Res.string.vps_cached else Res.string.vps_updated, TimeFormat.absolute(millis)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val attribution = stringResource(Res.string.vps_rate_attribution)
    val linkStyle = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)
    Text(
        buildAnnotatedString {
            withLink(LinkAnnotation.Url("https://www.exchangerate-api.com", TextLinkStyles(style = linkStyle))) {
                append(attribution)
            }
        },
        style = MaterialTheme.typography.bodySmall,
    )
}

private val VpsCurrency.label: StringResource
    get() = when (this) {
        VpsCurrency.CNY -> Res.string.vps_cny
        VpsCurrency.USD -> Res.string.vps_usd
        VpsCurrency.EUR -> Res.string.vps_eur
        VpsCurrency.GBP -> Res.string.vps_gbp
        VpsCurrency.JPY -> Res.string.vps_jpy
        VpsCurrency.HKD -> Res.string.vps_hkd
        VpsCurrency.AUD -> Res.string.vps_aud
        VpsCurrency.SGD -> Res.string.vps_sgd
        VpsCurrency.KRW -> Res.string.vps_krw
        VpsCurrency.TWD -> Res.string.vps_twd
        VpsCurrency.CAD -> Res.string.vps_cad
    }

internal val VpsBillingCycle.label: StringResource
    get() = when (this) {
        VpsBillingCycle.MONTHLY -> Res.string.vps_monthly
        VpsBillingCycle.QUARTERLY -> Res.string.vps_quarterly
        VpsBillingCycle.SEMI_ANNUALLY -> Res.string.vps_semi_annually
        VpsBillingCycle.ANNUALLY -> Res.string.vps_annually
        VpsBillingCycle.BIENNIALLY -> Res.string.vps_biennially
        VpsBillingCycle.TRIENNIALLY -> Res.string.vps_triennially
    }
