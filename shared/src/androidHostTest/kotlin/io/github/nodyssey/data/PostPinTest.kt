package io.github.nodyssey.data

import io.github.nodyssey.core.net.JsonWriteSource
import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Catches wrong wire actions, lost accepted pins, and refusals accidentally written to Room. */
@RunWith(RobolectricTestRunner::class)
class PostPinTest {
    private lateinit var database: NodeSeekDatabase
    private val remote = FakePostRemoteDataSource()
    private val clock = MutableClock()
    private var answer = """{"success":true,"message":"added"}"""
    private val writes = mutableListOf<Triple<String, String, String>>()
    private val api = object : JsonWriteSource {
        override suspend fun postJson(path: String, body: String, referer: String): String {
            writes += Triple(path, body, referer)
            return answer
        }
    }

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        remote.detailResult = { postId, page ->
            FakePostRemoteDataSource.detail(postId, page).copy(
                comments = listOf(
                    FakePostRemoteDataSource.content("reply").copy(commentId = 101, floor = "#1", canPin = true),
                    FakePostRemoteDataSource.content("other reply").copy(commentId = 102, floor = "#2", isPinned = true),
                ),
            )
        }
    }

    @After
    fun tearDown() = database.close()

    private fun repository() = OfflineFirstPostRepository(database, remote, clock, pins = PostPinWriter(api))

    @Test
    fun `pin and unpin send the site's actions and update only the accepted comment`() = runTest {
        val repository = repository()
        repository.refreshThread(42, 1)
        assertTrue(repository.isThreadFresh(42))

        assertTrue(repository.setCommentPinned(42, 101, true))
        assertEquals(Triple("/api/content/pin-comment", """{"commentId":101,"action":"add"}""", "https://www.nodeseek.com/post-42-1"), writes.single())
        assertEquals(listOf(true, true), repository.thread(42).first()!!.comments.map { it.isPinned })
        assertFalse(repository.isThreadFresh(42))

        answer = """{"success":true,"message":"removed"}"""
        assertFalse(repository.setCommentPinned(42, 101, false))
        assertEquals("""{"commentId":101,"action":"remove"}""", writes.last().second)
        assertEquals(listOf(false, true), repository.thread(42).first()!!.comments.map { it.isPinned })
    }

    @Test
    fun `a refusal preserves the cached state and the site's reason`() = runTest {
        val repository = repository()
        repository.refreshThread(42, 1)
        answer = """{"success":false,"message":"鸡腿不足"}"""

        val failure = try {
            repository.setCommentPinned(42, 101, true)
            error("expected refusal")
        } catch (exception: SiteException) {
            exception
        }
        assertEquals("鸡腿不足", failure.detail)
        assertEquals(listOf(false, true), repository.thread(42).first()!!.comments.map { it.isPinned })
        assertTrue(repository.isThreadFresh(42))
        assertEquals(1, writes.size)
    }

    @Test
    fun `the server echo wins over a stale requested state`() = runTest {
        val repository = repository()
        repository.refreshThread(42, 1)
        answer = """{"success":true,"message":"removed"}"""

        assertFalse(repository.setCommentPinned(42, 101, true))
        assertFalse(repository.thread(42).first()!!.comments.first().isPinned)
    }

    @Test
    fun `an unreadable answer is not retried or written as a successful pin`() = runTest {
        val repository = repository()
        repository.refreshThread(42, 1)
        answer = "not JSON"
        val failure = try {
            repository.setCommentPinned(42, 101, true)
            error("expected unreadable response")
        } catch (exception: SiteException) {
            exception
        }
        assertEquals(SiteError.Unparsable, failure.error)
        assertFalse(repository.thread(42).first()!!.comments.first().isPinned)
        assertEquals(1, writes.size)
    }
}
