package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
