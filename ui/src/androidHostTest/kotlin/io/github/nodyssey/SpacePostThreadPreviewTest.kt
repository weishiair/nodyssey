package io.github.nodyssey

import io.github.nodyssey.data.SpacePost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpacePostThreadPreviewTest {
    @Test
    fun `a row without an author hands over nothing so the thread keeps its slide`() {
        // The header has no name to draw on its first frame, and a blank one would be replaced by
        // the real name once the request returns; no preview is what keeps the page from stuttering.
        val post =
            SpacePost(
                postId = 7,
                title = "第一次用 nftables 做端口转发",
                categoryTitle = "技术",
                categorySlug = "tech",
                authorName = null,
                commentCount = 12,
                viewCount = 843,
                createdAtText = "3天前",
            )

        assertNull(post.threadPreview("space-42"))
    }

    @Test
    fun `a row with an author carries what the header draws before the request returns`() {
        val post =
            SpacePost(
                postId = 7,
                title = "第一次用 nftables 做端口转发",
                categoryTitle = "技术",
                categorySlug = "tech",
                authorName = "alice",
                commentCount = 12,
                viewCount = 843,
                createdAtText = "3天前",
            )

        val preview = post.threadPreview("space-42")

        assertEquals("第一次用 nftables 做端口转发", preview?.title)
        assertEquals("alice", preview?.authorName)
        assertEquals("技术", preview?.categoryTitle)
        assertEquals("tech", preview?.categorySlug)
        assertEquals(12, preview?.replyCount)
        // The header matches this space page's keys, not another list's for the same post.
        assertEquals("space-42", preview?.origin)
    }
}
