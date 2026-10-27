package io.github.nodyssey.ui.notifications

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.JsonApi
import io.github.nodyssey.data.MessageConversation
import io.github.nodyssey.data.MessageRepository
import io.github.nodyssey.data.MessageThread
import io.github.nodyssey.data.NotificationRepository
import io.github.nodyssey.data.NotificationTab
import io.github.nodyssey.data.SearchRepository
import io.github.nodyssey.data.UserSearchResult
import io.github.nodyssey.data.contentPreview
import io.github.nodyssey.data.session.FakeSessionCookieStore
import io.github.nodyssey.data.session.SessionRepository
import io.github.plaza.core.AppClock
import io.github.plaza.core.net.SessionCookies
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The badge half of board 7d.
 *
 * Every case here is the same bug seen from a different side: acting on a notification used to move
 * the row and leave the number that pointed at it exactly where it was.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NotificationsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val cookies = FakeSessionCookieStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        cookies.setCookie(NodeSeekSite.BASE_URL, "session=test")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `opening a notification tells the server and drops the badge`() =
        runTest(dispatcher) {
            val api = FakeApi(counts = """{"atMe":2}""")
            val viewModel = viewModel(api)
            advanceUntilIdle()
            assertEquals(2, viewModel.uiState.value.counts.mentions)

            api.counts = """{"atMe":1}"""
            viewModel.markOpened("42")
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.items.single().isUnread)
            assertEquals(listOf("/api/notification/at-me/markViewed"), api.postedPaths)
            assertEquals(listOf("""{"atMe":[1]}"""), api.postedBodies)
            assertEquals(1, viewModel.uiState.value.counts.mentions)
        }

    /** Opening the same row twice must not post it twice, nor take the badge down twice. */
    @Test
    fun `opening a row that is already read posts nothing`() =
        runTest(dispatcher) {
            val api = FakeApi(counts = """{"atMe":2}""")
            val viewModel = viewModel(api, unread = false)
            advanceUntilIdle()

            viewModel.markOpened("42")
            advanceUntilIdle()

            assertEquals(emptyList<String>(), api.postedPaths)
            assertEquals(2, viewModel.uiState.value.counts.mentions)
        }

    /**
     * The merge, end to end: one comment that replied *and* @-ed is one row, and opening it has to
     * clear the row each group is holding. Clearing only one used to be the whole bug this screen
     * had twice over — the badge came back on the next refresh, pointing at something already read.
     */
    @Test
    fun `a comment in both groups is one row, and reading it clears both`() =
        runTest(dispatcher) {
            val api = FakeApi(counts = """{"atMe":1,"reply":1}""", replies = REPLY_LIST)
            val viewModel = viewModel(api)
            advanceUntilIdle()
            val item = viewModel.uiState.value.items.single()
            assertTrue(item.isReply && item.isMention)
            // Two groups, one thing to read: the badge says 1, not 2.
            assertEquals(1, viewModel.uiState.value.counts.interactions)

            api.counts = """{"atMe":0,"reply":0}"""
            viewModel.markOpened(item.id)
            advanceUntilIdle()

            assertEquals(
                listOf("/api/notification/reply-to-me/markViewed", "/api/notification/at-me/markViewed"),
                api.postedPaths,
            )
            assertEquals(listOf("""{"replys":[7]}""", """{"atMe":[1]}"""), api.postedBodies)
            assertEquals(0, viewModel.uiState.value.counts.interactions)
        }

    /** 全部已读 on 通知 used to zero every badge, 私信 included. */
    @Test
    fun `mark all read clears both notification groups but not 私信`() =
        runTest(dispatcher) {
            val api = FakeApi(counts = """{"atMe":2,"message":3}""")
            val viewModel = viewModel(api)
            advanceUntilIdle()

            api.counts = """{"atMe":0,"message":3}"""
            viewModel.markAllRead()
            advanceUntilIdle()

            assertEquals(
                listOf(
                    "/api/notification/reply-to-me/markViewed?all=true",
                    "/api/notification/at-me/markViewed?all=true",
                ),
                api.postedPaths,
            )
            assertEquals(0, viewModel.uiState.value.counts.mentions)
            assertEquals(3, viewModel.uiState.value.counts.messages)
        }

    /**
     * Every return to the screen calls this, so the throttle carries the difference between "timely"
     * and "a request per tab tap": straight back is served from what is showing, a stale return
     * re-reads the server.
     */
    @Test
    fun `coming back into view refreshes only once the last load is stale`() =
        runTest(dispatcher) {
            var now = 1_785_000_000_000L
            val api = FakeApi(counts = """{"atMe":2}""")
            val viewModel = viewModel(api, clock = { now })
            advanceUntilIdle()
            assertEquals(2, viewModel.uiState.value.counts.mentions)

            api.counts = """{"atMe":5}"""
            viewModel.refreshIfStale()
            advanceUntilIdle()
            assertEquals(2, viewModel.uiState.value.counts.mentions)

            now += 30_000L
            viewModel.refreshIfStale()
            advanceUntilIdle()
            assertEquals(5, viewModel.uiState.value.counts.mentions)
        }

    @Test
    fun `a failed badge refresh expires a recent successful load so returning retries immediately`() =
        runTest(dispatcher) {
            val api = FakeApi(counts = """{"message":1}""")
            val viewModel = viewModel(api)
            advanceUntilIdle()

            api.countsFail = true
            viewModel.refresh()
            advanceUntilIdle()
            assertEquals(1, viewModel.uiState.value.counts.messages)

            api.countsFail = false
            api.counts = """{"message":4}"""
            viewModel.refreshIfStale()
            advanceUntilIdle()
            assertEquals(4, viewModel.uiState.value.counts.messages)
            assertEquals(3, api.countRequests)
        }

    @Test
    fun `visible message polling updates previews even when unread totals stay the same and stops off screen`() =
        runTest(dispatcher) {
            val api = FakeApi(counts = """{"message":1}""")
            val messages = FakeMessages()
            val viewModel = viewModel(api, messages = messages)
            advanceUntilIdle()
            viewModel.selectTab(NotificationTab.MESSAGES)
            advanceUntilIdle()
            val initialRequests = messages.requests
            val initialInteractionRequests = api.interactionRequests
            viewModel.startMessagePolling()
            viewModel.startMessagePolling()
            try {
                runCurrent()
                assertEquals(initialRequests, messages.requests)
                messages.content = "new reply"
                advanceTimeBy(10_000)
                runCurrent()
                assertEquals(contentPreview("new reply"), viewModel.uiState.value.conversations.single().snippet)
                assertEquals(1, viewModel.uiState.value.counts.messages)
                assertEquals(initialRequests + 1, messages.requests)
                assertEquals(initialInteractionRequests, api.interactionRequests)
                assertFalse(viewModel.uiState.value.isLoading)

                viewModel.stopMessagePolling()
                messages.content = "reply while away"
                advanceTimeBy(30_000)
                runCurrent()
                assertEquals(initialRequests + 1, messages.requests)

                viewModel.startMessagePolling()
                advanceTimeBy(10_000)
                runCurrent()
                assertEquals(contentPreview("reply while away"), viewModel.uiState.value.conversations.single().snippet)

                viewModel.selectTab(NotificationTab.INTERACTIONS)
                runCurrent()
                val afterLeaving = messages.requests
                viewModel.startMessagePolling()
                advanceTimeBy(30_000)
                runCurrent()
                assertEquals(afterLeaving, messages.requests)
            } finally {
                viewModel.stopMessagePolling()
            }
        }

    @Test
    fun `failed badge polls back off to a minute then return to normal after recovery`() =
        runTest(dispatcher) {
            val api = FakeApi(counts = """{"message":1}""")
            val messages = FakeMessages()
            val viewModel = viewModel(api, messages = messages)
            advanceUntilIdle()
            viewModel.selectTab(NotificationTab.MESSAGES)
            advanceUntilIdle()
            val initialRequests = api.countRequests
            api.countsFail = true
            viewModel.startMessagePolling()
            try {
                advanceTimeBy(10_000)
                runCurrent()
                assertEquals(initialRequests + 1, api.countRequests)
                advanceTimeBy(19_999)
                runCurrent()
                assertEquals(initialRequests + 1, api.countRequests)
                advanceTimeBy(1)
                runCurrent()
                assertEquals(initialRequests + 2, api.countRequests)
                advanceTimeBy(40_000)
                runCurrent()
                assertEquals(initialRequests + 3, api.countRequests)
                advanceTimeBy(60_000)
                runCurrent()
                assertEquals(initialRequests + 4, api.countRequests)
                assertEquals(1, viewModel.uiState.value.counts.messages)
                assertEquals(1, viewModel.uiState.value.conversations.size)
                assertFalse(viewModel.uiState.value.isLoading)

                api.countsFail = false
                api.counts = """{"message":3}"""
                advanceTimeBy(59_999)
                runCurrent()
                assertEquals(initialRequests + 4, api.countRequests)
                advanceTimeBy(1)
                runCurrent()
                assertEquals(3, viewModel.uiState.value.counts.messages)
                advanceTimeBy(10_000)
                runCurrent()
                assertEquals(initialRequests + 6, api.countRequests)
            } finally {
                viewModel.stopMessagePolling()
            }
        }

    @Test
    fun `a failed conversation poll preserves the list and clears its error on recovery`() =
        runTest(dispatcher) {
            val messages = FakeMessages()
            val viewModel = viewModel(FakeApi(counts = """{"message":1}"""), messages = messages)
            advanceUntilIdle()
            viewModel.selectTab(NotificationTab.MESSAGES)
            advanceUntilIdle()
            messages.fails = true
            viewModel.startMessagePolling()
            try {
                advanceTimeBy(10_000)
                runCurrent()
                assertEquals(contentPreview("hello"), viewModel.uiState.value.conversations.single().snippet)
                assertEquals(SiteError.Network, viewModel.uiState.value.error)
                messages.fails = false
                messages.content = "recovered"
                advanceTimeBy(20_000)
                runCurrent()
                assertEquals(contentPreview("recovered"), viewModel.uiState.value.conversations.single().snippet)
                assertEquals(null, viewModel.uiState.value.error)
            } finally {
                viewModel.stopMessagePolling()
            }
        }

    @Test
    fun `manual refresh supersedes an active poll without overlapping requests or losing its loading state`() =
        runTest(dispatcher) {
            val messages = FakeMessages()
            val viewModel = viewModel(FakeApi(counts = """{"message":1}"""), messages = messages)
            advanceUntilIdle()
            viewModel.selectTab(NotificationTab.MESSAGES)
            advanceUntilIdle()
            val initialRequests = messages.requests
            messages.delayMillis = 30_000
            viewModel.startMessagePolling()
            try {
                advanceTimeBy(10_000)
                runCurrent()
                messages.content = "newer snapshot"
                viewModel.refresh()
                runCurrent()
                assertTrue(viewModel.uiState.value.isLoading)
                advanceTimeBy(10_000)
                runCurrent()
                assertTrue(viewModel.uiState.value.isLoading)
                advanceTimeBy(20_000)
                runCurrent()
                assertEquals(1, messages.maxConcurrentRequests)
                assertEquals(contentPreview("newer snapshot"), viewModel.uiState.value.conversations.single().snippet)
                assertFalse(viewModel.uiState.value.isLoading)
                advanceTimeBy(10_000)
                runCurrent()
                assertEquals(1, messages.maxConcurrentRequests)
                assertEquals(initialRequests + 3, messages.requests)
                assertFalse(viewModel.uiState.value.isLoading)
            } finally {
                viewModel.stopMessagePolling()
            }
        }

    @Test
    fun `pausing cancels an in flight poll before it can change the conversation list`() =
        runTest(dispatcher) {
            val messages = FakeMessages()
            val viewModel = viewModel(FakeApi(counts = """{"message":1}"""), messages = messages)
            advanceUntilIdle()
            viewModel.selectTab(NotificationTab.MESSAGES)
            advanceUntilIdle()
            messages.delayMillis = 30_000
            messages.content = "arrived after opening the thread"
            viewModel.startMessagePolling()
            advanceTimeBy(10_000)
            runCurrent()
            viewModel.stopMessagePolling()
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(contentPreview("hello"), viewModel.uiState.value.conversations.single().snippet)
            assertEquals(0, messages.activeRequests)
        }

    @Test
    fun `session renewal keeps visible polling alive and signing out cancels outstanding requests`() =
        runTest(dispatcher) {
            val session = SessionRepository(SessionCookies(NodeSeekSite.CONFIG, cookies))
            val api = FakeApi(counts = """{"message":1}""")
            val messages = FakeMessages()
            val viewModel = viewModel(api, messages = messages, session = session)
            advanceUntilIdle()
            viewModel.selectTab(NotificationTab.MESSAGES)
            advanceUntilIdle()
            viewModel.startMessagePolling()
            try {
                runCurrent()
                cookies.setCookie(NodeSeekSite.BASE_URL, "session=renewed")
                session.sync()
                runCurrent()
                messages.content = "after renewal"
                advanceTimeBy(10_000)
                runCurrent()
                assertEquals(contentPreview("after renewal"), viewModel.uiState.value.conversations.single().snippet)

                messages.delayMillis = 30_000
                advanceTimeBy(10_000)
                runCurrent()
                assertEquals(1, messages.activeRequests)
                session.signOut()
                runCurrent()
                val requestsAtSignOut = messages.requests
                advanceTimeBy(60_000)
                runCurrent()
                assertEquals(requestsAtSignOut, messages.requests)
                assertEquals(0, messages.activeRequests)
                assertFalse(viewModel.uiState.value.isSignedIn)
                assertTrue(viewModel.uiState.value.conversations.isEmpty())
                assertEquals(0, viewModel.uiState.value.counts.messages)
            } finally {
                viewModel.stopMessagePolling()
            }
        }

    /** One endpoint behind a wall must not put an error screen over the group that loaded fine. */
    @Test
    fun `a failure in one group leaves the other alone`() =
        runTest(dispatcher) {
            val messages = FakeMessages(fails = true)
            val viewModel = viewModel(FakeApi(counts = """{"atMe":2}"""), messages = messages)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.items.isNotEmpty())
            assertEquals(null, state.error)
            assertTrue(state.errors.containsKey(NotificationTab.MESSAGES))
        }

    private fun viewModel(
        api: FakeApi,
        unread: Boolean = true,
        clock: AppClock = AppClock { 1_785_000_000_000L },
        messages: MessageRepository = NoMessages,
        session: SessionRepository = SessionRepository(SessionCookies(NodeSeekSite.CONFIG, cookies)),
    ): NotificationsViewModel {
        val notifications = NotificationRepository(api)
        api.mentionUnread = unread
        return NotificationsViewModel(
            repository = notifications,
            messages = messages,
            search = NoSearch,
            session = session,
            clock = clock,
        )
    }
}

/** A conversation list that answers, or refuses to. */
private class FakeMessages(var fails: Boolean = false) : MessageRepository by NoMessages {
    var content = "hello"
    var delayMillis = 0L
    var requests = 0
    var activeRequests = 0
    var maxConcurrentRequests = 0

    override suspend fun conversations(): List<MessageConversation> {
        requests++
        activeRequests++
        maxConcurrentRequests = maxOf(maxConcurrentRequests, activeRequests)
        val snapshot = listOf(
            MessageConversation(
                uid = 7,
                userName = "nssk",
                avatarUrl = null,
                snippet = contentPreview(content),
                isSnippetMine = false,
                updatedAtMillis = 1_785_000_000_000L,
                updatedAtText = null,
                unreadCount = 0,
                isSystem = false,
            ),
        )
        try {
            delay(delayMillis)
            if (fails) throw SiteException(SiteError.Network)
            return snapshot
        } finally {
            activeRequests--
        }
    }
}

private object NoSearch : SearchRepository {
    override suspend fun searchUsers(query: String) = emptyList<UserSearchResult>()

    override suspend fun resolveMemberUid(name: String): Long? = null
}

private object NoMessages : MessageRepository {
    override suspend fun conversations() = emptyList<MessageConversation>()

    override suspend fun thread(uid: Long) =
        MessageThread(uid = uid, userName = "", avatarUrl = null, level = null, messages = emptyList())

    override suspend fun send(
        uid: Long,
        content: String,
        markdown: Boolean,
    ) = null

    override suspend fun markRead(messageIds: List<Long>) = Unit

    override suspend fun markAllRead() = Unit
}

/** The reply row for the same comment 42 the mention row names, as the site sends it. */
private const val REPLY_LIST =
    """{"replyList":[{"id":7,"comment_id":42,"post_id":703863,"floor_id":12,"commenter_name":"nssk",
       "content":"@me [#3](/post-703863-1#3) 还没有这个功能","post_title":"求教如何改用户名","viewed":0}]}"""

private class FakeApi(
    var counts: String,
    /** Empty by default: most cases here are about one group, and an empty list is a valid answer. */
    private val replies: String = """{"replyList":[]}""",
) : JsonApi {
    var mentionUnread = true
    var countsFail = false
    var countRequests = 0
    var interactionRequests = 0
    val postedPaths = mutableListOf<String>()
    val postedBodies = mutableListOf<String>()

    override suspend fun getJson(path: String, referer: String): String =
        when {
            path.startsWith("/api/notification/unread-count") -> {
                countRequests++
                if (countsFail) throw SiteException(SiteError.Network)
                counts
            }

            path.startsWith("/api/notification/reply-to-me") -> {
                interactionRequests++
                replies
            }

            else ->
                """{"atList":[{"id":1,"comment_id":42,"post_id":703863,"member_id":12,"username":"nssk",
                   "post_title":"求教如何改用户名","viewed":${if (mentionUnread) 0 else 1}}]}"""
        }

    override suspend fun postJson(path: String, body: String, referer: String): String {
        postedPaths += path
        postedBodies += body
        return """{"success":true}"""
    }
}
