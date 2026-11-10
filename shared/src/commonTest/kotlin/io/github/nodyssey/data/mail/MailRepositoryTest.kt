package io.github.nodyssey.data.mail

import io.github.nodyssey.core.html.Fixtures
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.HttpBody
import io.github.plaza.core.net.httpResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MailRepositoryTest {
    @Test
    fun `inbox explicitly filters mailbox and advances by consumed rows on a short page`() = runTest {
        val transport = MailTestTransport().apply {
            answer = { httpResponse("""{"data":[$MAIL_JSON],"pagination":{"total":43,"limit":20,"offset":40}}""") }
        }
        val client = SeekMailClient(transport)
        val page = client.inbox("sk_live_example", mailboxId = 7, offset = 40)
        assertEquals("https://seek.li/openapi/v1/mails?mailbox_id=7&folder=inbox&limit=20&offset=40", transport.requests.single().url)
        assertEquals("Bearer sk_live_example", transport.requests.single().headers["Authorization"])
        assertEquals(41, page.nextOffset)
        assertEquals(21L, page.messages.single().id)
    }

    @Test
    fun `an empty last page stops pagination and a foreign mailbox response is rejected`() = runTest {
        val transport = MailTestTransport()
        val client = SeekMailClient(transport)
        transport.answer = { httpResponse("""{"data":[],"pagination":{"total":43,"limit":20,"offset":40}}""") }
        assertEquals(null, client.inbox("key", 7, 40).nextOffset)
        transport.answer = { httpResponse("""{"data":[$MAIL_JSON],"pagination":{"total":1,"limit":20,"offset":0}}""") }
        assertEquals(MailError.INVALID_RESPONSE, assertFailsWith<MailException> { client.inbox("key", 9, 0) }.error)
        transport.answer = { httpResponse("""{"data":$MAIL_JSON}""") }
        assertEquals(MailError.NOT_FOUND, assertFailsWith<MailException> { client.detail("key", 9, 21) }.error)
    }

    @Test
    fun `API empty inbox accepts null rows only when pagination confirms there are no messages`() = runTest {
        val transport = MailTestTransport().apply {
            answer = { httpResponse(Fixtures.load("mail/api-empty-inbox.json")) }
        }
        val client = SeekMailClient(transport)

        assertEquals(MailPage(emptyList(), nextOffset = null, total = 0), client.inbox("sk_live_example", 7, 0))

        transport.answer = { httpResponse("""{"data":null,"pagination":{"total":1,"limit":20,"offset":0}}""") }
        assertEquals(MailError.INVALID_RESPONSE, assertFailsWith<MailException> { client.inbox("sk_live_example", 7, 0) }.error)
        transport.answer = { httpResponse("""{"pagination":{"total":0,"limit":20,"offset":0}}""") }
        assertEquals(MailError.INVALID_RESPONSE, assertFailsWith<MailException> { client.inbox("sk_live_example", 7, 0) }.error)
    }

    @Test
    fun `API detail accepts nullable fields from the published mail schema`() = runTest {
        val transport = MailTestTransport().apply {
            answer = {
                httpResponse(
                    """
                    {
                      "data": {
                        "mail": {
                          "id": 21,
                          "mailboxId": 7,
                          "fromAddress": null,
                          "fromName": null,
                          "toAddress": null,
                          "subject": "Hello",
                          "bodyText": null,
                          "bodyHtml": "<p>Private words</p>",
                          "isRead": false,
                          "hasAttachments": false,
                          "receivedAt": "2026-11-09T03:00:00Z",
                          "tags": []
                        },
                        "tags": [],
                        "authInfo": null
                      }
                    }
                    """.trimIndent(),
                )
            }
        }

        val mail = SeekMailClient(transport).detail("sk_live_example", mailboxId = 7, id = 21)

        assertEquals("", mail.fromAddress)
        assertEquals("", mail.toAddress)
        assertEquals("<p>Private words</p>", mail.bodyHtml)
    }

    @Test
    fun `refused keys and storage failures never publish a connected account`() = runTest {
        val transport = MailTestTransport()
        val store = MemoryMailCredentials()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(store, transport, AppDispatchers(dispatcher, dispatcher))
        transport.answer = { httpResponse("""{"error":{"message":"private server text"}}""", code = 401) }
        assertEquals(MailError.UNAUTHORIZED, assertFailsWith<MailException> { repository.connect("sk_live_example") }.error)
        assertEquals("", store.key)
        assertFalse(repository.connection.value.connected)
        transport.answer = { httpResponse(MAILBOXES_JSON) }
        store.refuseSave = true
        assertEquals(MailError.STORAGE, assertFailsWith<MailException> { repository.connect("sk_live_example") }.error)
        assertFalse(repository.connection.value.connected)
    }

    @Test
    fun `a rejected API key leaves the current connection usable`() = runTest {
        val transport = MailTestTransport()
        val store = MemoryMailCredentials("sk_live_previous")
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(store, transport, AppDispatchers(dispatcher, dispatcher))
        repository.initialize()
        transport.answer = { request ->
            if (request.headers["Authorization"] == "Bearer sk_live_next") {
                httpResponse("{}", code = 401)
            } else {
                httpResponse(MAILBOXES_JSON)
            }
        }

        assertEquals(MailError.UNAUTHORIZED, assertFailsWith<MailException> { repository.connect("sk_live_next") }.error)
        assertEquals("sk_live_previous", store.key)
        assertEquals(MailAuthMode.API_KEY, repository.connection.value.authMode)
        assertTrue(repository.connection.value.connected)
    }

    @Test
    fun `replacing the credential resets mailbox selection and notification baseline`() = runTest {
        val store = MemoryMailCredentials("web:previous-token").apply {
            defaultMailboxId = 9
            notificationCursors[9] = 120
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(store, MailTestTransport(), AppDispatchers(dispatcher, dispatcher))
        repository.initialize()

        repository.connect("sk_live_replacement")

        assertEquals(MailAuthMode.API_KEY, repository.connection.value.authMode)
        assertEquals(null, store.defaultMailboxId)
        assertEquals(emptyMap(), store.notificationCursors)
    }

    @Test
    fun `mail polling establishes a baseline and reports only later message ids`() = runTest {
        var page = listOf(21L)
        val transport = MailTestTransport().apply {
            answer = { request ->
                if (request.url.endsWith("/mailboxes")) {
                    httpResponse(MAILBOXES_JSON)
                } else {
                    val messages = page.joinToString(",") { id -> """{"id":$id,"mailboxId":7}""" }
                    httpResponse("""{"data":[$messages],"pagination":{"total":${page.size},"limit":20,"offset":0}}""")
                }
            }
        }
        val store = MemoryMailCredentials("sk_live_example")
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(store, transport, AppDispatchers(dispatcher, dispatcher))

        assertEquals(null, repository.checkForNewMessages())
        assertEquals(21L, store.notificationCursors[7L])
        assertEquals(null, repository.checkForNewMessages())

        page = listOf(24L, 23L, 21L)
        assertEquals(MailArrival(mailboxId = 7, count = 2, generation = 1), repository.checkForNewMessages())
        assertEquals(null, repository.checkForNewMessages())
    }

    @Test
    fun `an empty inbox first arrival reaches observers once across concurrent foreground and worker checks`() = runTest {
        var rows = ""
        val transport = MailTestTransport().apply {
            answer = { request ->
                if (request.url.endsWith("/mailboxes")) {
                    httpResponse(MAILBOXES_JSON)
                } else {
                    httpResponse("""{"data":[$rows],"pagination":{"total":${if (rows.isEmpty()) 0 else 1},"limit":20,"offset":0}}""")
                }
            }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(MemoryMailCredentials("sk_live_example"), transport, AppDispatchers(dispatcher, dispatcher))
        val arrivals = mutableListOf<MailArrival>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.arrivals.collect { arrivals += it } }
        assertEquals(null, repository.checkForNewMessages())
        assertEquals(emptyList(), repository.inboxUpdates.value!!.page.messages)

        rows = MAIL_JSON
        val foreground = async { repository.checkForNewMessages() }
        val worker = async { repository.checkForNewMessages() }
        assertEquals(listOf(MailArrival(7, 1, 1)), listOfNotNull(foreground.await(), worker.await()))
        assertEquals(listOf(MailArrival(7, 1, 1)), arrivals)
        assertEquals(21L, repository.inboxUpdates.value!!.page.messages.single().id)
        repository.disconnect()
        assertEquals(null, repository.inboxUpdates.value)
    }

    @Test
    fun `switching the default inbox during a poll cannot publish or advance the old inbox`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val store = MemoryMailCredentials("sk_live_example").apply {
            defaultMailboxId = 7
            notificationCursors[7] = 0
        }
        val transport = MailTestTransport().apply {
            answer = { request ->
                if (request.url.endsWith("/mailboxes")) {
                    httpResponse(MAILBOXES_JSON)
                } else {
                    gate.await()
                    httpResponse("""{"data":[$MAIL_JSON],"pagination":{"total":1,"limit":20,"offset":0}}""")
                }
            }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(store, transport, AppDispatchers(dispatcher, dispatcher))
        val pending = async { repository.checkForNewMessages() }
        runCurrent()
        repository.setDefaultMailboxId(1, 9)
        gate.complete(Unit)
        assertEquals(null, pending.await())
        assertEquals(mapOf(7L to 0L), store.notificationCursors)
        assertEquals(null, repository.inboxUpdates.value)
    }

    @Test
    fun `a new id already read on the website advances the cursor without a new mail alert`() = runTest {
        val store = MemoryMailCredentials("sk_live_example").apply {
            notificationCursors[7] = 0
        }
        val transport = MailTestTransport().apply {
            answer = { request ->
                if (request.url.endsWith("/mailboxes")) {
                    httpResponse(MAILBOXES_JSON)
                } else {
                    httpResponse("""{"data":[{"id":21,"mailboxId":7,"isRead":true}],"pagination":{"total":1,"limit":20,"offset":0}}""")
                }
            }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(store, transport, AppDispatchers(dispatcher, dispatcher))
        assertEquals(null, repository.checkForNewMessages())
        assertEquals(21L, store.notificationCursors[7])
        assertTrue(repository.inboxUpdates.value!!.page.messages.single().isRead)
    }

    @Test
    fun `a previous account poll cannot publish a page or cursor after replacement`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val store = MemoryMailCredentials("sk_live_example")
        val transport = MailTestTransport().apply {
            answer = { request ->
                if (request.url.endsWith("/mailboxes")) {
                    httpResponse(MAILBOXES_JSON)
                } else {
                    gate.await()
                    httpResponse("""{"data":[$MAIL_JSON],"pagination":{"total":1,"limit":20,"offset":0}}""")
                }
            }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(store, transport, AppDispatchers(dispatcher, dispatcher))
        val pending = async { repository.checkForNewMessages() }
        runCurrent()
        repository.connect("sk_live_replacement")
        gate.complete(Unit)
        assertFailsWith<CancellationException> { pending.await() }
        assertEquals(null, repository.inboxUpdates.value)
        assertEquals(emptyMap(), store.notificationCursors)
    }

    @Test
    fun `frequent checks reuse the mailbox catalog but refresh after expiry or a removed mailbox`() = runTest {
        var now = 0L
        var catalogRequests = 0
        var removeMailbox = false
        val transport = MailTestTransport().apply {
            answer = { request ->
                when {
                    request.url.endsWith("/mailboxes") -> {
                        catalogRequests++
                        httpResponse(if (removeMailbox) """{"data":[{"id":9,"address":"b@example.org"}]}""" else MAILBOXES_JSON)
                    }

                    request.url.contains("mailbox_id=7") && removeMailbox -> httpResponse("{}", code = 404)

                    else -> httpResponse("""{"data":[],"pagination":{"total":0,"limit":20,"offset":0}}""")
                }
            }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = MemoryMailCredentials("sk_live_example")
        val repository = DefaultMailRepository(store, transport, AppDispatchers(dispatcher, dispatcher), clock = io.github.plaza.core.AppClock { now })
        repository.checkForNewMessages()
        now = 30_000
        repository.checkForNewMessages()
        assertEquals(1, catalogRequests)
        now = 300_000
        repository.checkForNewMessages()
        assertEquals(2, catalogRequests)
        removeMailbox = true
        assertEquals(MailError.NOT_FOUND, assertFailsWith<MailException> { repository.checkForNewMessages() }.error)
        repository.checkForNewMessages()
        assertEquals(3, catalogRequests)
        assertEquals(9L, store.defaultMailboxId)
        assertEquals(9L, repository.inboxUpdates.value!!.mailboxId)
    }

    @Test
    fun `an older first page arriving late cannot replace a newer inbox snapshot`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val transport = MailTestTransport().apply {
            answer = {
                val first = ++calls == 1
                if (first) gate.await()
                val id = if (first) 21 else 22
                httpResponse("""{"data":[{"id":$id,"mailboxId":7}],"pagination":{"total":1,"limit":20,"offset":0}}""")
            }
        }
        val store = MemoryMailCredentials("sk_live_example").apply { defaultMailboxId = 7 }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(store, transport, AppDispatchers(dispatcher, dispatcher))
        repository.initialize()
        val pending = async { repository.inbox(1, 7) }
        runCurrent()
        repository.inbox(1, 7)
        gate.complete(Unit)
        pending.await()
        assertEquals(22L, repository.inboxUpdates.value!!.page.messages.single().id)
    }

    @Test
    fun `overlapping refreshes reconcile the newest website read status even when the older request finishes first`() = runTest {
        val firstGate = CompletableDeferred<Unit>()
        val secondGate = CompletableDeferred<Unit>()
        var calls = 0
        val transport = MailTestTransport().apply {
            answer = {
                val first = ++calls == 1
                if (first) firstGate.await() else secondGate.await()
                httpResponse("""{"data":[{"id":21,"mailboxId":7,"isRead":${!first}}],"pagination":{"total":1,"limit":20,"offset":0}}""")
            }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(MemoryMailCredentials("sk_live_example").apply { defaultMailboxId = 7 }, transport, AppDispatchers(dispatcher, dispatcher))
        repository.initialize()
        val first = async { repository.inbox(1, 7) }
        runCurrent()
        val second = async { repository.inbox(1, 7) }
        runCurrent()
        firstGate.complete(Unit)
        first.await()
        secondGate.complete(Unit)
        second.await()
        assertEquals(true, repository.readStates.value.values[21])
        assertTrue(repository.inboxUpdates.value!!.page.messages.single().isRead)
    }

    @Test
    fun `disconnect invalidates an in flight connection before it can persist the key`() = runTest {
        val response = CompletableDeferred<Unit>()
        val transport = MailTestTransport().apply {
            answer = {
                response.await()
                httpResponse(MAILBOXES_JSON)
            }
        }
        val store = MemoryMailCredentials()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(store, transport, AppDispatchers(dispatcher, dispatcher))
        val connect = async { repository.connect("sk_live_example") }
        runCurrent()
        repository.disconnect()
        response.complete(Unit)
        assertFailsWith<CancellationException> { connect.await() }
        assertEquals("", store.key)
        assertFalse(repository.connection.value.connected)
    }

    @Test
    fun `mailbox preference survives reauthorization and clears on disconnect`() = runTest {
        val store = MemoryMailCredentials().apply { defaultMailboxId = 9 }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(store, MailTestTransport(), AppDispatchers(dispatcher, dispatcher))

        repository.initialize()
        repository.beginAuthorization()
        assertEquals(9L, store.defaultMailboxId)
        repository.disconnect()

        assertEquals(null, store.defaultMailboxId)
    }

    @Test
    fun `a late inbox cannot return data after disconnect`() = runTest {
        val response = CompletableDeferred<Unit>()
        val transport = MailTestTransport().apply {
            answer = {
                response.await()
                httpResponse("""{"data":[$MAIL_JSON],"pagination":{"total":1,"limit":20,"offset":0}}""")
            }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(MemoryMailCredentials("sk_live_example"), transport, AppDispatchers(dispatcher, dispatcher))
        repository.initialize()
        val pending = async { repository.inbox(repository.connection.value.generation, 7) }
        runCurrent()
        repository.disconnect()
        response.complete(Unit)
        assertFailsWith<CancellationException> { pending.await() }
    }

    @Test
    fun `read state is optimistic and rolls back when permission is refused`() = runTest {
        val response = CompletableDeferred<Unit>()
        val transport = MailTestTransport().apply {
            answer = {
                response.await()
                httpResponse("{}", code = 403)
            }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(MemoryMailCredentials("sk_live_example"), transport, AppDispatchers(dispatcher, dispatcher))
        repository.initialize()
        val mark = async {
            runCatching { repository.markRead(repository.connection.value.generation, MailMessage(21, 7), true) }
        }
        runCurrent()
        assertEquals(true, repository.readStates.value.values[21])
        assertEquals("PUT", transport.requests.single().method)
        assertEquals(HttpBody.Text("{\"read\":true}"), transport.requests.single().body)
        response.complete(Unit)
        assertEquals(MailError.PERMISSION, (mark.await().exceptionOrNull() as MailException).error)
        assertEquals(emptyMap(), repository.readStates.value.values)
    }

    @Test
    fun `a previous connection's pending read cannot block or roll back the next connection`() = runTest {
        val response = CompletableDeferred<Unit>()
        val transport = MailTestTransport().apply {
            answer = { request ->
                if (request.method == "PUT" && request.headers["Authorization"] == "Bearer sk_live_previous") {
                    response.await()
                    httpResponse("{}", code = 403)
                } else {
                    httpResponse(MAILBOXES_JSON)
                }
            }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(MemoryMailCredentials("sk_live_previous"), transport, AppDispatchers(dispatcher, dispatcher))
        repository.initialize()
        val previousGeneration = repository.connection.value.generation
        val previousWrite = async {
            runCatching { repository.markRead(previousGeneration, MailMessage(21, 7), true) }
        }
        runCurrent()
        repository.disconnect()
        repository.connect("sk_live_next")
        val nextGeneration = repository.connection.value.generation
        repository.markRead(nextGeneration, MailMessage(21, 7), false)
        response.complete(Unit)
        previousWrite.await()
        assertEquals(nextGeneration, repository.readStates.value.generation)
        assertEquals(mapOf(21L to false), repository.readStates.value.values)
    }

    @Test
    fun `refresh observes website read changes but a page already in flight cannot undo a newer write`() = runTest {
        val transport = MailTestTransport()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(MemoryMailCredentials("sk_live_example"), transport, AppDispatchers(dispatcher, dispatcher))
        repository.initialize()
        val generation = repository.connection.value.generation
        repository.markRead(generation, MailMessage(21, 7), true)
        transport.answer = { httpResponse("""{"data":[$MAIL_JSON],"pagination":{"total":1,"limit":20,"offset":0}}""") }
        repository.inbox(generation, 7)
        // A lagging inbox must acknowledge the successful write before a later remote change wins.
        assertEquals(true, repository.readStates.value.values[21])
        transport.answer = { httpResponse("""{"data":[{"id":21,"mailboxId":7,"isRead":true}],"pagination":{"total":1,"limit":20,"offset":0}}""") }
        repository.inbox(generation, 7)
        transport.answer = { httpResponse("""{"data":[$MAIL_JSON],"pagination":{"total":1,"limit":20,"offset":0}}""") }
        repository.inbox(generation, 7)
        assertEquals(false, repository.readStates.value.values[21])
        val pageGate = CompletableDeferred<Unit>()
        transport.answer = { request ->
            if (request.method == "GET") {
                pageGate.await()
                httpResponse("""{"data":[$MAIL_JSON],"pagination":{"total":1,"limit":20,"offset":0}}""")
            } else {
                httpResponse("{}")
            }
        }
        val stalePage = async { repository.inbox(generation, 7) }
        runCurrent()
        repository.markRead(generation, MailMessage(21, 7), true)
        pageGate.complete(Unit)
        stalePage.await()
        assertEquals(true, repository.readStates.value.values[21])
    }

    @Test
    fun `a detail already marked read updates shared unread state and waits for inbox acknowledgement`() = runTest {
        var remoteRead = false
        val transport = MailTestTransport().apply {
            answer = { request ->
                if (request.url.endsWith("/mails/21")) {
                    httpResponse("""{"data":{"mail":{"id":21,"mailboxId":7,"isRead":true}}}""")
                } else {
                    httpResponse("""{"data":[{"id":21,"mailboxId":7,"isRead":$remoteRead}],"pagination":{"total":1,"limit":20,"offset":0}}""")
                }
            }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(MemoryMailCredentials("sk_live_example").apply { defaultMailboxId = 7 }, transport, AppDispatchers(dispatcher, dispatcher))
        repository.initialize()
        repository.inbox(1, 7)
        assertEquals(false, repository.readStates.value.values[21])
        assertTrue(repository.detail(1, 7, 21).isRead)
        assertEquals(true, repository.readStates.value.values[21])
        repository.inbox(1, 7)
        assertEquals(true, repository.readStates.value.values[21])
        remoteRead = true
        repository.inbox(1, 7)
        remoteRead = false
        repository.inbox(1, 7)
        assertEquals(false, repository.readStates.value.values[21])
    }

    @Test
    fun `cancelling a read rolls back even while a mailbox preference holds the control lock`() = runTest {
        val writeGate = CompletableDeferred<Unit>()
        val preferenceGate = CompletableDeferred<Unit>()
        val backing = MemoryMailCredentials("sk_live_example")
        val store = object : MailCredentialStore by backing {
            override suspend fun saveDefaultMailboxId(id: Long?) {
                preferenceGate.await()
                backing.saveDefaultMailboxId(id)
            }
        }
        val transport = MailTestTransport().apply {
            answer = {
                writeGate.await()
                httpResponse("{}")
            }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(store, transport, AppDispatchers(dispatcher, dispatcher))
        repository.initialize()
        val read = launch { repository.markRead(1, MailMessage(21, 7), true) }
        runCurrent()
        assertEquals(true, repository.readStates.value.values[21])
        val preference = launch { repository.setDefaultMailboxId(1, 7) }
        runCurrent()
        read.cancel()
        runCurrent()
        preferenceGate.complete(Unit)
        preference.join()
        read.join()
        assertEquals(emptyMap(), repository.readStates.value.values)
    }

    @Test
    fun `all read visits unloaded pages deduplicates shifted rows and leaves later arrivals unread in both modes`() = runTest {
        for (credential in listOf("sk_live_example", "web:synthetic-session")) {
            val web = credential.startsWith("web:")
            val transport = MailTestTransport().apply {
                answer = { request ->
                    if (request.method == "PUT") {
                        httpResponse("{}")
                    } else {
                        assertTrue(request.url.contains("mailbox_id=7&folder=inbox"))
                        val offset = request.url.substringAfter("offset=").toInt()
                        val rows = when (offset) {
                            0 -> """{"id":24,"mailboxId":7},{"id":23,"mailboxId":7,"isRead":true}"""
                            2 -> """{"id":25,"mailboxId":7},{"id":24,"mailboxId":7}"""
                            4 -> """{"id":23,"mailboxId":7,"isRead":true},{"id":22,"mailboxId":7}"""
                            else -> """{"id":21,"mailboxId":7}"""
                        }
                        httpResponse(
                            if (web) {
                                """{"mails":[$rows],"total":${if (offset == 0) 4 else 7},"limit":20,"offset":$offset}"""
                            } else {
                                """{"data":[$rows],"pagination":{"total":${if (offset == 0) 4 else 7},"limit":20,"offset":$offset}}"""
                            },
                        )
                    }
                }
            }
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = DefaultMailRepository(MemoryMailCredentials(credential).apply { defaultMailboxId = 7 }, transport, AppDispatchers(dispatcher, dispatcher))
            repository.initialize()
            assertEquals(3, repository.markAllRead(1, 7))
            assertEquals(listOf("24", "22", "21"), transport.requests.filter { it.method == "PUT" }.map { it.url.substringAfter("/mails/").substringBefore('/') })
            assertEquals(mapOf(24L to true, 23L to true, 22L to true, 21L to true), repository.readStates.value.values)

            transport.answer = {
                httpResponse(
                    if (web) {
                        """{"mails":[{"id":25,"mailboxId":7},{"id":24,"mailboxId":7}],"total":5,"limit":20,"offset":0}"""
                    } else {
                        """{"data":[{"id":25,"mailboxId":7},{"id":24,"mailboxId":7}],"pagination":{"total":5,"limit":20,"offset":0}}"""
                    },
                )
            }
            repository.inbox(1, 7)
            assertEquals(false, repository.readStates.value.values[25])
            assertEquals(true, repository.readStates.value.values[24])
        }
    }

    @Test
    fun `all read stops on a refused or limited write and retains successful changes`() = runTest {
        for ((code, error) in listOf(403 to MailError.PERMISSION, 429 to MailError.RATE_LIMITED)) {
            var writes = 0
            val transport = MailTestTransport().apply {
                answer = { request ->
                    if (request.method == "PUT") {
                        httpResponse("{}", code = if (++writes == 1) 200 else code)
                    } else {
                        httpResponse("""{"data":[{"id":24,"mailboxId":7},{"id":23,"mailboxId":7},{"id":22,"mailboxId":7}],"pagination":{"total":3,"limit":20,"offset":0}}""")
                    }
                }
            }
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = DefaultMailRepository(MemoryMailCredentials("sk_live_example"), transport, AppDispatchers(dispatcher, dispatcher))
            repository.initialize()
            assertEquals(error, assertFailsWith<MailException> { repository.markAllRead(1, 7) }.error)
            assertEquals(2, writes)
            assertEquals(mapOf(24L to true, 23L to false, 22L to false), repository.readStates.value.values)
            repository.inbox(1, 7)
            assertEquals(true, repository.readStates.value.values[24])
        }
    }

    @Test
    fun `replacing a connection during all read prevents further writes and stale read overrides`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var writes = 0
        val transport = MailTestTransport().apply {
            answer = { request ->
                when {
                    request.method == "PUT" -> {
                        writes++
                        gate.await()
                        httpResponse("{}")
                    }

                    request.url.endsWith("/mailboxes") -> httpResponse(MAILBOXES_JSON)

                    else -> httpResponse("""{"data":[{"id":24,"mailboxId":7},{"id":23,"mailboxId":7}],"pagination":{"total":2,"limit":20,"offset":0}}""")
                }
            }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(MemoryMailCredentials("sk_live_example"), transport, AppDispatchers(dispatcher, dispatcher))
        repository.initialize()
        val pending = async { runCatching { repository.markAllRead(1, 7) } }
        runCurrent()
        assertEquals(true, repository.readStates.value.values[24])
        repository.connect("sk_live_replacement")
        gate.complete(Unit)
        assertTrue(pending.await().exceptionOrNull() is CancellationException)
        assertEquals(1, writes)
        assertEquals(emptyMap(), repository.readStates.value.values)
    }

    @Test
    fun `HTML only mail keeps readable paragraphs and links while dropping active and tracking content`() {
        val text = MailBody.text(MailMessage(1, 7, bodyHtml = Fixtures.load("mail/html-only.html")))
        assertTrue(text.contains("Welcome & hello\n"))
        assertTrue(text.contains("First line\nSecond line"))
        assertTrue(text.contains("your account (https://example.com/verify?code=123&source=mail)"))
        assertTrue(text.contains("Code\t123456"))
        assertFalse(text.contains("stealSession"))
        assertFalse(text.contains("tracker.invalid"))
        assertFalse(text.contains("sendToken"))
        assertEquals("Text\nwith lines", MailBody.text(MailMessage(1, 7, bodyText = "Text\nwith lines", bodyHtml = "<p>different</p>")))
    }
}
