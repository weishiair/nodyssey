package io.github.nodyssey.ui.settings

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import io.github.plaza.designsys.settings.SettingsItem
import io.github.plaza.designsys.settings.SettingsPage
import io.github.plaza.designsys.settings.SettingsSection
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class SettingsPageInfoRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `an info row reports its value without offering a tap`() {
        composeRule.setContent {
            PlazaTheme {
                SettingsPageScreen(
                    page =
                    SettingsPage(
                        title = "Check-in",
                        sections =
                        listOf(
                            SettingsSection(
                                key = "status",
                                items =
                                listOf(
                                    SettingsItem.Info(
                                        key = "check-in-status",
                                        title = "Status",
                                        value = "Already checked in",
                                        testTag = "status-row",
                                    ),
                                    SettingsItem.Link(
                                        key = "check-in-history",
                                        title = "History",
                                        onClick = {},
                                        testTag = "history-row",
                                    ),
                                ),
                            ),
                        ),
                    ),
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("status-row").assert(hasClickAction().not())
        composeRule.onNodeWithText("Already checked in").assertIsDisplayed()
        composeRule.onNodeWithTag("history-row").assertHasClickAction()
    }
}
