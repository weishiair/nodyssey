package io.github.nodyssey.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.nodyssey.model.TitleKeywordKind
import io.github.nodyssey.ui.settings.TitleKeywordsScreen
import io.github.nodyssey.ui.settings.TitleKeywordsUiState
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 标题关键词 in both themes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class TitleKeywordsScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @Composable
    private fun Screen(darkTheme: Boolean) {
        PlazaTheme(darkTheme = darkTheme) {
            TitleKeywordsScreen(
                state = STATE,
                onBack = {},
                onInputChange = {},
                onAdd = {},
                onRemove = {},
            )
        }
    }

    @Test
    fun `title keywords in light`() {
        composeRule.setContent { Screen(darkTheme = false) }

        composeRule.onRoot().captureRender("title-keywords-light")
    }

    @Test
    fun `title keywords in dark`() {
        composeRule.setContent { Screen(darkTheme = true) }

        composeRule.onRoot().captureRender("title-keywords-dark")
    }

    private companion object {
        val STATE =
            TitleKeywordsUiState(
                kind = TitleKeywordKind.BLOCK,
                keywords = listOf("拼车", "出鸡", "aff"),
                input = "",
            )
    }
}
