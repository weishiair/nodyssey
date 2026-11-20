package io.github.nodyssey.render

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.github.nodyssey.ui.account.HighRiskDialog
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.account_confirm_password_action
import io.github.nodyssey.ui.resources.account_confirm_password_body
import io.github.nodyssey.ui.resources.account_confirm_password_title
import io.github.nodyssey.ui.resources.imagehost_delete_action
import io.github.nodyssey.ui.resources.imagehost_delete_body
import io.github.nodyssey.ui.resources.imagehost_delete_title
import io.github.plaza.designsys.theme.PlazaTheme
import org.jetbrains.compose.resources.stringResource
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [HighRiskDialog] in both its tones, before it moves off a hand-written `AlertDialog` onto
 * `AdaptiveAlertDialog`: the migration is supposed to change which widget the composable calls,
 * not what the dialog draws.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class HighRiskDialogRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @Test
    fun `the destructive confirmation`() {
        composeRule.setContent {
            PlazaTheme(darkTheme = false) {
                HighRiskDialog(
                    icon = Icons.Default.Delete,
                    title = stringResource(Res.string.imagehost_delete_title),
                    body = stringResource(Res.string.imagehost_delete_body, "shot.png"),
                    confirmLabel = stringResource(Res.string.imagehost_delete_action),
                    onConfirm = {},
                    onDismiss = {},
                    destructive = true,
                )
            }
        }

        composeRule.onNode(isDialog()).captureRender("high-risk-dialog-destructive")
    }

    @Test
    fun `the plain confirmation`() {
        composeRule.setContent {
            PlazaTheme(darkTheme = false) {
                HighRiskDialog(
                    icon = Icons.Default.Lock,
                    title = stringResource(Res.string.account_confirm_password_title),
                    body = stringResource(Res.string.account_confirm_password_body),
                    confirmLabel = stringResource(Res.string.account_confirm_password_action),
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNode(isDialog()).captureRender("high-risk-dialog")
    }
}
