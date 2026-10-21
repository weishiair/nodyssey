package io.github.nodyssey.ui.common

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.NavigationEventTransitionState.Idle
import androidx.navigationevent.NavigationEventTransitionState.InProgress
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.NavigationEventState
import androidx.navigationevent.compose.rememberNavigationEventState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NavigationBackStateTest {
    @get:Rule
    val compose = createComposeRule()

    private val stack = mutableStateListOf("list", "thread")
    private val input = BackInput()
    private lateinit var displayState: NavigationEventState<SceneInfo<String>>
    private var childBacks = 0

    private fun show(predictive: Boolean, withChild: Boolean = false) {
        val dispatcher = NavigationEventDispatcher()
        dispatcher.addInput(input)
        val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = dispatcher
        }
        compose.setContent {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                val sceneState = rememberSceneState(
                    entries = stack.map { key -> NavEntry(key) {} },
                    sceneStrategies = listOf(SinglePaneSceneStrategy()),
                    onBack = { stack.removeLastOrNull() },
                )
                displayState = rememberNavigationBackState(sceneState, predictive) { stack.removeLastOrNull() }
                NavDisplay(sceneState, displayState)
                if (withChild) {
                    NavigationBackHandler(
                        state = rememberNavigationEventState(NavigationEventInfo.None),
                        isBackEnabled = true,
                        onBackCompleted = { childBacks++ },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `disabled preview stays idle until completed and cancellation never pops`() {
        show(predictive = false)
        compose.runOnIdle { input.startAndProgress() }
        compose.runOnIdle {
            assertTrue(displayState.transitionState is Idle)
            assertEquals(listOf("list", "thread"), stack.toList())
            input.cancel()
        }
        compose.runOnIdle { assertEquals(2, stack.size) }
        compose.runOnIdle { input.startAndProgress() }
        compose.runOnIdle { input.finish() }
        compose.runOnIdle { assertEquals(listOf("list"), stack.toList()) }
    }

    @Test
    fun `enabled preview receives gesture progress and can still be cancelled`() {
        show(predictive = true)
        compose.runOnIdle { input.startAndProgress() }
        compose.runOnIdle {
            assertTrue(displayState.transitionState is InProgress)
            input.cancel()
        }
        compose.runOnIdle {
            assertTrue(displayState.transitionState is Idle)
            assertEquals(2, stack.size)
        }
    }

    @Test
    fun `child handler consumes back before the page with previews disabled`() {
        show(predictive = false, withChild = true)
        compose.runOnIdle { input.startAndProgress() }
        compose.runOnIdle { input.finish() }
        compose.runOnIdle {
            assertEquals(1, childBacks)
            assertEquals(2, stack.size)
        }
    }

    private class BackInput : NavigationEventInput() {
        fun startAndProgress() {
            dispatchOnBackStarted(NavigationEvent(progress = 0f, swipeEdge = NavigationEvent.EDGE_LEFT))
            dispatchOnBackProgressed(NavigationEvent(progress = 0.6f, swipeEdge = NavigationEvent.EDGE_LEFT))
        }

        fun finish() = dispatchOnBackCompleted()

        fun cancel() = dispatchOnBackCancelled()
    }
}
