package io.github.nodyssey.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.nodyssey.ui.navigation.FloatingGlassTestContent
import io.github.nodyssey.ui.navigation.TopLevelDestination
import io.github.nodyssey.ui.profile.ProfileDestinations
import io.github.nodyssey.ui.profile.ProfileScreen
import io.github.nodyssey.ui.profile.ProfileUiState
import io.github.plaza.core.net.SiteError
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Review images only; no screenshot baselines or assertions about appearance. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class SnackbarRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @Composable
    private fun NetworkFailure(error: SiteError = SiteError.Network) {
        ProfileScreen(
            state = ProfileUiState(
                isSignedIn = true,
                displayName = "nodyssey_dev",
                attendanceKnown = true,
                attendanceFailure = error,
            ),
            destinations = ProfileDestinations(),
            onSignIn = {},
            onRetry = {},
            onOpenWebsite = {},
            onVerify = {},
            onAttendance = {},
            onAttendanceBoard = {},
        )
    }

    @Test
    fun `network failure above the glass navigation in light`() {
        composeRule.setContent {
            FloatingGlassTestContent(TopLevelDestination.PROFILE) { NetworkFailure() }
        }
        composeRule.onRoot().captureRender("snackbar-glass-light")
    }

    @Test
    fun `network failure above the glass navigation in dark`() {
        composeRule.setContent {
            FloatingGlassTestContent(TopLevelDestination.PROFILE, darkTheme = true) { NetworkFailure() }
        }
        composeRule.onRoot().captureRender("snackbar-glass-dark")
    }

    @Test
    fun `verification above the glass navigation`() {
        composeRule.setContent {
            FloatingGlassTestContent(TopLevelDestination.PROFILE) {
                NetworkFailure(SiteError.Cloudflare("https://www.nodeseek.com/page-2"))
            }
        }
        composeRule.onRoot().captureRender("snackbar-glass-challenge")
    }

    @Test
    fun `network failure with the original theme`() {
        composeRule.setContent { PlazaTheme { NetworkFailure() } }
        composeRule.onRoot().captureRender("snackbar-standard-light")
    }
}
