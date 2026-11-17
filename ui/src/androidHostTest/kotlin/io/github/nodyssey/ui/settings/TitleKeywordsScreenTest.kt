package io.github.nodyssey.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import io.github.nodyssey.data.TitleKeywordStore
import io.github.nodyssey.model.TitleKeywordKind
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.keywords_add_action
import io.github.nodyssey.ui.resources.keywords_alert_footer
import io.github.nodyssey.ui.resources.keywords_alert_footer_no_polling
import io.github.nodyssey.ui.resources.keywords_alert_title
import io.github.nodyssey.ui.resources.keywords_block_footer
import io.github.nodyssey.ui.resources.keywords_block_title
import io.github.nodyssey.ui.resources.keywords_empty
import io.github.nodyssey.ui.resources.keywords_remove
import io.github.plaza.designsys.theme.PlazaTheme
import org.jetbrains.compose.resources.stringResource
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
class TitleKeywordsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `add button, IME done and single-item delete manage keywords cleanly without touching siblings`() {
        var addLabel = ""
        var removeAlphaLabel = ""
        var emptyLabel = ""
        val addedWords = mutableListOf<String>()

        composeRule.setContent {
            PlazaTheme {
                addLabel = stringResource(Res.string.keywords_add_action)
                removeAlphaLabel = stringResource(Res.string.keywords_remove, "alpha")
                emptyLabel = stringResource(Res.string.keywords_empty)
                var state by remember { mutableStateOf(TitleKeywordsUiState(kind = TitleKeywordKind.ALERT)) }
                val submit = {
                    val normalized = TitleKeywordStore.normalizeKeyword(state.input)
                    if (normalized != null) {
                        addedWords += normalized
                        val updated = if (normalized in state.keywords) state.keywords else state.keywords + normalized
                        state = state.copy(keywords = updated, input = "")
                    }
                }
                TitleKeywordsScreen(
                    state = state,
                    onBack = {},
                    onInputChange = {
                        state = state.copy(input = it.take(TitleKeywordStore.MAX_KEYWORD_LENGTH))
                    },
                    onAdd = submit,
                    onRemove = { target ->
                        state = state.copy(keywords = state.keywords - target)
                    },
                    backgroundPollingSupported = false,
                )
            }
        }

        composeRule.onNodeWithText(emptyLabel).assertExists()
        composeRule.onNodeWithText(addLabel).assertIsNotEnabled()

        // Blank spaces keep Add disabled and IME Done does not submit.
        composeRule.onNode(hasSetTextAction()).performTextReplacement("   ")
        composeRule.onNodeWithText(addLabel).assertIsNotEnabled()
        composeRule.onNode(hasSetTextAction()).performImeAction()
        assertEquals(emptyList<String>(), addedWords)

        // Submit first word via Add button.
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Alpha")
        composeRule.onNodeWithText(addLabel).assertIsEnabled().performClick()
        composeRule.onNodeWithText("alpha").assertExists()

        // Submit second word via keyboard IME Done.
        composeRule.onNode(hasSetTextAction()).performTextReplacement("beta")
        composeRule.onNode(hasSetTextAction()).performImeAction()
        composeRule.onNodeWithText("beta").assertExists()

        // Delete only "alpha", keeping "beta".
        composeRule.onNodeWithContentDescription(removeAlphaLabel).performClick()
        composeRule.onNodeWithText("alpha").assertDoesNotExist()
        composeRule.onNodeWithText("beta").assertExists()
        assertEquals(listOf("alpha", "beta"), addedWords)
    }

    @Test
    fun `alert footer omits background polling promise when polling is unsupported`() {
        var alertTitle = ""
        var pollingFooter = ""
        var noPollingFooter = ""

        composeRule.setContent {
            PlazaTheme {
                alertTitle = stringResource(Res.string.keywords_alert_title)
                pollingFooter = stringResource(Res.string.keywords_alert_footer)
                noPollingFooter = stringResource(Res.string.keywords_alert_footer_no_polling)
                TitleKeywordsScreen(
                    state = TitleKeywordsUiState(kind = TitleKeywordKind.ALERT, keywords = listOf("racknerd")),
                    onBack = {},
                    onInputChange = {},
                    onAdd = {},
                    onRemove = {},
                    backgroundPollingSupported = false,
                )
            }
        }

        composeRule.onNodeWithText(alertTitle).assertExists()
        composeRule.onNodeWithText(noPollingFooter).assertExists()
        composeRule.onNodeWithText(pollingFooter).assertDoesNotExist()
    }

    @Test
    fun `block mode preserves block title and block footer regardless of polling support`() {
        var blockTitle = ""
        var blockFooter = ""

        composeRule.setContent {
            PlazaTheme {
                blockTitle = stringResource(Res.string.keywords_block_title)
                blockFooter = stringResource(Res.string.keywords_block_footer)
                TitleKeywordsScreen(
                    state = TitleKeywordsUiState(kind = TitleKeywordKind.BLOCK, keywords = listOf("ad")),
                    onBack = {},
                    onInputChange = {},
                    onAdd = {},
                    onRemove = {},
                    backgroundPollingSupported = false,
                )
            }
        }

        composeRule.onNodeWithText(blockTitle).assertExists()
        composeRule.onNodeWithText(blockFooter).assertExists()
    }
}
