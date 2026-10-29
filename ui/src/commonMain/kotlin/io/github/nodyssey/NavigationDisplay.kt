package io.github.nodyssey

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import io.github.nodyssey.ui.common.LocalThreadTransition
import io.github.nodyssey.ui.common.rememberNavigationBackState

/** The same navigation and shared-element policy for every tab's display. */
@Composable
internal fun <T : Any> NodysseyNavDisplay(
    entries: List<NavEntry<T>>,
    predictiveBack: Boolean,
    eink: Boolean,
    onBack: () -> Unit,
    sceneStrategies: List<SceneStrategy<T>> = listOf(SinglePaneSceneStrategy()),
    sharedTransitionScope: SharedTransitionScope? = null,
    sharedThreadTransitions: Boolean = true,
) {
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    CompositionLocalProvider(
        LocalThreadTransition provides sharedTransitionScope.takeUnless { !sharedThreadTransitions || eink },
    ) {
        val sceneState = rememberSceneState(
            entries = entries,
            sceneStrategies = sceneStrategies,
            sharedTransitionScope = sharedTransitionScope,
            onBack = onBack,
        )
        val backState = rememberNavigationBackState(sceneState, predictiveBack, onBack)
        NavDisplay(
            sceneState = sceneState,
            navigationEventState = backState,
            transitionSpec = {
                if (eink) SNAP_CONTENT_TRANSFORM else slidePage(SlideDirection.Start, spatialSpec)
            },
            popTransitionSpec = {
                if (eink || !predictiveBack) SNAP_CONTENT_TRANSFORM else slidePage(SlideDirection.End, spatialSpec)
            },
            predictivePopTransitionSpec = { edge ->
                val direction = when (edge) {
                    NavigationEvent.EDGE_LEFT -> SlideDirection.Right
                    NavigationEvent.EDGE_RIGHT -> SlideDirection.Left
                    else -> SlideDirection.End
                }
                if (eink) SNAP_CONTENT_TRANSFORM else slidePage(direction, spatialSpec)
            },
        )
    }
}

/** Stationary scene backgrounds; shared elements can still animate independently above them. */
internal val SNAP_CONTENT_TRANSFORM = EnterTransition.None togetherWith ExitTransition.None

/** Keep page content opaque while the container supplies size-aware, RTL-aware navigation. */
private fun <T : Any> AnimatedContentTransitionScope<Scene<T>>.slidePage(
    direction: SlideDirection,
    animationSpec: FiniteAnimationSpec<IntOffset>,
): ContentTransform =
    slideIntoContainer(direction, animationSpec) togetherWith slideOutOfContainer(direction, animationSpec)
