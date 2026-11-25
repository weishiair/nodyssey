package io.github.nodyssey.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.nodyssey.core.VpsCalculator
import io.github.nodyssey.ui.common.appName
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.vps_above_value
import io.github.nodyssey.ui.resources.vps_approximate
import io.github.nodyssey.ui.resources.vps_at_value
import io.github.nodyssey.ui.resources.vps_below_value
import io.github.nodyssey.ui.resources.vps_cny_amount
import io.github.nodyssey.ui.resources.vps_days
import io.github.nodyssey.ui.resources.vps_due_date
import io.github.nodyssey.ui.resources.vps_equivalent
import io.github.nodyssey.ui.resources.vps_estimate_title
import io.github.nodyssey.ui.resources.vps_expiry_detail
import io.github.nodyssey.ui.resources.vps_foreign_amount
import io.github.nodyssey.ui.resources.vps_percent
import io.github.nodyssey.ui.resources.vps_rate_detail
import io.github.nodyssey.ui.resources.vps_reference_rate
import io.github.nodyssey.ui.resources.vps_remaining
import io.github.nodyssey.ui.resources.vps_renewal
import io.github.nodyssey.ui.resources.vps_renewal_detail
import io.github.nodyssey.ui.resources.vps_table_header
import io.github.nodyssey.ui.resources.vps_total
import io.github.nodyssey.ui.resources.vps_unknown
import io.github.nodyssey.ui.resources.vps_with_note
import io.github.plaza.core.runCatchingExceptCancellation
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

private val VpsShareCardWidth = 420.dp

internal data class VpsSummaryRow(
    val label: String,
    val detail: String,
    val markdownDetail: String = detail,
)

internal data class VpsSummary(
    val title: String,
    val amount: String,
    val originalEquivalent: String?,
    val days: String,
    val progress: Float,
    val percent: String,
    val rows: List<VpsSummaryRow>,
    val markdown: String,
)

@Composable
internal fun cnyAmount(amount: String): String = stringResource(Res.string.vps_cny_amount, amount)

private inline fun valuationRow(
    label: String,
    amount: String,
    note: String?,
    format: (StringResource, Array<out Any>) -> String,
): VpsSummaryRow {
    val bold = "**$amount**"
    return VpsSummaryRow(
        label = label,
        detail = if (note == null) amount else format(Res.string.vps_with_note, arrayOf(amount, note)),
        markdownDetail = if (note == null) bold else format(Res.string.vps_with_note, arrayOf(bold, note)),
    )
}

@Composable
internal fun vpsSummary(state: VpsCalculatorUiState): VpsSummary = buildSummary(
    state,
    text = { stringResource(it) },
    format = { resource, args -> stringResource(resource, *args) },
)

internal suspend fun buildVpsMarkdown(state: VpsCalculatorUiState): String = buildSummary(
    state,
    text = { getString(it) },
    format = { resource, args -> getString(resource, *args) },
).markdown

private inline fun buildSummary(
    state: VpsCalculatorUiState,
    text: (StringResource) -> String,
    format: (StringResource, Array<out Any>) -> String,
): VpsSummary {
    val result = state.result
    val foreign = !state.currency.isBaseCny
    val code = state.currency.code
    val unknown = text(Res.string.vps_unknown)
    val days = result.remainingDays?.let { format(Res.string.vps_days, arrayOf(it)) } ?: unknown
    val amount = result.remainingCny?.let {
        format(Res.string.vps_cny_amount, arrayOf(VpsCalculator.formatDecimal(it)))
    } ?: unknown
    val original = result.remainingOriginal?.takeIf { foreign }?.let {
        format(Res.string.vps_foreign_amount, arrayOf(VpsCalculator.formatDecimal(it), code))
    }
    val price = state.priceText.trim().takeIf { VpsCalculator.parseNonNegativeDecimal(it) != null }
    val renewalAmount = when {
        price == null -> unknown
        foreign -> format(Res.string.vps_foreign_amount, arrayOf(price, code))
        else -> format(Res.string.vps_cny_amount, arrayOf(price))
    }
    val renewal = format(Res.string.vps_renewal_detail, arrayOf(renewalAmount, text(state.cycle.label)))
    val premium = result.effectivePremiumCny ?: 0.0
    val total = result.effectiveSalePriceCny ?: result.remainingCny
    val quote = when {
        total == null -> null

        abs(premium) < 0.005 -> text(Res.string.vps_at_value)

        else -> {
            val difference = format(Res.string.vps_cny_amount, arrayOf(VpsCalculator.formatDecimal(abs(premium), 2)))
            format(if (premium > 0) Res.string.vps_above_value else Res.string.vps_below_value, arrayOf(difference))
        }
    }
    val rows = buildList {
        val priceCny = result.priceCny
        val renewalDetail = if (foreign && priceCny != null) {
            val convertedPrice = format(Res.string.vps_cny_amount, arrayOf(VpsCalculator.formatDecimal(priceCny)))
            val converted = format(Res.string.vps_approximate, arrayOf(convertedPrice))
            format(Res.string.vps_with_note, arrayOf(renewal, converted))
        } else {
            renewal
        }
        add(VpsSummaryRow(text(Res.string.vps_renewal), renewalDetail))
        val expiry = format(Res.string.vps_expiry_detail, arrayOf(state.dueDate?.toString() ?: unknown, state.tradeDate.toString(), days))
        add(VpsSummaryRow(text(Res.string.vps_due_date), expiry))
        if (foreign) {
            val rate = state.rateText.trim().takeIf { (VpsCalculator.parseNonNegativeDecimal(it) ?: 0.0) > 0 } ?: unknown
            add(VpsSummaryRow(text(Res.string.vps_reference_rate), format(Res.string.vps_rate_detail, arrayOf(code, rate))))
        }
        val equivalent = original?.let { format(Res.string.vps_approximate, arrayOf(it)) }
        add(valuationRow(text(Res.string.vps_remaining), amount, equivalent, format))
        val totalAmount = total?.let { format(Res.string.vps_cny_amount, arrayOf(VpsCalculator.formatDecimal(it))) } ?: unknown
        add(valuationRow(text(Res.string.vps_total), totalAmount, quote, format))
    }
    val title = text(Res.string.vps_estimate_title)
    val header = text(Res.string.vps_table_header)
    return VpsSummary(
        title = title,
        amount = amount,
        originalEquivalent = original?.let { format(Res.string.vps_equivalent, arrayOf(it)) },
        days = days,
        progress = result.progressFraction,
        percent = result.progressPercent?.let { format(Res.string.vps_percent, arrayOf(it)) } ?: unknown,
        rows = rows,
        markdown = buildString {
            appendLine("### $title")
            appendLine()
            appendLine(header)
            append("| --- | --- |")
            for (row in rows) append("\n| ${row.label} | ${row.markdownDetail} |")
        },
    )
}

@Composable
internal fun VpsCalculatorShareCapture(summary: VpsSummary, onCaptured: (ImageBitmap?) -> Unit) {
    val layer = rememberGraphicsLayer()
    val currentOnCaptured by rememberUpdatedState(onCaptured)
    LaunchedEffect(summary) {
        repeat(2) { withFrameNanos { } }
        currentOnCaptured(runCatchingExceptCancellation { layer.toImageBitmap() }.getOrNull())
    }
    Box(
        Modifier.requiredWidth(VpsShareCardWidth)
            .wrapContentHeight(align = Alignment.Top, unbounded = true)
            .clearAndSetSemantics {}
            .drawWithContent { layer.record { this@drawWithContent.drawContent() } },
    ) {
        VpsCalculatorShareCard(summary)
    }
}

@Composable
private fun VpsCalculatorShareCard(summary: VpsSummary) {
    val layers = LocalPlazaLayers.current
    val colors = MaterialTheme.colorScheme
    val detailStyle = MaterialTheme.typography.bodySmall
    val valueStyle = detailStyle.copy(fontFeatureSettings = TABULAR_FIGURES)
    Column(
        modifier = Modifier.fillMaxWidth().background(layers.card).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(summary.title, style = MaterialTheme.typography.titleMedium)
            Text(
                summary.amount,
                color = colors.primary,
                style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = TABULAR_FIGURES),
            )
            summary.originalEquivalent?.let { Text(it, style = valueStyle, color = colors.onSurfaceVariant) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            VpsProgress(summary.days, summary.percent, summary.progress)
        }
        HorizontalDivider(color = layers.divider)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            for (row in summary.rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.Top) {
                    Text(
                        row.label,
                        style = detailStyle,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        row.detail,
                        modifier = Modifier.weight(1f),
                        style = valueStyle,
                        color = colors.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
        HorizontalDivider(color = layers.divider)
        Text(appName(), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
    }
}

@Composable
internal fun VpsProgress(days: String, percent: String, progress: Float) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(days, style = MaterialTheme.typography.labelLarge)
        Text(percent, style = MaterialTheme.typography.labelLarge)
    }
    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
}
