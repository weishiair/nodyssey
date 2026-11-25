package io.github.nodyssey.ui.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CommunityToolsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `available calculator opens from the row between mail and about`() {
        var available by mutableStateOf(false)
        var opened = 0
        composeRule.setContent {
            PlazaTheme {
                CommunityToolsScreen(
                    onBack = {},
                    onAward = {},
                    onProviders = {},
                    onFriends = {},
                    onLucky = {},
                    onInvite = {},
                    onRuling = {},
                    onReportCompare = {},
                    onAboutCommunity = {},
                    onVpsCalculator = if (available) ({ opened++ }) else null,
                )
            }
        }

        val mail = "NodeSeek 邮箱"
        val calculator = "剩余价值计算器"
        val about = "关于 · 社区"
        composeRule.onNodeWithText(calculator).assertDoesNotExist()

        composeRule.runOnIdle { available = true }
        val rows = composeRule.onAllNodes(hasText(mail) or hasText(calculator) or hasText(about))
        rows[0].assertTextContains(mail)
        rows[1].assertTextContains(calculator).performScrollTo().performClick()
        rows[2].assertTextContains(about)
        assertEquals(1, opened)
    }
}
