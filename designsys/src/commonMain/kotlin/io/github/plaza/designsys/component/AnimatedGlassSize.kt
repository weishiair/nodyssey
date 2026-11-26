package io.github.plaza.designsys.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.approachLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import io.github.plaza.designsys.theme.LocalEinkMode
import kotlinx.coroutines.launch

/**
 * Smoothly morphs a native glass view's size using the theme's expressive spatial spring.
 *
 * Neither `Modifier.animateContentSize` nor `Modifier.animateBounds` fits a `UIKitView` stacked in
 * a column with another floating control: `animateContentSize` leaves the child measured at its
 * final target size (so the native glass capsule still snaps), while `animateBounds` reports the
 * lookahead size to the outer layout immediately (causing stacked controls like `PageJumpRail` and
 * `PlazaExtendedFab` to overlap mid-flight). Measuring the child at the interpolated dimensions
 * inside `LookaheadScope` + `approachLayout` updates both the native view's frame and the parent
 * layout together on every frame.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun AnimatedGlassSize(
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val spatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntSize>()
    val snapMotion = LocalEinkMode.current || spatialSpec is SnapSpec<*>
    val scope = rememberCoroutineScope()
    val state = remember { GlassSizeAnimationState() }

    Box(modifier = modifier, propagateMinConstraints = true) {
        LookaheadScope {
            val sizeModifier =
                if (snapMotion) {
                    Modifier
                } else {
                    Modifier.approachLayout(
                        isMeasurementApproachInProgress = { lookaheadSize ->
                            state.isApproachInProgress(lookaheadSize)
                        },
                        approachMeasure = { measurable, constraints ->
                            val target = lookaheadSize
                            val current = state.stepToward(target, scope, spatialSpec)
                            val width = constraints.constrainWidth(current.width.coerceAtLeast(1))
                            val height = constraints.constrainHeight(current.height.coerceAtLeast(1))
                            val placeable = measurable.measure(Constraints.fixed(width, height))
                            layout(placeable.width, placeable.height) {
                                placeable.place(0, 0)
                            }
                        },
                    )
                }
            content(sizeModifier)
        }
    }
}

private class GlassSizeAnimationState {
    private var animatable: Animatable<IntSize, AnimationVector2D>? = null
    private var lastLookaheadSize: IntSize? = null
    private var lastTarget: IntSize? = null

    fun isApproachInProgress(lookaheadSize: IntSize): Boolean {
        val previous = lastLookaheadSize
        if (previous == null) {
            lastLookaheadSize = lookaheadSize
            return false
        }
        if (previous != lookaheadSize && animatable == null) {
            animatable = Animatable(previous, IntSize.VectorConverter)
            lastTarget = previous
        }
        lastLookaheadSize = lookaheadSize
        val anim = animatable ?: return false
        return anim.value != lookaheadSize || lastTarget != lookaheadSize
    }

    fun stepToward(
        target: IntSize,
        scope: kotlinx.coroutines.CoroutineScope,
        spec: androidx.compose.animation.core.FiniteAnimationSpec<IntSize>,
    ): IntSize {
        val anim = animatable ?: Animatable(target, IntSize.VectorConverter).also {
            animatable = it
            lastTarget = target
        }
        if (lastTarget != target) {
            lastTarget = target
            scope.launch {
                anim.animateTo(target, animationSpec = spec)
            }
        }
        return anim.value
    }
}
