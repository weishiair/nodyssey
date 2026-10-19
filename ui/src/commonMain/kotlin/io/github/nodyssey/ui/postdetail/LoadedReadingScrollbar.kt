package io.github.nodyssey.ui.postdetail

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.post_loaded_scroll_position
import io.github.plaza.designsys.theme.LocalEinkMode
import io.github.plaza.designsys.theme.LocalPlazaLayers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * Foundation's VerticalScrollbar is desktop-only. Draw Android's thumb from the official
 * LazyListState.scrollIndicatorState, and remove this when a common scrollbar becomes available.
 * The range is the loaded list, never an extrapolation over replies not fetched from the server.
 */
@Composable
internal fun LoadedReadingScrollbar(
    listState: LazyListState,
    itemKeys: List<Any>,
    currentFloor: Int,
    onDraggingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val latestKeys by rememberUpdatedState(itemKeys)
    DisposableEffect(Unit) {
        onDispose { onDraggingChanged(false) }
    }
    val requests = remember { MutableSharedFlow<LoadedScrollTarget>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST) }
    LaunchedEffect(listState) {
        requests.collectLatest { target ->
            val index = latestKeys.indexOf(target.key)
            if (index >= 0) listState.scrollToItem(index, target.offset)
        }
    }
    val metrics by remember(listState, itemKeys) {
        derivedStateOf {
            val indicator = listState.scrollIndicatorState
            val layout = listState.layoutInfo
            if (indicator == null || indicator.contentSize == Int.MAX_VALUE || indicator.viewportSize == Int.MAX_VALUE || indicator.scrollOffset == Int.MAX_VALUE || itemKeys.isEmpty()) {
                null
            } else {
                LoadedScrollMetrics(itemKeys, indicator.contentSize, indicator.viewportSize, indicator.scrollOffset, layout.beforeContentPadding, layout.afterContentPadding)
            }
        }
    }
    val latestMetrics by rememberUpdatedState(metrics)
    var dragMetrics by remember { mutableStateOf<LoadedScrollMetrics?>(null) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var railHeight by remember { mutableIntStateOf(0) }
    val minThumb = with(density) { 32.dp.toPx() }
    val thumbWidth = with(density) { 4.dp.toPx() }
    val thumbInset = with(density) { 4.dp.toPx() }
    fun thumbSize(metrics: LoadedScrollMetrics): Float = (railHeight * metrics.viewportFraction).coerceIn(minThumb.coerceAtMost(railHeight.toFloat()), railHeight.toFloat())
    fun fractionAt(y: Float, metrics: LoadedScrollMetrics): Float = ((y - thumbSize(metrics) / 2) / (railHeight - thumbSize(metrics)).coerceAtLeast(1f)).coerceIn(0f, 1f)
    fun request(metrics: LoadedScrollMetrics, fraction: Float): Boolean = metrics.targetAt(fraction)?.let(requests::tryEmit) ?: false
    val displayed = dragMetrics ?: metrics ?: return
    if (displayed.maxOffset <= 0) return
    val fraction = if (dragMetrics != null) dragFraction else displayed.fraction
    val positionLabel = stringResource(Res.string.post_loaded_scroll_position)
    val thumbColor = MaterialTheme.colorScheme.outline.copy(alpha = if (LocalEinkMode.current || dragMetrics != null) 1f else 0.6f)
    Box(
        modifier = modifier.fillMaxHeight().width(24.dp).padding(vertical = 12.dp)
            .onSizeChanged { railHeight = it.height }
            .semantics {
                contentDescription = positionLabel
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                setProgress { value -> request(displayed, value) }
            }
            .draggable(
                state = rememberDraggableState { delta ->
                    val current = dragMetrics ?: return@rememberDraggableState
                    dragFraction = (dragFraction + delta / (railHeight - thumbSize(current)).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    request(current, dragFraction)
                },
                orientation = Orientation.Vertical,
                onDragStarted = { position ->
                    latestMetrics?.let { current ->
                        dragMetrics = current
                        val top = current.fraction * (railHeight - thumbSize(current))
                        dragFraction = if (position.y in top..top + thumbSize(current)) current.fraction else fractionAt(position.y, current)
                        onDraggingChanged(true)
                        request(current, dragFraction)
                    }
                },
                onDragStopped = {
                    dragMetrics = null
                    onDraggingChanged(false)
                },
            )
            .pointerInput(railHeight) {
                detectTapGestures { position -> latestMetrics?.let { request(it, fractionAt(position.y, it)) } }
            }
            .drawBehind {
                val current = dragMetrics ?: metrics ?: return@drawBehind
                val height = thumbSize(current)
                val position = if (dragMetrics != null) dragFraction else current.fraction
                drawRoundRect(thumbColor, Offset(size.width - thumbInset - thumbWidth, position * (size.height - height)), Size(thumbWidth, height), CornerRadius(thumbWidth))
            },
    ) {
        if (dragMetrics != null) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd)
                    .offset { IntOffset(with(density) { (-20).dp.roundToPx() }, ((railHeight - thumbSize(displayed)) * fraction).roundToInt()) }
                    .wrapContentWidth(Alignment.End, unbounded = true),
                color = LocalPlazaLayers.current.card,
                shape = RoundedCornerShape(9.dp),
            ) {
                Text(
                    currentFloor.toString(),
                    Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
