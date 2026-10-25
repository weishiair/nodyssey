package io.github.nodyssey.ui.messages

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.JsonApi
import io.github.nodyssey.data.DirectMessage
import io.github.nodyssey.data.MessageRepository
import io.github.nodyssey.data.MessageThread
import io.github.nodyssey.data.NotificationRepository
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.UserProfile
import io.github.nodyssey.data.composer.ImageUploader
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MessageThreadViewModelTest {
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
    fun `the header has the avatar before the history arrives`() =
        runTest(dispatcher) {
            val viewModel = viewModel(FakeMessageRepository())

            // Nothing has been fetched yet: the header would otherwise draw the initial and swap the
            // picture in when the thread loads.
            assertEquals(NodeSeekSite.avatarUrl(4471), viewModel.uiState.value.avatarUrl)

            advanceUntilIdle()
            assertEquals(NodeSeekSite.avatarUrl(4471), viewModel.uiState.value.avatarUrl)
        }

    @Test
    fun `falls back to the public profile for joined days when the thread omits createdAt`() =
        runTest(dispatcher) {
            val profiles =
                object : ProfileRepository {
                    override suspend fun profile(refresh: Boolean): UserProfile = error("unused")

                    override suspend fun profile(uid: Long): UserProfile =
                        UserProfile(
                            uid = uid,
                            name = "iwil",
                            avatarUrl = "",
                            rank = 1,
                            createdAt = "2026-07-15T00:00:00.000Z",
                        )
                }
            val viewModel =
                viewModel(
                    FakeMessageRepository(level = null),
                    profiles = profiles,
                    clock = AppClock { 1_784_980_800_000L },
                )
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.level)
            assertEquals(10, viewModel.uiState.value.joinedDays)
        }

    @Test
    fun `a sent message appears immediately and settles once accepted`() =
        runTest(dispatcher) {
            val repository = FakeMessageRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.draftState.setTextAndPlaceCursorAtEnd("行，我发个投票试试")
            viewModel.send()

            // The bubble is on screen before the network answers, and the field is already clear.
            assertEquals(SendStatus.SENDING, viewModel.uiState.value.messages.last().status)
            assertEquals("", viewModel.draftState.text.toString())

            advanceUntilIdle()
            assertEquals(SendStatus.SENT, viewModel.uiState.value.messages.last().status)
        }

    @Test
    fun `a failed send keeps the text and can be retried`() =
        runTest(dispatcher) {
            val repository = FakeMessageRepository(sendError = SiteException(SiteError.Network))
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.draftState.setTextAndPlaceCursorAtEnd("顺便问下星辰能转账吗")
            viewModel.send()
            advanceUntilIdle()

            val failed = viewModel.uiState.value.messages.last()
            assertEquals(SendStatus.FAILED, failed.status)
            assertEquals("顺便问下星辰能转账吗", failed.content)

            repository.sendError = null
            viewModel.retry(failed.id)
            advanceUntilIdle()

            assertEquals(SendStatus.SENT, viewModel.uiState.value.messages.last().status)
            assertEquals(2, repository.sendCount)
        }

    /** A refresh mid-flight must not drop the bubble the user is still waiting on. */
    @Test
    fun `reloading the thread keeps a message that has not been delivered`() =
        runTest(dispatcher) {
            val repository = FakeMessageRepository(sendError = SiteException(SiteError.Network))
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.draftState.setTextAndPlaceCursorAtEnd("在吗")
            viewModel.send()
            advanceUntilIdle()

            viewModel.refresh()
            advanceUntilIdle()

            val messages = viewModel.uiState.value.messages
            assertEquals(2, messages.size)
            assertTrue(messages.any { it.status == SendStatus.FAILED && it.content == "在吗" })
        }

    @Test
    fun `foreground polling receives replies and resumes immediately without duplicate loops`() =
        runTest(dispatcher) {
            val repository = FakeMessageRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()
            viewModel.startPolling()
            viewModel.startPolling()
            try {
                runCurrent()
                assertEquals(2, repository.threadRequests)
                repository.incoming += incoming("2", "第一条回复")
                advanceTimeBy(10_000)
                runCurrent()
                assertEquals("第一条回复", viewModel.uiState.value.messages.last().content)

                viewModel.stopPolling()
                repository.incoming += incoming("3", "后台期间的回复")
                advanceTimeBy(30_000)
                runCurrent()
                assertEquals(3, repository.threadRequests)
                assertEquals(2, viewModel.uiState.value.messages.size)

                viewModel.startPolling()
                runCurrent()
                assertEquals("后台期间的回复", viewModel.uiState.value.messages.last().content)
                assertEquals(4, repository.threadRequests)
            } finally {
                viewModel.stopPolling()
            }
        }

    @Test
    fun `an old poll cannot erase a send that completed before the history returned`() =
        runTest(dispatcher) {
            val repository = FakeMessageRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()
            repository.threadDelayMillis = 30_000
            viewModel.startPolling()
            try {
                runCurrent()
                viewModel.draftState.setTextAndPlaceCursorAtEnd("刚刚发出的消息")
                viewModel.send()
                runCurrent()
                assertEquals(SendStatus.SENT, viewModel.uiState.value.messages.last().status)

                advanceTimeBy(30_000)
                runCurrent()
                assertEquals("刚刚发出的消息", viewModel.uiState.value.messages.last().content)
                assertEquals(SendStatus.SENT, viewModel.uiState.value.messages.last().status)
            } finally {
                viewModel.stopPolling()
            }
        }

    @Test
    fun `manual refresh and polling never overlap or consume each others loading state`() =
        runTest(dispatcher) {
            val repository = FakeMessageRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()
            repository.threadDelayMillis = 30_000
            viewModel.startPolling()
            try {
                runCurrent()
                viewModel.refresh()
                runCurrent()
                assertTrue(viewModel.uiState.value.isLoading)
                advanceTimeBy(30_000)
                runCurrent()
                assertTrue(viewModel.uiState.value.isLoading)
                assertEquals(3, repository.threadRequests)

                advanceTimeBy(30_000)
                runCurrent()
                assertEquals(1, repository.maxConcurrentReads)
                assertEquals(false, viewModel.uiState.value.isLoading)
            } finally {
                viewModel.stopPolling()
            }
        }

    @Test
    fun `polling pauses a history fetch while a message is still being sent`() =
        runTest(dispatcher) {
            val repository = FakeMessageRepository(sendDelayMillis = 30_000)
            val viewModel = viewModel(repository)
            advanceUntilIdle()
            viewModel.draftState.setTextAndPlaceCursorAtEnd("还在发送")
            viewModel.send()
            repository.incoming += incoming("server-echo", "还在发送").copy(isMine = true)
            viewModel.startPolling()
            try {
                runCurrent()
                advanceTimeBy(10_000)
                runCurrent()
                assertEquals(1, repository.threadRequests)
                assertEquals(1, viewModel.uiState.value.messages.count { it.content == "还在发送" })
            } finally {
                viewModel.stopPolling()
            }
            advanceUntilIdle()
        }

    @Test
    fun `failed polls preserve readable history and back off until a successful fetch`() =
        runTest(dispatcher) {
            val repository = FakeMessageRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()
            repository.threadError = SiteException(SiteError.Network)
            viewModel.startPolling()
            try {
                runCurrent()
                advanceTimeBy(19_999)
                runCurrent()
                assertEquals(2, repository.threadRequests)
                assertEquals(1, viewModel.uiState.value.messages.size)
                assertEquals(null, viewModel.uiState.value.error)
                advanceTimeBy(1)
                runCurrent()
                assertEquals(3, repository.threadRequests)
                advanceTimeBy(40_000)
                runCurrent()
                assertEquals(4, repository.threadRequests)

                repository.threadError = null
                repository.incoming += incoming("2", "恢复连接")
                advanceTimeBy(60_000)
                runCurrent()
                assertEquals("恢复连接", viewModel.uiState.value.messages.last().content)
                advanceTimeBy(10_000)
                runCurrent()
                assertEquals(6, repository.threadRequests)
            } finally {
                viewModel.stopPolling()
            }
        }

    @Test
    fun `a response from before sign out cannot add messages to the screen`() =
        runTest(dispatcher) {
            val session = SessionRepository(SessionCookies(NodeSeekSite.CONFIG, cookies))
            val repository = FakeMessageRepository()
            val viewModel = viewModel(repository, session = session)
            advanceUntilIdle()
            repository.incoming += incoming("2", "旧账号的回复")
            repository.threadDelayMillis = 30_000
            viewModel.startPolling()
            try {
                runCurrent()
                session.signOut()
                advanceTimeBy(40_000)
                runCurrent()
                assertEquals(1, viewModel.uiState.value.messages.size)
                assertEquals(2, repository.threadRequests)
            } finally {
                viewModel.stopPolling()
            }
        }

    @Test
    fun `repeated server message ids produce only one bubble`() =
        runTest(dispatcher) {
            val repository = FakeMessageRepository()
            repository.incoming += incoming("1", "重复的同一条消息")
            val viewModel = viewModel(repository)
            advanceUntilIdle()
            assertEquals(listOf("1"), viewModel.uiState.value.messages.map { it.id })
        }

    /**
     * Bug: fetching a conversation is not what clears it — the ids have to go back — so the 私信
     * badge survived reading the message that put it there.
     */
    @Test
    fun `opening a conversation clears its unread messages and the badge`() =
        runTest(dispatcher) {
            val repository = FakeMessageRepository(unreadIds = listOf(11L, 12L))
            val counts = FakeCountsApi(unread = """{"message":0}""")
            val notifications = NotificationRepository(counts)
            notifications.refreshCounts()
            advanceUntilIdle()

            viewModel(repository, notifications)
            advanceUntilIdle()

            assertEquals(listOf(11L, 12L), repository.markedRead)
            assertEquals(0, notifications.counts.value.messages)
        }

    /** 引用. The message waits over the bar as a card (3e) and goes out as a blockquote ahead of the reply. */
    @Test
    fun `引用 sends the message as a blockquote ahead of the reply`() =
        runTest(dispatcher) {
            val repository = FakeMessageRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.quote(viewModel.uiState.value.messages.single())
            // The draft is left alone: the quotation is the card's, not the field's.
            assertEquals("", viewModel.draftState.text.toString())

            viewModel.draftState.setTextAndPlaceCursorAtEnd("问到了吗")
            viewModel.send()
            advanceUntilIdle()

            assertEquals("> 改名的事我问过管理\n\n问到了吗", repository.lastContent)
            assertTrue(viewModel.uiState.value.quotes.isEmpty())
        }

    /** Every line takes a `>`, or a blank line inside the message would end the quotation early. */
    @Test
    fun `a multi-line message is quoted line by line`() =
        runTest(dispatcher) {
            val repository = FakeMessageRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.quote(bubble("第一行\n\n第三行"))
            viewModel.draftState.setTextAndPlaceCursorAtEnd("好")
            viewModel.send()
            advanceUntilIdle()

            assertEquals("> 第一行\n> \n> 第三行\n\n好", repository.lastContent)
        }

    /** Cumulative, like the reply editor's: quoting a second message must not eat the first. */
    @Test
    fun `每引用一条就多一段，互不覆盖`() =
        runTest(dispatcher) {
            val repository = FakeMessageRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.quote(bubble("在的", id = "a"))
            viewModel.quote(bubble("那明天见", id = "b"))
            viewModel.draftState.setTextAndPlaceCursorAtEnd("看到了")
            viewModel.send()
            advanceUntilIdle()

            assertEquals("> 在的\n\n> 那明天见\n\n看到了", repository.lastContent)
        }

    private fun bubble(
        content: String,
        id: String = "b",
    ) = MessageBubble(
        id = id,
        isMine = false,
        content = content,
        isMarkdown = true,
        sentAtMillis = NOW,
        sentAtText = null,
        status = SendStatus.SENT,
    )

    private fun viewModel(
        repository: MessageRepository,
        notifications: NotificationRepository = NotificationRepository(FakeCountsApi()),
        profiles: ProfileRepository? = null,
        clock: AppClock = AppClock { NOW },
        session: SessionRepository = SessionRepository(SessionCookies(NodeSeekSite.CONFIG, cookies)),
    ) = MessageThreadViewModel(
        repository = repository,
        notifications = notifications,
        session = session,
        clock = clock,
        uploader = ImageUploader { _, _ -> "https://cdn.nodeimage.com/i/x.webp" },
        uid = 4471,
        userName = "iwil",
        profiles = profiles,
    )

    private companion object {
        const val NOW = 1_785_000_000_000L
    }

    private fun incoming(id: String, content: String) =
        DirectMessage(id = id, isMine = false, content = content, isMarkdown = true, sentAtMillis = NOW, sentAtText = null)
}

private class FakeCountsApi(
    private val unread: String = """{"message":0}""",
) : JsonApi {
    override suspend fun getJson(path: String, referer: String): String = unread

    override suspend fun postJson(path: String, body: String, referer: String): String =
        """{"success":true}"""
}

private class FakeMessageRepository(
    var sendError: Throwable? = null,
    private val unreadIds: List<Long> = emptyList(),
    private val level: Int? = 4,
    private val sendDelayMillis: Long = 0,
) : MessageRepository {
    val incoming = mutableListOf<DirectMessage>()
    var threadDelayMillis = 0L
    var threadError: Throwable? = null
    var threadRequests = 0
    var maxConcurrentReads = 0
    private var concurrentReads = 0
    var sendCount = 0
    var lastContent: String? = null
    val markedRead = mutableListOf<Long>()

    override suspend fun conversations() = emptyList<io.github.nodyssey.data.MessageConversation>()

    override suspend fun markAllRead() = Unit

    override suspend fun markRead(messageIds: List<Long>) {
        markedRead += messageIds
    }

    override suspend fun thread(uid: Long): MessageThread {
        threadRequests++
        concurrentReads++
        maxConcurrentReads = maxOf(maxConcurrentReads, concurrentReads)
        val snapshot = MessageThread(
            uid = uid,
            userName = "iwil",
            avatarUrl = null,
            level = level,
            messages =
            listOf(
                DirectMessage(
                    id = "1",
                    isMine = false,
                    content = "改名的事我问过管理",
                    isMarkdown = true,
                    sentAtMillis = 1_784_999_000_000L,
                    sentAtText = null,
                ),
            ) + incoming.toList(),
            unreadIds = unreadIds,
        )
        try {
            delay(threadDelayMillis)
            threadError?.let { throw it }
            return snapshot
        } finally {
            concurrentReads--
        }
    }

    override suspend fun send(
        uid: Long,
        content: String,
        markdown: Boolean,
    ): DirectMessage? {
        sendCount++
        lastContent = content
        delay(sendDelayMillis)
        sendError?.let { throw it }
        return DirectMessage(
            id = "sent-$sendCount",
            isMine = true,
            content = content,
            isMarkdown = markdown,
            sentAtMillis = 1_785_000_000_000L,
            sentAtText = null,
        ).also { incoming += it }
    }
}
