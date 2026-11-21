package io.github.nodyssey.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.paging.PagingData
import io.github.nodyssey.data.SpacePost
import io.github.nodyssey.data.composer.PostPermission
import io.github.nodyssey.ui.space.SpaceTab
import io.github.nodyssey.ui.space.UserSpaceScreen
import io.github.nodyssey.ui.space.UserSpaceUiState
import io.github.plaza.designsys.theme.PlazaTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The same space at tablet width: the page column is capped and centred, and the top bar's contents
 * are meant to stay over it through `ReadableTopBar`'s side insets — the path a phone render, whose
 * margin is zero, cannot see.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1000dp-h800dp")
class UserSpaceWideRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @Composable
    private fun Space(state: UserSpaceUiState) {
        val topics = remember { flowOf(PagingData.from(TOPICS)) }
        UserSpaceScreen(
            state = state,
            topics = topics,
            onBack = {},
            onTabSelected = {},
            onPostClick = { _, _ -> },
            onCommentClick = { _, _ -> },
            onRetryProfile = {},
            onMessage = {},
            onEditProfile = {},
            onOpenBrowser = {},
            onSignIn = {},
            onVerify = {},
        )
    }

    private fun render(name: String, state: UserSpaceUiState) {
        composeRule.setContent { PlazaTheme { Space(state) } }
        composeRule.onRoot().captureRender(name)
    }

    @Test
    fun `someone else's space in light`() = render("space-wide-light", OTHER)

    @Test
    fun `own space in light`() = render("space-wide-self-light", SELF)

    private companion object {
        val OTHER =
            UserSpaceUiState(
                uid = 18_842,
                isSelf = false,
                isLoadingProfile = false,
                name = "轻舟",
                level = 3,
                bio = "在香港和东京各跑一台小鸡，主业写 Go。偶尔发测评，欢迎私信交流线路。",
                readme = "## 关于我的机器\n常驻 CN2 GIA 与软银线路。",
                joinedDays = 612,
                chickenCount = 1286,
                topicCount = 47,
                commentCount = 1302,
                followed = false,
                selectedTab = SpaceTab.GENERAL,
            )

        val SELF =
            OTHER.copy(
                uid = 21_736,
                isSelf = true,
                name = "homelab_er",
                level = 1,
                selectedTab = SpaceTab.TOPICS,
            )

        val TOPICS =
            listOf(
                SpacePost(1, "四盘位 NAS 选 ZFS 还是 Btrfs？", "技术", "tech", null, 12, 843, "3天前"),
                SpacePost(2, "签到鸡腿是随机的吗？连续七天都是 6 个", "日常", "daily", null, 18, 976, "上周"),
                SpacePost(3, "【出】甲骨文 ARM 一台求接手", "交易", "trade", null, 6, 412, "5月2日", PostPermission(3)),
            )
    }
}
