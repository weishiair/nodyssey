package io.github.nodyssey.render

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.nodyssey.ui.account.PreferencesScreen
import io.github.nodyssey.ui.account.PreferencesUiState
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
class PreferencesScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @Test
    fun `account preferences in light`() = render("account-preferences-light", darkTheme = false)

    @Test
    fun `account preferences in dark`() = render("account-preferences-dark", darkTheme = true)

    private fun render(name: String, darkTheme: Boolean) {
        composeRule.setContent {
            PlazaTheme(darkTheme = darkTheme) {
                PreferencesScreen(
                    state = PreferencesUiState(holidayTheme = true, hiddenBoards = setOf("life", "photo-share")),
                    snackbarHostState = remember { SnackbarHostState() },
                    onBack = {},
                    onHolidayThemeChange = {},
                    onBoardHiddenChange = { _, _ -> },
                )
            }
        }
        composeRule.onRoot().captureRender(name)
    }
}
