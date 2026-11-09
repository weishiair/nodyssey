package io.github.nodyssey.ui.mail

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.nodyssey.data.mail.MailMessage
import io.github.nodyssey.data.mail.MailPreview
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class MailCodeCopyTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `copying from the inbox keeps the leading zero and never opens the mail`() {
        val mail = MailMessage(21, 7, subject = "Verification code", bodyText = "Your verification code: 016845")
        var opened = false
        composeRule.setContent {
            PlazaTheme {
                MailMessageItem(mail, MailPreview.from(mail), first = true, last = true, nowMillis = 0, onOpen = { opened = true })
            }
        }
        composeRule.onNodeWithText("016845").performClick()
        composeRule.waitForIdle()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals("016845", clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString())
        assertFalse(opened)
    }
}
