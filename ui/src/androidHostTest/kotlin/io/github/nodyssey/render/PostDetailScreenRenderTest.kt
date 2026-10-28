package io.github.nodyssey.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.nodyssey.ThreadPreview
import io.github.nodyssey.data.FreeChickenLegs
import io.github.nodyssey.data.ReadingPosition
import io.github.nodyssey.data.settings.PostReplyStyle
import io.github.nodyssey.model.PostContent
import io.github.nodyssey.model.PostReactions
import io.github.nodyssey.ui.postdetail.PostDetailScreen
import io.github.nodyssey.ui.postdetail.PostDetailUiState
import io.github.nodyssey.ui.postdetail.ReplyAuthorInfo
import io.github.nodyssey.ui.postdetail.readingView
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode
import io.github.plaza.designsys.theme.PlazaSoftPalette
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 帖子详情 in both themes — a thread rendered as native Compose content, which is the app's whole
 * pitch, so the second picture on the promo page. See [PostListScreenRenderTest] on the null avatars.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class PostDetailScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @Composable
    private fun Screen(
        darkTheme: Boolean,
        state: PostDetailUiState = STATE,
        fontScale: Float = 1f,
        pureWhite: Boolean = false,
        softPalette: PlazaSoftPalette? = null,
    ) {
        PlazaTheme(darkTheme = darkTheme, fontScale = fontScale, pureWhite = pureWhite, softPalette = softPalette) {
            PostDetailScreen(
                state = state,
                postUrl = "https://www.nodeseek.com/post-100-1",
                onBack = {},
                onOpenBrowser = {},
                onImageClick = {},
                onRetry = {},
                onLoadMore = {},
                onVerify = {},
            )
        }
    }

    @Test
    fun `the thread in light`() {
        composeRule.setContent { Screen(darkTheme = false) }

        composeRule.onRoot().captureRender("post-detail-light")
    }

    @Test
    fun `the thread in dark`() {
        composeRule.setContent { Screen(darkTheme = true) }

        composeRule.onRoot().captureRender("post-detail-dark")
    }

    /** The reply cards, scrolled to — the first screen shows little more than the opening post. */
    @Test
    fun `the replies in light`() {
        composeRule.setContent { Screen(darkTheme = false) }
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(1)

        composeRule.onRoot().captureRender("post-detail-replies-light")
    }

    @Test
    fun `the replies in dark`() {
        composeRule.setContent { Screen(darkTheme = true) }
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(1)

        composeRule.onRoot().captureRender("post-detail-replies-dark")
    }

    @Test
    fun `light cards with signature links and quotes`() {
        composeRule.setContent { Screen(darkTheme = false, state = SIGNED_REPLIES, pureWhite = true) }
        composeRule.onRoot().captureRender("post-detail-cards-signatures")
    }

    @Test
    fun `flat replies with signature links and quotes`() {
        composeRule.setContent { Screen(darkTheme = false, state = SIGNED_REPLIES.copy(postReplyStyle = PostReplyStyle.FLAT), pureWhite = true) }
        composeRule.onRoot().captureRender("post-detail-flat-signatures")
    }

    @Test
    fun `the original layout with signature links and quotes`() {
        composeRule.setContent { Screen(darkTheme = false, state = SIGNED_REPLIES.copy(postReplyStyle = PostReplyStyle.ORIGINAL), pureWhite = true) }
        composeRule.onRoot().captureRender("post-detail-original-layout")
    }

    @Test
    fun `the original layout in neutral dark`() {
        composeRule.setContent { Screen(darkTheme = true, state = SIGNED_REPLIES.copy(postReplyStyle = PostReplyStyle.ORIGINAL), pureWhite = true) }
        composeRule.onRoot().captureRender("post-detail-original-layout-dark")
    }

    @Test
    fun `the original opening post with replies`() {
        composeRule.setContent { Screen(darkTheme = false, state = SHORT_STATE.copy(postReplyStyle = PostReplyStyle.ORIGINAL), pureWhite = true) }
        composeRule.onRoot().captureRender("post-detail-original-opening")
    }

    @Test
    fun `flat replies in dark`() {
        composeRule.setContent { Screen(darkTheme = true, state = SIGNED_REPLIES.copy(postReplyStyle = PostReplyStyle.FLAT), pureWhite = true) }
        composeRule.onRoot().captureRender("post-detail-flat-dark")
    }

    @Test
    fun `neutral dark cards with signature links and quotes`() {
        composeRule.setContent { Screen(darkTheme = true, state = SIGNED_REPLIES, pureWhite = true) }
        composeRule.onRoot().captureRender("post-detail-neutral-dark-cards")
    }

    @Test
    fun `neutral dark opening post with replies`() {
        composeRule.setContent { Screen(darkTheme = true, state = SHORT_STATE, pureWhite = true) }
        composeRule.onRoot().captureRender("post-detail-neutral-dark-opening")
    }

    @Test
    fun `neutral dark loading card`() {
        composeRule.setContent { Screen(darkTheme = true, state = LOADING_STATE, pureWhite = true) }
        composeRule.onRoot().captureRender("post-detail-neutral-dark-loading")
    }

    @Test
    @Config(qualifiers = "w320dp-h900dp")
    fun `neutral dark cards with large text`() {
        composeRule.setContent { Screen(darkTheme = true, state = SIGNED_REPLIES, fontScale = 1.5f, pureWhite = true) }
        composeRule.onRoot().captureRender("post-detail-neutral-dark-large-text")
    }

    @Test
    @Config(qualifiers = "w320dp-h900dp")
    fun `light card actions with large text`() {
        composeRule.setContent { Screen(darkTheme = false, state = SIGNED_REPLIES, fontScale = 1.5f, pureWhite = true) }
        composeRule.onRoot().captureRender("post-detail-cards-large-text")
    }

    @Test
    fun `account info pending without blocking the thread`() {
        composeRule.setContent { Screen(darkTheme = false, state = SIGNED_REPLIES.copy(replyAuthors = emptyMap(), loadingAuthorUids = setOf(121, 122, 123)), pureWhite = true) }
        composeRule.onRoot().captureRender("post-detail-account-loading")
    }

    @Test
    fun `loading opening card with a list preview`() {
        composeRule.setContent { Screen(darkTheme = false, state = LOADING_STATE, pureWhite = true) }
        composeRule.onRoot().captureRender("post-detail-loading-card")
    }

    @Test
    fun `loading flat opening post with a list preview`() {
        composeRule.setContent { Screen(darkTheme = false, state = LOADING_STATE.copy(postReplyStyle = PostReplyStyle.FLAT), pureWhite = true) }
        composeRule.onRoot().captureRender("post-detail-loading-flat")
    }

    @Test
    @Config(qualifiers = "w320dp-h900dp")
    fun `loading opening card with large text`() {
        composeRule.setContent { Screen(darkTheme = false, state = LOADING_STATE, fontScale = 1.5f, pureWhite = true) }
        composeRule.onRoot().captureRender("post-detail-loading-large-text")
    }

    @Test
    fun `account info switched off with original forum badges retained`() {
        composeRule.setContent { Screen(darkTheme = false, state = SIGNED_REPLIES.copy(showAuthorAccountInfo = false), pureWhite = true) }
        composeRule.onRoot().captureRender("post-detail-account-off")
    }

    @Test
    @Config(qualifiers = "w320dp-h900dp")
    fun `reply account badges with long names and large text`() {
        val state = LATER_PAGE.copy(
            comments = LATER_PAGE.comments.mapIndexed { index, reply ->
                if (index == 0) reply.copy(authorName = "A very long forum username for wrapping") else reply
            },
        )
        composeRule.setContent { Screen(darkTheme = false, state = state, fontScale = 1.3f) }
        composeRule.onRoot().captureRender("post-detail-author-badges-narrow")
    }

    @Test
    fun `a short thread in light`() {
        composeRule.setContent { Screen(darkTheme = false, state = SHORT_STATE) }

        composeRule.onRoot().captureRender("post-detail-short-light")
    }

    @Test
    fun `a short thread in dark`() {
        composeRule.setContent { Screen(darkTheme = true, state = SHORT_STATE) }

        composeRule.onRoot().captureRender("post-detail-short-dark")
    }

    @Test
    fun `a short thread in pure white`() {
        composeRule.setContent { Screen(darkTheme = false, state = SHORT_STATE, pureWhite = true) }

        composeRule.onRoot().captureRender("post-detail-pure-white")
    }

    @Test
    fun `the replies in pure white`() {
        composeRule.setContent { Screen(darkTheme = false, pureWhite = true) }
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(1)

        composeRule.onRoot().captureRender("post-detail-replies-pure-white")
    }

    @Test
    fun `a later page with a cached opening post`() {
        composeRule.setContent { Screen(darkTheme = false, state = LATER_PAGE, pureWhite = true) }

        composeRule.onRoot().captureRender("post-detail-later-page")
    }

    @Test
    fun `a later page in dark`() {
        composeRule.setContent { Screen(darkTheme = true, state = LATER_PAGE) }

        composeRule.onRoot().captureRender("post-detail-later-page-dark")
    }

    @Test
    fun `bubble replies in mist violet`() {
        composeRule.setContent { Screen(darkTheme = false, state = LATER_PAGE, softPalette = PlazaSoftPalette.VIOLET) }
        composeRule.onRoot().captureRender("post-detail-bubbles-violet")
    }

    @Test
    fun `bubble replies in mist violet dark`() {
        composeRule.setContent { Screen(darkTheme = true, state = LATER_PAGE, softPalette = PlazaSoftPalette.VIOLET) }
        composeRule.onRoot().captureRender("post-detail-bubbles-violet-dark")
    }

    @Test
    fun `reading tools in mist violet`() = readingTools(darkTheme = false)

    @Test
    fun `reading tools in mist violet dark`() = readingTools(darkTheme = true)

    @Test
    fun `neutral dark reading tools`() = readingTools(darkTheme = true, pureWhite = true)

    @Test
    @Config(qualifiers = "w320dp-h900dp")
    fun `reading tools on a narrow screen`() = readingTools(darkTheme = false, narrow = true)

    private fun readingTools(darkTheme: Boolean, narrow: Boolean = false, pureWhite: Boolean = false) {
        composeRule.setContent { Screen(darkTheme = darkTheme, state = LATER_PAGE, pureWhite = pureWhite, softPalette = if (pureWhite) null else PlazaSoftPalette.VIOLET) }
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(LATER_PAGE.comments.size + 1)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRender("post-detail-reading-tools-${if (darkTheme) "dark" else "light"}${if (narrow) "-narrow" else ""}${if (pureWhite) "-neutral" else ""}")
    }

    @Test
    fun `bubble replies in breeze blue`() {
        composeRule.setContent { Screen(darkTheme = false, state = LATER_PAGE, softPalette = PlazaSoftPalette.BLUE) }
        composeRule.onRoot().captureRender("post-detail-bubbles-blue")
    }

    @Test
    fun `bubble replies in mint green`() {
        composeRule.setContent { Screen(darkTheme = false, state = LATER_PAGE, softPalette = PlazaSoftPalette.MINT) }
        composeRule.onRoot().captureRender("post-detail-bubbles-mint")
    }

    @Test
    fun `bubble replies in warm sand`() {
        composeRule.setContent { Screen(darkTheme = false, state = LATER_PAGE, softPalette = PlazaSoftPalette.SAND) }
        composeRule.onRoot().captureRender("post-detail-bubbles-sand")
    }

    @Test
    @Config(qualifiers = "w320dp-h900dp")
    fun `mist violet bubbles at the largest reading size`() {
        composeRule.setContent { Screen(darkTheme = false, state = LATER_PAGE, fontScale = 1.3f, softPalette = PlazaSoftPalette.VIOLET) }
        composeRule.onRoot().captureRender("post-detail-bubbles-violet-large-text")
    }

    @Test
    fun `the boundary between two loaded pages`() {
        composeRule.setContent {
            Screen(
                darkTheme = false,
                pureWhite = true,
                state = LATER_PAGE.copy(
                    comments = COMMENTS + LATER_PAGE.comments,
                    commentPages = List(COMMENTS.size) { 12 } + LATER_PAGE.commentPages,
                    firstLoadedPage = 12,
                ),
            )
        }
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(COMMENTS.size + 1)

        composeRule.onRoot().captureRender("post-detail-page-boundary")
    }

    @Test
    @Config(qualifiers = "w320dp-h900dp")
    fun `a narrow thread at the largest reading size`() {
        composeRule.setContent { Screen(darkTheme = false, state = SHORT_STATE, fontScale = 1.3f) }

        composeRule.onRoot().captureRender("post-detail-narrow-large-text")
    }

    @Test
    fun `author replies keep original floors and quote sources`() {
        composeRule.setContent { Screen(darkTheme = false, state = LATER_PAGE.copy(onlyAuthor = true).readingView()) }
        composeRule.onRoot().captureRender("post-detail-author-replies")
    }

    @Test
    @Config(qualifiers = "w320dp-h900dp")
    fun `a later page with quotes at the largest reading size`() {
        composeRule.setContent { Screen(darkTheme = false, state = LATER_PAGE, fontScale = 1.3f) }
        composeRule.onRoot().captureRender("post-detail-later-narrow-large-text")
    }

    /** 1c — the first reply's panel, opened from its ⋯ (the opening post's is the first one). */
    @Test
    fun `the floor panel in light`() = floorPanel(darkTheme = false)

    @Test
    fun `the floor panel in dark`() = floorPanel(darkTheme = true)

    /** The floor-jump sheet, opened from the floating reading position. */
    @Test
    fun `the page jump in light`() = pageJump(darkTheme = false)

    @Test
    fun `the page jump in dark`() = pageJump(darkTheme = true)

    private fun floorPanel(darkTheme: Boolean) {
        composeRule.setContent { Screen(darkTheme = darkTheme) }
        // Open through its semantics action so this capture does not depend on scroll placement.
        composeRule.onAllNodesWithContentDescription("楼层操作")[1].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        captureWindows("post-detail-actions-${if (darkTheme) "dark" else "light"}")
    }

    private fun pageJump(darkTheme: Boolean) {
        composeRule.setContent { Screen(darkTheme = darkTheme) }
        composeRule.onNodeWithContentDescription("正在读主帖，点击跳楼").performClick()
        composeRule.waitForIdle()

        captureWindows("post-detail-jump-${if (darkTheme) "dark" else "light"}")
    }

    /**
     * A bottom sheet is a window of its own, which a node capture never reaches; this draws every
     * window the screen has open, scrim included, as the phone would show them.
     */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun captureWindows(name: String) {
        captureScreenRoboImage(filePath = "build/outputs/renders/$name.png")
    }

    private companion object {
        val LOADING_STATE = PostDetailUiState(
            postId = 100,
            title = "QQG.NET靠谱吗",
            preview = ThreadPreview(
                title = "QQG.NET靠谱吗",
                authorName = "ccsky",
                categoryTitle = "日常",
                categorySlug = "daily",
            ),
            isLoading = true,
        )

        private fun paragraphs(vararg text: String): List<RichNode> =
            text.map { RichNode.Paragraph(listOf(InlineNode.Text(it))) }

        private fun floor(
            id: Long,
            author: String,
            floor: String,
            createdAt: String,
            text: List<String>,
            isOp: Boolean = false,
            badges: List<String> = emptyList(),
            reactions: PostReactions? = null,
        ) = PostContent(
            commentId = id,
            floor = floor,
            authorName = author,
            authorUid = id,
            avatarUrl = null,
            isOriginalPoster = isOp,
            badges = badges,
            createdAtText = createdAt,
            createdAtTitle = null,
            categoryTitle = null,
            nodes = paragraphs(*text.toTypedArray()),
            reactions = reactions,
        )

        val BODY =
            PostContent(
                commentId = 1000,
                floor = null,
                authorName = "homelab_er",
                authorUid = 1000,
                avatarUrl = null,
                isOriginalPoster = true,
                badges = listOf("Lv.4"),
                createdAtText = "2小时前",
                createdAtTitle = null,
                categoryTitle = "技术",
                // 1b's own opening: a paragraph, then a code block on the card's inset layer.
                nodes =
                paragraphs(
                    "折腾了一年 NAS，从最早的一块二手硬盘挂在路由器上，到现在四盘位 + 万兆内网。第一个坑是电源，杂牌电源在硬盘同时启动时压不住。",
                ) + RichNode.CodeBlock(code = "smartctl -a /dev/sda | grep Power_On", language = null) +
                    paragraphs(
                        "第二个是备份策略。RAID 不是备份，重要的东西一定要有异地冷备，我现在是本地一份 + 云端加密一份。",
                    ),
                reactions = PostReactions(likeCount = 86, upvoteCount = 214, dislikeCount = 1),
            )

        val COMMENTS =
            listOf(
                floor(
                    id = 1001,
                    author = "轻舟",
                    floor = "#1",
                    createdAt = "1小时前",
                    text = listOf("同折腾党，电源那条深有体会。补充一点：机械硬盘尽量买不同批次，避免同时坏盘。"),
                    reactions = PostReactions(likeCount = 12, upvoteCount = 40),
                ),
                floor(
                    id = 1002,
                    author = "homelab_er",
                    floor = "#2",
                    createdAt = "1小时前",
                    text = listOf("对，这个太重要了。我第一批四块盘是同型号同批次，用了两年差点一起阵亡。"),
                    isOp = true,
                    reactions = PostReactions(upvoteCount = 18),
                ),
                floor(
                    id = 1003,
                    author = "过路人",
                    floor = "#3",
                    createdAt = "48分钟前",
                    text = listOf("云端冷备用的哪家？想找个便宜大碗的对象存储。"),
                    reactions = PostReactions(upvoteCount = 6),
                ),
                floor(
                    id = 1004,
                    author = "省钱达人",
                    floor = "#4",
                    createdAt = "30分钟前",
                    text = listOf("Mark 一下，正好准备装第一台，收藏了慢慢看。感谢楼主的总结！"),
                ),
            )

        val STATE =
            PostDetailUiState(
                postId = 100,
                title = "自建 NAS 一年，聊聊我踩过的那些坑和真香时刻",
                body = BODY,
                comments = COMMENTS,
                replyAuthors = mapOf(
                    1001L to ReplyAuthorInfo(4, 459),
                    1002L to ReplyAuthorInfo(0, 2),
                    1003L to ReplyAuthorInfo(4, 252),
                    1004L to ReplyAuthorInfo(null, 82),
                ),
                commentPages = List(COMMENTS.size) { 1 },
                totalPages = 3,
                hasNextPage = true,
                isSignedIn = true,
                collected = false,
                collectionCount = 37,
                isAwarded = true,
                freeChickenLegs = FreeChickenLegs(max = 1, used = 0),
                // So the jump sheet has both of 9d's destinations to draw.
                resumePosition = ReadingPosition(page = 3, floor = "#25"),
            )

        val SHORT_STATE = STATE.copy(
            body = BODY.copy(nodes = paragraphs("折腾 NAS 一年，最想提醒大家的是：RAID 不是备份，重要数据一定要留一份异地冷备。")),
            authorLevel = 4,
            authorJoinedDays = 388,
        )

        val LATER_PAGE = STATE.copy(
            replyCount = 396,
            replyAuthors = (121L..130L).associateWith { ReplyAuthorInfo(if (it % 2L == 0L) 0 else 4, if (it % 2L == 0L) 2 else 459) },
            comments = (121..130).map { number ->
                floor(
                    id = number.toLong(),
                    author = if (number % 2 == 0) "homelab_er" else "轻舟",
                    floor = "#$number",
                    createdAt = "10分钟前",
                    text = listOf("继续补充一个细节：重要数据建议定期验证备份能否恢复，真正需要时才安心。"),
                    isOp = number % 2 == 0,
                    reactions = PostReactions(upvoteCount = number % 5),
                ).let { reply ->
                    if (number == 122) {
                        reply.copy(
                            nodes = listOf(
                                RichNode.Quote(
                                    listOf(
                                        RichNode.Paragraph(
                                            listOf(
                                                InlineNode.QuoteRef("轻舟", "#121", "/post-100-13#121"),
                                                InlineNode.Text("补充一点：备份之后也要定期检查能否恢复。"),
                                            ),
                                        ),
                                    ),
                                ),
                            ) + reply.nodes,
                        )
                    } else {
                        reply
                    }
                }
            },
            commentPages = List(10) { 13 },
            firstLoadedPage = 13,
            lastLoadedPage = 13,
            totalPages = 40,
        )

        val SIGNED_REPLIES = LATER_PAGE.copy(
            comments = LATER_PAGE.comments.mapIndexed { index, comment ->
                comment.copy(
                    badges = listOf("Lv.4"),
                    signatureNodes = if (index % 2 == 0) {
                        listOf(
                            RichNode.Paragraph(listOf(InlineNode.Link(text = "探针 | TG | PM | NAS 技术交流", url = "https://www.nodeseek.com/"))),
                        )
                    } else {
                        emptyList()
                    },
                )
            },
        )
    }
}
