package io.github.plaza.designsys.component

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.plaza.designsys.theme.LocalPlazaFontScale
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.nativeChrome
import kotlin.math.roundToInt

/**
 * A switch whose visual control delegates to a native platform toggle where [nativeChrome] applies,
 * while drawing [fallback] everywhere else.
 */
@Composable
expect fun AdaptiveNativeSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String = "",
    accessibilityEnabled: Boolean = true,
    fallback: @Composable () -> Unit = {
        GroupedListItemSwitch(checked = checked, enabled = enabled)
    },
)

/**
 * A grouped settings toggle row with unified row-and-switch ownership.
 *
 * When [nativeChrome] is inactive, this preserves the Material [GroupedRow] contract: a display-only
 * [GroupedListItemSwitch] inside a row-wide toggle. When [nativeChrome] is active, the row's body
 * handles taps outside the native switch's bounds while the native switch handles its own region,
 * keeping vertical scrolling intact and exposing a single switch node to assistive technologies.
 */
@Composable
fun GroupedSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    first: Boolean = false,
    last: Boolean = false,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = icon?.let { { Icon(it, contentDescription = null) } },
    subtitle: String? = null,
    enabled: Boolean = true,
    /** Separate toggle state for the indicator when UI disables a switch while remembering its value. */
    switchChecked: Boolean = checked,
) {
    if (!LocalPlazaLayers.current.nativeChrome) {
        GroupedRow(
            title = title,
            modifier = modifier,
            first = first,
            last = last,
            leading = leading,
            subtitle = subtitle,
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            showChevron = false,
            trailing = { GroupedListItemSwitch(checked = switchChecked, enabled = enabled) },
        )
        return
    }
    val currentChecked by rememberUpdatedState(checked)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
    val interactionSource = remember { MutableInteractionSource() }
    var rowCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var switchCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val rowModifier =
        modifier
            .onGloballyPositioned { rowCoordinates = it }
            .indication(interactionSource, LocalIndication.current)
            .pointerInput(interactionSource) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val switchRect =
                        rowCoordinates?.takeIf { it.isAttached }?.let { row ->
                            switchCoordinates?.takeIf { it.isAttached }?.let { sw ->
                                row.localBoundingBoxOf(sw, clipBounds = false)
                            }
                        }
                    if (!currentEnabled || (switchRect != null && switchRect.contains(down.position))) {
                        return@awaitEachGesture
                    }
                    val press = PressInteraction.Press(down.position)
                    interactionSource.tryEmit(press)
                    val up = waitForUpOrCancellation()
                    if (up != null && !up.isConsumed && currentEnabled) {
                        up.consume()
                        interactionSource.tryEmit(PressInteraction.Release(press))
                        currentOnCheckedChange(!currentChecked)
                    } else {
                        interactionSource.tryEmit(PressInteraction.Cancel(press))
                    }
                }
            }.semantics(mergeDescendants = true) {
                role = Role.Switch
                toggleableState = ToggleableState(switchChecked)
                if (!currentEnabled) {
                    disabled()
                } else {
                    onClick {
                        currentOnCheckedChange(!currentChecked)
                        true
                    }
                }
            }

    GroupedListItem(
        first = first,
        last = last,
        modifier = rowModifier,
        enabled = enabled,
        colors = groupedListItemColors(),
        headlineContent = { Text(text = title, style = groupedRowTitleStyle()) },
        supportingContent =
        subtitle?.let {
            { Text(text = it) }
        },
        leadingContent = leading,
        trailingContent = {
            AdaptiveNativeSwitch(
                checked = switchChecked,
                onCheckedChange = currentOnCheckedChange,
                modifier = Modifier.onGloballyPositioned { switchCoordinates = it },
                enabled = enabled,
                label = title,
                accessibilityEnabled = false,
            )
        },
    )
}

/**
 * A segmented choice selector that renders a native platform control when [nativeChrome] is active,
 * falling back to [fallback] otherwise.
 */
@Composable
expect fun AdaptiveSegmentedControl(
    labels: List<String>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    segmentEnabled: List<Boolean> = List(labels.size) { true },
    label: String = "",
    fallback: @Composable () -> Unit,
)

/**
 * A slider that separates continuous local preview ([onValueChange]) from final commit
 * ([onValueChangeFinished]) on native platforms when [nativeChrome] is active.
 */
@Composable
expect fun AdaptiveSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String = "",
    valueDescription: String = "",
    fallback: @Composable () -> Unit,
)

/**
 * Snaps [raw] to the nearest discrete value within [[min], [max]] using the same interval
 * definition as Material's `Slider`: `steps` intermediate points produce `steps + 1` equal intervals.
 */
fun snapSliderValue(
    raw: Float,
    min: Float,
    max: Float,
    steps: Int,
): Float {
    if (max <= min || raw.isNaN()) return min
    val clamped = raw.coerceIn(min, max)
    if (steps <= 0) return clamped
    val intervals = steps + 1
    val fraction = (clamped - min) / (max - min)
    val index = (fraction * intervals).roundToInt().coerceIn(0, intervals)
    return (min + (max - min) * index / intervals).coerceIn(min, max)
}

/**
 * Returns whether all equal-width segments comfortably fit within [availableWidthPx] without
 * clipping or shrinking text. Used on iOS settings to drop down to a menu row for long translations
 * or large accessibility text sizes.
 */
fun shouldUseSegmentedControl(
    labelWidthsPx: List<Float>,
    availableWidthPx: Float,
    segmentPaddingPx: Float,
    maxSegments: Int = 4,
): Boolean {
    val count = labelWidthsPx.size
    if (count < 2 || count > maxSegments || availableWidthPx <= 0f) return false
    val segmentWidth = availableWidthPx / count
    val maxLabelWidth = labelWidthsPx.maxOrNull() ?: return false
    return maxLabelWidth + segmentPaddingPx <= segmentWidth
}

/**
 * Measures [labels] at full display scale (system text scale via [density] and in-app reading
 * scale via [appFontScale]) and returns true only when every segment can be shown at its natural
 * single-line width without truncation inside a native equal-width segmented control.
 */
@Composable
fun rememberFitsSegmentedControl(
    labels: List<String>,
    availableWidth: Dp,
    density: Density = LocalDensity.current,
    appFontScale: Float = LocalPlazaFontScale.current,
    style: TextStyle =
        MaterialTheme.typography.labelMedium.copy(
            fontSize = SegmentFontSize * appFontScale.coerceAtLeast(0.5f),
            fontWeight = FontWeight.SemiBold,
        ),
): Boolean {
    val measurer = rememberTextMeasurer()
    return remember(labels, availableWidth, density.density, density.fontScale, appFontScale, style) {
        with(density) {
            val widths =
                labels.map { text ->
                    measurer.measure(
                        text = text,
                        style = style,
                        maxLines = 1,
                        density = density,
                    ).size.width.toFloat()
                }
            shouldUseSegmentedControl(
                labelWidthsPx = widths,
                availableWidthPx = availableWidth.toPx(),
                segmentPaddingPx = SegmentHorizontalPadding.toPx(),
            )
        }
    }
}

private val SegmentFontSize = 13.sp
private val SegmentHorizontalPadding = 30.dp
