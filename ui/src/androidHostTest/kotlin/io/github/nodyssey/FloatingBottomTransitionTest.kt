package io.github.nodyssey

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performTouchInput
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.nodyssey.data.Board
import io.github.nodyssey.data.FeedPost
import io.github.nodyssey.model.PostSummary
import io.github.nodyssey.ui.common.LocalFloatingBottomBarHeight
import io.github.nodyssey.ui.navigation.FloatingGlassBarClearance
import io.github.nodyssey.ui.postlist.PostListScreen
import io.github.nodyssey.ui.postlist.PostListUiState
import io.github.plaza.designsys.theme.PlazaTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
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

    @Test
    fun `page rail spring retraction never supplies negative padding`() = retractAndRevealControls(pageBarEnabled = true)

    @Test
    fun `standalone FAB spring retraction never supplies negative padding`() = retractAndRevealControls(pageBarEnabled = false)

    private fun retractAndRevealControls(pageBarEnabled: Boolean) {
        val posts = samplePosts()
        val visibilityChanges = mutableListOf<Boolean>()
        composeRule.setContent {
            PlazaTheme(darkTheme = true, frostedGlass = true) {
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
                        state = PostListUiState(boards = boards, pageBarEnabled = pageBarEnabled, totalPages = 100),
                        postsFor = { feed.collectAsLazyPagingItems() },
                        onPostClick = {},
                        onBoardClick = {},
                        onSortChange = {},
                        onSignInClick = {},
                        onRecoverInBrowser = {},
                        onNavigationBarHiddenChanged = visibilityChanges::add,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        val listNode = composeRule.onAllNodes(
            hasScrollAction() and hasAnyDescendant(hasText("测试帖子标题 #", substring = true)),
        ).onLast()
        for (delta in listOf(-250f, 250f)) {
            listNode.performTouchInput {
                down(Offset(width / 2f, height / 2f))
                advanceEventTime(16)
                moveBy(Offset(0f, delta))
                advanceEventTime(16)
                up()
            }
            // The expressive spring crosses zero before settling. Advancing frame by frame
            // catches the negative-padding crash instead of skipping straight to its final value.
            repeat(60) {
                composeRule.mainClock.advanceTimeByFrame()
                composeRule.waitForIdle()
            }
        }
        // Ensure both animation targets were reached; a gesture that missed the list would hide
        // the crash just as effectively as advancing past the spring's overshoot.
        assertEquals(listOf(true, false), visibilityChanges)
    }
}
