package io.github.nodyssey.ui.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.nodyssey.data.session.AccountFailure
import io.github.nodyssey.data.session.AccountsState
import io.github.nodyssey.data.session.SavedAccount
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class AccountsScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `only the other account can switch and failure permits retry`() {
        var state by mutableStateOf(
            AccountsState(activeUid = 1, accounts = listOf(SavedAccount(1, "First", ""), SavedAccount(2, "Second", ""))),
        )
        val switches = mutableListOf<Long>()
        rule.setContent {
            PlazaTheme {
                AccountsScreen(state, false, {}, {}, { switches.add(it) }, {}, {}, {})
            }
        }
        val clickable = hasClickAction()
        rule.onNode(hasText("First") and clickable).assertDoesNotExist()
        rule.onNode(hasText("First") and hasAnyAncestor(clickable), useUnmergedTree = true).assertDoesNotExist()
        val target = rule.onNodeWithText("Second")
        target.performClick()
        val progress = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
        rule.onNode(progress, useUnmergedTree = true).assertExists()
        target.performClick()
        rule.runOnIdle {
            assertEquals(listOf(2L), switches)
            state = state.copy(busy = true)
        }
        rule.runOnIdle { state = state.copy(busy = false, failure = AccountFailure.NETWORK) }
        rule.onNode(progress, useUnmergedTree = true).assertDoesNotExist()
        target.performClick()
        rule.runOnIdle { assertEquals(listOf(2L, 2L), switches) }
    }
}
