package io.github.nodyssey.ui.tools

import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.nodyssey.data.InviteCode
import io.github.nodyssey.data.InviteCodePage
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InviteRecoveryScreenTest {
    @get:Rule val compose = createComposeRule()

    // Bug: successful pricing hid the retry action for a failed code list, stranding the user.
    @Test
    fun `a failed code list can be recovered while the prices remain available`() {
        val repository = FakeInviteRepository(codesPage = Result.failure(SiteException(SiteError.Network)))
        lateinit var viewModel: InviteViewModel
        compose.setContent {
            viewModel = remember { InviteViewModel(FakeProfileRepository(chicken = 1_165, stardust = 1), repository) }
            PlazaTheme {
                InviteRoute(viewModel = viewModel, onBack = {}, onBuyOnSite = {})
            }
        }
        val code = InviteCode("RECOVERED", applied = false, createdAtMillis = null)
        compose.runOnIdle {
            assertNull(viewModel.uiState.value.error)
            assertEquals(SiteError.Network, viewModel.uiState.value.codesError)
            repository.codesPage = Result.success(InviteCodePage(listOf(code), total = 1))
        }

        compose.onNodeWithText("重试").performScrollTo().performClick()

        compose.runOnIdle {
            assertNull(viewModel.uiState.value.codesError)
            assertEquals(listOf(code), viewModel.uiState.value.codes)
            assertEquals(0, repository.buys)
        }
    }
}
