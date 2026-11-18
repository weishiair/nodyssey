package io.github.nodyssey.ui.messages

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import io.github.nodyssey.core.NodeSeekSite
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 复制 on a bubble (7f).
 *
 * The bug this exists for: the only way to copy a message used to be the text selection the Markdown
 * renderer brought with it, so whether a message could be copied at all was decided by the *sender's*
 * MD switch — mine were selectable, a plain-text one from the other side was not. Both bubbles are
 * therefore asserted, and the plain one is the regression.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class MessageBubbleActionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setScreen(
        plain: String = "改名的事我问过管理",
        markdown: String = "那大概什么时候",
        onLinkClick: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            PlazaTheme {
                MessageThreadScreen(
                    state =
                    MessageThreadUiState(
                        uid = 4471,
                        userName = "iwil",
                        nowMillis = NOW,
                        messages =
                        listOf(
                            MessageBubble(
                                id = "1",
                                isMine = false,
                                content = plain,
                                // The other side's MD switch was off, which is what used to leave
                                // this bubble with no way to copy it.
                                isMarkdown = false,
                                sentAtMillis = NOW - 60_000L,
                                sentAtText = null,
                                status = SendStatus.SENT,
                            ),
                            MessageBubble(
                                id = "2",
                                isMine = true,
                                content = markdown,
                                isMarkdown = true,
                                sentAtMillis = NOW,
                                sentAtText = null,
                                status = SendStatus.SENT,
                            ),
                        ),
                    ),
                    draftState = TextFieldState(),
                    onBack = {},
                    onSignIn = {},
                    onVerify = {},
                    onOpenBrowser = {},
                    onLinkClick = onLinkClick,
                    onOpenSpace = {},
                    onRetryLoad = {},
                    onSend = {},
                    onRetrySend = {},
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

    @Test
    fun `a plain-text message from the other side can be copied`() {
        setScreen()

        composeRule.onNodeWithText("改名的事我问过管理").performTouchInput { longClick() }
        composeRule.onNodeWithText("复制").assertIsDisplayed()
        composeRule.onNodeWithText("复制").performClick()

        assertEquals("改名的事我问过管理", clipboardText())
    }

    /** The Markdown side goes through the same menu, so the two bubbles behave alike. */
    @Test
    fun `a markdown message of mine can be copied`() {
        setScreen()

        composeRule.onNodeWithText("那大概什么时候").performTouchInput { longClick() }
        composeRule.onNodeWithText("复制").performClick()

        assertEquals("那大概什么时候", clipboardText())
    }

    @Test
    fun `a pasted post URL and a relative Markdown link resolve to the native post and page`() {
        val post = "https://nodeseek.com/post-42-2"
        val opened = mutableListOf<NodeSeekSite.InternalRoute?>()
        setScreen(plain = post, markdown = "[另一篇帖子](/post-96-3)") { opened += NodeSeekSite.parseInternalRoute(it) }

        composeRule.onNodeWithText(post).performTouchInput { click(center) }
        composeRule.onNodeWithText("另一篇帖子").performTouchInput { click(center) }
        assertEquals(listOf(NodeSeekSite.InternalRoute.Post(42, 2), NodeSeekSite.InternalRoute.Post(96, 3)), opened)
    }

    @Test
    fun `long pressing a pasted URL copies the message without opening the link`() {
        val url = "https://example.com/guide"
        val opened = mutableListOf<String>()
        setScreen(plain = url, onLinkClick = { opened += it })

        composeRule.onNodeWithText(url).performTouchInput { longClick(center) }
        composeRule.onNodeWithText("复制").performClick()

        assertEquals(url, clipboardText())
        assertEquals(emptyList<String>(), opened)

        composeRule.onNodeWithText(url).performTouchInput { click(center) }
        assertEquals(listOf(url), opened)
    }

    private fun clipboardText(): String? {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
        return clip?.getItemAt(0)?.text?.toString()
    }

    private companion object {
        const val NOW = 1_785_000_000_000L
    }
}
