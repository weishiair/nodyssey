package io.github.plaza.designsys.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.asComposeShader
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.shadow.InnerShadow
import io.github.plaza.designsys.theme.FloatingCapsuleShape
import io.github.plaza.designsys.theme.GlassSurfaceStyle
import io.github.plaza.designsys.theme.LocalEinkMode
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.backdropGlass
import io.github.plaza.designsys.theme.glassSurfaceHighlight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.tanh
import com.kyant.backdrop.shadow.Shadow as BackdropShadow

/**
 * Dynamic icon/label scale exposed to tabs rendered inside the hidden magnifying capture layer of
 * [LiquidGlassBottomTabs]. Ordinary tabs read `1f` at rest and smoothly magnify inside the droplet
 * while the user presses or drags across the bar.
 */
val LocalLiquidGlassTabScale = staticCompositionLocalOf { { 1f } }

/**
 * Kyant `backdrop` 2.0 liquid-glass bottom tab bar:
 * 1. An outer refracting capsule (`blur(8.dp)` + `lens(24.dp, 24.dp)` + punched-out `Shadow`) that
 *    shifts subtly with drag inertia and carries an interactive radial press highlight.
 * 2. A hidden accent-tinted capture layer (`tabsBackdrop`) that mirrors the tabs so the floating
 *    indicator droplet can sample and refract both the page behind the bar and the tab icons inside.
 * 3. A draggable liquid-glass lens indicator that rests as a clean neutral pill and swells into a
 *    magnifying refractive droplet with chromatic edge dispersion and velocity squash/stretch while
 *    dragging or animating between tabs.
 */
@Composable
fun LiquidGlassBottomTabs(
    selectedIndex: Int,
    tabsCount: Int,
    onTabSelected: (Int) -> Unit,
    backdrop: Backdrop,
    containerColor: Color,
    accentColor: Color,
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val reducedMotion =
        LocalEinkMode.current || MaterialTheme.motionScheme.defaultSpatialSpec<Float>() is SnapSpec<*>
    val tabsBackdrop = rememberLayerBackdrop()
    val hasSelection = selectedIndex in 0 until tabsCount
    val selectTab by rememberUpdatedState(onTabSelected)

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        val density = LocalDensity.current
        val barPaddingPx = with(density) { BarInnerPadding.toPx() }
        val tabWidth = ((constraints.maxWidth.toFloat() - barPaddingPx * 2f) / tabsCount).coerceAtLeast(1f)
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                if (reducedMotion || constraints.maxWidth <= 0) {
                    0f
                } else {
                    val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                    with(density) {
                        4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                    }
                }
            }
        }

        var currentIndex by remember { mutableIntStateOf(selectedIndex.coerceIn(0, tabsCount - 1)) }
        val drag = remember(animationScope, tabsCount, reducedMotion) {
            LiquidTabDragAnimation(
                animationScope = animationScope,
                initialValue = selectedIndex.coerceIn(0, tabsCount - 1).toFloat(),
                valueRange = 0f..(tabsCount - 1).coerceAtLeast(1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = if (reducedMotion) 1f else 68f / 50f,
                reducedMotion = reducedMotion,
                onTap = { selectTab(currentIndex) },
                onDragStopped = { commit ->
                    val targetIndex = if (commit) targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1) else currentIndex
                    if (targetIndex != currentIndex) {
                        currentIndex = targetIndex
                        selectTab(targetIndex)
                    }
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                },
                onDrag = { _, dragAmount, uptimeMillis ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat()),
                        uptimeMillis,
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                },
            )
        }

        LaunchedEffect(selectedIndex) {
            if (selectedIndex in 0 until tabsCount && selectedIndex != currentIndex) {
                currentIndex = selectedIndex
                drag.animateToValue(selectedIndex.toFloat())
            }
        }

        val interactiveHighlight = remember(animationScope, isLtr, tabWidth) {
            LiquidInteractiveHighlight(
                animationScope = animationScope,
                enabled = !reducedMotion,
                position = { size, _ ->
                    Offset(
                        if (isLtr) {
                            (drag.value + 0.5f) * tabWidth + panelOffset
                        } else {
                            size.width - (drag.value + 0.5f) * tabWidth + panelOffset
                        },
                        size.height / 2f,
                    )
                },
            )
        }

        // Layer 1: Outer liquid-glass capsule and interactive tabs.
        Row(
            modifier = Modifier
                .graphicsLayer {
                    translationX = panelOffset
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { FloatingCapsuleShape },
                    effects = {
                        vibrancy()
                        blur(8.dp.toPx())
                        if (isRuntimeShaderSupported()) {
                            lens(
                                refractionHeight = 24.dp.toPx(),
                                refractionAmount = 24.dp.toPx(),
                            )
                        }
                    },
                    highlight = { glassSurfaceHighlight(darkTheme, GlassSurfaceStyle.Navigation) },
                    shadow = { BackdropShadow.Default },
                    layerBlock = {
                        val progress = drag.pressProgress
                        val scale = lerp(1f, 1f + 14.dp.toPx() / size.width.coerceAtLeast(1f), progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .then(interactiveHighlight.modifier)
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(BarInnerPadding)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )

        // Layer 2: Hidden accent-tinted capture of the bar so the floating droplet magnifies and
        // refracts the icons underneath it in the active theme's accent colour.
        if (hasSelection) {
            CompositionLocalProvider(
                LocalLiquidGlassTabScale provides {
                    lerp(1f, 1.18f, drag.pressProgress)
                },
            ) {
                Row(
                    modifier = Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .layerBackdrop(tabsBackdrop)
                        .graphicsLayer {
                            translationX = panelOffset
                        }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { FloatingCapsuleShape },
                            effects = {
                                val progress = drag.pressProgress
                                vibrancy()
                                blur(8.dp.toPx())
                                if (isRuntimeShaderSupported()) {
                                    lens(
                                        refractionHeight = 24.dp.toPx() * progress,
                                        refractionAmount = 24.dp.toPx() * progress,
                                    )
                                }
                            },
                            highlight = {
                                glassSurfaceHighlight(
                                    darkTheme = darkTheme,
                                    style = GlassSurfaceStyle.Navigation,
                                    alphaScale = drag.pressProgress,
                                )
                            },
                            shadow = null,
                            onDrawSurface = { drawRect(containerColor) },
                        )
                        .then(interactiveHighlight.modifier)
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(BarInnerPadding)
                        .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }
        }

        // Layer 3: Draggable liquid-glass indicator lens.
        if (hasSelection) {
            val dropletBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)
            Box(
                modifier = Modifier
                    .padding(BarInnerPadding)
                    .graphicsLayer {
                        translationX =
                            if (isLtr) {
                                drag.value * tabWidth + panelOffset
                            } else {
                                (tabsCount - 1 - drag.value) * tabWidth + panelOffset
                            }
                    }
                    .then(interactiveHighlight.gestureModifier)
                    .then(drag.modifier)
                    .drawBackdrop(
                        backdrop = dropletBackdrop,
                        shape = { FloatingCapsuleShape },
                        effects = {
                            val progress = drag.pressProgress
                            if (isRuntimeShaderSupported() && progress > 0.001f) {
                                lens(
                                    refractionHeight = 10.dp.toPx() * progress,
                                    refractionAmount = 14.dp.toPx() * progress,
                                    chromaticAberration = true,
                                )
                            }
                        },
                        highlight = {
                            glassSurfaceHighlight(
                                darkTheme = darkTheme,
                                style = GlassSurfaceStyle.Navigation,
                                alphaScale = drag.pressProgress * (if (darkTheme) 1.35f else 1f),
                            )
                        },
                        shadow = {
                            BackdropShadow(alpha = drag.pressProgress)
                        },
                        innerShadow = {
                            InnerShadow(
                                radius = 8.dp * drag.pressProgress,
                                alpha = drag.pressProgress,
                            )
                        },
                        layerBlock = {
                            scaleX = drag.scaleX
                            scaleY = drag.scaleY
                            val velocity = drag.velocity / 10f
                            scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                        },
                        onDrawSurface = {
                            val progress = drag.pressProgress
                            val restColor =
                                if (darkTheme) {
                                    Color.White.copy(alpha = 0.07f)
                                } else {
                                    Color.Black.copy(alpha = 0.08f)
                                }
                            drawRect(restColor, alpha = 1f - progress)
                            drawRect(accentColor.copy(alpha = 0.05f * (1f - progress)))
                            drawRect(Color.Black.copy(alpha = 0.03f * progress))
                        },
                    )
                    .fillMaxHeight()
                    .fillMaxWidth(1f / tabsCount),
            )
        }
    }
}

private val BarInnerPadding = 4.dp

/**
 * Kyant `LiquidButton`-style tactile liquid-glass press feedback: subtly swells and stretches the
 * floating glass control toward the user's finger (`tanh` damped micro-offset) while casting a
 * clipped radial specular glow across [shape]. Automatically disables itself outside live glass,
 * in 墨水屏模式, and when reduced motion is active.
 */
@Composable
fun Modifier.liquidGlassInteractive(
    shape: Shape,
    enabled: Boolean = true,
): Modifier {
    val layers = LocalPlazaLayers.current
    val reducedMotion =
        LocalEinkMode.current || MaterialTheme.motionScheme.defaultSpatialSpec<Float>() is SnapSpec<*>
    if (!enabled || !layers.backdropGlass || reducedMotion) return this

    val animationScope = rememberCoroutineScope()
    val highlight = remember(animationScope) {
        LiquidInteractiveHighlight(animationScope = animationScope, enabled = true)
    }
    return this
        .graphicsLayer {
            val width = size.width.coerceAtLeast(1f)
            val height = size.height.coerceAtLeast(1f)
            val progress = highlight.pressProgress
            if (progress <= 0.0001f) return@graphicsLayer

            val pressSwell = 4.dp.toPx() / height
            val scale = lerp(1f, 1f + pressSwell, progress)
            val maxOffset = size.minDimension.coerceAtLeast(1f)
            val offset = highlight.offset
            translationX = maxOffset * tanh(0.05f * offset.x / maxOffset)
            translationY = maxOffset * tanh(0.05f * offset.y / maxOffset)

            val angle = atan2(offset.y, offset.x)
            val maxDim = size.maxDimension.coerceAtLeast(1f)
            scaleX =
                scale +
                pressSwell * abs(cos(angle) * offset.x / maxDim) * (width / height).fastCoerceAtMost(1f)
            scaleY =
                scale +
                pressSwell * abs(sin(angle) * offset.y / maxDim) * (height / width).fastCoerceAtMost(1f)
        }
        .drawWithContent {
            drawContent()
            val progress = highlight.pressProgress
            if (progress > 0.0001f) {
                val clip = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithContent)) }
                clipPath(clip) {
                    highlight.drawGlow(this)
                }
            }
        }
        .then(highlight.gestureModifier)
}

private class LiquidTabDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    private val reducedMotion: Boolean,
    val onTap: () -> Unit,
    val onDragStopped: LiquidTabDragAnimation.(commit: Boolean) -> Unit,
    val onDrag: LiquidTabDragAnimation.(size: IntSize, dragAmount: Offset, uptimeMillis: Long) -> Unit,
) {
    private val valueAnimationSpec = spring(1f, 1000f, visibilityThreshold)
    private val velocityAnimationSpec = spring(0.5f, 300f, visibilityThreshold * 10f)
    private val pressProgressAnimationSpec = spring(1f, 1000f, 0.001f)
    private val scaleXAnimationSpec = spring(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec = spring(0.7f, 250f, 0.001f)

    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)

    private val mutatorMutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()
    private var lastUptimeMillis = 0L

    val value: Float get() = valueAnimation.value
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        var firstPointer: PointerId? = null
        var movement = Offset.Zero
        var moved = false
        inspectLiquidDragGestures(
            onDragStart = { down ->
                lastUptimeMillis = down.uptimeMillis
                firstPointer = down.id
                movement = Offset.Zero
                moved = false
                press()
            },
            onDragEnd = { up ->
                onDragStopped(moved)
                // The lens is hit before the tab underneath, so it also handles the reselect tap.
                if (!moved && up.id == firstPointer && !up.isOutOfBounds(size, extendedTouchPadding)) onTap()
                release()
            },
            onDragCancel = {
                onDragStopped(false)
                release()
            },
        ) { change, dragAmount ->
            movement += dragAmount
            if (movement.getDistance() > viewConfiguration.touchSlop) moved = true
            onDrag(size, dragAmount, change.uptimeMillis)
        }
    }

    fun press() {
        velocityTracker.resetTracking()
        if (reducedMotion) return
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    fun release() {
        if (reducedMotion) return
        animationScope.launch {
            withFrameNanos { }
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .filter { abs(it - valueAnimation.targetValue) < threshold }
                    .first()
            }
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
        }
    }

    fun updateValue(newValue: Float, uptimeMillis: Long) {
        val target = newValue.coerceIn(valueRange)
        lastUptimeMillis = uptimeMillis
        animationScope.launch {
            if (reducedMotion) {
                valueAnimation.snapTo(target)
            } else {
                launch { valueAnimation.animateTo(target, valueAnimationSpec) { updateVelocity(lastUptimeMillis) } }
            }
        }
    }

    fun animateToValue(newValue: Float) {
        val target = newValue.coerceIn(valueRange)
        animationScope.launch {
            mutatorMutex.mutate {
                if (reducedMotion) {
                    valueAnimation.snapTo(target)
                    velocityAnimation.snapTo(0f)
                    return@mutate
                }
                press()
                launch { valueAnimation.animateTo(target, valueAnimationSpec) }
                if (velocity != 0f) {
                    launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
                }
                release()
            }
        }
    }

    private fun updateVelocity(uptimeMillis: Long) {
        velocityTracker.addPosition(uptimeMillis, Offset(value, 0f))
        val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1f)
        val targetVelocity = velocityTracker.calculateVelocity().x / span
        animationScope.launch { velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec) }
    }
}

private class LiquidInteractiveHighlight(
    private val animationScope: CoroutineScope,
    private val enabled: Boolean,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
) {
    private val pressProgressAnimationSpec = spring(0.5f, 300f, 0.001f)
    private val positionAnimationSpec = spring(0.5f, 300f, Offset.VisibilityThreshold)

    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private var startPosition = Offset.Zero
    val pressProgress: Float get() = pressProgressAnimation.value
    val offset: Offset get() = positionAnimation.value - startPosition

    private val shader =
        if (isRuntimeShaderSupported()) {
            RuntimeShader(
                """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}
                """.trimIndent(),
            )
        } else {
            null
        }

    fun drawGlow(scope: androidx.compose.ui.graphics.drawscope.DrawScope) {
        val progress = pressProgressAnimation.value
        if (!enabled || progress <= 0f) return
        with(scope) {
            if (shader != null) {
                drawRect(Color.White.copy(alpha = 0.08f * progress), blendMode = BlendMode.Plus)
                val resolved = position(size, positionAnimation.value)
                shader.setFloatUniform("size", size.width, size.height)
                shader.setColorUniform("color", Color.White.copy(alpha = 0.15f * progress))
                shader.setFloatUniform("radius", size.minDimension * 1.5f)
                shader.setFloatUniform(
                    "position",
                    resolved.x.fastCoerceIn(0f, size.width),
                    resolved.y.fastCoerceIn(0f, size.height),
                )
                drawRect(ShaderBrush(shader.asComposeShader()), blendMode = BlendMode.Plus)
            } else {
                drawRect(Color.White.copy(alpha = 0.20f * progress), blendMode = BlendMode.Plus)
            }
        }
    }

    val modifier: Modifier =
        Modifier.drawWithContent {
            drawGlow(this)
            drawContent()
        }

    val gestureModifier: Modifier =
        if (!enabled) {
            Modifier
        } else {
            Modifier.pointerInput(animationScope) {
                inspectLiquidDragGestures(
                    onDragStart = { down ->
                        startPosition = down.position
                        animationScope.launch {
                            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                            launch { positionAnimation.snapTo(startPosition) }
                        }
                    },
                    onDragEnd = {
                        animationScope.launch {
                            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                            launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                        }
                    },
                    onDragCancel = {
                        animationScope.launch {
                            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                            launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                        }
                    },
                ) { change, _ ->
                    animationScope.launch { positionAnimation.snapTo(change.position) }
                }
            }
        }
}

private suspend fun PointerInputScope.inspectLiquidDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: AwaitPointerEventScope.(change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        val initialDown = awaitFirstDown(false, PointerEventPass.Initial)
        val down = awaitFirstDown(false)
        onDragStart(down)
        onDrag(initialDown, Offset.Zero)
        val upEvent =
            trackLiquidDrag(
                pointerId = initialDown.id,
                onDrag = { onDrag(it, it.positionChange()) },
            )
        if (upEvent == null) {
            onDragCancel()
        } else {
            onDragEnd(upEvent)
        }
    }
}

private suspend inline fun AwaitPointerEventScope.trackLiquidDrag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit,
): PointerInputChange? {
    val isPointerUp = currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true
    if (isPointerUp) return null
    var pointer = pointerId
    while (true) {
        val change = awaitLiquidDragOrUp(pointer) ?: return null
        if (change.isConsumed) return null
        if (change.changedToUpIgnoreConsumed()) return change
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitLiquidDragOrUp(
    pointerId: PointerId,
): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) {
                return dragEvent
            } else {
                pointer = otherDown.id
            }
        } else if (dragEvent.previousPosition != dragEvent.position) {
            return dragEvent
        }
    }
}
