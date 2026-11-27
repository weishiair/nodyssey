package io.github.nodyssey.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.nodyssey.ui.account.AccountSettingsScreen
import io.github.nodyssey.ui.account.AccountSettingsUiState
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class AccountSettingsScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @Composable
    private fun Screen(darkTheme: Boolean, blockedCount: Int?) {
        PlazaTheme(darkTheme = darkTheme) {
            AccountSettingsScreen(
                state = AccountSettingsUiState(blockedCount = blockedCount),
                onBack = {},
                onOpenProfileFields = {},
                onOpenSecurity = {},
                onOpenContact = {},
                onOpenBlockList = {},
                onOpenPreferences = {},
                onSignOut = {},
            )
        }
    }

    @Test
    fun `account settings in light`() {
        composeRule.setContent { Screen(darkTheme = false, blockedCount = 3) }

        composeRule.onRoot().captureRender("account-settings-light")
    }

    @Test
    fun `account settings in dark`() {
        composeRule.setContent { Screen(darkTheme = true, blockedCount = 3) }

        composeRule.onRoot().captureRender("account-settings-dark")
    }

    @Test
    fun `account settings with unknown blocked count`() {
        composeRule.setContent { Screen(darkTheme = false, blockedCount = null) }

        composeRule.onRoot().captureRender("account-settings-unknown-blocked-count")
    }
}
