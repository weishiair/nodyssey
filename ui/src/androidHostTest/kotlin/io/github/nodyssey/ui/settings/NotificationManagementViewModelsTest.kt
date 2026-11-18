package io.github.nodyssey.ui.settings

import io.github.nodyssey.data.MutableClock
import io.github.nodyssey.data.TitleKeywordStore
import io.github.nodyssey.data.TrackedThreadStore
import io.github.nodyssey.data.inMemoryDatabase
import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.nodyssey.data.testSettingsRepository
import io.github.nodyssey.model.TitleKeywordKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NotificationManagementViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = MutableClock()
    private lateinit var database: NodeSeekDatabase
    private lateinit var keywords: TitleKeywordStore
    private lateinit var tracked: TrackedThreadStore

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        database = inMemoryDatabase(dispatcher)
        keywords = TitleKeywordStore(database.titleKeywordDao(), clock)
        tracked = TrackedThreadStore(database.trackedThreadDao(), clock)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun `notification alert counts react to alert keywords and tracked threads without leaking block words`() = runTest(dispatcher) {
        val vm = NotificationSettingsViewModel(
            settings = testSettingsRepository(backgroundScope),
            titleKeywords = keywords,
            trackedThreads = tracked,
        )
        backgroundScope.launch { vm.alertCounts.collect {} }
        advanceUntilIdle()
        assertEquals(AlertCounts(keywords = 0, trackedThreads = 0), vm.alertCounts.value)

        keywords.add(TitleKeywordKind.BLOCK, "muted-word")
        advanceUntilIdle()
        assertEquals(0, vm.alertCounts.value.keywords)

        keywords.add(TitleKeywordKind.ALERT, "vps")
        tracked.track(postId = 42L, title = "Deal thread", commentCount = 5)
        advanceUntilIdle()
        assertEquals(AlertCounts(keywords = 1, trackedThreads = 1), vm.alertCounts.value)

        tracked.untrack(42L)
        advanceUntilIdle()
        assertEquals(AlertCounts(keywords = 1, trackedThreads = 0), vm.alertCounts.value)
    }

    @Test
    fun `title keywords view models keep alert and block isolated across add, duplicate, cap and remove`() = runTest(dispatcher) {
        val alertVm = TitleKeywordsViewModel(TitleKeywordKind.ALERT, keywords)
        val blockVm = TitleKeywordsViewModel(TitleKeywordKind.BLOCK, keywords)
        backgroundScope.launch { alertVm.uiState.collect {} }
        backgroundScope.launch { blockVm.uiState.collect {} }
        advanceUntilIdle()

        // Blank input does nothing.
        alertVm.onInputChange("   ")
        alertVm.add()
        advanceUntilIdle()
        assertEquals(emptyList<String>(), alertVm.uiState.value.keywords)

        // Trimmed + lowercased on save, input clears, duplicate ignored.
        alertVm.onInputChange("  VPS  ")
        alertVm.add()
        advanceUntilIdle()
        assertEquals(listOf("vps"), alertVm.uiState.value.keywords)
        assertEquals("", alertVm.uiState.value.input)
        assertEquals(emptyList<String>(), blockVm.uiState.value.keywords)

        alertVm.onInputChange("vps")
        alertVm.add()
        advanceUntilIdle()
        assertEquals(listOf("vps"), alertVm.uiState.value.keywords)

        // Input respects MAX_KEYWORD_LENGTH.
        alertVm.onInputChange("a".repeat(TitleKeywordStore.MAX_KEYWORD_LENGTH + 10))
        advanceUntilIdle()
        assertEquals(TitleKeywordStore.MAX_KEYWORD_LENGTH, alertVm.uiState.value.input.length)

        // Block word stays separate, and removing alert keyword leaves block intact.
        blockVm.onInputChange("Deal")
        blockVm.add()
        advanceUntilIdle()
        assertEquals(listOf("deal"), blockVm.uiState.value.keywords)

        alertVm.remove("vps")
        advanceUntilIdle()
        assertEquals(emptyList<String>(), alertVm.uiState.value.keywords)
        assertEquals(listOf("deal"), blockVm.uiState.value.keywords)
    }

    @Test
    fun `tracked threads view model removes only target thread and reaches empty list on last untrack`() = runTest(dispatcher) {
        tracked.track(postId = 10L, title = "First", commentCount = 2)
        clock.advanceBy(1_000L)
        tracked.track(postId = 20L, title = "Second", commentCount = null)
        val vm = TrackedThreadsViewModel(tracked)
        backgroundScope.launch { vm.threads.collect {} }
        advanceUntilIdle()

        assertEquals(listOf(20L, 10L), vm.threads.value.map { it.postId })
        assertEquals(TrackedThreadStore.UNKNOWN_COUNT, vm.threads.value.first().lastKnownCount)

        vm.untrack(10L)
        advanceUntilIdle()
        assertEquals(listOf(20L), vm.threads.value.map { it.postId })

        vm.untrack(20L)
        advanceUntilIdle()
        assertTrue(vm.threads.value.isEmpty())
    }
}
