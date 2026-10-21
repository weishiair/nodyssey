package io.github.nodyssey

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.defaultPopTransitionSpec
import androidx.navigation3.ui.defaultPredictivePopTransitionSpec
import androidx.navigation3.ui.defaultTransitionSpec
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
    CompositionLocalProvider(
        // Shared bounds are child animations of the scene; even a None pop otherwise waits for
        // their spring to finish while the outgoing page keeps receiving pointer events.
        LocalThreadTransition provides sharedTransitionScope.takeUnless { !sharedThreadTransitions || eink || !predictiveBack },
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
            transitionSpec = if (eink) snapTransition() else defaultTransitionSpec(),
            popTransitionSpec = if (eink || !predictiveBack) snapTransition() else defaultPopTransitionSpec(),
            predictivePopTransitionSpec = if (eink) {
                { _ -> SNAP_CONTENT_TRANSFORM }
            } else {
                defaultPredictivePopTransitionSpec()
            },
        )
    }
}

/** A cut removes the outgoing scene instead of retaining it for an invisible animation. */
internal val SNAP_CONTENT_TRANSFORM = EnterTransition.None togetherWith ExitTransition.None

private fun <T : Any> snapTransition(): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform =
    { SNAP_CONTENT_TRANSFORM }
