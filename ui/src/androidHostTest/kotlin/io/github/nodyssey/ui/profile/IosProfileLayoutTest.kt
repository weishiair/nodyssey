package io.github.nodyssey.ui.profile

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class IosProfileLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `clicking switch account does not trigger personal space navigation`() {
        var spaceClicks = 0
        var switchClicks = 0
        composeRule.setContent {
            PlazaTheme {
                IosAccountCard(
                    state = ProfileUiState(
                        isSignedIn = true,
                        uid = 88423,
                        displayName = "nodyssey_dev",
                        chickenCount = 1284,
                        starCount = 356,
                        level = "Lv 3",
                        rank = 3,
                    ),
                    onOpenSpace = { spaceClicks++ },
                    onCredit = {},
                    onStardust = {},
                    onAssets = {},
                    onSwitchAccount = { switchClicks++ },
                )
            }
        }

        composeRule.onNodeWithText("切换").performClick()
        assertEquals(1, switchClicks)
        assertEquals(0, spaceClicks)

        composeRule.onNodeWithText("nodyssey_dev").performClick()
        assertEquals(1, spaceClicks)
    }

    @Test
    fun `null balances stay unknown instead of zero and null accounts hides switch button`() {
        composeRule.setContent {
            PlazaTheme {
                IosAccountCard(
                    state = ProfileUiState(
                        isSignedIn = true,
                        uid = 88423,
                        displayName = "nodyssey_dev",
                        registeredYear = 2024,
                        registeredMonth = null,
                        chickenCount = null,
                        starCount = null,
                        level = null,
                    ),
                    onOpenSpace = {},
                    onCredit = {},
                    onStardust = {},
                    onAssets = {},
                    onSwitchAccount = null,
                )
            }
        }

        composeRule.onNodeWithText("UID 88423").assertIsDisplayed()
        composeRule.onNodeWithText("Lv —").assertIsDisplayed()
        composeRule.onNodeWithText("0").assertDoesNotExist()
        composeRule.onNodeWithText("切换").assertDoesNotExist()
    }

    @Test
    fun `large balances remain clickable at 2x font scale`() {
        var creditClicks = 0
        var starClicks = 0
        var assetClicks = 0
        composeRule.setContent {
            PlazaTheme {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale = 2.0f)) {
                    IosAccountCard(
                        state = ProfileUiState(
                            isSignedIn = true,
                            uid = 88423,
                            displayName = "very_long_member_name_for_accessibility_wrapping_check",
                            chickenCount = 12345678,
                            starCount = 9876543,
                            level = "Lv 10",
                            rank = 10,
                        ),
                        onOpenSpace = {},
                        onCredit = { creditClicks++ },
                        onStardust = { starClicks++ },
                        onAssets = { assetClicks++ },
                        onSwitchAccount = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("12345678").performClick()
        composeRule.onNodeWithText("9876543").performClick()
        composeRule.onNodeWithText("Lv 10").performClick()
        assertEquals(1, creditClicks)
        assertEquals(1, starClicks)
        assertEquals(1, assetClicks)
    }

    @Test
    fun `attendance card blocks duplicate check-in while unknown or signing in and keeps receipt on background recheck`() {
        var checkInClicks = 0
        var boardClicks = 0
        var state by mutableStateOf(
            ProfileUiState(
                isSignedIn = true,
                displayName = "nodyssey_dev",
                isCheckingAttendance = true,
                attendanceKnown = false,
            ),
        )
        composeRule.setContent {
            PlazaTheme {
                IosAttendanceCard(
                    state = state,
                    onAttendance = { checkInClicks++ },
                    onAttendanceBoard = { boardClicks++ },
                )
            }
        }

        composeRule.onNodeWithText("正在检查今日签到…").assertIsNotEnabled()
        composeRule.onNodeWithText("签到榜").performClick()
        assertEquals(0, checkInClicks)
        assertEquals(1, boardClicks)

        state =
            ProfileUiState(
                isSignedIn = true,
                displayName = "nodyssey_dev",
                isSigningIn = true,
            )
        composeRule.waitForIdle()
        composeRule.onNodeWithText("签到中…").assertIsNotEnabled()
        assertEquals(0, checkInClicks)

        state =
            ProfileUiState(
                isSignedIn = true,
                displayName = "nodyssey_dev",
                isCheckingAttendance = true,
                attendanceKnown = true,
                hasSignedInToday = true,
                attendanceGain = 7,
            )
        composeRule.waitForIdle()
        composeRule.onNodeWithText("今日已签 +7 鸡腿").performClick()
        assertEquals(2, boardClicks)
    }

    @Test
    fun `signed-out iOS profile hides null accounts action and retains guest tools with unread hints`() {
        val opened = mutableListOf<String>()
        composeRule.setContent {
            PlazaTheme {
                IosSignedOutProfile(
                    onSignIn = { opened += "signIn" },
                    destinations = ProfileDestinations(
                        accounts = null,
                        mail = { opened += "mail" },
                        lotteryAssistant = { opened += "lottery" },
                        award = { opened += "award" },
                        lucky = { opened += "lucky" },
                        ruling = { opened += "ruling" },
                        providers = { opened += "providers" },
                        tools = { opened += "tools" },
                    ),
                    hasUnreadLotteryResults = true,
                    hasUnreadMail = true,
                    scaffoldPadding = PaddingValues(0.dp),
                )
            }
        }

        composeRule.onNodeWithText("账号管理").assertDoesNotExist()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("NodeSeek 邮箱"))
        composeRule.onNodeWithContentDescription("有未读邮件", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("抽奖助手"))
        composeRule.onNodeWithContentDescription("有未读抽奖结果", useUnmergedTree = true).assertIsDisplayed()

        listOf("NodeSeek 邮箱", "抽奖助手", "推荐阅读", "幸运抽奖", "管理记录", "合作商家", "社区工具").forEach { label ->
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(label))
            composeRule.onNodeWithText(label).performClick()
        }
        assertEquals(listOf("mail", "lottery", "award", "lucky", "ruling", "providers", "tools"), opened)
    }

    @Test
    @Config(qualifiers = "w1280dp-h960dp-land")
    fun `signed-in iOS wide layout keeps all grouped entries and unread indicators accessible`() {
        val opened = mutableListOf<String>()
        composeRule.setContent {
            PlazaTheme {
                IosProfileSignedInContent(
                    state = SIGNED_IN_STATE,
                    destinations = recordingDestinations(opened),
                    hasUnreadLotteryResults = true,
                    hasUnreadMail = true,
                    onAttendance = {},
                    onAttendanceBoard = {},
                    scaffoldPadding = PaddingValues(0.dp),
                )
            }
        }

        val rightPane = composeRule.onNode(hasScrollAction() and hasAnyDescendant(hasText("收藏")))
        rightPane.performScrollToNode(hasText("NodeSeek 邮箱"))
        composeRule.onNodeWithContentDescription("有未读邮件", useUnmergedTree = true).assertIsDisplayed()
        rightPane.performScrollToNode(hasText("抽奖助手"))
        composeRule.onNodeWithContentDescription("有未读抽奖结果", useUnmergedTree = true).assertIsDisplayed()
        SIGNED_IN_ENTRY_LABELS.forEach { label ->
            rightPane.performScrollToNode(hasText(label))
            composeRule.onNodeWithText(label).performClick()
        }
        assertEquals(20, opened.size)
        assertFalse(opened.distinct().size < 20)
    }

    @Test
    fun `signed-in iOS single-column layout scrolls to every grouped entry without missing destinations`() {
        val opened = mutableListOf<String>()
        composeRule.setContent {
            PlazaTheme {
                IosProfileSignedInContent(
                    state = SIGNED_IN_STATE,
                    destinations = recordingDestinations(opened),
                    hasUnreadLotteryResults = true,
                    hasUnreadMail = true,
                    onAttendance = {},
                    onAttendanceBoard = {},
                    scaffoldPadding = PaddingValues(0.dp),
                )
            }
        }

        SIGNED_IN_ENTRY_LABELS.forEach { label ->
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(label))
            composeRule.onNodeWithText(label).performClick()
        }
        assertEquals(20, opened.size)
        assertFalse(opened.distinct().size < 20)
    }

    private companion object {
        val SIGNED_IN_STATE =
            ProfileUiState(
                isSignedIn = true,
                uid = 88423,
                displayName = "nodyssey_dev",
                level = "Lv 3",
                rank = 3,
                chickenCount = 1284,
                starCount = 356,
                attendanceKnown = true,
            )

        val SIGNED_IN_ENTRY_LABELS =
            listOf(
                "收藏",
                "浏览历史",
                "主题帖",
                "我的评论",
                "我的关注",
                "我的粉丝",
                "NodeSeek 邮箱",
                "抽奖助手",
                "推荐阅读",
                "幸运抽奖",
                "管理记录",
                "合作商家",
                "友站",
                "屏蔽",
                "邀请码",
                "关于社区",
                "账号设置",
                "通知提醒",
                "主题外观",
                "关于反馈",
            )

        fun recordingDestinations(opened: MutableList<String>): ProfileDestinations =
            ProfileDestinations(
                collections = { opened += "collections" },
                history = { opened += "history" },
                topics = { opened += "topics" },
                comments = { opened += "comments" },
                following = { opened += "following" },
                followers = { opened += "followers" },
                mail = { opened += "mail" },
                lotteryAssistant = { opened += "lottery" },
                award = { opened += "award" },
                lucky = { opened += "lucky" },
                ruling = { opened += "ruling" },
                providers = { opened += "providers" },
                friends = { opened += "friends" },
                blockList = { opened += "blockList" },
                invite = { opened += "invite" },
                aboutCommunity = { opened += "aboutCommunity" },
                accountSettings = { opened += "accountSettings" },
                notificationSettings = { opened += "notificationSettings" },
                themeSettings = { opened += "themeSettings" },
                about = { opened += "about" },
            )
    }
}
