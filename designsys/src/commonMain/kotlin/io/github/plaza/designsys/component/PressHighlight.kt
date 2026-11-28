package io.github.plaza.designsys.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.input.pointer.pointerInput
import io.github.plaza.designsys.theme.LocalPlazaDarkTheme
import io.github.plaza.designsys.theme.nativeControls

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PressHighlight(
    shape: Shape,
    enabled: Boolean = true,
    native: Boolean = nativeControls,
    content: @Composable (MutableInteractionSource?, Modifier) -> Unit,
) {
    if (!native || !enabled) {
        content(null, Modifier)
        return
    }

    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    var touching by remember { mutableStateOf(false) }
    // Clickable can defer PressInteraction in scroll containers. Observe touch-down without
    // consuming it until Foundation exposes immediate press feedback on the clickable itself.
    val touch = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            touching = true
            try {
                waitForUpOrCancellation()
            } finally {
                touching = false
            }
        }
    }
    val active = touching || pressed
    val ink = MaterialTheme.colorScheme.onSurface
    val peak = if (LocalPlazaDarkTheme.current) 0.14f else 0.10f
    val fade = remember { Animatable(0f) }
    val release = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    LaunchedEffect(active, peak, release) {
        if (active) fade.snapTo(peak) else fade.animateTo(0f, release)
    }
    val overlay = touch.drawWithContent {
        drawContent()
        val alpha = if (touching || pressed) peak else fade.value
        if (alpha > 0f) drawOutline(shape.createOutline(size, layoutDirection, this), ink, alpha)
    }
    CompositionLocalProvider(LocalRippleConfiguration provides null) { content(source, overlay) }
}
