package io.github.nodyssey.ui.postlist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.nodyssey.data.Board
import io.github.nodyssey.data.FeedPost
import io.github.plaza.designsys.theme.PlazaTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pixel-level alignment regression tests for [BoardStrip] in collapsed, expanded, and edit modes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class BoardStripLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val nodeSeekBoards =
        listOf(
            Board(null, "综合", null),
            Board("daily", "日常", null),
            Board("tech", "技术", null),
            Board("info", "情报", null),
            Board("review", "测评", null),
            Board("trade", "交易", null),
            Board("carpool", "拼车", null),
            Board("promotion", "推广", null),
            Board("life", "生活", null),
            Board("Dev", "Dev", null),
            Board("photo", "贴图", null),
            Board("expose", "曝光", null),
            Board("inside", "内版", null, adminOnly = true),
            Board("sandbox", "沙盒", null),
            Board("meaningless", "无意义", null, adminOnly = true),
        )

    private val loggedInTwoCharBoards =
        nodeSeekBoards.dropLast(1).map { it.copy(adminOnly = false) }

    private fun SemanticsNodeInteraction.boundsPx(): Rect {
        val dpRect = getUnclippedBoundsInRoot()
        return with(composeRule.density) {
            Rect(
                left = dpRect.left.toPx(),
                top = dpRect.top.toPx(),
                right = dpRect.right.toPx(),
                bottom = dpRect.bottom.toPx(),
            )
        }
    }

    private fun dpToPx(dp: Dp): Float = with(composeRule.density) { dp.toPx() }

    private fun setFullPostListScreen(boards: List<Board> = nodeSeekBoards) {
        composeRule.setContent {
            val emptyFeed =
                remember {
                    flowOf(
                        PagingData.from(
                            data = emptyList<FeedPost>(),
                            sourceLoadStates =
                            LoadStates(
                                refresh = LoadState.NotLoading(true),
                                prepend = LoadState.NotLoading(true),
                                append = LoadState.NotLoading(true),
                            ),
                        ),
                    )
                }
            PlazaTheme {
                PostListScreen(
                    state = PostListUiState(boards = boards),
                    postsFor = { emptyFeed.collectAsLazyPagingItems() },
                    feedStates = rememberSaveable(saver = HomeFeedStates.Saver) { HomeFeedStates() },
                    onPostClick = {},
                    onBoardClick = {},
                    onSortChange = {},
                    onSignInClick = {},
                    onRecoverInBrowser = {},
                )
            }
        }
    }

    private fun setBoardStripOnly(
        boards: List<Board>,
        parkedBoards: List<Board> = emptyList(),
    ) {
        composeRule.setContent {
            var currentBoards by remember { mutableStateOf(boards) }
            var currentParked by remember { mutableStateOf(parkedBoards) }
            PlazaTheme {
                BoardStrip(
                    boards = currentBoards,
                    parkedBoards = currentParked,
                    selectedSlug = null,
                    highlightedSlug = null,
                    onBoardClick = {},
                    onArrangementChange = { order, parked ->
                        val all = currentBoards + currentParked
                        val byKey = all.associateBy { it.slug ?: FRONT_PAGE_KEY }
                        currentBoards = order.mapNotNull { byKey[it] }
                        currentParked = parked.mapNotNull { byKey[it] }
                    },
                )
            }
        }
    }

    private fun assertNearPx(
        message: String,
        expected: Float,
        actual: Float,
        tolerancePx: Float = 1.5f,
    ) {
        assertTrue(
            "$message (expected=$expected, actual=$actual, diff=${abs(expected - actual)})",
            abs(expected - actual) <= tolerancePx,
        )
    }

    private fun assertExpandedUniformColumns(
        boards: List<Board>,
        expectedHorizontalMarginDp: Dp = 12.dp,
        expectCollapseButton: Boolean = true,
    ) {
        val rects = boards.map { board -> composeRule.onNodeWithText(board.title).boundsPx() }
        val firstWidth = rects.first().width
        rects.forEachIndexed { index, rect ->
            assertNearPx(
                "pill '${boards[index].title}' width should match uniform column width",
                firstWidth,
                rect.width,
            )
        }

        val rows =
            rects
                .withIndex()
                .groupBy { (_, rect) -> rect.top.roundToInt() }
                .entries
                .sortedBy { it.key }
                .map { entry -> entry.value.sortedBy { it.value.left }.map { it.value } }

        assertTrue("expected multiple rows when expanded, got ${rows.size}", rows.size >= 2)
        val expectedLeftPx = dpToPx(expectedHorizontalMarginDp)
        assertNearPx("row 0 should start at left margin", expectedLeftPx, rows.first().first().left)

        val gapPx = dpToPx(6.dp)
        if (expectCollapseButton) {
            val collapseBounds = composeRule.onNodeWithContentDescription("收起版块").boundsPx()
            assertTrue(
                "row 0 last pill right (${rows.first().last().right}) must stay clear of collapse button left (${collapseBounds.left}) by at least $gapPx",
                rows.first().last().right <= collapseBounds.left - gapPx + 1.5f,
            )
            // Any subsequent full row spans up to the collapse button's right edge.
            val fullRowCols = rows[1].size
            rows.drop(1).forEachIndexed { rIdx, row ->
                assertNearPx("row ${rIdx + 1} should align left with row 0", expectedLeftPx, row.first().left)
                if (row.size == fullRowCols) {
                    assertNearPx(
                        "full row ${rIdx + 1} right edge should match collapse button right edge",
                        collapseBounds.right,
                        row.last().right,
                    )
                }
                row.forEachIndexed { col, cell ->
                    if (col < rows.first().size) {
                        assertNearPx(
                            "row ${rIdx + 1} col $col left should align with row 0",
                            rows.first()[col].left,
                            cell.left,
                        )
                        assertNearPx(
                            "row ${rIdx + 1} col $col right should align with row 0",
                            rows.first()[col].right,
                            cell.right,
                        )
                    }
                }
            }
        } else {
            val fullRowCols = rows.first().size
            rows.drop(1).forEachIndexed { rIdx, row ->
                assertNearPx("row ${rIdx + 1} should align left with row 0", expectedLeftPx, row.first().left)
                if (row.size == fullRowCols) {
                    assertNearPx(
                        "edit-mode full row ${rIdx + 1} right edge should match row 0",
                        rows.first().last().right,
                        row.last().right,
                    )
                }
                row.forEachIndexed { col, cell ->
                    assertNearPx(
                        "edit-mode row ${rIdx + 1} col $col left should align with row 0",
                        rows.first()[col].left,
                        cell.left,
                    )
                    assertNearPx(
                        "edit-mode row ${rIdx + 1} col $col right should align with row 0",
                        rows.first()[col].right,
                        cell.right,
                    )
                }
            }
        }
    }

    @Test
    fun `collapsed toggle aligns right and width with SortButton at 360dp`() {
        setFullPostListScreen(loggedInTwoCharBoards)

        val searchBounds = composeRule.onNodeWithText("搜索帖子或用户").boundsPx()
        val sortBounds = composeRule.onNodeWithContentDescription("排序方式").boundsPx()
        val toggleBounds = composeRule.onNodeWithContentDescription("全部版块").boundsPx()
        val firstPillBounds = composeRule.onNodeWithText("综合").boundsPx()
        val fifthPillBounds = composeRule.onNodeWithText("测评").boundsPx()

        assertNearPx("综合 left aligns with SearchPill left", searchBounds.left, firstPillBounds.left)
        assertTrue(
            "collapsed pills before toggle stay clear by PillGap (${fifthPillBounds.right} <= ${toggleBounds.left - dpToPx(6.dp)})",
            fifthPillBounds.right <= toggleBounds.left - dpToPx(6.dp) + 1.5f,
        )
        assertNearPx("BoardToggle left aligns with SortButton left", sortBounds.left, toggleBounds.left)
        assertNearPx("BoardToggle right aligns with SortButton right", sortBounds.right, toggleBounds.right)
        assertNearPx("BoardToggle width equals SortButton width (40dp)", sortBounds.width, toggleBounds.width)
        assertEquals(dpToPx(40.dp).roundToInt(), toggleBounds.width.roundToInt())
    }

    @Test
    @Config(qualifiers = "w393dp-h800dp")
    fun `collapsed toggle and expanded rows align with header at 393dp`() {
        setFullPostListScreen(loggedInTwoCharBoards)

        val searchBounds = composeRule.onNodeWithText("搜索帖子或用户").boundsPx()
        val sortBounds = composeRule.onNodeWithContentDescription("排序方式").boundsPx()
        val collapsedToggle = composeRule.onNodeWithContentDescription("全部版块").boundsPx()
        val firstCollapsedPill = composeRule.onNodeWithText("综合").boundsPx()

        assertNearPx("393dp 综合 left aligns with SearchPill left", searchBounds.left, firstCollapsedPill.left)
        assertNearPx("393dp BoardToggle right aligns with SortButton right", sortBounds.right, collapsedToggle.right)
        assertNearPx("393dp BoardToggle width matches SortButton", sortBounds.width, collapsedToggle.width)

        composeRule.onNodeWithContentDescription("全部版块").performClick()
        composeRule.waitForIdle()

        val expandedCollapse = composeRule.onNodeWithContentDescription("收起版块").boundsPx()
        assertNearPx("393dp expanded ^ aligns with SortButton right", sortBounds.right, expandedCollapse.right)
        assertNearPx("393dp expanded ^ aligns with SortButton left", sortBounds.left, expandedCollapse.left)
        assertExpandedUniformColumns(loggedInTwoCharBoards)
    }

    @Test
    fun `expanded strip keeps Dev row aligned with other rows at 360dp`() {
        setBoardStripOnly(loggedInTwoCharBoards)

        composeRule.onNodeWithContentDescription("全部版块").performClick()
        composeRule.waitForIdle()

        assertExpandedUniformColumns(loggedInTwoCharBoards)
    }

    @Test
    fun `expanded strip aligns when signed-out admin locks and 3-char title widen columns`() {
        setBoardStripOnly(nodeSeekBoards)

        composeRule.onNodeWithContentDescription("全部版块").performClick()
        composeRule.waitForIdle()

        assertExpandedUniformColumns(nodeSeekBoards)
    }

    @Test
    fun `expanded strip aligns 4-char DeepFlood titles uniformly`() {
        val deepFloodBoards =
            listOf(
                Board(null, "综合", null),
                Board("daily", "日常交流", null),
                Board("tech", "技术探讨", null),
                Board("info", "情报资讯", null),
                Board("review", "测评晒单", null),
                Board("trade", "二手交易", null),
                Board("life", "生活随笔", null),
                Board("photo", "摄影贴图", null),
            )
        setBoardStripOnly(deepFloodBoards)

        composeRule.onNodeWithContentDescription("全部版块").performClick()
        composeRule.waitForIdle()

        assertExpandedUniformColumns(deepFloodBoards)
    }

    @Test
    fun `edit mode keeps uniform grid alignment without collapse toggle`() {
        setBoardStripOnly(loggedInTwoCharBoards)

        composeRule.onNodeWithContentDescription("全部版块").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("日常").performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("完成").fetchSemanticsNode()
        assertExpandedUniformColumns(loggedInTwoCharBoards, expectCollapseButton = false)
    }
}
