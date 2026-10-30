package io.github.nodyssey

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventTransitionState
import io.github.nodyssey.ui.common.LocalThreadBackGestureInProgress
import io.github.nodyssey.ui.common.LocalThreadMatches
import io.github.nodyssey.ui.common.LocalThreadSharedReturnEnabled
import io.github.nodyssey.ui.common.LocalThreadTransition
import io.github.nodyssey.ui.common.ThreadTransitionMatches
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
    // Material sheets use fastEffectsSpec for hiding: quick, with no overshoot back into view.
    val returnSpec = MaterialTheme.motionScheme.fastEffectsSpec<IntOffset>()
    val matches = remember { ThreadTransitionMatches() }
    CompositionLocalProvider(
        LocalThreadTransition provides sharedTransitionScope.takeUnless { !sharedThreadTransitions || eink },
        LocalThreadMatches provides matches,
        LocalThreadSharedReturnEnabled provides predictiveBack,
    ) {
        val sceneState = rememberSceneState(
            entries = entries,
            sceneStrategies = sceneStrategies,
            sharedTransitionScope = sharedTransitionScope,
            onBack = onBack,
        )
        val backState = rememberNavigationBackState(sceneState, predictiveBack, onBack)
        CompositionLocalProvider(
            LocalThreadBackGestureInProgress provides (backState.transitionState is NavigationEventTransitionState.InProgress),
        ) {
            NavDisplay(
                // Page slides stay inside the content pane; shared elements use the outer overlay.
                modifier = Modifier.clipToBounds(),
                sceneState = sceneState,
                navigationEventState = backState,
                transitionSpec = {
                    if (eink) SNAP_CONTENT_TRANSFORM else slidePage(SlideDirection.Start, spatialSpec)
                },
                popTransitionSpec = {
                    if (eink || !predictiveBack) {
                        SNAP_CONTENT_TRANSFORM
                    } else {
                        returnPage(SlideDirection.End, returnSpec) {
                            hasSharedThreadReturn(matches)
                        }
                    }
                },
                predictivePopTransitionSpec = { edge ->
                    val direction = when (edge) {
                        NavigationEvent.EDGE_LEFT -> SlideDirection.Right
                        NavigationEvent.EDGE_RIGHT -> SlideDirection.Left
                        else -> SlideDirection.End
                    }
                    if (eink) {
                        SNAP_CONTENT_TRANSFORM
                    } else {
                        returnPage(direction, predictiveBackAnimationSpec()) {
                            hasSharedThreadReturn(matches)
                        }
                    }
                },
            )
        }
    }
}

/** Stationary scene backgrounds; shared elements can still animate independently above them. */
internal val SNAP_CONTENT_TRANSFORM = EnterTransition.None togetherWith ExitTransition.None

// NavDisplay seeks animation time during a gesture. MotionScheme's springs move most of the page
// before half that time, so it outruns the finger. Material has no linear gesture spec; keep this
// timeline linear until NavDisplay supports mapping gesture progress directly to page displacement.
internal fun <T> predictiveBackAnimationSpec(): FiniteAnimationSpec<T> = tween(durationMillis = 180, easing = LinearEasing)

/** Keep page content opaque while the container supplies size-aware, RTL-aware navigation. */
private fun <T : Any> AnimatedContentTransitionScope<Scene<T>>.slidePage(
    direction: SlideDirection,
    animationSpec: FiniteAnimationSpec<IntOffset>,
): ContentTransform =
    slideIntoContainer(direction, animationSpec) togetherWith slideOutOfContainer(direction, animationSpec)

/** Reveal the stationary destination behind one opaque departing page. */
private fun <T : Any> AnimatedContentTransitionScope<Scene<T>>.returnPage(
    direction: SlideDirection,
    animationSpec: FiniteAnimationSpec<IntOffset>,
    sharedReturn: () -> Boolean,
): ContentTransform = EnterTransition.None togetherWith slideOutOfContainer(direction, animationSpec) { distance ->
    // A LazyColumn finds shared matches during measurement, after AnimatedContent remembers its
    // exit spec. The official offset lambda runs late enough to avoid sliding a matched header's
    // page underneath it. An unmatched/offscreen row keeps the full exit distance.
    if (sharedReturn()) 0 else distance
}

internal data object ThreadReturnKey : NavMetadataKey<PostDetailKey>

private fun <T : Any> AnimatedContentTransitionScope<Scene<T>>.hasSharedThreadReturn(matches: ThreadTransitionMatches): Boolean {
    val thread = initialState.entries.lastOrNull()?.metadata?.get(ThreadReturnKey) ?: return false
    val origin = thread.preview?.origin?.takeIf { it.isNotEmpty() } ?: return false
    return matches.hasMatch(thread.postId, origin)
}
