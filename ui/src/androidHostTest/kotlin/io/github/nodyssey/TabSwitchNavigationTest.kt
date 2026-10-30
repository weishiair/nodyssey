package io.github.nodyssey

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.github.nodyssey.ui.navigation.TopLevelDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TabSwitchNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private val selected = mutableStateOf(TopLevelDestination.HOME)
    private val stacks = TopLevelDestination.entries.associateWith { mutableStateListOf<NavKey>(it.key) }
    private val lists = mutableMapOf<TopLevelDestination, LazyListState>()
    private val input = BackInput()
    private val providers = TopLevelDestination.entries.associateWith { destination ->
        stackScopedEntryProvider(
            destination = destination,
            backStack = stacks.getValue(destination),
            provider = { key ->
                NavEntry(
                    key,
                    // An entry's own transition must not reinstate a fade between peer tabs.
                    metadata = NavDisplay.transitionSpec { fadeIn() togetherWith fadeOut() } +
                        NavDisplay.popTransitionSpec { fadeIn() togetherWith fadeOut() },
                ) {
                    val list = rememberLazyListState()
                    lists[destination] = list
                    LazyColumn(Modifier.fillMaxSize(), state = list) {
                        items(100) { index ->
                            Box(Modifier.fillMaxWidth().height(48.dp)) {
                                Text("${destination.name} $index")
                            }
                        }
                    }
                }
            },
        )
    }

    @Composable
    private fun entries(destination: TopLevelDestination): List<NavEntry<NavKey>> =
        rememberDecoratedNavEntries(
            backStack = stacks.getValue(destination),
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
            entryProvider = providers.getValue(destination),
        )

    private fun show(initial: TopLevelDestination = TopLevelDestination.HOME) {
        selected.value = initial
        val dispatcher = NavigationEventDispatcher().apply { addInput(input) }
        val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = dispatcher
        }
        compose.setContent {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                MaterialTheme {
                    val home = entries(TopLevelDestination.HOME)
                    val notifications = entries(TopLevelDestination.NOTIFICATIONS)
                    val profile = entries(TopLevelDestination.PROFILE)
                    val active = when (selected.value) {
                        TopLevelDestination.HOME -> home
                        TopLevelDestination.NOTIFICATIONS -> home + notifications
                        TopLevelDestination.PROFILE -> home + profile
                    }
                    SharedTransitionLayout(Modifier.fillMaxSize().testTag("screen")) {
                        NodysseyNavDisplay(
                            entries = active,
                            predictiveBack = true,
                            eink = false,
                            sharedTransitionScope = this,
                            onBack = { selected.value = TopLevelDestination.HOME },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun advanceSceneFrames() {
        repeat(4) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    private fun select(destination: TopLevelDestination) {
        compose.runOnIdle { selected.value = destination }
        advanceSceneFrames()
    }

    @Test
    fun `the first drag after every tab switch scrolls the selected list immediately`() {
        show()
        listOf(
            TopLevelDestination.NOTIFICATIONS,
            TopLevelDestination.PROFILE,
            TopLevelDestination.HOME,
            TopLevelDestination.PROFILE,
            TopLevelDestination.NOTIFICATIONS,
            TopLevelDestination.HOME,
        ).forEach { destination ->
            select(destination)
            val list = lists.getValue(destination)
            val beforeIndex = list.firstVisibleItemIndex
            val beforeOffset = list.firstVisibleItemScrollOffset
            // Keep animation time frozen: waiting out a fade would hide input reaching the old tab.
            compose.onNodeWithTag("screen").performTouchInput {
                swipe(Offset(width / 2f, height * 0.8f), Offset(width / 2f, height * 0.2f), durationMillis = 200)
            }
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle {
                assertTrue(
                    "The outgoing tab intercepted the first drag into $destination",
                    list.firstVisibleItemIndex > beforeIndex ||
                        (list.firstVisibleItemIndex == beforeIndex && list.firstVisibleItemScrollOffset > beforeOffset),
                )
            }
        }
    }

    @Test
    fun `switching tabs restores each list position instead of recreating its navigation state`() {
        show()
        compose.runOnIdle { lists.getValue(TopLevelDestination.HOME).requestScrollToItem(12, 7) }
        advanceSceneFrames()
        select(TopLevelDestination.NOTIFICATIONS)
        compose.runOnIdle { lists.getValue(TopLevelDestination.NOTIFICATIONS).requestScrollToItem(8, 3) }
        advanceSceneFrames()
        select(TopLevelDestination.HOME)
        compose.runOnIdle {
            val home = lists.getValue(TopLevelDestination.HOME)
            assertEquals(12, home.firstVisibleItemIndex)
            assertEquals(7, home.firstVisibleItemScrollOffset)
        }
        select(TopLevelDestination.NOTIFICATIONS)
        compose.runOnIdle {
            val notifications = lists.getValue(TopLevelDestination.NOTIFICATIONS)
            assertEquals(8, notifications.firstVisibleItemIndex)
            assertEquals(3, notifications.firstVisibleItemScrollOffset)
        }
    }

    @Test
    fun `predictive back from a tab can be cancelled before another gesture returns home`() {
        show(TopLevelDestination.NOTIFICATIONS)
        compose.runOnIdle { input.startAndProgress() }
        advanceSceneFrames()
        compose.runOnIdle { input.cancel() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(TopLevelDestination.NOTIFICATIONS, selected.value)
            input.startAndProgress()
            input.finish()
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(TopLevelDestination.HOME, selected.value) }
    }

    private class BackInput : NavigationEventInput() {
        fun startAndProgress() {
            dispatchOnBackStarted(NavigationEvent(progress = 0f, swipeEdge = NavigationEvent.EDGE_LEFT))
            dispatchOnBackProgressed(NavigationEvent(progress = 0.6f, swipeEdge = NavigationEvent.EDGE_LEFT))
        }

        fun cancel() = dispatchOnBackCancelled()

        fun finish() = dispatchOnBackCompleted()
    }
}
