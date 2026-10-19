package io.github.nodyssey.ui.postdetail

import kotlin.math.floor

internal data class LoadedScrollTarget(val key: Any, val offset: Int)

/** A frozen Foundation estimate while dragging, so a new layout cannot move the drag's endpoints. */
internal data class LoadedScrollMetrics(
    val keys: List<Any>,
    val contentSize: Int,
    val viewportSize: Int,
    val scrollOffset: Int,
    val beforePadding: Int,
    val afterPadding: Int,
) {
    val maxOffset: Int get() = (contentSize - viewportSize).coerceAtLeast(0)
    val fraction: Float get() = if (maxOffset == 0) 0f else (scrollOffset.toFloat() / maxOffset).coerceIn(0f, 1f)
    val viewportFraction: Float get() = (viewportSize.toFloat() / contentSize.coerceAtLeast(1)).coerceIn(0f, 1f)

    /** ScrollIndicatorState has no offset-to-item API; use its row-size estimate with stable keys. */
    fun targetAt(fraction: Float): LoadedScrollTarget? {
        if (keys.isEmpty()) return null
        val position = fraction.coerceIn(0f, 1f)
        if (position == 0f) return LoadedScrollTarget(keys.first(), 0)
        if (position == 1f) return LoadedScrollTarget(keys.last(), 0)
        val average = ((contentSize - beforePadding - afterPadding).toFloat() / keys.size).coerceAtLeast(1f)
        val distance = (position * maxOffset - beforePadding).coerceAtLeast(0f)
        val index = floor(distance / average).toInt().coerceIn(keys.indices)
        return LoadedScrollTarget(keys[index], (distance - index * average).toInt().coerceIn(0, average.toInt().coerceAtLeast(1) - 1))
    }
}
