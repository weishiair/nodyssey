package io.github.nodyssey

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.github.nodyssey.ui.common.THREAD_ORIGIN_FEED
import io.github.nodyssey.ui.common.sharedThreadAvatar
import io.github.nodyssey.ui.common.sharedThreadSurface
import io.github.nodyssey.ui.common.sharedThreadTitle
import io.github.nodyssey.ui.navigation.TopLevelDestination
import org.junit.Assert.assertEquals
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
    private var detailClicks = 0
    private lateinit var sharedScope: SharedTransitionScope

    private fun show(
        entryFade: Boolean = false,
        shared: Boolean = false,
        initiallyPredictive: Boolean = false,
        settleEntrance: Boolean = true,
    ) {
        predictive.value = initiallyPredictive
        val dispatcher = NavigationEventDispatcher().apply { addInput(input) }
        val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = dispatcher
        }
        val provider = stackScopedEntryProvider(
            destination = TopLevelDestination.HOME,
            backStack = stack,
            predictiveBack = { predictive.value },
            provider = { key ->
                NavEntry(
                    key = key,
                    metadata = if (shared && key is PostDetailKey) {
                        stationaryPageEntrance()
                    } else if (entryFade && key is PostDetailKey) {
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
                                    if (shared && index == 0) {
                                        Box(Modifier.size(20.dp).sharedThreadAvatar(1L, THREAD_ORIGIN_FEED))
                                    }
                                }
                            }
                        }
                    } else {
                        // An outgoing page still in AnimatedContent intercepts the next drag.
                        Box(
                            Modifier
                                .fillMaxSize()
                                .then(if (shared) Modifier.sharedThreadSurface(1L, THREAD_ORIGIN_FEED) else Modifier)
                                .clickable { detailClicks++ },
                        ) {
                            Text("Thread", modifier = if (shared) Modifier.sharedThreadTitle(1L, THREAD_ORIGIN_FEED) else Modifier)
                            if (shared) Box(Modifier.size(40.dp).sharedThreadAvatar(1L, THREAD_ORIGIN_FEED))
                        }
                    }
                }
            },
        )
        compose.setContent {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                MaterialTheme {
                    SharedTransitionLayout(Modifier.fillMaxSize().testTag("screen")) {
                        sharedScope = this
                        val entries = rememberDecoratedNavEntries(
                            backStack = stack,
                            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                            entryProvider = provider,
                        )
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
        if (!settleEntrance) compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            stack.add(PostDetailKey(1L, preview = if (shared) ThreadPreview("Thread", "Author", origin = THREAD_ORIGIN_FEED) else null))
        }
        if (settleEntrance) {
            compose.waitForIdle()
        } else {
            advanceSceneFrames()
        }
        compose.mainClock.autoAdvance = false
    }

    private fun assertImmediateScroll() {
        // Leave animation time frozen: waiting for idle with an advancing clock hides this bug.
        Snapshot.sendApplyNotifications()
        // NavDisplay commits the new scene and its lifecycle over several composition frames.
        // Four frames still leave the old slide/fade running, rather than waiting it away.
        advanceSceneFrames()
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

    private fun advanceSceneFrames() {
        repeat(4) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
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

    @Test
    fun `shared elements do not delay the first drag after a pop with previews disabled`() {
        show(entryFade = true, shared = true)
        compose.runOnIdle { stack.removeLastOrNull() }
        assertImmediateScroll()
    }

    @Test
    fun `a quick back interrupts shared entrance without blocking the returned list`() {
        show(entryFade = true, shared = true, settleEntrance = false)
        compose.runOnIdle {
            // Exercise a running shared animation, rather than accidentally waiting the bug away.
            assertTrue(sharedScope.isTransitionActive)
            input.completeGesture()
            assertEquals(listOf(PostListKey), stack.toList())
        }
        assertImmediateScroll()
    }

    @Test
    fun `cancelling shared predictive back keeps the thread and a second gesture pops once`() {
        show(entryFade = true, shared = true, initiallyPredictive = true)
        compose.runOnIdle { input.startAndProgress(NavigationEvent.EDGE_RIGHT) }
        advanceSceneFrames()
        compose.runOnIdle { input.cancel() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(2, stack.size)
            input.completeGesture()
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(PostListKey), stack.toList()) }
        compose.mainClock.autoAdvance = false
        assertImmediateScroll()
    }

    @Test
    fun `cancelling back during shared entrance leaves the thread interactive`() {
        show(shared = true, initiallyPredictive = true, settleEntrance = false)
        compose.runOnIdle { input.startAndProgress(NavigationEvent.EDGE_LEFT) }
        advanceSceneFrames()
        compose.runOnIdle { input.cancel() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("screen").performTouchInput {
            click(Offset(width * 0.9f, height * 0.7f))
        }
        compose.runOnIdle {
            assertEquals(2, stack.size)
            assertEquals("The restored thread must receive the first tap after cancellation", 1, detailClicks)
        }
    }

    @Test
    fun `a completed shared return releases input to the original list`() {
        show(shared = true, initiallyPredictive = true)
        compose.runOnIdle { input.completeGesture() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(PostListKey), stack.toList()) }
        compose.mainClock.autoAdvance = false
        assertImmediateScroll()
    }

    @Test
    fun `losing the source row during a cancelled return leaves the thread interactive`() {
        show(shared = true, initiallyPredictive = true)
        compose.runOnIdle { input.startAndProgress(NavigationEvent.EDGE_LEFT) }
        advanceSceneFrames()
        compose.runOnIdle { list.requestScrollToItem(20) }
        advanceSceneFrames()
        compose.runOnIdle { input.cancel() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("screen").performTouchInput {
            click(Offset(width * 0.9f, height * 0.7f))
        }
        compose.runOnIdle {
            assertEquals(2, stack.size)
            assertEquals("Cancelling after the row disappears must restore the thread's input", 1, detailClicks)
        }
    }

    private class BackInput : NavigationEventInput() {
        fun completeGesture() {
            startAndProgress(NavigationEvent.EDGE_LEFT)
            dispatchOnBackCompleted()
        }

        fun startAndProgress(edge: Int) {
            dispatchOnBackStarted(NavigationEvent(progress = 0f, swipeEdge = edge))
            dispatchOnBackProgressed(NavigationEvent(progress = 0.6f, swipeEdge = edge))
        }

        fun cancel() = dispatchOnBackCancelled()
    }
}
