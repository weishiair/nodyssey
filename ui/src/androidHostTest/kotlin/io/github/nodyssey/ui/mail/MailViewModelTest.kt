package io.github.nodyssey.ui.mail

import io.github.nodyssey.data.mail.DefaultMailRepository
import io.github.nodyssey.data.mail.MailAuthMode
import io.github.nodyssey.data.mail.MailConnection
import io.github.nodyssey.data.mail.MailCredentialStore
import io.github.nodyssey.data.mail.MailError
import io.github.nodyssey.data.mail.MailException
import io.github.nodyssey.data.mail.MailMessage
import io.github.nodyssey.data.mail.MailPage
import io.github.nodyssey.data.mail.MailReadStates
import io.github.nodyssey.data.mail.MailRepository
import io.github.nodyssey.data.mail.Mailbox
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.HttpRequest
import io.github.plaza.core.net.HttpResponse
import io.github.plaza.core.net.HttpTransport
import io.github.plaza.core.net.UploadProgress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `global inbox checks update an empty inbox without another screen request and reject stale snapshots`() = runTest(dispatcher) {
        val repository = FakeMailRepository().apply { inboxAnswer = { _, _ -> MailPage(emptyList(), null, 0) } }
        val inbox = MailInboxViewModel(repository, AppDispatchers(dispatcher, dispatcher))
        advanceUntilIdle()
        val requests = repository.offsets.size
        repository.inboxUpdates.value = io.github.nodyssey.data.mail.MailInboxSnapshot(1, 7, MailPage(listOf(MAIL), null, 1))
        advanceUntilIdle()
        assertEquals(listOf(21L), inbox.uiState.value.messages.map { it.id })
        assertEquals(requests, repository.offsets.size)
        repository.inboxUpdates.value = io.github.nodyssey.data.mail.MailInboxSnapshot(0, 7, MailPage(emptyList(), null, 0))
        advanceUntilIdle()
        assertEquals(listOf(21L), inbox.uiState.value.messages.map { it.id })
        repository.inboxUpdates.value = io.github.nodyssey.data.mail.MailInboxSnapshot(1, 9, MailPage(emptyList(), null, 0))
        advanceUntilIdle()
        assertEquals(listOf(21L), inbox.uiState.value.messages.map { it.id })
    }

    @Test
    fun `an arrival while manual refresh is loading is applied after the older request completes`() = runTest(dispatcher) {
        val repository = FakeMailRepository().apply { inboxAnswer = { _, _ -> MailPage(emptyList(), null, 0) } }
        val inbox = MailInboxViewModel(repository, AppDispatchers(dispatcher, dispatcher))
        advanceUntilIdle()
        val gate = CompletableDeferred<MailPage>()
        repository.inboxAnswer = { _, _ -> gate.await() }
        inbox.refresh()
        advanceUntilIdle()
        assertTrue(inbox.uiState.value.loading)
        repository.inboxUpdates.value = io.github.nodyssey.data.mail.MailInboxSnapshot(1, 7, MailPage(listOf(MAIL), null, 1))
        advanceUntilIdle()
        gate.complete(MailPage(emptyList(), null, 0))
        advanceUntilIdle()
        assertFalse(inbox.uiState.value.loading)
        assertEquals(listOf(21L), inbox.uiState.value.messages.map { it.id })
    }

    @Test
    fun `api to native web switch keeps the current inbox through challenge and cancellation then reconnects`() = runTest(dispatcher) {
        val harness = NativeAuthorizationHarness(dispatcher)
        harness.saved = "sk_live_example"
        harness.forumCode = 468
        val viewModel = MailInboxViewModel(harness.repository, AppDispatchers(dispatcher, dispatcher))
        advanceUntilIdle()
        val generation = viewModel.uiState.value.generation
        viewModel.authorize()
        advanceUntilIdle()
        assertEquals(MailError.CHALLENGE, viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.connected)
        assertEquals(MailAuthMode.API_KEY, viewModel.uiState.value.authMode)
        assertEquals(generation, viewModel.uiState.value.generation)
        assertEquals(7L, viewModel.uiState.value.selectedMailboxId)
        assertEquals("sk_live_example", harness.saved)
        viewModel.authorizeOnWebsite()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.authorizing)
        viewModel.cancelAuthorization()
        viewModel.completeAuthorization("late-session")
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.authorizing)
        assertEquals("sk_live_example", harness.saved)
        harness.forumCode = 200
        viewModel.authorize()
        advanceUntilIdle()
        assertEquals("web:synthetic-session", harness.saved)
        assertTrue(viewModel.uiState.value.generation > generation)
        assertEquals(MailAuthMode.WEB_SESSION, viewModel.uiState.value.authMode)
        assertEquals(7L, viewModel.uiState.value.selectedMailboxId)
        assertFalse(viewModel.uiState.value.connecting)
    }

    @Test
    fun `authorization survives its reset event and cancellation rejects later token polls`() = runTest(dispatcher) {
        var saved = ""
        var calls = 0
        val gate = CompletableDeferred<Unit>()
        val store = object : MailCredentialStore {
            var defaultMailboxId: Long? = null

            override suspend fun read() = saved
            override suspend fun save(key: String) {
                saved = key
            }
            override suspend fun readDefaultMailboxId() = defaultMailboxId
            override suspend fun saveDefaultMailboxId(id: Long?) {
                defaultMailboxId = id
            }
            override suspend fun clearDefaultMailboxId() {
                defaultMailboxId = null
            }
            override suspend fun clear() {
                saved = ""
            }
        }
        val transport = object : HttpTransport {
            override suspend fun execute(request: HttpRequest, onUploadProgress: UploadProgress?): HttpResponse {
                calls++
                gate.await()
                return HttpResponse(
                    code = 200,
                    url = request.url,
                    headers = emptyMap(),
                    body = """{"owned":[{"id":7,"address":"a@example.org"}],"shared":[]}""",
                )
            }
        }
        val repository = DefaultMailRepository(store, transport, AppDispatchers(dispatcher, dispatcher))
        val viewModel = MailInboxViewModel(repository, AppDispatchers(dispatcher, dispatcher))
        advanceUntilIdle()
        viewModel.authorizeOnWebsite()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.authorizing)
        viewModel.completeAuthorization("example-session")
        viewModel.completeAuthorization("example-session")
        advanceUntilIdle()
        assertEquals(1, calls)
        viewModel.cancelAuthorization()
        viewModel.completeAuthorization("late-session")
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals("", saved)
        assertFalse(viewModel.uiState.value.authorizing)
        assertFalse(viewModel.uiState.value.connecting)
        assertFalse(repository.connection.value.connected)
    }

    @Test
    fun `native challenge stays on the inbox and a retry connects without opening the browser`() = runTest(dispatcher) {
        val harness = NativeAuthorizationHarness(dispatcher)
        harness.forumCode = 468
        val viewModel = MailInboxViewModel(harness.repository, AppDispatchers(dispatcher, dispatcher))
        advanceUntilIdle()
        viewModel.authorize()
        advanceUntilIdle()
        assertEquals(MailError.CHALLENGE, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.authorizing)
        assertFalse(viewModel.uiState.value.connecting)
        assertTrue(viewModel.uiState.value.challengeVerificationAvailable)
        assertEquals("", harness.saved)
        assertEquals(0, harness.callbacks)
        viewModel.openChallengeVerification()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.verifyingChallenge)
        harness.forumCode = 200
        viewModel.retryAfterChallengeVerification()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.connected)
        assertFalse(viewModel.uiState.value.connecting)
        assertFalse(viewModel.uiState.value.authorizing)
        assertNull(viewModel.uiState.value.error)
        assertEquals("web:synthetic-session", harness.saved)
        assertEquals(1, harness.callbacks)
        assertEquals(2, harness.grants)
    }

    @Test
    fun `selected mailbox remains the default after recreating the inbox and stale defaults recover`() = runTest(dispatcher) {
        val repository = FakeMailRepository()
        val first = MailInboxViewModel(repository, AppDispatchers(dispatcher, dispatcher))
        advanceUntilIdle()

        first.selectMailbox(9)
        advanceUntilIdle()
        assertEquals(9L, repository.savedDefaultMailboxId)

        val reopened = MailInboxViewModel(repository, AppDispatchers(dispatcher, dispatcher))
        advanceUntilIdle()
        assertEquals(9L, reopened.uiState.value.selectedMailboxId)

        repository.availableMailboxes = listOf(Mailbox(7, "a@example.org"))
        repository.savedDefaultMailboxId = 99
        val afterMailboxRemoval = MailInboxViewModel(repository, AppDispatchers(dispatcher, dispatcher))
        advanceUntilIdle()
        assertEquals(7L, afterMailboxRemoval.uiState.value.selectedMailboxId)
        assertEquals(7L, repository.savedDefaultMailboxId)
    }

    @Test
    fun `repeated native taps grant once and cancellation rejects a late signature before callback`() = runTest(dispatcher) {
        val harness = NativeAuthorizationHarness(dispatcher)
        val gate = CompletableDeferred<Unit>()
        harness.forumWait = { withContext(NonCancellable) { gate.await() } }
        val viewModel = MailInboxViewModel(harness.repository, AppDispatchers(dispatcher, dispatcher))
        advanceUntilIdle()
        viewModel.authorize()
        viewModel.authorize()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.connecting)
        assertFalse(viewModel.uiState.value.authorizing)
        assertEquals(1, harness.grants)
        viewModel.cancelAuthorization()
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.connecting)
        assertFalse(viewModel.uiState.value.connected)
        assertEquals("", harness.saved)
        assertEquals(0, harness.callbacks)
        harness.forumWait = {}
        viewModel.authorize()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.connected)
        assertEquals(2, harness.grants)
        assertEquals(1, harness.callbacks)
    }

    @Test
    fun `switching mailboxes ignores a late page from the previous mailbox`() = runTest(dispatcher) {
        val pending = CompletableDeferred<MailPage>()
        val repository = FakeMailRepository().apply {
            inboxAnswer = { mailbox, _ ->
                if (mailbox == 7L) {
                    withContext(NonCancellable) { pending.await() }
                } else {
                    MailPage(listOf(MailMessage(22, 9, subject = "second inbox")), null, 1)
                }
            }
        }
        val viewModel = MailInboxViewModel(repository, AppDispatchers(dispatcher, dispatcher))
        advanceUntilIdle()
        // Switching while the first inbox is loading must use the already known mailbox list.
        viewModel.selectMailbox(9)
        advanceUntilIdle()
        pending.complete(MailPage(listOf(MAIL), null, 1))
        advanceUntilIdle()
        assertEquals(9L, viewModel.uiState.value.selectedMailboxId)
        assertEquals(listOf(22L), viewModel.uiState.value.messages.map { it.id })
    }

    @Test
    fun `overlapping pages deduplicate and repeated load taps issue only one request`() = runTest(dispatcher) {
        val repository = FakeMailRepository().apply {
            inboxAnswer = { _, offset ->
                if (offset == 0) {
                    MailPage(listOf(MAIL), 1, 2)
                } else {
                    MailPage(listOf(MAIL, MailMessage(22, 7)), null, 2)
                }
            }
        }
        val viewModel = MailInboxViewModel(repository, AppDispatchers(dispatcher, dispatcher))
        advanceUntilIdle()
        viewModel.loadMore()
        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(listOf(21L, 22L), viewModel.uiState.value.messages.map { it.id })
        assertEquals(listOf(0, 1), repository.offsets)
        assertNull(viewModel.uiState.value.nextOffset)
    }

    @Test
    fun `all read prevents duplicate operations and mailbox switches and reports partial failure without success`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Int>()
        val repository = FakeMailRepository().apply { allReadAnswer = { gate.await() } }
        val inbox = MailInboxViewModel(repository, AppDispatchers(dispatcher, dispatcher))
        advanceUntilIdle()
        inbox.markAllRead()
        inbox.markAllRead()
        inbox.selectMailbox(9)
        advanceUntilIdle()
        assertEquals(listOf(7L), repository.allReadMailboxes)
        assertEquals(7L, inbox.uiState.value.selectedMailboxId)
        assertTrue(inbox.uiState.value.markingAllRead)
        gate.completeExceptionally(MailException(MailError.RATE_LIMITED))
        advanceUntilIdle()
        assertFalse(inbox.uiState.value.markingAllRead)
        assertFalse(inbox.uiState.value.allReadComplete)
        assertEquals(MailError.RATE_LIMITED, inbox.uiState.value.markAllReadError)
        repository.allReadAnswer = { 1 }
        inbox.markAllRead()
        advanceUntilIdle()
        assertNull(inbox.uiState.value.markAllReadError)
        assertTrue(inbox.uiState.value.allReadComplete)
    }

    @Test
    fun `a late all read completion cannot report success for a replacement account`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Int>()
        val repository = FakeMailRepository().apply { allReadAnswer = { withContext(NonCancellable) { gate.await() } } }
        val inbox = MailInboxViewModel(repository, AppDispatchers(dispatcher, dispatcher))
        advanceUntilIdle()
        inbox.markAllRead()
        advanceUntilIdle()
        repository.connect("synthetic-replacement")
        advanceUntilIdle()
        gate.complete(1)
        advanceUntilIdle()
        assertFalse(inbox.uiState.value.markingAllRead)
        assertFalse(inbox.uiState.value.allReadComplete)
        assertNull(inbox.uiState.value.markAllReadError)
        assertEquals(2L, inbox.uiState.value.generation)
    }

    @Test
    fun `read changes reach the inbox and a refused write restores unread state`() = runTest(dispatcher) {
        val response = CompletableDeferred<Unit>()
        val repository = FakeMailRepository().apply {
            readAnswer = {
                response.await()
                throw MailException(MailError.PERMISSION)
            }
        }
        val inbox = MailInboxViewModel(repository, AppDispatchers(dispatcher, dispatcher))
        advanceUntilIdle()
        val detail = MailDetailViewModel(repository, 7, 21)
        advanceUntilIdle()
        assertTrue(detail.uiState.value.message!!.isRead)
        assertTrue(inbox.uiState.value.messages.single().isRead)
        response.complete(Unit)
        advanceUntilIdle()
        assertFalse(detail.uiState.value.message!!.isRead)
        assertFalse(inbox.uiState.value.messages.single().isRead)
        assertEquals(MailError.PERMISSION, detail.uiState.value.error)
    }

    @Test
    fun `disconnect clears open details and a later connection cannot revive the old route`() = runTest(dispatcher) {
        val repository = FakeMailRepository().apply { detailMail = MAIL.copy(bodyText = "Your verification code: 012345") }
        val inbox = MailInboxViewModel(repository, AppDispatchers(dispatcher, dispatcher))
        val detail = MailDetailViewModel(repository, 7, 21)
        advanceUntilIdle()
        assertEquals("Your verification code: 012345", detail.uiState.value.body)
        assertEquals("012345", detail.uiState.value.verificationCode)
        repository.disconnect()
        advanceUntilIdle()
        assertTrue(detail.uiState.value.expired)
        assertNull(detail.uiState.value.message)
        assertEquals("", detail.uiState.value.body)
        assertNull(detail.uiState.value.verificationCode)
        assertTrue(inbox.uiState.value.messages.isEmpty())
        repository.connect("ignored")
        advanceUntilIdle()
        assertTrue(detail.uiState.value.expired)
        assertNull(detail.uiState.value.message)
    }
}

private val MAIL = MailMessage(21, 7, bodyText = "private words")

private class NativeAuthorizationHarness(dispatcher: kotlinx.coroutines.CoroutineDispatcher) {
    var saved = ""
    var forumCode = 200
    var grants = 0
    var callbacks = 0
    var forumWait: suspend () -> Unit = {}
    private val store = object : MailCredentialStore {
        var defaultMailboxId: Long? = null

        override suspend fun read() = saved
        override suspend fun clear() {
            saved = ""
        }
        override suspend fun save(key: String) {
            saved = key
        }

        override suspend fun readDefaultMailboxId() = defaultMailboxId

        override suspend fun saveDefaultMailboxId(id: Long?) {
            defaultMailboxId = id
        }

        override suspend fun clearDefaultMailboxId() {
            defaultMailboxId = null
        }
    }
    private val forum = object : HttpTransport {
        override suspend fun execute(request: HttpRequest, onUploadProgress: UploadProgress?): HttpResponse {
            grants++
            forumWait()
            return HttpResponse(forumCode, request.url, emptyMap(), """{"success":true,"links":["https://seek.li"],"data":{"id":123},"wtf":456,"sign":"synthetic"}""")
        }
    }
    private val mail = object : HttpTransport {
        override suspend fun execute(request: HttpRequest, onUploadProgress: UploadProgress?): HttpResponse {
            val body = when {
                request.url.endsWith("/auth/providers") -> """{"providers":[{"id":"nodeseek","enabled":true,"type":"oauth_popup","providerType":"nodeseek","config":{"origin":"https://www.nodeseek.com","popup_url":"https://www.nodeseek.com/connect?target=NodeMailDev","source":"ns-connect","target":"NodeMailDev"}}]}"""

                request.url.endsWith("/callback") -> {
                    callbacks++
                    """{"token":"synthetic-session"}"""
                }

                request.url.endsWith("/mailboxes") -> if (request.url.contains("/v1/")) {
                    """{"data":[{"id":7,"address":"a@example.org"}]}"""
                } else {
                    """{"owned":[{"id":7,"address":"a@example.org"}]}"""
                }

                request.url.contains("/v1/") -> """{"data":null,"pagination":{"total":0,"offset":0,"limit":20}}"""

                else -> """{"mails":[],"total":0,"limit":20,"offset":0}"""
            }
            return HttpResponse(200, request.url, emptyMap(), body)
        }
    }
    val repository = DefaultMailRepository(store, mail, AppDispatchers(dispatcher, dispatcher), authorizationHttp = forum)
}

internal class FakeMailRepository : MailRepository {
    override val inboxUpdates = MutableStateFlow<io.github.nodyssey.data.mail.MailInboxSnapshot?>(null)
    override val arrivals = kotlinx.coroutines.flow.MutableSharedFlow<io.github.nodyssey.data.mail.MailArrival>(extraBufferCapacity = 16)
    override val supportsNativeAuthorization = true
    override val connection = MutableStateFlow(MailConnection(initialized = true, generation = 1, connected = true))
    override val readStates = MutableStateFlow(MailReadStates(generation = 1))
    val offsets = mutableListOf<Int>()
    var savedDefaultMailboxId: Long? = null
    var availableMailboxes = listOf(Mailbox(7, "a@example.org"), Mailbox(9, "b@example.org"))
    var inboxAnswer: suspend (Long, Int) -> MailPage = { _, _ -> MailPage(listOf(MAIL), null, 1) }
    var readAnswer: suspend () -> Unit = {}
    val allReadMailboxes = mutableListOf<Long>()
    var allReadAnswer: suspend () -> Int = { 0 }
    var detailMail = MAIL
    var polls = 0
    var pollAnswer: suspend () -> Unit = {}

    override suspend fun checkForNewMessages(): io.github.nodyssey.data.mail.MailArrival? {
        polls++
        pollAnswer()
        return null
    }

    override suspend fun initialize() = Unit

    override suspend fun beginAuthorization(): Long {
        disconnect()
        return connection.value.generation
    }

    override suspend fun completeAuthorization(generation: Long, token: String) {
        if (generation != connection.value.generation) throw kotlinx.coroutines.CancellationException()
        connection.value = connection.value.copy(connected = true)
    }

    override suspend fun authorize(generation: Long) = completeAuthorization(generation, "synthetic-session")

    override suspend fun cancelAuthorization(generation: Long) {
        if (generation == connection.value.generation) disconnect()
    }

    override suspend fun connect(key: String) {
        connection.value = MailConnection(true, connection.value.generation + 1, true)
        readStates.value = MailReadStates(connection.value.generation)
    }

    override suspend fun defaultMailboxId(generation: Long) = savedDefaultMailboxId

    override suspend fun setDefaultMailboxId(generation: Long, id: Long?) {
        savedDefaultMailboxId = id
    }

    override suspend fun disconnect() {
        connection.value = MailConnection(true, connection.value.generation + 1, false)
        readStates.value = MailReadStates(connection.value.generation)
    }

    override suspend fun mailboxes(generation: Long) = availableMailboxes

    override suspend fun inbox(generation: Long, mailboxId: Long, offset: Int): MailPage {
        offsets += offset
        return inboxAnswer(mailboxId, offset)
    }

    override suspend fun detail(generation: Long, mailboxId: Long, id: Long) = detailMail

    override suspend fun markAllRead(generation: Long, mailboxId: Long): Int {
        allReadMailboxes += mailboxId
        return allReadAnswer()
    }

    override suspend fun markRead(generation: Long, mail: MailMessage, read: Boolean) {
        readStates.value = MailReadStates(generation, mapOf(mail.id to read))
        try {
            readAnswer()
        } catch (error: Exception) {
            readStates.value = MailReadStates(generation)
            throw error
        }
    }
}
