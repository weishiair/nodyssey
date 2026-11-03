package io.github.nodyssey.ui.messages

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.nodyssey.data.MessageFailure
import io.github.nodyssey.data.MessageRecovery
import io.github.plaza.core.net.SiteError
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A challenge/login recovery must open its recovery flow rather than repeat the refused send. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class MessageRecoveryTest {
    @get:Rule
    val composeRule = createComposeRule()
    private var retries = 0
    private var signIns = 0
    private var verified: String? = null

    @Test
    fun `a failed send opens the challenged URL without sending again`() {
        val url = "https://www.nodeseek.com/api/notification/message/send"
        setScreen(failure = MessageFailure(MessageRecovery.VERIFY, verificationUrl = url))
        composeRule.onNodeWithText("去验证").performClick()
        assertEquals(url, verified)
        assertEquals(0, retries)
    }

    @Test
    fun `an expired send session opens sign in without sending again`() {
        setScreen(failure = MessageFailure(MessageRecovery.SIGN_IN))
        composeRule.onNodeWithText("登录").performClick()
        assertEquals(1, signIns)
        assertEquals(0, retries)
    }

    @Test
    fun `a paused history update opens its own challenged URL`() {
        val url = "https://www.nodeseek.com/api/notification/message/with/42"
        setScreen(syncError = SiteError.Cloudflare(url))
        composeRule.onNodeWithText("去验证").performClick()
        assertEquals(url, verified)
        assertEquals(0, retries)
    }

    private fun setScreen(failure: MessageFailure? = null, syncError: SiteError? = null) {
        composeRule.setContent {
            PlazaTheme {
                MessageThreadScreen(
                    state = MessageThreadUiState(
                        uid = 42,
                        userName = "peer",
                        messages = listOf(MessageBubble("1", true, "消息", false, null, null, if (failure == null) SendStatus.SENT else SendStatus.FAILED, failure = failure)),
                        syncError = syncError,
                    ),
                    draftState = TextFieldState(),
                    onBack = {},
                    onSignIn = { signIns++ },
                    onVerify = { verified = it },
                    onOpenBrowser = {},
                    onOpenSpace = {},
                    onRetryLoad = { retries++ },
                    onSend = {},
                    onRetrySend = { retries++ },
                    onQuote = {},
                    onRemoveQuote = {},
                    onPickImages = {},
                    onRemoveAttachment = {},
                    onRetryAttachment = {},
                    onToolbarChange = {},
                    onToolbarReset = {},
                )
            }
        }
    }
}
