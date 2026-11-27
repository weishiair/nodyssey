package io.github.nodyssey.ui.account

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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class AccountSettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `account rows open their own destination or request sign out`() {
        val invoked = mutableListOf<String>()
        composeRule.setContent {
            PlazaTheme {
                AccountSettingsScreen(
                    state = AccountSettingsUiState(blockedCount = null),
                    onBack = { invoked.add("back") },
                    onOpenProfileFields = { invoked.add("profile") },
                    onOpenSecurity = { invoked.add("security") },
                    onOpenContact = { invoked.add("contact") },
                    onOpenBlockList = { invoked.add("blocked-users") },
                    onOpenPreferences = { invoked.add("preferences") },
                    onSignOut = { invoked.add("sign-out") },
                )
            }
        }

        composeRule.onNodeWithText("个人信息").performScrollTo().performClick()
        composeRule.onNodeWithText("安全").performScrollTo().performClick()
        composeRule.onNodeWithText("联系方式").performScrollTo().performClick()
        composeRule.onNodeWithText("屏蔽用户").performScrollTo().performClick()
        composeRule.onNodeWithText("偏好与首页版块").performScrollTo().performClick()
        composeRule.onNodeWithText("退出登录").performScrollTo().performClick()

        assertEquals(listOf("profile", "security", "contact", "blocked-users", "preferences", "sign-out"), invoked)
    }
}
