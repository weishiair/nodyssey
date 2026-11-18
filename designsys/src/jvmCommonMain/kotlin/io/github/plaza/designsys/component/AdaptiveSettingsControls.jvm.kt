package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified

@Composable
actual fun AdaptiveNativeSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    label: String,
    accessibilityEnabled: Boolean,
    fallback: @Composable () -> Unit,
) {
    fallback()
}

@Composable
actual fun AdaptiveSegmentedControl(
    labels: List<String>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    segmentEnabled: List<Boolean>,
    label: String,
    fallback: @Composable () -> Unit,
) {
    fallback()
}

@Composable
actual fun AdaptiveSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    label: String,
    valueDescription: String,
    fallback: @Composable () -> Unit,
) {
    fallback()
}

@Composable
internal actual fun rememberSegmentedControlMetrics(
    labels: List<String>,
    density: Density,
    appFontScale: Float,
    style: TextStyle,
): SegmentedControlMetrics {
    val measurer = rememberTextMeasurer()
    val unclippedStyle =
        if (style.lineHeight.isSpecified) style.copy(lineHeight = TextUnit.Unspecified) else style
    return remember(labels, density.density, density.fontScale, appFontScale, unclippedStyle) {
        with(density) {
            val results =
                labels.map { text ->
                    measurer.measure(
                        text = text,
                        style = unclippedStyle,
                        maxLines = 1,
                        density = density,
                    )
                }
            SegmentedControlMetrics(
                labelWidthsPx = results.map { it.size.width.toFloat() },
                labelHeightPx = results.maxOfOrNull { it.size.height.toFloat() } ?: 0f,
                availableHeightPx = SegmentControlMinHeight.toPx(),
                verticalPaddingPx = SegmentVerticalPadding.toPx(),
            )
        }
    }
}
