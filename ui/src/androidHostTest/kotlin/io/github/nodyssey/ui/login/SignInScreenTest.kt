package io.github.nodyssey.ui.login

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What h1's form must not do: offer a 登录 that cannot work. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class SignInScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(state: SignInUiState) {
        composeRule.setContent {
            PlazaTheme {
                SignInScreen(
                    state = state,
                    accountState = rememberTextFieldState(state.account),
                    passwordState = rememberTextFieldState("x".repeat(state.passwordLength)),
                    snackbarHostState = remember { SnackbarHostState() },
                    onClose = {},
                    onSubmit = {},
                    onOpenSiteSignInPage = {},
                    onUseWebSignIn = {},
                    onOneTapSignIn = {},
                )
            }
        }
    }

    @Test
    fun `a filled form still waiting on the widget cannot be submitted`() {
        setContent(SignInUiState(account = "nssk", passwordLength = 8))

        composeRule.onNodeWithText("登录", substring = false).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `a token in hand arms the button`() {
        setContent(
            SignInUiState(
                account = "nssk",
                passwordLength = 8,
                verification = VerificationState.Passed("token"),
            ),
        )

        composeRule.onNodeWithText("登录", substring = false).performScrollTo().assertIsEnabled()
    }

    @Test
    fun `a signed out reader can open the retained native failure report`() {
        val report = "Nodyssey login diagnostics\nError[0]: NSURLErrorDomain (-1200)"
        setContent(SignInUiState(failureReport = report))

        composeRule.onNodeWithText("查看登录诊断").performScrollTo().performClick()
        composeRule.onNodeWithText(report).assertIsDisplayed()
        composeRule.onNodeWithText("复制结果").assertIsEnabled()
        composeRule.onNodeWithText("关闭").performClick()
        composeRule.onNodeWithText(report).assertDoesNotExist()
    }
}
