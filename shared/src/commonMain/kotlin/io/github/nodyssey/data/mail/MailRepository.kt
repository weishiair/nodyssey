package io.github.nodyssey.data.mail

import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.HttpTransport
import io.github.plaza.core.net.SessionCookieStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface MailRepository {
    val connection: StateFlow<MailConnection>
    val readStates: StateFlow<MailReadStates>

    suspend fun initialize()

    /** Verifies access before storing the credential. The key never crosses into saved UI state. */
    suspend fun connect(key: String)

    /** An attempt belongs to this generation; sign-out or cancellation invalidates its result. */
    suspend fun beginAuthorization(): Long

    suspend fun completeAuthorization(generation: Long, token: String)

    suspend fun cancelAuthorization(generation: Long)

    suspend fun disconnect()

    suspend fun mailboxes(generation: Long): List<Mailbox>

    suspend fun inbox(generation: Long, mailboxId: Long, offset: Int = 0): MailPage

    suspend fun detail(generation: Long, mailboxId: Long, id: Long): MailMessage

    suspend fun markRead(generation: Long, mail: MailMessage, read: Boolean)
}

class DefaultMailRepository(
    private val credentials: MailCredentialStore,
    http: HttpTransport,
    private val dispatchers: AppDispatchers,
    sessionCookies: SessionCookieStore? = null,
    sessionHttp: HttpTransport = http,
) : MailRepository {
    private val client = SeekMailClient(http, sessionCookies, sessionHttp)
    private val control = Mutex()
    private var readWrites = Mutex()
    private var key = ""
    private val _connection = MutableStateFlow(MailConnection())
    override val connection = _connection.asStateFlow()
    private val _readStates = MutableStateFlow(MailReadStates())
    override val readStates = _readStates.asStateFlow()

    override suspend fun initialize() = control.withLock {
        if (_connection.value.initialized) return@withLock
        key = try {
            credentials.read()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw MailException(MailError.STORAGE)
        }
        _connection.value = MailConnection(initialized = true, generation = 1, connected = key.isNotBlank())
        _readStates.value = MailReadStates(generation = 1)
    }

    override suspend fun connect(key: String) {
        val candidate = key.trim()
        if (!candidate.startsWith("sk_live_") || candidate.length <= 8 || candidate.length > 512 || candidate.any { it.code !in 33..126 }) {
            throw MailException(MailError.INVALID_KEY)
        }
        val generation = beginAuthorization()
        finishConnection(generation, candidate)
    }

    override suspend fun beginAuthorization(): Long = control.withLock {
        reset()
        credentials.clear()
        _connection.value.generation
    }

    override suspend fun completeAuthorization(generation: Long, token: String) {
        if (token.isBlank() || token.length > 8192 || token.any { it.code !in 33..126 }) {
            throw MailException(MailError.UNAUTHORIZED)
        }
        checkGeneration(generation, mustBeConnected = false)
        finishConnection(generation, "web:$token")
    }

    override suspend fun cancelAuthorization(generation: Long) = control.withLock {
        if (_connection.value.generation == generation && !_connection.value.connected) {
            reset()
            credentials.clear()
        }
    }

    private suspend fun finishConnection(generation: Long, candidate: String) {
        withContext(dispatchers.io) { client.mailboxes(candidate) }
        control.withLock {
            checkGeneration(generation, mustBeConnected = false)
            credentials.save(candidate)
            this.key = candidate
            _connection.value = _connection.value.copy(connected = true)
        }
    }

    override suspend fun disconnect() = control.withLock {
        // Publish first: even a disk failure must not leave private content on live screens.
        reset()
        credentials.clear()
    }

    private fun reset(error: MailError? = null) {
        key = ""
        readWrites = Mutex()
        val generation = _connection.value.generation + 1
        _readStates.value = MailReadStates(generation)
        _connection.value = MailConnection(initialized = true, generation = generation, error = error)
    }

    override suspend fun mailboxes(generation: Long): List<Mailbox> = request(generation) { client.mailboxes(it) }

    override suspend fun inbox(generation: Long, mailboxId: Long, offset: Int): MailPage {
        val before = readStates.value
        val canReconcile = !readWrites.isLocked
        val page = request(generation) { client.inbox(it, mailboxId, offset) }
        // A refresh started after the last write may observe a change made on the website. A page
        // already in flight when a local read changes must never undo that newer optimistic state.
        if (canReconcile) {
            _readStates.update {
                if (it.generation != generation || it.revision != before.revision || readWrites.isLocked) {
                    it
                } else {
                    it.copy(
                        values = it.values + page.messages.associate { mail -> mail.id to mail.isRead },
                        revision = it.revision + 1,
                    )
                }
            }
        }
        return page
    }

    override suspend fun detail(generation: Long, mailboxId: Long, id: Long): MailMessage =
        request(generation) {
            val mail = client.detail(it, mailboxId, id)
            mail.copy(bodyText = MailBody.text(mail), bodyHtml = null)
        }

    override suspend fun markRead(generation: Long, mail: MailMessage, read: Boolean) = readWrites.withLock {
        val (credential, before) = control.withLock {
            checkGeneration(generation)
            val before = _readStates.value.values[mail.id]
            _readStates.update { it.copy(values = it.values + (mail.id to read), revision = it.revision + 1) }
            key to before
        }
        try {
            withContext(dispatchers.io) { client.markRead(credential, mail.id, read) }
            checkGeneration(generation)
        } catch (error: Throwable) {
            if (error is MailException) invalidateExpired(generation, error)
            _readStates.update {
                if (it.generation != generation) {
                    it
                } else {
                    it.copy(
                        values = if (before == null) it.values - mail.id else it.values + (mail.id to before),
                        revision = it.revision + 1,
                    )
                }
            }
            throw error
        }
    }

    private suspend fun <T> request(generation: Long, block: suspend (String) -> T): T {
        val credential = credential(generation)
        val result = try {
            withContext(dispatchers.io) { block(credential) }
        } catch (error: MailException) {
            invalidateExpired(generation, error)
            throw error
        }
        checkGeneration(generation)
        return result
    }

    private suspend fun invalidateExpired(generation: Long, error: MailException) {
        if (error.error != MailError.UNAUTHORIZED) return
        control.withLock {
            if (_connection.value.generation == generation) {
                reset(MailError.UNAUTHORIZED)
                credentials.clear()
            }
        }
    }

    private suspend fun credential(generation: Long): String = control.withLock {
        checkGeneration(generation)
        key
    }

    private fun checkGeneration(generation: Long, mustBeConnected: Boolean = true) {
        val current = _connection.value
        if (current.generation != generation || (mustBeConnected && !current.connected)) {
            throw CancellationException("Mail connection changed")
        }
    }
}
