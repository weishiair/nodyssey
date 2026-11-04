package io.github.nodyssey.render

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import io.github.nodyssey.data.attendance.AutoAttendanceConfig
import io.github.nodyssey.data.attendance.AutoAttendanceState
import io.github.nodyssey.data.session.AccountsState
import io.github.nodyssey.data.session.SavedAccount
import io.github.nodyssey.ui.account.AccountsScreen
import io.github.nodyssey.ui.login.SignInScreen
import io.github.nodyssey.ui.login.SignInUiState
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
class AccountSignInRenderTest {
    @get:Rule val compose = createComposeRule()

    @Before fun rendering() = assumeRendering()

    private fun login(dark: Boolean) {
        compose.setContent {
            PlazaTheme(darkTheme = dark) {
                SignInScreen(
                    state = SignInUiState(),
                    accountState = rememberTextFieldState(),
                    passwordState = rememberTextFieldState(),
                    snackbarHostState = remember { SnackbarHostState() },
                    onClose = {}, onSubmit = {}, onOpenSiteSignInPage = {}, onUseWebSignIn = {}, onOneTapSignIn = {},
                    addingAccount = true,
                    turnstile = {
                        Box(Modifier.fillMaxWidth().height(65.dp), contentAlignment = Alignment.Center) {
                            Text("Cloudflare · 人机验证")
                        }
                    },
                )
            }
        }
        compose.onRoot().captureRender(if (dark) "account-add-dark" else "account-add-light")
    }

    @Test fun `add light`() = login(false)

    @Test fun `add dark`() = login(true)

    @Test fun `accounts light`() {
        compose.setContent {
            PlazaTheme {
                AccountsScreen(
                    state = AccountsState(
                        ready = true,
                        activeUid = 1,
                        accounts = listOf(
                            SavedAccount(1, "NodeSeeker", "", attendance = AutoAttendanceConfig(enabled = true)),
                            SavedAccount(2, "Another account", "", needsSignIn = true),
                        ),
                    ),
                    hasOfflineDownloads = false,
                    onBack = {}, onAdd = {}, onSwitch = {}, onRemove = {}, onCancelAdd = {}, onFailureShown = {},
                    attendanceStates = mapOf(1L to AutoAttendanceState(completed = true)),
                )
            }
        }
        compose.onRoot().captureRender("accounts-light")
    }
}
