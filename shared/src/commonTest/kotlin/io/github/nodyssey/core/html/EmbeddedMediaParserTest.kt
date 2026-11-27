package io.github.nodyssey.core.html

import com.fleeksoft.ksoup.Ksoup
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Videos used to disappear because iframe/source URLs were never read as content. */
class EmbeddedMediaParserTest {
    private fun parse(id: String): List<RichNode> = RichContentParser.parse(
        Ksoup.parse(Fixtures.load("richtext/embedded-media.html")).getElementById(id),
    )

    @Test
    fun `a Bilibili-only post keeps its player instead of an empty body`() {
        val detail = PostDetailParser.parse(Fixtures.load("post-1054065-1-media.html"), postId = 1054065, page = 1)

        assertEquals(
            listOf(
                RichNode.EmbeddedMedia(
                    "https://player.bilibili.com/player.html?autoplay=0&high_quality=1&bvid=BV1kMWz6CESY&page=1",
                ),
            ),
            requireNotNull(detail.body).nodes,
        )
    }

    @Test
    fun `media splits a paragraph without losing either side or its sticker`() {
        val nodes = parse("mixed")

        assertEquals(3, nodes.size)
        assertEquals(listOf(InlineNode.Text("Before")), assertIs<RichNode.Paragraph>(nodes[0]).inlines)
        assertEquals(
            RichNode.EmbeddedMedia("https://player.bilibili.com/player.html?bvid=BV1kMWz6CESY&page=2", "Bilibili"),
            nodes[1],
        )
        assertEquals(
            listOf(InlineNode.Text("After"), InlineNode.Sticker("https://www.nodeseek.com/static/image/sticker/ac/01.png", null)),
            assertIs<RichNode.Paragraph>(nodes[2]).inlines,
        )
    }

    @Test
    fun `an iframe outside a paragraph is still content`() {
        assertEquals(listOf(RichNode.EmbeddedMedia("https://player.example.com/embed/1")), parse("bare"))
    }

    @Test
    fun `a colon in a relative URL query is not a scheme`() {
        assertEquals(
            listOf(RichNode.EmbeddedMedia("https://www.nodeseek.com/player?next=https://example.com/watch")),
            parse("relative-query"),
        )
    }

    @Test
    fun `an image beside a nested player does not replace it`() {
        val paragraph = assertIs<RichNode.Paragraph>(parse("media-and-image").single())

        assertEquals("https://player.example.com/embed/3", paragraph.inlines.filterIsInstance<InlineNode.Link>().single().url)
        assertEquals("https://www.nodeseek.com/media/cover.jpg", paragraph.inlines.filterIsInstance<InlineNode.Image>().single().url)
    }

    @Test
    fun `a video prefers its own source and never links the poster`() {
        assertEquals(listOf(RichNode.EmbeddedMedia("https://www.nodeseek.com/media/main.mp4", "Demo")), parse("video-src"))
    }

    @Test
    fun `a video without src keeps the first usable child source`() {
        assertEquals(listOf(RichNode.EmbeddedMedia("https://www.nodeseek.com/media/fallback.mp4?x=1&y=2")), parse("video-sources"))
    }

    @Test
    fun `floor 4 of post 1081071 keeps the Fluent sticker beside its text`() {
        val paragraph = assertIs<RichNode.Paragraph>(parse("fluent-sticker").single())

        assertEquals(
            listOf(InlineNode.Text("还在pending "), InlineNode.Sticker("https://www.nodeseek.com/static/image/sticker/emoji/22.png", null)),
            paragraph.inlines,
        )
    }

    @Test
    fun `a bare Fluent sticker with a MOV source uses the same image preview`() {
        val paragraph = assertIs<RichNode.Paragraph>(parse("bare-fluent-sticker").single())

        assertEquals(listOf(InlineNode.Sticker("https://www.nodeseek.com/static/image/sticker/emoji/04.png", null)), paragraph.inlines)
    }

    @Test
    fun `a sticker class and path on an external video still leave it as media`() {
        assertEquals(
            listOf(RichNode.EmbeddedMedia("https://cdn.example.com/static/image/sticker/emoji/22.webm")),
            parse("external-sticker-video"),
        )
    }

    @Test
    fun `formatting and table cells retain media as inline links`() {
        val nodes = parse("nested")
        val paragraph = assertIs<RichNode.Paragraph>(nodes[0])
        val link = paragraph.inlines.filterIsInstance<InlineNode.Link>().single()
        assertEquals("https://player.example.com/embed/2", link.url)
        assertEquals("Clip", link.text)
        assertEquals(listOf("Before", "After"), paragraph.inlines.filterIsInstance<InlineNode.Text>().map { it.text })
        val table = assertIs<RichNode.Table>(nodes[1])
        assertEquals(
            InlineNode.Link(text = "https://cdn.example.com/clip.mp4", url = "https://cdn.example.com/clip.mp4"),
            table.content.single().single().single(),
        )
    }

    @Test
    fun `executable and non-web media sources never become links`() {
        val paragraph = assertIs<RichNode.Paragraph>(parse("unsafe").single())

        assertEquals(listOf(InlineNode.Text("Before"), InlineNode.Text("After")), paragraph.inlines)
    }
}
