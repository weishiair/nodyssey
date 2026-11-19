package io.github.nodyssey.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import io.github.plaza.designsys.theme.PlazaTheme
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
        composeRule.onNodeWithText("正在检查今日签到…").performTouchInput { click() }
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
        composeRule.onNodeWithText("签到中…").performTouchInput { click() }
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
}
