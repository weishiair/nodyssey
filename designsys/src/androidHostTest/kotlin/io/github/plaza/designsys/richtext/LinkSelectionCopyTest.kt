package io.github.plaza.designsys.richtext

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuDropdownProvider
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode
import io.github.plaza.designsys.theme.PlazaTheme
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Link holds must copy the destination; prose selection and short link taps keep their actions. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Avoid Robolectric's unsupported platform Magnifier; the selection and clipboard are unchanged.
@Config(qualifiers = "w360dp-h800dp", sdk = [27])
class LinkSelectionCopyTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val toolbar = RecordingToolbar()
    private val followed = mutableListOf<String>()
    private val clipboard: ClipboardManager
        get() = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(ClipboardManager::class.java)

    private fun setContent(nodes: List<RichNode>) {
        composeRule.setContent {
            PlazaTheme {
                CompositionLocalProvider(
                    LocalTextContextMenuToolbarProvider provides toolbar,
                    LocalTextContextMenuDropdownProvider provides RecordingToolbar(),
                ) {
                    DismissTextSelectionOnTap {
                        RichContent(
                            nodes = nodes,
                            onLinkClick = followed::add,
                            onImageClick = {},
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `a long press on a standalone url can copy the full link without opening it`() {
        setContent(listOf(RichNode.Paragraph(listOf(InlineNode.Link(text = URL, url = URL)))))

        composeRule.onNodeWithText(URL).performTouchInput { longClick(Offset(12f, centerY)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("复制链接").performClick()

        assertEquals(URL, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        assertTrue("long press opened the link", followed.isEmpty())
    }

    @Test
    fun `select all from the prose also copies the following link`() {
        setContent(
            listOf(
                RichNode.Paragraph(listOf(InlineNode.Text(LABEL))),
                RichNode.Paragraph(listOf(InlineNode.Link(text = URL, url = URL))),
            ),
        )

        composeRule.onNodeWithText(LABEL).performTouchInput { longClick(center) }
        composeRule.waitForIdle()
        toolbar.invoke(TextContextMenuKeys.SelectAllKey)
        toolbar.invoke(TextContextMenuKeys.CopyKey)

        assertEquals("$LABEL\n$URL", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        assertTrue(followed.isEmpty())
    }

    @Test
    fun `a short tap still opens the link once`() {
        setContent(listOf(RichNode.Paragraph(listOf(InlineNode.Link(text = URL, url = URL)))))

        composeRule.onNodeWithText(URL).performTouchInput { click(Offset(12f, centerY)) }

        assertEquals(listOf(URL), followed)
    }

    @Test
    fun `copy link on a named blue link copies its destination not its label`() {
        setContent(
            listOf(
                RichNode.Paragraph(listOf(InlineNode.Text(LABEL))),
                RichNode.Paragraph(listOf(InlineNode.Text("详情："), InlineNode.Link(text = "查看报告", url = URL))),
            ),
        )

        composeRule.onNodeWithText("详情：查看报告").performTouchInput { longClick(Offset(width - 12f, centerY)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("复制链接").performClick()

        assertEquals(URL, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        assertTrue(followed.isEmpty())
    }

    @Test
    fun `copy text on a named link copies the label only`() {
        setContent(
            listOf(
                RichNode.Paragraph(listOf(InlineNode.Text(LABEL))),
                RichNode.Paragraph(listOf(InlineNode.Text("详情："), InlineNode.Link(text = "查看报告", url = URL))),
            ),
        )

        composeRule.onNodeWithText("详情：查看报告").performTouchInput { longClick(Offset(width - 12f, centerY)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("复制文字").performClick()

        assertEquals("查看报告", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    }

    @Test
    fun `a standalone link after prose copies without an extra paragraph separator`() {
        setContent(
            listOf(
                RichNode.Paragraph(listOf(InlineNode.Text(LABEL))),
                RichNode.Paragraph(listOf(InlineNode.Link(text = URL, url = URL))),
            ),
        )

        composeRule.onNodeWithText(URL).performTouchInput { longClick(Offset(12f, centerY)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("复制链接").performClick()

        assertEquals(URL, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    }

    @Test
    fun `scrolling from a link neither selects nor opens it`() {
        setContent(listOf(RichNode.Paragraph(listOf(InlineNode.Link(text = URL, url = URL)))))

        composeRule.onNodeWithText(URL).performTouchInput {
            down(Offset(12f, centerY))
            moveBy(Offset(0f, 200f))
            advanceEventTime(1000)
            up()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("复制链接").assertDoesNotExist()
        assertEquals(null, toolbar.dataProvider)
        assertTrue(followed.isEmpty())
    }

    @Test
    fun `identically named table links copy the destination of the pressed cell`() {
        setContent(
            listOf(
                RichNode.Paragraph(listOf(InlineNode.Text(LABEL))),
                RichNode.Table(
                    cells = listOf(
                        listOf(listOf(InlineNode.Text("项目")), listOf(InlineNode.Text("报告"))),
                        listOf(listOf(InlineNode.Text("一号")), listOf(InlineNode.Link(text = "NQ", url = "$URL?first"))),
                        listOf(listOf(InlineNode.Text("二号")), listOf(InlineNode.Link(text = "NQ", url = URL))),
                    ),
                ),
            ),
        )

        composeRule.onAllNodesWithText("NQ")[1].performTouchInput { longClick(Offset(3f, centerY)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("复制链接").performClick()

        assertEquals(URL, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        assertTrue(followed.isEmpty())
    }

    @Test
    fun `a wrapping table link copies its entire destination`() {
        val longUrl = "$URL?report=long-wrapping-report-in-a-narrow-table-cell"
        setContent(
            listOf(
                RichNode.Table(
                    cells = listOf(
                        listOf(listOf(InlineNode.Text("项目")), listOf(InlineNode.Text("报告"))),
                        listOf(listOf(InlineNode.Text("结果")), listOf(InlineNode.Link(text = longUrl, url = longUrl))),
                    ),
                ),
            ),
        )

        composeRule.onNodeWithText(longUrl).performTouchInput { longClick(Offset(8f, 8f)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("复制链接").performClick()

        assertEquals(longUrl, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        assertTrue(followed.isEmpty())
    }

    private fun RecordingToolbar.invoke(key: Any) {
        composeRule.runOnIdle {
            val item = dataProvider!!.data().components.filterIsInstance<TextContextMenuItem>()
                .single { it.key == key }
            item.onClick(object : TextContextMenuSession {
                override fun close() = Unit
            })
        }
        composeRule.waitForIdle()
    }

    private class RecordingToolbar : TextContextMenuProvider {
        var dataProvider: TextContextMenuDataProvider? = null

        override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
            this.dataProvider = dataProvider
            awaitCancellation()
        }
    }

    companion object {
        private const val LABEL = "报告链接："
        private const val URL = "https://nodequality.ibsqqs.uk/r/jhT47PrlCP"
    }
}
