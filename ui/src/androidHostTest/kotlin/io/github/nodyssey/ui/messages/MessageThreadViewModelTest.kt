package io.github.nodyssey.ui.messages

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.text.TextRange
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.lifecycle.viewModelScope
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.JsonApi
import io.github.nodyssey.data.DirectMessage
import io.github.nodyssey.data.MessageDraftStore
import io.github.nodyssey.data.MessageRecovery
import io.github.nodyssey.data.MessageRepository
import io.github.nodyssey.data.MessageThread
import io.github.nodyssey.data.NotificationRepository
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.UserProfile
import io.github.nodyssey.data.composer.ImageUploader
import io.github.nodyssey.data.session.FakeSessionCookieStore
import io.github.nodyssey.data.session.SessionRepository
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.plaza.core.AppClock
import io.github.plaza.core.net.SessionCookies
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files

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
                assertEquals(null, viewModel.uiState.value.syncError)
                advanceTimeBy(1)
                runCurrent()
                assertEquals(3, repository.threadRequests)
                assertEquals(SiteError.Network, viewModel.uiState.value.syncError)
                advanceTimeBy(40_000)
                runCurrent()
                assertEquals(4, repository.threadRequests)

                repository.threadError = null
                repository.incoming += incoming("2", "恢复连接")
                advanceTimeBy(60_000)
                runCurrent()
                assertEquals("恢复连接", viewModel.uiState.value.messages.last().content)
                assertEquals(null, viewModel.uiState.value.syncError)
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
                assertEquals(0, viewModel.uiState.value.messages.size)
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

    @Test
    fun `leaving immediately after editing saves the latest text and selection with its quotes`() = runTest(dispatcher) {
        val drafts = draftStore(backgroundScope)
        val session = SessionRepository(SessionCookies(NodeSeekSite.CONFIG, cookies))
        val first = viewModel(FakeMessageRepository(), drafts = drafts, session = session)
        advanceUntilIdle()
        first.uiState.first { !it.isRestoringDraft }
        assertFalse(first.uiState.value.draftSaveFailed)
        first.quote(first.uiState.value.messages.single())
        first.draftState.edit {
            replace(0, length, "还没写完的内容")
            selection = TextRange(2, 5)
        }
        // Do not let the editor observer run before removal: Back can follow the last keystroke.
        first.viewModelScope.cancel()
        first.viewModelScope.coroutineContext[kotlinx.coroutines.Job]!!.join()
        advanceUntilIdle()

        val reopened = viewModel(FakeMessageRepository(), drafts = drafts, session = session)
        advanceUntilIdle()
        reopened.uiState.first { !it.isRestoringDraft }
        assertEquals("还没写完的内容", reopened.draftState.text.toString())
        assertEquals(TextRange(2, 5), reopened.draftState.selection)
        assertEquals(listOf("改名的事我问过管理"), reopened.uiState.value.quotes.map { it.content })
        reopened.viewModelScope.cancel()
        advanceUntilIdle()
    }

    @Test
    fun `a failure survives reopening and acceptance removes only that pending message`() = runTest(dispatcher) {
        val drafts = draftStore(backgroundScope)
        val session = SessionRepository(SessionCookies(NodeSeekSite.CONFIG, cookies))
        val repository = FakeMessageRepository(sendError = SiteException(SiteError.Network))
        val first = viewModel(repository, drafts = drafts, session = session)
        advanceUntilIdle()
        first.uiState.first { !it.isRestoringDraft }
        first.draftState.setTextAndPlaceCursorAtEnd("这条不能丢")
        first.send()
        first.uiState.first { state -> state.messages.any { it.status == SendStatus.FAILED } }
        advanceUntilIdle()
        first.viewModelScope.cancel()
        first.viewModelScope.coroutineContext[kotlinx.coroutines.Job]!!.join()
        advanceUntilIdle()

        val reopened = viewModel(repository, drafts = drafts, session = session)
        advanceUntilIdle()
        reopened.uiState.first { !it.isRestoringDraft }
        val pending = reopened.uiState.value.messages.single { it.status == SendStatus.FAILED }
        assertEquals("这条不能丢", pending.content)
        assertEquals(1, repository.sendCount)
        repository.sendError = null
        reopened.retry(pending.id)
        reopened.uiState.first { state -> state.messages.any { it.id == pending.id && it.status == SendStatus.SENT } }
        advanceUntilIdle()
        assertEquals(2, repository.sendCount)
        runCurrent()
        assertTrue(drafts.drafts(session.state.value.fingerprint).first().isEmpty())
        reopened.viewModelScope.cancel()
        advanceUntilIdle()
    }

    @Test
    fun `an interrupted send is restored as uncertain without automatically sending it again`() = runTest(dispatcher) {
        val drafts = draftStore(backgroundScope)
        val session = SessionRepository(SessionCookies(NodeSeekSite.CONFIG, cookies))
        val first = viewModel(FakeMessageRepository(sendDelayMillis = 30_000), drafts = drafts, session = session)
        advanceUntilIdle()
        first.uiState.first { !it.isRestoringDraft }
        first.draftState.setTextAndPlaceCursorAtEnd("正在发送的内容")
        first.send()
        runCurrent()
        first.viewModelScope.cancel()
        first.viewModelScope.coroutineContext[kotlinx.coroutines.Job]!!.join()
        advanceUntilIdle()

        val repository = FakeMessageRepository()
        val reopened = viewModel(repository, drafts = drafts, session = session)
        advanceUntilIdle()
        reopened.uiState.first { !it.isRestoringDraft }
        val pending = reopened.uiState.value.messages.single { it.status == SendStatus.FAILED }
        assertEquals("正在发送的内容", pending.content)
        assertTrue(pending.failure!!.uncertain)
        assertEquals(0, repository.sendCount)
        reopened.dismissFailed(pending.id)
        advanceUntilIdle()
        runCurrent()
        assertTrue(drafts.drafts(session.state.value.fingerprint).first().isEmpty())
        reopened.viewModelScope.cancel()
        advanceUntilIdle()
    }

    @Test
    fun `authentication and permanent rejections cannot be retried as network failures`() = runTest(dispatcher) {
        val cases = listOf(
            SiteException(SiteError.LoginRequired) to MessageRecovery.SIGN_IN,
            SiteException(SiteError.Cloudflare("https://www.nodeseek.com/notification.html")) to MessageRecovery.VERIFY,
            SiteException(SiteError.Unknown, detail = "对方已屏蔽你") to MessageRecovery.NONE,
        )
        for ((error, recovery) in cases) {
            val repository = FakeMessageRepository(sendError = error)
            val viewModel = viewModel(repository)
            advanceUntilIdle()
            viewModel.draftState.setTextAndPlaceCursorAtEnd("消息")
            viewModel.send()
            advanceUntilIdle()
            val pending = viewModel.uiState.value.messages.last()
            assertEquals(recovery, pending.failure!!.recovery)
            viewModel.retry(pending.id)
            advanceUntilIdle()
            assertEquals(1, repository.sendCount)
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `a rate limit enforces its wait before retrying`() = runTest(dispatcher) {
        val repository = FakeMessageRepository(sendError = SiteException(SiteError.Http(429)))
        val viewModel = viewModel(repository, clock = AppClock { NOW + testScheduler.currentTime })
        advanceUntilIdle()
        viewModel.draftState.setTextAndPlaceCursorAtEnd("别连续重发")
        viewModel.send()
        runCurrent()
        val pending = viewModel.uiState.value.messages.last()
        assertEquals(MessageRecovery.WAIT, pending.failure!!.recovery)
        viewModel.retry(pending.id)
        runCurrent()
        assertEquals(1, repository.sendCount)
        advanceTimeBy(60_000)
        runCurrent()
        repository.sendError = null
        viewModel.retry(pending.id)
        advanceUntilIdle()
        assertEquals(2, repository.sendCount)
        assertEquals(SendStatus.SENT, viewModel.uiState.value.messages.last().status)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `a challenge exposes recovery on the first failed poll even with readable history`() = runTest(dispatcher) {
        val repository = FakeMessageRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        val challenge = SiteError.Cloudflare("https://www.nodeseek.com/api/notification/message/with/4471")
        repository.threadError = SiteException(challenge)
        viewModel.startPolling()
        runCurrent()
        assertEquals(challenge, viewModel.uiState.value.syncError)
        assertEquals(1, viewModel.uiState.value.messages.size)
        repository.threadError = null
        viewModel.refresh()
        runCurrent()
        assertEquals(null, viewModel.uiState.value.syncError)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `text and quotes autosave while the conversation stays open`() = runTest(dispatcher) {
        val drafts = draftStore(backgroundScope)
        val session = SessionRepository(SessionCookies(NodeSeekSite.CONFIG, cookies))
        val viewModel = viewModel(FakeMessageRepository(), drafts = drafts, session = session)
        viewModel.uiState.first { !it.isRestoringDraft && it.messages.isNotEmpty() }
        viewModel.draftState.setTextAndPlaceCursorAtEnd("无需先退出页面")
        viewModel.quote(viewModel.uiState.value.messages.single())
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()
        runCurrent()

        val saved = drafts.open(session.state.value.fingerprint, 4471).initial
        assertEquals("无需先退出页面", saved.text)
        assertEquals(listOf("改名的事我问过管理"), saved.quotes.map { it.content })
        viewModel.viewModelScope.cancel()
        viewModel.viewModelScope.coroutineContext[kotlinx.coroutines.Job]!!.join()
    }

    @Test
    fun `renewing the same account resumes its failed send and the next draft`() = runTest(dispatcher) {
        val drafts = draftStore(backgroundScope)
        val session = SessionRepository(SessionCookies(NodeSeekSite.CONFIG, cookies))
        val repository = FakeMessageRepository(sendError = SiteException(SiteError.LoginRequired))
        val viewModel = viewModel(repository, drafts = drafts, session = session, profiles = accountProfiles { 100L })
        viewModel.uiState.first { !it.isRestoringDraft }
        viewModel.draftState.setTextAndPlaceCursorAtEnd("登录失效时发出的内容")
        viewModel.send()
        val failed = viewModel.uiState.first { state -> state.messages.any { it.status == SendStatus.FAILED } }.messages.last()
        viewModel.draftState.setTextAndPlaceCursorAtEnd("还有下一条草稿")

        cookies.setCookie(NodeSeekSite.BASE_URL, "session=renewed")
        session.sync()
        runCurrent()
        val resumed = viewModel.uiState.first { state ->
            !state.isRestoringDraft && state.messages.any { it.id == failed.id && it.failure?.recovery == MessageRecovery.RETRY }
        }
        assertEquals("还有下一条草稿", viewModel.draftState.text.toString())
        assertEquals("登录失效时发出的内容", resumed.messages.last().content)
        assertEquals(1, repository.sendCount)
        repository.sendError = null
        viewModel.retry(failed.id)
        viewModel.uiState.first { state -> state.messages.any { it.id == failed.id && it.status == SendStatus.SENT } }
        viewModel.viewModelScope.cancel()
        viewModel.viewModelScope.coroutineContext[kotlinx.coroutines.Job]!!.join()
        val saved = drafts.open(session.state.value.fingerprint, 4471, accountUid = 100).initial
        assertEquals("还有下一条草稿", saved.text)
        assertTrue(saved.pending.isEmpty())
    }

    @Test
    fun `switching to a different verified account never restores the previous private draft`() = runTest(dispatcher) {
        val drafts = draftStore(backgroundScope)
        val session = SessionRepository(SessionCookies(NodeSeekSite.CONFIG, cookies))
        var accountUid = 100L
        val viewModel = viewModel(FakeMessageRepository(), drafts = drafts, session = session, profiles = accountProfiles { accountUid })
        viewModel.uiState.first { !it.isRestoringDraft }
        viewModel.draftState.setTextAndPlaceCursorAtEnd("上一个账号的私信草稿")
        accountUid = 200L
        cookies.setCookie(NodeSeekSite.BASE_URL, "session=other-account")
        session.sync()
        runCurrent()
        viewModel.uiState.first { !it.isRestoringDraft }
        assertEquals("", viewModel.draftState.text.toString())
        assertTrue(viewModel.uiState.value.quotes.isEmpty())
        assertTrue(drafts.drafts(session.state.value.fingerprint).first().isEmpty())
        viewModel.viewModelScope.cancel()
        viewModel.viewModelScope.coroutineContext[kotlinx.coroutines.Job]!!.join()
    }

    private fun accountProfiles(uid: () -> Long) = object : ProfileRepository {
        override fun observeProfile(sessionFingerprint: Int) = flowOf(UserProfile(uid(), "self", ""))
        override suspend fun profile(refresh: Boolean) = UserProfile(uid(), "self", "")
        override suspend fun profile(uid: Long) = UserProfile(uid, "peer", "", rank = 4)
    }

    private fun draftStore(scope: kotlinx.coroutines.CoroutineScope): MessageDraftStore {
        val directory = Files.createTempDirectory("message-viewmodel").toFile().apply { deleteOnExit() }
        return SettingsRepository(PreferenceDataStoreFactory.create(storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { File(directory, "settings.preferences_pb").toOkioPath() }, scope = scope)).messageDrafts
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
        drafts: MessageDraftStore? = null,
    ) = MessageThreadViewModel(
        repository = repository,
        notifications = notifications,
        session = session,
        clock = clock,
        uploader = ImageUploader { _, _ -> "https://cdn.nodeimage.com/i/x.webp" },
        uid = 4471,
        userName = "iwil",
        profiles = profiles,
        drafts = drafts,
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
