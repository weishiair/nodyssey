package io.github.plaza.designsys.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The 8dp spacing grid, plus the two half-steps a dense list actually needs.
 *
 * Named rather than inlined so a density change is one edit — the list's whole reason to exist is
 * fitting nine rows on a 800dp screen, and that target is set by these numbers.
 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object Sizes {
    /** Anything tappable must clear this; Material's own minimum and the brief's hard requirement. */
    val minTouchTarget = 48.dp

    /** A reply's avatar, beside the whole of its header — the name line and the time under it. */
    val avatarComment = 30.dp

    /** The opening post's, a hair smaller: in its card the title above it is what carries the weight. */
    val avatarOriginalPost = 28.dp
    val avatarProfile = 64.dp

    /** Beyond this a body column stops being comfortable to read, so it stops growing. */
    val readableContentWidth = 640.dp

    /** Tall screenshots are common on this forum and would otherwise fill several screens. */
    val maxInlineImageHeight = 520.dp
}

/**
 * How a page's readable column sizes itself inside its available width.
 *
 * The default holds every column to [Sizes.readableContentWidth] (640dp) with no outer gutter.
 * A host whose tab roots use a wider centred band (such as iPad full-page layout) provides a
 * custom spec through [LocalReadableWidthSpec] so pushed pages and their top bars share the same
 * column.
 */
@Immutable
data class ReadableWidthSpec(
    val maxWidth: Dp = Sizes.readableContentWidth,
    val landscapeMaxWidth: Dp = maxWidth,
    val horizontalGutter: Dp = 0.dp,
    val gutterMinWidth: Dp = 0.dp,
    val windowLandscape: Boolean? = null,
) {
    fun resolveWidth(
        availableWidth: Dp,
        availableHeight: Dp = Dp.Unspecified,
    ): Dp {
        val gutter = if (availableWidth >= gutterMinWidth) horizontalGutter else 0.dp
        val inner = (availableWidth - gutter * 2).coerceAtLeast(0.dp)
        if (availableWidth < gutterMinWidth && gutter <= 0.dp) return inner
        val landscape = windowLandscape ?: (availableHeight != Dp.Unspecified && availableWidth > availableHeight)
        val cap = if (landscape) landscapeMaxWidth else maxWidth
        return inner.coerceAtMost(cap)
    }

    companion object {
        val Default = ReadableWidthSpec()
    }
}

val LocalReadableWidthSpec = compositionLocalOf { ReadableWidthSpec.Default }

/**
 * Caps the content at the active [LocalReadableWidthSpec] and centres what is left over.
 *
 * Applied to the scrolling column itself rather than to each block of text inside it. Capping the
 * text alone still let the rows, dividers and row backgrounds run the full width of a tablet, so a
 * 1000dp window got a 640dp paragraph pinned to the left of a 1000dp divider.
 *
 * Implemented as a [LayoutModifierNode] reading [LocalReadableWidthSpec] during layout so hosts
 * can widen the column dynamically on rotation or layout-mode changes without recomposing screens.
 */
fun Modifier.readableWidth(): Modifier = this.then(ReadableWidthElement)

private data object ReadableWidthElement : ModifierNodeElement<ReadableWidthNode>() {
    override fun create(): ReadableWidthNode = ReadableWidthNode()

    override fun update(node: ReadableWidthNode) = Unit

    override fun InspectorInfo.inspectableProperties() {
        name = "readableWidth"
    }
}

private class ReadableWidthNode :
    Modifier.Node(),
    LayoutModifierNode,
    CompositionLocalConsumerModifierNode {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val spec = currentValueOf(LocalReadableWidthSpec)
        val hasBoundedWidth = constraints.hasBoundedWidth
        val maxPx =
            if (hasBoundedWidth) {
                val availableWidth = constraints.maxWidth.toDp()
                val availableHeight = if (constraints.hasBoundedHeight) constraints.maxHeight.toDp() else Dp.Unspecified
                spec.resolveWidth(availableWidth, availableHeight).roundToPx().coerceIn(0, constraints.maxWidth)
            } else {
                val cap = if (spec.windowLandscape == true) spec.landscapeMaxWidth else spec.maxWidth
                cap.roundToPx().coerceAtLeast(0)
            }
        val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = maxPx))
        val layoutWidth =
            if (hasBoundedWidth) {
                constraints.maxWidth
            } else {
                placeable.width.coerceIn(constraints.minWidth, constraints.maxWidth)
            }
        val x = ((layoutWidth - placeable.width) / 2).coerceAtLeast(0)
        return layout(layoutWidth, placeable.height) {
            placeable.placeRelative(x, 0)
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ): Int {
        val spec = currentValueOf(LocalReadableWidthSpec)
        val cap = if (spec.windowLandscape == true) spec.landscapeMaxWidth else spec.maxWidth
        return measurable.minIntrinsicWidth(height).coerceAtMost(cap.roundToPx().coerceAtLeast(0))
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ): Int {
        val spec = currentValueOf(LocalReadableWidthSpec)
        val cap = if (spec.windowLandscape == true) spec.landscapeMaxWidth else spec.maxWidth
        return measurable.maxIntrinsicWidth(height).coerceAtMost(cap.roundToPx().coerceAtLeast(0))
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int = measurable.minIntrinsicHeight(boundedWidthForIntrinsic(width))

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int = measurable.maxIntrinsicHeight(boundedWidthForIntrinsic(width))

    private fun IntrinsicMeasureScope.boundedWidthForIntrinsic(width: Int): Int {
        if (width == Constraints.Infinity) return width
        val spec = currentValueOf(LocalReadableWidthSpec)
        return spec.resolveWidth(width.toDp()).roundToPx().coerceIn(0, width)
    }
}

/**
 * Takes a `Scaffold`'s content padding *and* the keyboard, without paying for the bottom twice.
 *
 * `Scaffold` hands out padding that already contains the navigation bar, and `imePadding` asks for
 * the keyboard's full height — which is measured from the bottom of the window, navigation bar
 * included. Applied one after the other they stack, and whatever sits at the bottom of the screen
 * floats a navigation bar's worth above the keyboard instead of resting on it. That gap is the
 * recurring bug: every editor that pins a formatting strip to the keyboard has had it, and it looks
 * enough like a design decision that it survives review.
 *
 * [consumeWindowInsets] is the missing step. It tells the `imePadding` below it that the bottom
 * inset has already been handled, so the keyboard padding applies only what is left.
 *
 * Only for content inside a `Scaffold`. A bottom sheet gets no such padding and wants a bare
 * `imePadding()` — see the reply composer, which is why that one has always sat flush.
 */
fun Modifier.paddingWithKeyboard(padding: PaddingValues): Modifier =
    this
        .padding(padding)
        .consumeWindowInsets(padding)
        .imePadding()
