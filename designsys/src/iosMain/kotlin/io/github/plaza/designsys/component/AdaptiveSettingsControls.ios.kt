@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package io.github.plaza.designsys.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.viewinterop.remeasureRequester
import androidx.compose.ui.viewinterop.rememberUIKitInteropRemeasureRequester
import io.github.plaza.designsys.nativeglass.plaza_control_add_value_changed_action
import io.github.plaza.designsys.nativeglass.plaza_segmented_control_intrinsic_height
import io.github.plaza.designsys.nativeglass.plaza_segmented_control_selected_index
import io.github.plaza.designsys.nativeglass.plaza_segmented_control_set_appearance
import io.github.plaza.designsys.nativeglass.plaza_segmented_control_set_segment_enabled
import io.github.plaza.designsys.nativeglass.plaza_segmented_control_set_segments
import io.github.plaza.designsys.nativeglass.plaza_segmented_control_set_selected_index
import io.github.plaza.designsys.nativeglass.plaza_slider_configure
import io.github.plaza.designsys.nativeglass.plaza_slider_is_tracking
import io.github.plaza.designsys.nativeglass.plaza_slider_set_value
import io.github.plaza.designsys.nativeglass.plaza_slider_value
import io.github.plaza.designsys.nativeglass.plaza_slider_wire_events
import io.github.plaza.designsys.nativeglass.plaza_switch_is_on
import io.github.plaza.designsys.nativeglass.plaza_switch_set_on
import io.github.plaza.designsys.nativeglass.plaza_switch_set_on_tint
import io.github.plaza.designsys.nativeglass.plaza_text_height_for_font
import io.github.plaza.designsys.nativeglass.plaza_text_width_for_font
import io.github.plaza.designsys.theme.LocalPlazaDarkTheme
import io.github.plaza.designsys.theme.LocalPlazaFontScale
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.nativeChrome
import kotlinx.cinterop.CValue
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGPoint
import platform.CoreGraphics.CGRectZero
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIColor
import platform.UIKit.UIControl
import platform.UIKit.UIEvent
import platform.UIKit.UIFont
import platform.UIKit.UIFontMetrics
import platform.UIKit.UIFontWeightMedium
import platform.UIKit.UISegmentedControl
import platform.UIKit.UISlider
import platform.UIKit.UISwitch
import platform.UIKit.UIView
import platform.UIKit.accessibilityLabel
import platform.UIKit.accessibilityValue
import platform.UIKit.isAccessibilityElement

// Scrolling controls stay below the Compose canvas so fixed headers cover rows that leave the viewport.
private fun settingsControlInterop(accessibilityEnabled: Boolean): UIKitInteropProperties =
    UIKitInteropProperties(
        interactionMode = UIKitInteropInteractionMode.Cooperative(),
        isNativeAccessibilityEnabled = accessibilityEnabled,
    )

private val SettingsSliderInterop = settingsControlInterop(accessibilityEnabled = true)
private val SettingsSegmentedInterop = settingsControlInterop(accessibilityEnabled = true)

// Compose's InteropWrappingView clips to Auto Layout's alignment rect, while iOS 26+ UISwitch
// extends ~2pt past its trailing alignment inset (63pt visual vs 61pt intrinsic). Wrapping in a
// non-clipping host view keeps the right capsule edge and Liquid Glass highlights intact.
private const val SWITCH_CONTAINER_HORIZONTAL_INSET = 6.0
private const val SWITCH_CONTAINER_VERTICAL_INSET = 4.0

private val SettingsSliderMaxWidth = 248.dp
private val SettingsSliderHorizontalPadding = 24.dp

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
    val layers = LocalPlazaLayers.current
    if (!layers.nativeChrome) {
        fallback()
        return
    }
    val dark = LocalPlazaDarkTheme.current
    val accent = MaterialTheme.colorScheme.primary
    val backdrop = layers.card
    val host = remember { NativeSettingsSwitchHost() }
    val remeasure = rememberUIKitInteropRemeasureRequester()
    val interop = remember(accessibilityEnabled) { settingsControlInterop(accessibilityEnabled) }
    UIKitView(
        factory = { host.containerView },
        modifier = modifier.defaultMinSize(minWidth = 51.dp, minHeight = 31.dp).remeasureRequester(remeasure),
        properties = interop,
        update = {
            host.update(
                checked = checked,
                enabled = enabled,
                label = label,
                accessibilityEnabled = accessibilityEnabled,
                accent = accent.toUIColor(),
                backdrop = backdrop.toUIColor(),
                dark = dark,
                onCheckedChange = onCheckedChange,
            )
            remeasure.requestRemeasure()
        },
        onRelease = { host.dispose() },
    )
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
    val layers = LocalPlazaLayers.current
    if (!layers.nativeChrome || labels.isEmpty()) {
        fallback()
        return
    }
    val dark = LocalPlazaDarkTheme.current
    val scale = LocalPlazaFontScale.current
    val systemFontScale = LocalDensity.current.fontScale
    val backdrop = layers.card
    val host = remember { NativeSegmentedControlHost() }
    val remeasure = rememberUIKitInteropRemeasureRequester()
    UIKitView(
        factory = { host.containerView },
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = SegmentControlMinHeight).remeasureRequester(remeasure),
        properties = SettingsSegmentedInterop,
        update = {
            host.update(
                labels = labels,
                selectedIndex = selectedIndex,
                enabled = enabled,
                segmentEnabled = segmentEnabled,
                label = label,
                backdrop = backdrop.toUIColor(),
                dark = dark,
                scale = scale,
                systemFontScale = systemFontScale,
                onSelectIndex = onSelectIndex,
            )
            remeasure.requestRemeasure()
        },
        onRelease = { host.dispose() },
    )
}

@Composable
internal actual fun rememberSegmentedControlMetrics(
    labels: List<String>,
    density: Density,
    appFontScale: Float,
    style: TextStyle,
): SegmentedControlMetrics {
    val probeControl = remember { UISegmentedControl() }
    val liveSystemScale = LocalDensity.current.fontScale.coerceAtLeast(0.5f)
    val syntheticScale = (density.fontScale / liveSystemScale).coerceAtLeast(1f)
    val basePointSize =
        if (style.fontSize.isSp) {
            style.fontSize.value.toDouble()
        } else {
            (SegmentFontSize.value * appFontScale.coerceAtLeast(0.5f)).toDouble()
        } * syntheticScale
    return remember(labels, density.density, density.fontScale, basePointSize) {
        val scaledFont =
            UIFontMetrics.defaultMetrics.scaledFontForFont(
                UIFont.systemFontOfSize(basePointSize, weight = UIFontWeightMedium),
            )
        if (labels.isNotEmpty()) {
            plaza_segmented_control_set_segments(probeControl, labels)
            plaza_segmented_control_set_appearance(
                probeControl,
                selectedTint = null,
                selectedTextColor = null,
                normalTextColor = null,
                font = scaledFont,
            )
        }
        val controlHeightPt =
            maxOf(
                SegmentControlMinHeight.value.toDouble(),
                plaza_segmented_control_intrinsic_height(probeControl),
            )
        val scaleFactor = density.density
        val widthsPx =
            labels.map { text ->
                (plaza_text_width_for_font(text, scaledFont) * scaleFactor).toFloat()
            }
        val maxTextHeightPt =
            labels.maxOfOrNull { text -> plaza_text_height_for_font(text, scaledFont) } ?: 0.0
        SegmentedControlMetrics(
            labelWidthsPx = widthsPx,
            labelHeightPx = (maxTextHeightPt * scaleFactor).toFloat(),
            availableHeightPx = (controlHeightPt * scaleFactor).toFloat(),
            verticalPaddingPx = with(density) { SegmentVerticalPadding.toPx() },
        )
    }
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
    val layers = LocalPlazaLayers.current
    if (!layers.nativeChrome) {
        fallback()
        return
    }
    val dark = LocalPlazaDarkTheme.current
    val accent = MaterialTheme.colorScheme.primary
    val backdrop = layers.card
    val host = remember { NativeSliderHost() }
    val remeasure = rememberUIKitInteropRemeasureRequester()
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SettingsSliderHorizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        UIKitView(
            factory = { host.containerView },
            modifier = modifier.widthIn(max = SettingsSliderMaxWidth).fillMaxWidth()
                .defaultMinSize(minHeight = 34.dp).remeasureRequester(remeasure),
            properties = SettingsSliderInterop,
            update = {
                host.update(
                    value = value,
                    min = valueRange.start,
                    max = valueRange.endInclusive,
                    steps = steps,
                    enabled = enabled,
                    label = label,
                    valueDescription = valueDescription,
                    accent = accent.toUIColor(),
                    backdrop = backdrop.toUIColor(),
                    dark = dark,
                    onValueChange = onValueChange,
                    onValueChangeFinished = onValueChangeFinished,
                )
                remeasure.requestRemeasure()
            },
            onRelease = { host.dispose() },
        )
    }
}

// UIAction retains only these state objects (no UIView reference), avoiding ARC retain cycles.
private class SwitchCallbackState {
    var checked: Boolean = false
    var enabled: Boolean = true
    var isSyncing: Boolean = false
    var disposed: Boolean = false
    var onCheckedChange: (Boolean) -> Unit = {}
}

// Hosts a below-canvas UIKit control inside an opaque card backdrop so Compose's BlendMode.Clear
// punch-through shows `layers.card` instead of ComposeContainerView's default black/white fill.
private class SettingsControlContainerView(
    private val control: UIControl,
    horizontalInset: Double = 0.0,
    verticalInset: Double = 0.0,
    private val routeMarginTouchesToControl: Boolean = false,
) : UIView(frame = CGRectZero.readValue()) {
    init {
        clipsToBounds = false
        backgroundColor = UIColor.clearColor
        isAccessibilityElement = false
        addSubview(control)
        control.translatesAutoresizingMaskIntoConstraints = false
        if (horizontalInset > 0.0 || verticalInset > 0.0) {
            control.clipsToBounds = false
            NSLayoutConstraint.activateConstraints(
                listOf(
                    control.centerXAnchor.constraintEqualToAnchor(centerXAnchor),
                    control.centerYAnchor.constraintEqualToAnchor(centerYAnchor),
                    control.leadingAnchor.constraintGreaterThanOrEqualToAnchor(
                        leadingAnchor,
                        constant = horizontalInset,
                    ),
                    trailingAnchor.constraintGreaterThanOrEqualToAnchor(
                        control.trailingAnchor,
                        constant = horizontalInset,
                    ),
                    control.topAnchor.constraintGreaterThanOrEqualToAnchor(
                        topAnchor,
                        constant = verticalInset,
                    ),
                    bottomAnchor.constraintGreaterThanOrEqualToAnchor(
                        control.bottomAnchor,
                        constant = verticalInset,
                    ),
                ),
            )
        } else {
            NSLayoutConstraint.activateConstraints(
                listOf(
                    control.leadingAnchor.constraintEqualToAnchor(leadingAnchor),
                    trailingAnchor.constraintEqualToAnchor(control.trailingAnchor),
                    control.topAnchor.constraintEqualToAnchor(topAnchor),
                    bottomAnchor.constraintEqualToAnchor(control.bottomAnchor),
                ),
            )
        }
    }

    // GroupedSwitchRow yields touches inside containerView's bounds to UIKitView, so route taps
    // landing in the outer inset margin straight to the switch when enabled.
    override fun hitTest(point: CValue<CGPoint>, withEvent: UIEvent?): UIView? {
        val target = super.hitTest(point, withEvent) ?: return null
        return if (routeMarginTouchesToControl && target === this) {
            control.takeIf { it.enabled && it.isUserInteractionEnabled() }
        } else {
            target
        }
    }
}

private fun syncControlBackdrop(
    containerView: UIView,
    control: UIView,
    dark: Boolean,
    backdrop: UIColor,
) {
    val style = interfaceStyle(dark)
    containerView.overrideUserInterfaceStyle = style
    containerView.backgroundColor = backdrop
    containerView.isAccessibilityElement = false
    control.overrideUserInterfaceStyle = style
}

internal class NativeSettingsSwitchHost {
    private val state = SwitchCallbackState()
    val view: UISwitch = UISwitch().apply {
        val currentState = this@NativeSettingsSwitchHost.state
        plaza_control_add_value_changed_action(this) { sender ->
            if (currentState.disposed || !currentState.enabled || currentState.isSyncing) {
                return@plaza_control_add_value_changed_action
            }
            val switchControl = sender as? UISwitch ?: return@plaza_control_add_value_changed_action
            val next = plaza_switch_is_on(switchControl)
            if (next != currentState.checked) {
                currentState.checked = next
                currentState.onCheckedChange(next)
            }
        }
    }
    val containerView: UIView =
        SettingsControlContainerView(
            control = view,
            horizontalInset = SWITCH_CONTAINER_HORIZONTAL_INSET,
            verticalInset = SWITCH_CONTAINER_VERTICAL_INSET,
            routeMarginTouchesToControl = true,
        )

    fun update(
        checked: Boolean,
        enabled: Boolean,
        label: String,
        accessibilityEnabled: Boolean,
        accent: UIColor,
        dark: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        backdrop: UIColor = UIColor.clearColor,
    ) {
        state.disposed = false
        state.enabled = enabled
        state.checked = checked
        state.onCheckedChange = onCheckedChange

        syncControlBackdrop(containerView = containerView, control = view, dark = dark, backdrop = backdrop)
        view.enabled = enabled
        plaza_switch_set_on_tint(view, accent)
        view.isAccessibilityElement = accessibilityEnabled
        view.accessibilityLabel = label.takeIf { accessibilityEnabled && it.isNotEmpty() }

        if (plaza_switch_is_on(view) != checked) {
            state.isSyncing = true
            try {
                plaza_switch_set_on(view, checked, animated = view.window != null)
            } finally {
                state.isSyncing = false
            }
        }
        view.invalidateIntrinsicContentSize()
        containerView.invalidateIntrinsicContentSize()
    }

    fun dispose() {
        state.disposed = true
        state.onCheckedChange = {}
    }
}

private class SegmentedCallbackState {
    var labels: List<String> = emptyList()
    var enabledFlags: List<Boolean> = emptyList()
    var selectedIndex: Int = -1
    var enabled: Boolean = true
    var isSyncing: Boolean = false
    var disposed: Boolean = false
    var onSelectIndex: (Int) -> Unit = {}
}

internal class NativeSegmentedControlHost {
    private val state = SegmentedCallbackState()
    private var lastLabels: List<String> = emptyList()
    private var lastEnabledFlags: List<Boolean> = emptyList()
    private var lastScale: Float = -1f
    private var lastSystemFontScale: Float = -1f

    val view: UISegmentedControl = UISegmentedControl().apply {
        val currentState = this@NativeSegmentedControlHost.state
        plaza_control_add_value_changed_action(this) { sender ->
            if (currentState.disposed || !currentState.enabled || currentState.isSyncing) {
                return@plaza_control_add_value_changed_action
            }
            val segmented = sender as? UISegmentedControl ?: return@plaza_control_add_value_changed_action
            val next = plaza_segmented_control_selected_index(segmented)
            val segmentAllowed = next in currentState.enabledFlags.indices && currentState.enabledFlags[next]
            if (!segmentAllowed) {
                currentState.isSyncing = true
                try {
                    plaza_segmented_control_set_selected_index(segmented, currentState.selectedIndex)
                } finally {
                    currentState.isSyncing = false
                }
                return@plaza_control_add_value_changed_action
            }
            if (next != currentState.selectedIndex) {
                currentState.selectedIndex = next
                currentState.onSelectIndex(next)
            }
        }
    }

    val containerView: UIView = SettingsControlContainerView(control = view)

    fun update(
        labels: List<String>,
        selectedIndex: Int,
        enabled: Boolean,
        segmentEnabled: List<Boolean>,
        label: String,
        dark: Boolean,
        scale: Float,
        onSelectIndex: (Int) -> Unit,
        backdrop: UIColor = UIColor.clearColor,
        systemFontScale: Float = 1f,
    ) {
        state.disposed = false
        state.enabled = enabled
        state.onSelectIndex = onSelectIndex

        val normalizedFlags =
            if (segmentEnabled.size == labels.size) {
                segmentEnabled
            } else {
                List(labels.size) { index -> segmentEnabled.getOrElse(index) { true } }
            }
        state.labels = labels
        state.enabledFlags = normalizedFlags
        val validSelection = if (selectedIndex in labels.indices) selectedIndex else -1
        state.selectedIndex = validSelection

        syncControlBackdrop(containerView = containerView, control = view, dark = dark, backdrop = backdrop)
        view.enabled = enabled
        view.accessibilityLabel = label.takeIf { it.isNotEmpty() }

        if (scale != lastScale || systemFontScale != lastSystemFontScale) {
            lastScale = scale
            lastSystemFontScale = systemFontScale
            val font =
                UIFontMetrics.defaultMetrics.scaledFontForFont(
                    UIFont.systemFontOfSize(SegmentFontSize.value.toDouble() * scale, weight = UIFontWeightMedium),
                )
            plaza_segmented_control_set_appearance(
                view,
                selectedTint = null,
                selectedTextColor = null,
                normalTextColor = null,
                font = font,
            )
        }

        state.isSyncing = true
        try {
            if (labels != lastLabels) {
                lastLabels = labels
                plaza_segmented_control_set_segments(view, labels)
                lastEnabledFlags = emptyList()
            }
            if (normalizedFlags != lastEnabledFlags) {
                lastEnabledFlags = normalizedFlags
                normalizedFlags.forEachIndexed { index, flag ->
                    plaza_segmented_control_set_segment_enabled(view, flag, index)
                }
            }
            if (plaza_segmented_control_selected_index(view) != validSelection) {
                plaza_segmented_control_set_selected_index(view, validSelection)
            }
        } finally {
            state.isSyncing = false
        }
        view.invalidateIntrinsicContentSize()
        containerView.invalidateIntrinsicContentSize()
    }

    fun dispose() {
        state.disposed = true
        state.onSelectIndex = {}
    }
}

private class SliderCallbackState {
    var min: Float = 0f
    var max: Float = 1f
    var steps: Int = 0
    var enabled: Boolean = true
    var isSyncing: Boolean = false
    var disposed: Boolean = false
    var isDragging: Boolean = false
    var hasActiveTouchSession: Boolean = false
    var dragBaseline: Float = 0f
    var lastDispatchedPreview: Float = 0f
    var onValueChange: (Float) -> Unit = {}
    var onValueChangeFinished: () -> Unit = {}

    fun snap(raw: Float): Float = snapSliderValue(raw, min, max, steps)
}

internal class NativeSliderHost {
    private val state = SliderCallbackState()

    val view: UISlider = UISlider().apply {
        val currentState = this@NativeSliderHost.state
        plaza_slider_wire_events(
            this,
            onTouchDown = { _ ->
                if (currentState.disposed || !currentState.enabled || currentState.isSyncing) return@plaza_slider_wire_events
                currentState.isDragging = true
                currentState.hasActiveTouchSession = true
                currentState.dragBaseline = currentState.lastDispatchedPreview
            },
            onValueChanged = { slider ->
                if (slider == null) return@plaza_slider_wire_events
                if (currentState.disposed || !currentState.enabled || currentState.isSyncing) return@plaza_slider_wire_events
                val snapped = currentState.snap(plaza_slider_value(slider))
                val tracking = currentState.isDragging || plaza_slider_is_tracking(slider)
                if (tracking) {
                    currentState.isDragging = true
                    currentState.hasActiveTouchSession = true
                    if (snapped != currentState.lastDispatchedPreview) {
                        currentState.lastDispatchedPreview = snapped
                        currentState.onValueChange(snapped)
                    }
                } else {
                    // VoiceOver adjustable swipes update the value without TouchDown/TouchUp;
                    // snap the control and commit immediately so accessibility edits persist.
                    if (plaza_slider_value(slider) != snapped) {
                        currentState.isSyncing = true
                        try {
                            plaza_slider_set_value(slider, snapped)
                        } finally {
                            currentState.isSyncing = false
                        }
                    }
                    if (snapped != currentState.lastDispatchedPreview) {
                        currentState.lastDispatchedPreview = snapped
                        currentState.onValueChange(snapped)
                    }
                    currentState.dragBaseline = snapped
                    currentState.onValueChangeFinished()
                }
            },
            onTouchEnd = { slider ->
                if (slider == null) return@plaza_slider_wire_events
                if (currentState.disposed || !currentState.enabled || currentState.isSyncing) return@plaza_slider_wire_events
                if (!currentState.hasActiveTouchSession && !currentState.isDragging) return@plaza_slider_wire_events
                currentState.isDragging = false
                currentState.hasActiveTouchSession = false
                val snapped = currentState.snap(plaza_slider_value(slider))
                if (plaza_slider_value(slider) != snapped) {
                    currentState.isSyncing = true
                    try {
                        plaza_slider_set_value(slider, snapped)
                    } finally {
                        currentState.isSyncing = false
                    }
                }
                if (snapped != currentState.lastDispatchedPreview) {
                    currentState.lastDispatchedPreview = snapped
                    currentState.onValueChange(snapped)
                }
                currentState.dragBaseline = snapped
                currentState.onValueChangeFinished()
            },
            onTouchCancel = { slider ->
                if (slider == null) return@plaza_slider_wire_events
                if (currentState.disposed || currentState.isSyncing) return@plaza_slider_wire_events
                if (!currentState.hasActiveTouchSession && !currentState.isDragging) return@plaza_slider_wire_events
                val baseline = currentState.dragBaseline
                currentState.isDragging = false
                currentState.hasActiveTouchSession = false
                if (plaza_slider_value(slider) != baseline) {
                    currentState.isSyncing = true
                    try {
                        plaza_slider_set_value(slider, baseline)
                    } finally {
                        currentState.isSyncing = false
                    }
                }
                if (currentState.lastDispatchedPreview != baseline) {
                    currentState.lastDispatchedPreview = baseline
                    currentState.onValueChange(baseline)
                }
            },
        )
    }

    val containerView: UIView = SettingsControlContainerView(control = view)

    fun update(
        value: Float,
        min: Float,
        max: Float,
        steps: Int,
        enabled: Boolean,
        label: String,
        valueDescription: String,
        accent: UIColor,
        dark: Boolean,
        onValueChange: (Float) -> Unit,
        onValueChangeFinished: () -> Unit,
        backdrop: UIColor = UIColor.clearColor,
    ) {
        val rangeChanged = state.min != min || state.max != max || state.steps != steps
        state.disposed = false
        state.min = min
        state.max = max
        state.steps = steps
        state.enabled = enabled
        state.onValueChange = onValueChange
        state.onValueChangeFinished = onValueChangeFinished

        val snappedInput = state.snap(value)
        syncControlBackdrop(containerView = containerView, control = view, dark = dark, backdrop = backdrop)
        view.enabled = enabled
        view.accessibilityLabel = label.takeIf { it.isNotEmpty() }
        view.accessibilityValue = valueDescription.takeIf { it.isNotEmpty() }

        state.isSyncing = true
        try {
            plaza_slider_configure(view, min, max, minTint = accent, maxTint = null)
            if (!enabled || rangeChanged) {
                state.isDragging = false
                state.hasActiveTouchSession = false
                state.lastDispatchedPreview = snappedInput
                state.dragBaseline = snappedInput
                plaza_slider_set_value(view, snappedInput)
            } else if (state.isDragging) {
                // While actively dragging, keep the UIKit thumb gliding smoothly under the finger
                // unless an external state change moved away from the preview value we just sent.
                if (snappedInput != state.lastDispatchedPreview) {
                    state.isDragging = false
                    state.hasActiveTouchSession = false
                    state.lastDispatchedPreview = snappedInput
                    state.dragBaseline = snappedInput
                    plaza_slider_set_value(view, snappedInput)
                }
            } else {
                state.lastDispatchedPreview = snappedInput
                state.dragBaseline = snappedInput
                if (plaza_slider_value(view) != snappedInput) {
                    plaza_slider_set_value(view, snappedInput)
                }
            }
        } finally {
            state.isSyncing = false
        }
        view.invalidateIntrinsicContentSize()
        containerView.invalidateIntrinsicContentSize()
    }

    fun dispose() {
        state.disposed = true
        state.isDragging = false
        state.hasActiveTouchSession = false
        state.onValueChange = {}
        state.onValueChangeFinished = {}
    }
}
