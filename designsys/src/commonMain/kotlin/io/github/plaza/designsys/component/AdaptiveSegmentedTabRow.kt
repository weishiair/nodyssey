package io.github.plaza.designsys.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.theme.LocalPlazaDarkTheme
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.PlazaLayers
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import io.github.plaza.designsys.theme.cardBorder
import io.github.plaza.designsys.theme.cardShadow
import io.github.plaza.designsys.theme.nativeGlass
import kotlin.math.roundToInt

/**
 * Adaptive segmented tab switch: renders an iOS-style capsule track with a sliding thumb when
 * [PlazaLayers.nativeGlass] is active, and invokes [fallback] unchanged everywhere else.
 */
@Composable
fun AdaptiveSegmentedTabRow(
    tabs: List<TabLabel>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectionFraction: (() -> Float)? = null,
    fallback: @Composable () -> Unit,
) {
    AdaptiveSegmentedTabRowContent(
        tabs = tabs,
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        modifier = modifier,
        selectionFraction = selectionFraction,
        useCapsuleStyle = LocalPlazaLayers.current.nativeGlass,
        haptics = rememberPlatformHaptics(),
        fallback = fallback,
    )
}

@Composable
internal fun AdaptiveSegmentedTabRowContent(
    tabs: List<TabLabel>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectionFraction: (() -> Float)? = null,
    useCapsuleStyle: Boolean = LocalPlazaLayers.current.nativeGlass,
    haptics: PlatformHaptics = rememberPlatformHaptics(),
    fallback: @Composable () -> Unit,
) {
    if (!useCapsuleStyle) {
        fallback()
        return
    }
    if (tabs.isEmpty()) return

    val layers = LocalPlazaLayers.current
    val darkTheme = LocalPlazaDarkTheme.current
    val clampedSelected = selectedIndex.coerceIn(0, tabs.lastIndex)
    val thumbColor = segmentedThumbColor(layers)
    val trackColor = segmentedTrackColor(layers, thumbColor, darkTheme)

    val animatedFraction by animateFloatAsState(
        targetValue = clampedSelected.toFloat(),
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "segmentedTabFraction",
    )
    val readFraction: () -> Float = selectionFraction ?: { animatedFraction }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(trackColor, CircleShape)
            .padding(CapsuleThumbInset),
    ) {
        // Slide the thumb during layout so pager scroll progress moves without recomposing tabs.
        Box(
            Modifier
                .matchParentSize()
                .layout { measurable, constraints ->
                    val count = tabs.size.coerceAtLeast(1)
                    val totalWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
                    val segmentWidthF = totalWidth.toFloat() / count
                    val thumbWidth = segmentWidthF.roundToInt().coerceIn(0, totalWidth)
                    val childConstraints =
                        if (constraints.hasBoundedWidth) {
                            constraints.copy(minWidth = thumbWidth, maxWidth = thumbWidth)
                        } else {
                            constraints
                        }
                    val placeable = measurable.measure(childConstraints)
                    val layoutWidth = if (constraints.hasBoundedWidth) totalWidth else placeable.width
                    val layoutHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else placeable.height
                    layout(layoutWidth, layoutHeight) {
                        val rawFraction = readFraction().coerceIn(0f, (count - 1).toFloat())
                        val maxX = (layoutWidth - placeable.width).coerceAtLeast(0)
                        val x = (rawFraction * segmentWidthF).roundToInt().coerceIn(0, maxX)
                        placeable.placeRelative(x = x, y = 0)
                    }
                }
                .cardShadow(CircleShape, layers.shadows)
                .background(thumbColor, CircleShape)
                .cardBorder(layers, CircleShape),
        )

        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == clampedSelected
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = CapsuleSegmentMinHeight)
                        .clip(CircleShape)
                        .selectable(
                            selected = selected,
                            onClick = {
                                if (!selected) haptics.toggle()
                                onSelect(index)
                            },
                            role = Role.Tab,
                            interactionSource = interactionSource,
                            indication = null,
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = tab.text,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color =
                            if (selected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        tab.badge?.let { count ->
                            Badge {
                                Text(
                                    text = count,
                                    style = LocalTextStyle.current.copy(fontFeatureSettings = TABULAR_FIGURES),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun segmentedThumbColor(layers: PlazaLayers): Color =
    if (layers.raised == layers.inset) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        layers.raised
    }

@Composable
private fun segmentedTrackColor(
    layers: PlazaLayers,
    thumbColor: Color,
    darkTheme: Boolean,
): Color {
    if (layers.cardBorder != null) return layers.card
    if (layers.inset != layers.page && layers.inset != thumbColor) return layers.inset
    val high = MaterialTheme.colorScheme.surfaceContainerHigh
    if (high != layers.page && high != thumbColor) return high
    return if (darkTheme) {
        Color.Black.copy(alpha = 0.24f).compositeOver(layers.inset)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f).compositeOver(layers.inset)
    }
}

private val CapsuleThumbInset = 3.dp
private val CapsuleSegmentMinHeight = 34.dp
