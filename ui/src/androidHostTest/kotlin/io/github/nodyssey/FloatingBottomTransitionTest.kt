package io.github.nodyssey

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.nodyssey.data.Board
import io.github.nodyssey.data.FeedPost
import io.github.nodyssey.model.PostSummary
import io.github.nodyssey.ui.common.FloatingBottomBarVisibility
import io.github.nodyssey.ui.common.LocalFloatingBottomBarHeight
import io.github.nodyssey.ui.common.THREAD_ORIGIN_FEED
import io.github.nodyssey.ui.navigation.FloatingGlassBarClearance
import io.github.nodyssey.ui.navigation.GlassBottomNavigationBar
import io.github.nodyssey.ui.navigation.TopLevelDestination
import io.github.nodyssey.ui.postlist.HomeFeedStates
import io.github.nodyssey.ui.postlist.PostListScreen
import io.github.nodyssey.ui.postlist.PostListUiState
import io.github.plaza.designsys.theme.LocalLiquidGlassBackdrop
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.liquidGlassBackdropSource
import io.github.plaza.designsys.theme.rememberLiquidGlassBackdrop
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class FloatingBottomTransitionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class DummyBackInput : NavigationEventInput()

    private fun samplePosts(count: Int = 20): List<FeedPost> =
        (1..count).map { idx ->
            FeedPost(
                summary = PostSummary(
                    postId = idx.toLong(),
                    title = "测试帖子标题 #$idx",
                    authorName = "user$idx",
                    authorUid = idx.toLong(),
                    avatarUrl = null,
                    categoryTitle = "日常",
                    categorySlug = "daily",
                    viewCount = 50 + idx,
                    commentCount = idx % 5,
                    lastActiveText = "${idx}min",
                    lastActiveTitle = null,
                    isPinned = false,
                    isLocked = false,
                    isAwarded = false,
                ),
                isRead = false,
                newCommentCount = 0,
                page = 1,
            )
        }

    private val boards = listOf(
        Board(null, "综合", null),
        Board("daily", "日常", null),
        Board("tech", "技术", null),
    )

    private fun mountFeedWithGlassBar(pageBarEnabled: Boolean): MutableList<NavKey> {
        val backStack = mutableStateListOf<NavKey>(PostListKey)
        var tabBarHiddenByScroll by mutableStateOf(false)
        val dispatcher = NavigationEventDispatcher().apply { addInput(DummyBackInput()) }
        val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = dispatcher
        }
        val posts = samplePosts()

        composeRule.setContent {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                PlazaTheme(darkTheme = true, frostedGlass = true) {
                    val layers = LocalPlazaLayers.current
                    val atTabRoot = TopLevelDestination.forKey(backStack.lastOrNull()) != null
                    val showNavigationSuite = atTabRoot && !tabBarHiddenByScroll
                    val glassBarVisibility = remember { MutableTransitionState(showNavigationSuite) }
                    glassBarVisibility.targetState = showNavigationSuite
                    val navBackdrop = rememberLiquidGlassBackdrop(
                        layers.page,
                        enabled = glassBarVisibility.currentState || glassBarVisibility.targetState || !glassBarVisibility.isIdle,
                    )
                    val feedStates = remember { HomeFeedStates() }
                    val provider = remember(pageBarEnabled) {
                        stackScopedEntryProvider(
                            destination = TopLevelDestination.HOME,
                            backStack = backStack,
                            floatingBottomBarHeight = { key ->
                                if (TopLevelDestination.forKey(key) != null) FloatingGlassBarClearance else 0.dp
                            },
                            provider = { key ->
                                when (key) {
                                    PostListKey -> NavEntry(key) {
                                        val feed = remember {
                                            flowOf(
                                                PagingData.from(
                                                    data = posts,
                                                    sourceLoadStates = LoadStates(
                                                        refresh = LoadState.NotLoading(false),
                                                        prepend = LoadState.NotLoading(true),
                                                        append = LoadState.NotLoading(false),
                                                    ),
                                                ),
                                            )
                                        }
                                        PostListScreen(
                                            state = PostListUiState(
                                                boards = boards,
                                                pageBarEnabled = pageBarEnabled,
                                                totalPages = 100,
                                            ),
                                            postsFor = { feed.collectAsLazyPagingItems() },
                                            feedStates = feedStates,
                                            onPostClick = { post ->
                                                backStack.add(
                                                    PostDetailKey(
                                                        post.summary.postId,
                                                        preview = ThreadPreview(
                                                            title = post.summary.title,
                                                            authorName = post.summary.authorName,
                                                            origin = THREAD_ORIGIN_FEED,
                                                        ),
                                                    ),
                                                )
                                            },
                                            onBoardClick = {},
                                            onSortChange = {},
                                            onCreatePost = {},
                                            onSignInClick = {},
                                            onRecoverInBrowser = {},
                                            onNavigationBarHiddenChanged = { tabBarHiddenByScroll = it },
                                        )
                                    }

                                    is PostDetailKey -> NavEntry(key) {
                                        Surface(Modifier.fillMaxSize().background(layers.page)) {
                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text("Detail ${key.postId}")
                                            }
                                        }
                                    }

                                    is SearchKey -> NavEntry(key) {
                                        Surface(Modifier.fillMaxSize().background(layers.page)) {
                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text("Search")
                                            }
                                        }
                                    }

                                    else -> NavEntry(key) { Text("Other") }
                                }
                            },
                        )
                    }
                    val entries = rememberDecoratedNavEntries(
                        backStack = backStack,
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                        entryProvider = provider,
                    )

                    Box(Modifier.fillMaxSize()) {
                        @OptIn(ExperimentalSharedTransitionApi::class)
                        SharedTransitionLayout(
                            Modifier
                                .fillMaxSize()
                                .liquidGlassBackdropSource(navBackdrop),
                        ) {
                            NodysseyNavDisplay(
                                entries = entries,
                                predictiveBack = true,
                                eink = false,
                                sharedTransitionScope = this@SharedTransitionLayout,
                                sharedThreadTransitions = true,
                                onBack = { backStack.removeLastOrNull() },
                            )
                        }
                        CompositionLocalProvider(LocalLiquidGlassBackdrop provides navBackdrop) {
                            FloatingBottomBarVisibility(
                                visibleState = glassBarVisibility,
                                modifier = Modifier.align(Alignment.BottomCenter),
                            ) {
                                GlassBottomNavigationBar(
                                    current = TopLevelDestination.HOME,
                                    onSelect = {},
                                    unreadCount = 0,
                                )
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        return backStack
    }

    @Test
    fun `exiting feed keeps page bar and FAB clear of retracting glass bar`() {
        val backStack = mountFeedWithGlassBar(pageBarEnabled = true)
        val restingFabBounds = composeRule.onNodeWithContentDescription("发帖").fetchSemanticsNode().boundsInRoot
        val restingRailBounds = composeRule.onNodeWithContentDescription("第 1 / 100 页").fetchSemanticsNode().boundsInRoot
        val restingNavTop = composeRule.onNodeWithText("首页").fetchSemanticsNode().boundsInRoot.top
        assertTrue(restingFabBounds.bottom <= restingNavTop)

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { backStack.add(PostDetailKey(postId = 4)) }

        for (step in listOf(16L, 32L, 32L, 32L)) {
            composeRule.mainClock.advanceTimeBy(step)
            composeRule.waitForIdle()
            val fabNodes = composeRule.onAllNodes(hasContentDescription("发帖")).fetchSemanticsNodes()
            if (fabNodes.isNotEmpty()) {
                val fabBounds = fabNodes.first().boundsInRoot
                assertTrue(
                    "Post FAB dropped during exit: was ${restingFabBounds.top}, now ${fabBounds.top}",
                    fabBounds.top <= restingFabBounds.top + 1f,
                )
            }
            val railNodes = composeRule.onAllNodes(hasContentDescription("第 1 / 100 页")).fetchSemanticsNodes()
            if (railNodes.isNotEmpty()) {
                val railBounds = railNodes.first().boundsInRoot
                assertTrue(
                    "Page rail dropped during exit: was ${restingRailBounds.bottom}, now ${railBounds.bottom}",
                    railBounds.bottom <= restingRailBounds.bottom + 1f,
                )
            }
        }
    }

    @Test
    fun `standalone FAB keeps clearance during push transition`() {
        val backStack = mountFeedWithGlassBar(pageBarEnabled = false)
        val restingFabTop = composeRule.onNodeWithContentDescription("发帖").fetchSemanticsNode().boundsInRoot.top

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { backStack.add(SearchKey()) }

        for (step in listOf(16L, 32L, 32L, 32L)) {
            composeRule.mainClock.advanceTimeBy(step)
            composeRule.waitForIdle()
            val fabNodes = composeRule.onAllNodes(hasContentDescription("发帖")).fetchSemanticsNodes()
            if (fabNodes.isNotEmpty()) {
                val fabTop = fabNodes.first().boundsInRoot.top
                assertTrue(
                    "Standalone FAB dropped during slide push: was $restingFabTop, now $fabTop",
                    fabTop <= restingFabTop + 1f,
                )
            }
        }
    }

    @Test
    fun `scroll hiding and showing animate FAB smoothly without negative padding crash`() {
        val posts = samplePosts()
        var clearancePx = 0f
        composeRule.setContent {
            PlazaTheme(darkTheme = true, frostedGlass = true) {
                clearancePx = with(LocalDensity.current) { FloatingGlassBarClearance.toPx() }
                CompositionLocalProvider(LocalFloatingBottomBarHeight provides FloatingGlassBarClearance) {
                    val feed = remember {
                        flowOf(
                            PagingData.from(
                                data = posts,
                                sourceLoadStates = LoadStates(
                                    refresh = LoadState.NotLoading(false),
                                    prepend = LoadState.NotLoading(true),
                                    append = LoadState.NotLoading(false),
                                ),
                            ),
                        )
                    }
                    PostListScreen(
                        state = PostListUiState(boards = boards, pageBarEnabled = true, totalPages = 100),
                        postsFor = { feed.collectAsLazyPagingItems() },
                        onPostClick = {},
                        onBoardClick = {},
                        onSortChange = {},
                        onSignInClick = {},
                        onRecoverInBrowser = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val restingTop = composeRule.onNodeWithContentDescription("发帖").fetchSemanticsNode().boundsInRoot.top
        composeRule.mainClock.autoAdvance = false

        val listNode = composeRule.onAllNodes(
            hasScrollAction() and hasAnyDescendant(hasText("测试帖子标题 #", substring = true)),
        ).onLast()

        listNode.performTouchInput {
            down(Offset(width / 2f, height * 0.7f))
            advanceEventTime(16)
            moveBy(Offset(0f, -250f))
            advanceEventTime(16)
            up()
        }
        composeRule.mainClock.advanceTimeBy(16)
        composeRule.waitForIdle()

        val earlyHideTop = composeRule.onNodeWithContentDescription("发帖").fetchSemanticsNode().boundsInRoot.top
        assertTrue(
            "Bottom controls snapped too quickly on scroll hide: early=$earlyHideTop resting=$restingTop clearance=$clearancePx",
            earlyHideTop < restingTop + clearancePx * 0.85f,
        )

        repeat(40) {
            composeRule.mainClock.advanceTimeBy(16)
            composeRule.waitForIdle()
        }
        val settledHideTop = composeRule.onNodeWithContentDescription("发帖").fetchSemanticsNode().boundsInRoot.top
        assertTrue(
            "Bottom controls should finish lowering after hide animation: settled=$settledHideTop resting=$restingTop",
            settledHideTop > restingTop + clearancePx * 0.9f,
        )

        listNode.performTouchInput {
            down(Offset(width / 2f, height * 0.3f))
            advanceEventTime(16)
            moveBy(Offset(0f, 250f))
            advanceEventTime(16)
            up()
        }
        composeRule.mainClock.advanceTimeBy(16)
        composeRule.waitForIdle()
        val earlyShowTop = composeRule.onNodeWithContentDescription("发帖").fetchSemanticsNode().boundsInRoot.top
        assertTrue(
            "Bottom controls snapped too quickly on scroll reveal: early=$earlyShowTop settled=$settledHideTop",
            earlyShowTop > restingTop + clearancePx * 0.15f,
        )

        repeat(40) {
            composeRule.mainClock.advanceTimeBy(16)
            composeRule.waitForIdle()
        }
        val restoredTop = composeRule.onNodeWithContentDescription("发帖").fetchSemanticsNode().boundsInRoot.top
        assertTrue(
            "Bottom controls should restore resting clearance: restored=$restoredTop resting=$restingTop",
            kotlin.math.abs(restoredTop - restingTop) < 3f,
        )
    }

    @Test
    fun `eink mode snaps bottom clearance without lingering frames`() {
        val posts = samplePosts()
        var clearancePx = 0f
        composeRule.setContent {
            PlazaTheme(darkTheme = false, einkMode = true) {
                clearancePx = with(LocalDensity.current) { FloatingGlassBarClearance.toPx() }
                CompositionLocalProvider(LocalFloatingBottomBarHeight provides FloatingGlassBarClearance) {
                    val feed = remember {
                        flowOf(
                            PagingData.from(
                                data = posts,
                                sourceLoadStates = LoadStates(
                                    refresh = LoadState.NotLoading(false),
                                    prepend = LoadState.NotLoading(true),
                                    append = LoadState.NotLoading(false),
                                ),
                            ),
                        )
                    }
                    PostListScreen(
                        state = PostListUiState(boards = boards, pageBarEnabled = false, totalPages = 100),
                        postsFor = { feed.collectAsLazyPagingItems() },
                        onPostClick = {},
                        onBoardClick = {},
                        onSortChange = {},
                        onSignInClick = {},
                        onRecoverInBrowser = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val restingTop = composeRule.onNodeWithContentDescription("发帖").fetchSemanticsNode().boundsInRoot.top

        composeRule.onAllNodes(
            hasScrollAction() and hasAnyDescendant(hasText("测试帖子标题 #1")),
        ).onLast().performTouchInput {
            down(Offset(width / 2f, height * 0.7f))
            advanceEventTime(16)
            moveBy(Offset(0f, -250f))
            advanceEventTime(16)
            up()
        }
        composeRule.waitForIdle()

        val collapsedTop = composeRule.onNodeWithContentDescription("发帖").fetchSemanticsNode().boundsInRoot.top
        assertTrue(
            "E-ink mode should snap to collapsed clearance ($collapsedTop vs resting=$restingTop + $clearancePx)",
            collapsedTop >= restingTop + clearancePx - 3f,
        )
    }

    @Test
    fun `stackScopedEntryProvider scopes LocalFloatingBottomBarHeight per entry and tracks live toggle`() {
        var floatingGlassEnabled by mutableStateOf(true)
        var measured = mutableMapOf<NavKey, Dp>()
        val backStack = listOf<NavKey>(PostListKey, PostDetailKey(1), SearchKey())

        val entryProvider = stackScopedEntryProvider(
            destination = TopLevelDestination.HOME,
            backStack = backStack,
            floatingBottomBarHeight = { key ->
                if (floatingGlassEnabled && TopLevelDestination.forKey(key) != null) {
                    FloatingGlassBarClearance
                } else {
                    0.dp
                }
            },
            provider = { key ->
                NavEntry(key) {
                    measured[key] = LocalFloatingBottomBarHeight.current
                }
            },
        )

        composeRule.setContent {
            backStack.forEach { key -> entryProvider(key).Content() }
        }
        composeRule.waitForIdle()
        assertEquals(FloatingGlassBarClearance, measured[PostListKey])
        assertEquals(0.dp, measured[PostDetailKey(1)])
        assertEquals(0.dp, measured[SearchKey()])

        composeRule.runOnUiThread { floatingGlassEnabled = false }
        composeRule.waitForIdle()
        assertEquals(0.dp, measured[PostListKey])
    }
}
