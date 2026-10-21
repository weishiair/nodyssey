package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable
import androidx.navigation3.scene.NavigationBackHandler
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.SceneState
import androidx.navigation3.scene.rememberNavigationEventState
import androidx.navigationevent.compose.NavigationEventState

/** Keeps gesture completion and child handlers active even when the reader opts out of previews. */
@Composable
internal fun <T : Any> rememberNavigationBackState(
    sceneState: SceneState<T>,
    predictiveBack: Boolean,
    onBack: () -> Unit,
): NavigationEventState<SceneInfo<T>> {
    val gestureState = rememberNavigationEventState(sceneState)
    val idleState = rememberNavigationEventState(sceneState)
    NavigationBackHandler(sceneState, gestureState, onBackCompleted = onBack)
    // NavDisplay's animation spec alone cannot opt out: it still seeks to the previous scene.
    // The unregistered state's transition stays idle, so completion becomes an ordinary pop.
    return if (predictiveBack) gestureState else idleState
}
