package io.github.nodyssey.ui.settings

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
class OpenSourceLicensesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the app opens its license and each dependency opens its own source`() {
        val openedUris = mutableListOf<String>()
        composeRule.setContent {
            PlazaTheme {
                OpenSourceLicensesScreen(onBack = {}, onOpenUri = { openedUris.add(it) })
            }
        }

        composeRule.onNodeWithText("Nodyssey").performScrollTo().performClick()
        composeRule.onNodeWithText("OkHttp").performScrollTo().performClick()
        composeRule.onNodeWithText("Ksoup").performScrollTo().performClick()

        assertEquals(
            listOf(AppLinks.LICENSE, "https://github.com/square/okhttp", "https://github.com/fleeksoft/ksoup"),
            openedUris,
        )
    }
}
