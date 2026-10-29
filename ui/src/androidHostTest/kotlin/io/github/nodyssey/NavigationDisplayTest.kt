package io.github.nodyssey

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.github.nodyssey.ui.common.THREAD_ORIGIN_FEED
import io.github.nodyssey.ui.common.sharedThreadTitle
import io.github.nodyssey.ui.navigation.TopLevelDestination
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NavigationDisplayTest {
    @get:Rule
    val compose = createComposeRule()

    private val stack = mutableStateListOf<NavKey>(PostListKey)
    private val predictive = mutableStateOf(false)
    private val list = LazyListState()
    private val input = BackInput()

    private fun show(entryFade: Boolean = false, shared: Boolean = false, initiallyPredictive: Boolean = false) {
        predictive.value = initiallyPredictive
        val dispatcher = NavigationEventDispatcher().apply { addInput(input) }
        val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = dispatcher
        }
        val provider = stackScopedEntryProvider(
            destination = TopLevelDestination.HOME,
            predictiveBack = { predictive.value },
            provider = { key ->
                NavEntry(
                    key = key,
                    metadata = if (entryFade && key is PostDetailKey) {
                        NavDisplay.popTransitionSpec { fadeIn() togetherWith fadeOut() }
                    } else {
                        emptyMap()
                    },
                ) {
                    if (key == PostListKey) {
                        LazyColumn(Modifier.fillMaxSize(), state = list) {
                            items(100) { index ->
                                Box(Modifier.fillMaxWidth().height(48.dp)) {
                                    Text(
                                        "Post $index",
                                        modifier = if (shared && index == 0) Modifier.sharedThreadTitle(1L, THREAD_ORIGIN_FEED) else Modifier,
                                    )
                                }
                            }
                        }
                    } else {
                        // An outgoing page still in AnimatedContent intercepts the next drag.
                        Box(Modifier.fillMaxSize().clickable {}) {
                            Text("Thread", modifier = if (shared) Modifier.sharedThreadTitle(1L, THREAD_ORIGIN_FEED) else Modifier)
                        }
                    }
                }
            },
        )
        compose.setContent {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                MaterialTheme {
                    SharedTransitionLayout(Modifier.fillMaxSize().testTag("screen")) {
                        val entries = remember(stack.toList()) { stack.map(provider) }
                        NodysseyNavDisplay(
                            entries = entries,
                            predictiveBack = predictive.value,
                            eink = false,
                            onBack = { stack.removeLastOrNull() },
                            sharedTransitionScope = this.takeIf { shared },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { stack.add(PostDetailKey(1L)) }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun assertImmediateScroll() {
        // Leave animation time frozen: waiting for idle with an advancing clock hides this bug.
        Snapshot.sendApplyNotifications()
        // NavDisplay commits the new scene and its lifecycle over several composition frames.
        // Four frames still leave the old slide/fade running, rather than waiting it away.
        repeat(4) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.onNodeWithTag("screen").performTouchInput {
            swipe(Offset(width / 2f, height * 0.8f), Offset(width / 2f, height * 0.2f), durationMillis = 200)
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            assertTrue(
                "The first drag after returning must scroll the list without waiting for the old page's animation",
                list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0,
            )
        }
    }

    @Test
    fun `disabled predictive back lets the first drag scroll after gesture completion`() {
        show()
        compose.runOnIdle { input.completeGesture() }
        assertImmediateScroll()
    }

    @Test
    fun `entry fade cannot keep blocking input after a non gesture pop`() {
        show(entryFade = true)
        compose.runOnIdle { stack.removeLastOrNull() }
        assertImmediateScroll()
    }

    @Test
    fun `turning previews off also releases shared element input without rebuilding the entry`() {
        show(entryFade = true, shared = true, initiallyPredictive = true)
        compose.runOnIdle { predictive.value = false }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { input.completeGesture() }
        assertImmediateScroll()
    }

    private class BackInput : NavigationEventInput() {
        fun completeGesture() {
            dispatchOnBackStarted(NavigationEvent(progress = 0f, swipeEdge = NavigationEvent.EDGE_LEFT))
            dispatchOnBackProgressed(NavigationEvent(progress = 0.6f, swipeEdge = NavigationEvent.EDGE_LEFT))
            dispatchOnBackCompleted()
        }
    }
}
