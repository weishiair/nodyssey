package io.github.plaza.designsys.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.theme.LocalPlazaDarkTheme
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.nativeGlass
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

internal const val IOS_REFRESH_TICK_COUNT = 12
internal const val IOS_REFRESH_CYCLE_MS = 960
private const val PULL_APPEAR_START_FRACTION = 0.15f
private const val PULL_MAX_TICK_ALPHA = 0.78f
private const val SPIN_MIN_TICK_ALPHA = 0.20f
private const val SPIN_MAX_TICK_ALPHA = 0.92f
private val IosSpinnerSize = 24.dp
private val IosSpinnerStrokeWidth = 2.2.dp

/**
 * Adaptive [PullToRefreshBox]: preserves Material's disc-and-arrow indicator on Android/JVM/non-glass
 * surfaces and draws an iOS 12-tick radial spinner without a disc backdrop when
 * [io.github.plaza.designsys.theme.PlazaLayers.nativeGlass] is active.
 */
@Composable
fun AdaptivePullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    state: PullToRefreshState = rememberPullToRefreshState(),
    contentAlignment: Alignment = Alignment.TopStart,
    indicator: @Composable BoxScope.() -> Unit = {
        AdaptivePullToRefreshIndicator(
            state = state,
            isRefreshing = isRefreshing,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    },
    enabled: Boolean = true,
    threshold: Dp = PullToRefreshDefaults.PositionalThreshold,
    content: @Composable BoxScope.() -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        contentAlignment = contentAlignment,
        indicator = indicator,
        enabled = enabled,
        threshold = threshold,
        content = content,
    )
}

/**
 * Adaptive pull-to-refresh indicator matching [PullToRefreshDefaults.Indicator] when
 * `!LocalPlazaLayers.current.nativeGlass`, or a disc-free 12-tick radial iOS spinner otherwise.
 */
@Composable
fun AdaptivePullToRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    containerColor: Color = PullToRefreshDefaults.indicatorContainerColor,
    color: Color = PullToRefreshDefaults.indicatorColor,
    maxDistance: Dp = PullToRefreshDefaults.IndicatorMaxDistance,
) {
    AdaptivePullToRefreshIndicatorContent(
        state = state,
        isRefreshing = isRefreshing,
        modifier = modifier,
        containerColor = containerColor,
        color = color,
        maxDistance = maxDistance,
        useIosStyle = LocalPlazaLayers.current.nativeGlass,
    )
}

@Composable
internal fun AdaptivePullToRefreshIndicatorContent(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    containerColor: Color = PullToRefreshDefaults.indicatorContainerColor,
    color: Color = PullToRefreshDefaults.indicatorColor,
    maxDistance: Dp = PullToRefreshDefaults.IndicatorMaxDistance,
    useIosStyle: Boolean = LocalPlazaLayers.current.nativeGlass,
) {
    if (!useIosStyle) {
        PullToRefreshDefaults.Indicator(
            state = state,
            isRefreshing = isRefreshing,
            modifier = modifier,
            containerColor = containerColor,
            color = color,
            maxDistance = maxDistance,
        )
        return
    }

    val tickColor =
        if (LocalPlazaDarkTheme.current) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    PullToRefreshDefaults.IndicatorBox(
        state = state,
        isRefreshing = isRefreshing,
        modifier = modifier,
        maxDistance = maxDistance,
        shape = CircleShape,
        containerColor = Color.Transparent,
        elevation = 0.dp,
    ) {
        IosRefreshSpinner(
            progress = state.distanceFraction,
            isRefreshing = isRefreshing,
            color = tickColor,
        )
    }
}

@Composable
private fun IosRefreshSpinner(
    progress: Float,
    isRefreshing: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val stepState: State<Float> =
        if (isRefreshing) {
            val transition = rememberInfiniteTransition(label = "iosRefreshSpinner")
            transition.animateFloat(
                initialValue = 0f,
                targetValue = IOS_REFRESH_TICK_COUNT.toFloat(),
                animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = IOS_REFRESH_CYCLE_MS, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "iosRefreshStep",
            )
        } else {
            remember { mutableFloatStateOf(0f) }
        }

    val semanticsModifier =
        when {
            isRefreshing -> modifier.progressSemantics()
            progress > 0f -> modifier.progressSemantics(progress.coerceIn(0f, 1f))
            else -> modifier
        }

    Canvas(modifier = semanticsModifier.size(IosSpinnerSize)) {
        val activeStep = floor(stepState.value).toInt().mod(IOS_REFRESH_TICK_COUNT)
        val alphas =
            iosRefreshTickAlphas(
                progress = progress,
                isRefreshing = isRefreshing,
                activeStep = activeStep,
            )
        val strokePx = IosSpinnerStrokeWidth.toPx()
        val radius = size.minDimension / 2f
        val innerRadius = radius * 0.48f
        val outerRadius = (radius - strokePx / 2f).coerceAtLeast(innerRadius)
        val center = Offset(size.width / 2f, size.height / 2f)

        for (index in 0 until IOS_REFRESH_TICK_COUNT) {
            val alpha = alphas[index]
            if (alpha <= 0.001f) continue
            // Start at 12 o'clock (-90 deg) and step clockwise by 30 deg per tick.
            val angleRad = ((-90.0 + index * (360.0 / IOS_REFRESH_TICK_COUNT)) * PI / 180.0)
            val cosA = cos(angleRad).toFloat()
            val sinA = sin(angleRad).toFloat()
            drawLine(
                color = color.copy(alpha = alpha),
                start = Offset(center.x + innerRadius * cosA, center.y + innerRadius * sinA),
                end = Offset(center.x + outerRadius * cosA, center.y + outerRadius * sinA),
                strokeWidth = strokePx,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * Computes per-tick alpha for a 12-tick radial iOS spinner (index 0 at 12 o'clock, clockwise):
 * during pull, ticks illuminate sequentially from [PULL_APPEAR_START_FRACTION] to `1f`;
 * during refresh, [activeStep] is brightest with a quadratic trailing decay.
 */
internal fun iosRefreshTickAlphas(
    progress: Float,
    isRefreshing: Boolean,
    activeStep: Int,
): List<Float> {
    if (!isRefreshing) {
        val normalized =
            ((progress - PULL_APPEAR_START_FRACTION) / (1f - PULL_APPEAR_START_FRACTION))
                .coerceIn(0f, 1f)
        return List(IOS_REFRESH_TICK_COUNT) { index ->
            (normalized * IOS_REFRESH_TICK_COUNT - index).coerceIn(0f, 1f) * PULL_MAX_TICK_ALPHA
        }
    }
    val head = activeStep.mod(IOS_REFRESH_TICK_COUNT)
    return List(IOS_REFRESH_TICK_COUNT) { index ->
        val distanceBehind = (head - index).mod(IOS_REFRESH_TICK_COUNT)
        val decay = 1f - distanceBehind.toFloat() / (IOS_REFRESH_TICK_COUNT - 1)
        SPIN_MIN_TICK_ALPHA + (SPIN_MAX_TICK_ALPHA - SPIN_MIN_TICK_ALPHA) * decay * decay
    }
}
