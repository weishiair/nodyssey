package io.github.nodyssey.ui.mail

import io.github.nodyssey.data.mail.DefaultMailRepository
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
    fun `authorization survives its reset event and cancellation rejects later token polls`() = runTest(dispatcher) {
        var saved = ""
        var calls = 0
        val gate = CompletableDeferred<Unit>()
        val store = object : MailCredentialStore {
            override suspend fun read() = saved
            override suspend fun save(key: String) {
                saved = key
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
        val viewModel = MailInboxViewModel(repository)
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
        val viewModel = MailInboxViewModel(harness.repository)
        advanceUntilIdle()
        viewModel.authorize()
        advanceUntilIdle()
        assertEquals(MailError.CHALLENGE, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.authorizing)
        assertFalse(viewModel.uiState.value.connecting)
        assertEquals("", harness.saved)
        assertEquals(0, harness.callbacks)
        harness.forumCode = 200
        viewModel.authorize()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.connected)
        assertFalse(viewModel.uiState.value.connecting)
        assertFalse(viewModel.uiState.value.authorizing)
        assertNull(viewModel.uiState.value.error)
        assertEquals("web:synthetic-session", harness.saved)
        assertEquals(1, harness.callbacks)
    }

    @Test
    fun `repeated native taps grant once and cancellation rejects a late signature before callback`() = runTest(dispatcher) {
        val harness = NativeAuthorizationHarness(dispatcher)
        val gate = CompletableDeferred<Unit>()
        harness.forumWait = { withContext(NonCancellable) { gate.await() } }
        val viewModel = MailInboxViewModel(harness.repository)
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
        val viewModel = MailInboxViewModel(repository)
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
        val viewModel = MailInboxViewModel(repository)
        advanceUntilIdle()
        viewModel.loadMore()
        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(listOf(21L, 22L), viewModel.uiState.value.messages.map { it.id })
        assertEquals(listOf(0, 1), repository.offsets)
        assertNull(viewModel.uiState.value.nextOffset)
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
        val inbox = MailInboxViewModel(repository)
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
        val repository = FakeMailRepository()
        val inbox = MailInboxViewModel(repository)
        val detail = MailDetailViewModel(repository, 7, 21)
        advanceUntilIdle()
        assertEquals("private words", detail.uiState.value.body)
        repository.disconnect()
        advanceUntilIdle()
        assertTrue(detail.uiState.value.expired)
        assertNull(detail.uiState.value.message)
        assertEquals("", detail.uiState.value.body)
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
        override suspend fun read() = saved
        override suspend fun clear() {
            saved = ""
        }
        override suspend fun save(key: String) {
            saved = key
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

                request.url.endsWith("/mailboxes") -> """{"owned":[{"id":7,"address":"a@example.org"}]}"""

                else -> """{"mails":[],"total":0,"limit":20,"offset":0}"""
            }
            return HttpResponse(200, request.url, emptyMap(), body)
        }
    }
    val repository = DefaultMailRepository(store, mail, AppDispatchers(dispatcher, dispatcher), authorizationHttp = forum)
}

private class FakeMailRepository : MailRepository {
    override val supportsNativeAuthorization = true
    override val connection = MutableStateFlow(MailConnection(initialized = true, generation = 1, connected = true))
    override val readStates = MutableStateFlow(MailReadStates(generation = 1))
    val offsets = mutableListOf<Int>()
    var inboxAnswer: suspend (Long, Int) -> MailPage = { _, _ -> MailPage(listOf(MAIL), null, 1) }
    var readAnswer: suspend () -> Unit = {}

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

    override suspend fun disconnect() {
        connection.value = MailConnection(true, connection.value.generation + 1, false)
        readStates.value = MailReadStates(connection.value.generation)
    }

    override suspend fun mailboxes(generation: Long) = listOf(Mailbox(7, "a@example.org"), Mailbox(9, "b@example.org"))

    override suspend fun inbox(generation: Long, mailboxId: Long, offset: Int): MailPage {
        offsets += offset
        return inboxAnswer(mailboxId, offset)
    }

    override suspend fun detail(generation: Long, mailboxId: Long, id: Long) = MAIL

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
