package io.github.nodyssey.data.mail

import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.httpResponse
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MailUnifiedInboxTest {
    @Test
    fun `all mailboxes omit the filter and preserve server ordering and pagination in both connection modes`() = runTest {
        for (credential in listOf("sk_live_example", "web:example-session")) {
            val transport = MailTestTransport().apply {
                answer = { request ->
                    val offset = if (request.url.endsWith("offset=2")) 2 else 0
                    val rows = if (offset == 0) "${row(25, 9)},${row(24, 7)}" else row(23, 9)
                    httpResponse(page(credential, rows, total = 3, offset = offset))
                }
            }
            val client = SeekMailClient(transport)
            val first = client.inbox(credential, null, 0)
            val last = client.inbox(credential, null, first.nextOffset!!)

            assertEquals(listOf(25L, 24L, 23L), (first.messages + last.messages).map { it.id })
            assertEquals(listOf(9L, 7L, 9L), (first.messages + last.messages).map { it.mailboxId })
            assertEquals(listOf("b@example.org", "a@example.org"), first.messages.map { it.toAddress })
            assertNull(last.nextOffset)
            assertEquals(
                listOf("folder=inbox&limit=20&offset=0", "folder=inbox&limit=20&offset=2"),
                transport.requests.map { it.url.substringAfter('?') },
            )
        }
    }

    @Test
    fun `all read includes unloaded mail from different mailboxes and skips mail already read in both modes`() = runTest {
        for (credential in listOf("sk_live_example", "web:example-session")) {
            val transport = MailTestTransport().apply {
                answer = { request ->
                    when {
                        request.method == "PUT" -> httpResponse("{}")
                        request.url.endsWith("offset=2") -> httpResponse(page(credential, row(23, 9), total = 3, offset = 2))
                        else -> httpResponse(page(credential, "${row(25, 9, read = true)},${row(24, 7)}", total = 3))
                    }
                }
            }
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = DefaultMailRepository(MemoryMailCredentials(credential), transport, AppDispatchers(dispatcher, dispatcher))
            repository.initialize()

            assertEquals(2, repository.markAllRead(1, null))
            assertEquals(listOf("24", "23"), transport.requests.filter { it.method == "PUT" }.map { it.url.substringAfter("/mails/").substringBefore('/') })
            assertEquals(mapOf(25L to true, 24L to true, 23L to true), repository.readStates.value.values)
        }
    }

    @Test
    fun `unified monitoring detects arrivals in another mailbox and keeps its cursor separate from a single inbox`() = runTest {
        val credential = "sk_live_example"
        var rows = "${row(22, 7)},${row(21, 9)}"
        val store = MemoryMailCredentials(credential).apply { notificationCursors[7] = 99 }
        val transport = MailTestTransport().apply {
            answer = { request ->
                if (request.url.endsWith("/mailboxes")) httpResponse(MAILBOXES_JSON) else httpResponse(page(credential, rows, total = 2))
            }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultMailRepository(store, transport, AppDispatchers(dispatcher, dispatcher))
        assertNull(repository.checkForNewMessages())
        assertEquals(22L, store.notificationCursors[null])

        rows = "${row(24, 9)},${row(23, 7, read = true)}"
        assertEquals(MailArrival(null, 1, 1), repository.checkForNewMessages())
        assertEquals(24L, store.notificationCursors[null])
        assertEquals(99L, store.notificationCursors[7])
        assertNull(repository.inboxUpdates.value!!.mailboxId)
        assertEquals(listOf(9L, 7L), repository.inboxUpdates.value!!.page.messages.map { it.mailboxId })
        assertNull(repository.checkForNewMessages())
    }
}

private fun row(id: Long, mailboxId: Long, read: Boolean = false): String {
    val address = if (mailboxId == 7L) "a@example.org" else "b@example.org"
    return """{"id":$id,"mailboxId":$mailboxId,"toAddress":"$address","isRead":$read}"""
}

private fun page(credential: String, rows: String, total: Int, offset: Int = 0): String =
    if (credential.startsWith("web:")) {
        """{"mails":[$rows],"total":$total,"limit":20,"offset":$offset}"""
    } else {
        """{"data":[$rows],"pagination":{"total":$total,"limit":20,"offset":$offset}}"""
    }
