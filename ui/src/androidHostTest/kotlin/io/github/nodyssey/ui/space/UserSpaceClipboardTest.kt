package io.github.nodyssey.ui.space

import android.content.ClipboardManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Copies the underlying identity, including when the name is ellipsized or the profile changes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class UserSpaceClipboardTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var state by mutableStateOf(
        UserSpaceUiState(
            uid = 62897,
            isSelf = false,
            isLoadingProfile = false,
            name = "midnight86_with_a_very_long_username",
            level = 3,
        ),
    )

    private fun setScreen() {
        composeRule.setContent {
            PlazaTheme {
                UserSpaceScreen(
                    state = state,
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
        }
    }

    private fun assertClipboard(expected: String) {
        composeRule.runOnIdle {
            val clipboard = ApplicationProvider.getApplicationContext<android.content.Context>()
                .getSystemService(ClipboardManager::class.java)
            assertEquals(expected, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        }
    }

    @Test
    fun `tapping an ellipsized username copies the full name without the level`() {
        setScreen()

        composeRule.onNodeWithText(state.name).performClick()

        assertClipboard("midnight86_with_a_very_long_username")
    }

    @Test
    fun `long pressing UID copies only the numeric identifier`() {
        setScreen()

        composeRule.onNodeWithText("UID 62897").performTouchInput { longClick() }

        assertClipboard("62897")
    }

    @Test
    fun `copy actions use the refreshed profile instead of the previous identity`() {
        setScreen()
        composeRule.onNodeWithText(state.name).performClick()
        assertClipboard("midnight86_with_a_very_long_username")

        composeRule.runOnIdle { state = state.copy(uid = 5_000_000_001, name = "updated_user") }

        composeRule.onNodeWithText("updated_user").performTouchInput { longClick() }
        assertClipboard("updated_user")
        composeRule.onNodeWithText("UID 5000000001").performClick()
        assertClipboard("5000000001")
    }
}
