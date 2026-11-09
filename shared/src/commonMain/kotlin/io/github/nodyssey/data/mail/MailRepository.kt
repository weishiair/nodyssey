package io.github.nodyssey.data.mail

import io.github.plaza.core.AppClock
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.HttpTransport
import io.github.plaza.core.net.SessionCookieStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface MailRepository {
    val supportsNativeAuthorization: Boolean
    val connection: StateFlow<MailConnection>
    val readStates: StateFlow<MailReadStates>
    val inboxUpdates: StateFlow<MailInboxSnapshot?>
    val arrivals: SharedFlow<MailArrival>

    suspend fun initialize()

    /** Verifies access before storing the credential. The key never crosses into saved UI state. */
    suspend fun connect(key: String)

    suspend fun defaultMailboxId(generation: Long): Long?

    suspend fun setDefaultMailboxId(generation: Long, id: Long?)

    /** Returns an opaque attempt id. The current connection stays usable until verification succeeds. */
    suspend fun beginAuthorization(): Long

    /** Exchanges the current forum session through the official provider without a browser. */
    suspend fun authorize(generation: Long)

    suspend fun completeAuthorization(generation: Long, token: String)

    suspend fun cancelAuthorization(generation: Long)

    suspend fun disconnect()

    suspend fun mailboxes(generation: Long): List<Mailbox>

    suspend fun inbox(generation: Long, mailboxId: Long, offset: Int = 0): MailPage

    suspend fun detail(generation: Long, mailboxId: Long, id: Long): MailMessage

    suspend fun markRead(generation: Long, mail: MailMessage, read: Boolean)

    /** Marks the current inbox, including unloaded pages, without consuming mail arriving later. */
    suspend fun markAllRead(generation: Long, mailboxId: Long): Int

    /** Polls the selected inbox and advances its persisted cursor without treating old mail as new. */
    suspend fun checkForNewMessages(): MailArrival? = null
}

class DefaultMailRepository(
    private val credentials: MailCredentialStore,
    http: HttpTransport,
    private val dispatchers: AppDispatchers,
    sessionCookies: SessionCookieStore? = null,
    sessionHttp: HttpTransport = http,
    authorizationHttp: HttpTransport? = null,
    private val clock: AppClock = AppClock.System,
) : MailRepository {
    private val client = SeekMailClient(http, sessionCookies, sessionHttp)
    private val authorization = authorizationHttp?.let { SeekMailAuthorization(it, client) }
    override val supportsNativeAuthorization: Boolean get() = authorization != null
    private val control = Mutex()
    private val mailPoll = Mutex()
    private var readWrites = Mutex()
    private val pendingReads = mutableMapOf<Long, Boolean>()
    private var key = ""
    private var generationCounter = 0L
    private var mailboxCatalog: MailboxCatalog? = null
    private var inboxRequestSerial = 0L
    private val authorizationAttempt = MutableStateFlow<MailAuthorizationAttempt?>(null)
    private val _connection = MutableStateFlow(MailConnection())
    override val connection = _connection.asStateFlow()
    private val _readStates = MutableStateFlow(MailReadStates())
    override val readStates = _readStates.asStateFlow()
    private val _inboxUpdates = MutableStateFlow<MailInboxSnapshot?>(null)
    override val inboxUpdates = _inboxUpdates.asStateFlow()
    private val _arrivals = MutableSharedFlow<MailArrival>(extraBufferCapacity = 16)
    override val arrivals = _arrivals.asSharedFlow()

    override suspend fun initialize() = control.withLock {
        if (_connection.value.initialized) return@withLock
        key = try {
            credentials.read()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw MailException(MailError.STORAGE)
        }
        _connection.value = MailConnection(
            initialized = true,
            generation = 1,
            connected = key.isNotBlank(),
            authMode = key.authModeOrNull(),
        )
        _readStates.value = MailReadStates(generation = 1)
        generationCounter = 1
    }

    override suspend fun connect(key: String) {
        val candidate = key.trim()
        if (!candidate.startsWith("sk_live_") || candidate.length <= 8 || candidate.length > 512 || candidate.any { it.code !in 33..126 }) {
            throw MailException(MailError.INVALID_KEY)
        }
        initialize()
        val generation = control.withLock { _connection.value.generation }
        withContext(dispatchers.io) { client.mailboxes(candidate) }
        control.withLock {
            checkGeneration(generation, mustBeConnected = false)
            credentials.replace(candidate)
            this.key = candidate
            authorizationAttempt.value = null
            readWrites = Mutex()
            pendingReads.clear()
            val nextGeneration = ++generationCounter
            _readStates.value = MailReadStates(nextGeneration)
            _inboxUpdates.value = null
            mailboxCatalog = null
            _connection.value = _connection.value.copy(
                generation = nextGeneration,
                connected = true,
                error = null,
                authMode = MailAuthMode.API_KEY,
            )
        }
    }

    override suspend fun defaultMailboxId(generation: Long): Long? = control.withLock {
        checkGeneration(generation)
        credentials.readDefaultMailboxId()
    }

    override suspend fun setDefaultMailboxId(generation: Long, id: Long?) = control.withLock {
        checkGeneration(generation)
        if (credentials.readDefaultMailboxId() != id) _inboxUpdates.value = null
        credentials.saveDefaultMailboxId(id)
    }

    override suspend fun beginAuthorization(): Long {
        initialize()
        return control.withLock {
            if (!_connection.value.connected) {
                reset()
                credentials.clear()
            }
            val id = if (_connection.value.connected) ++generationCounter else _connection.value.generation
            authorizationAttempt.value = MailAuthorizationAttempt(id, _connection.value.generation)
            id
        }
    }

    override suspend fun completeAuthorization(generation: Long, token: String) {
        if (token.isBlank() || token.length > 8192 || token.any { it.code !in 33..126 }) {
            throw MailException(MailError.UNAUTHORIZED)
        }
        checkAuthorization(generation)
        finishConnection(generation, "web:$token")
    }

    override suspend fun authorize(generation: Long) {
        checkAuthorization(generation)
        val exchange = authorization ?: throw MailException(MailError.WEB_AUTHORIZATION)
        val token = withContext(dispatchers.io) { exchange.token { checkAuthorization(generation) } }
        completeAuthorization(generation, token)
    }

    override suspend fun cancelAuthorization(generation: Long) = control.withLock {
        if (authorizationAttempt.value?.id == generation) {
            authorizationAttempt.value = null
            if (!_connection.value.connected) {
                reset()
                credentials.clear()
            }
        }
    }

    private suspend fun finishConnection(generation: Long, candidate: String) {
        withContext(dispatchers.io) { client.mailboxes(candidate) }
        control.withLock {
            checkAuthorization(generation)
            credentials.replace(candidate)
            this.key = candidate
            authorizationAttempt.value = null
            readWrites = Mutex()
            pendingReads.clear()
            val nextGeneration = ++generationCounter
            _readStates.value = MailReadStates(nextGeneration)
            _inboxUpdates.value = null
            mailboxCatalog = null
            _connection.value = _connection.value.copy(
                generation = nextGeneration,
                connected = true,
                error = null,
                authMode = candidate.authModeOrNull(),
            )
        }
    }

    override suspend fun disconnect() = control.withLock {
        // Publish first: even a disk failure must not leave private content on live screens.
        reset()
        credentials.clear()
        credentials.clearDefaultMailboxId()
        credentials.clearNotificationCursors()
    }

    private fun reset(error: MailError? = null) {
        key = ""
        authorizationAttempt.value = null
        readWrites = Mutex()
        pendingReads.clear()
        val generation = ++generationCounter
        _readStates.value = MailReadStates(generation)
        _inboxUpdates.value = null
        mailboxCatalog = null
        _connection.value = MailConnection(initialized = true, generation = generation, error = error)
    }

    override suspend fun mailboxes(generation: Long): List<Mailbox> {
        val boxes = request(generation) { client.mailboxes(it) }
        control.withLock {
            checkGeneration(generation)
            mailboxCatalog = MailboxCatalog(generation, clock.nowMillis(), boxes)
        }
        return boxes
    }

    override suspend fun inbox(generation: Long, mailboxId: Long, offset: Int): MailPage {
        val serial = if (offset == 0) {
            control.withLock {
                checkGeneration(generation)
                ++inboxRequestSerial
            }
        } else {
            null
        }
        val before = readStates.value
        val canReconcile = !readWrites.isLocked
        val page = request(generation) { client.inbox(it, mailboxId, offset) }
        control.withLock {
            checkGeneration(generation)
            val latestHead = serial == null || serial == inboxRequestSerial
            // Only local writes advance the revision. Concurrent GETs must not block the latest
            // page from reconciling website reads, or let an older response undo an optimistic read.
            if (canReconcile && latestHead) {
                _readStates.update {
                    if (it.revision != before.revision || readWrites.isLocked) {
                        it
                    } else {
                        it.copy(
                            values = it.values + page.messages.associate { mail ->
                                val pending = pendingReads[mail.id]
                                // A successful write stays authoritative until the inbox has
                                // acknowledged it. An eventually consistent GET can lag the PUT.
                                if (pending == mail.isRead) pendingReads.remove(mail.id)
                                mail.id to (pending ?: mail.isRead)
                            },
                        )
                    }
                }
            }
            if (offset == 0 && latestHead && credentials.readDefaultMailboxId() == mailboxId) {
                _inboxUpdates.value = MailInboxSnapshot(generation, mailboxId, page)
            }
        }
        return page
    }

    override suspend fun detail(generation: Long, mailboxId: Long, id: Long): MailMessage {
        val before = readStates.value
        val canReconcile = !readWrites.isLocked
        val mail = request(generation) {
            val mail = client.detail(it, mailboxId, id)
            mail.copy(bodyText = MailBody.text(mail), bodyHtml = null)
        }
        return control.withLock {
            checkGeneration(generation)
            val current = _readStates.value
            if (canReconcile && !readWrites.isLocked && current.revision == before.revision) {
                val read = pendingReads[id] ?: mail.isRead
                // The detail endpoint can already report a message as read. Publish that
                // observation to the inbox and badge, and protect it from an older list response.
                if (read && current.values[id] != true) pendingReads[id] = true
                _readStates.value = current.copy(values = current.values + (id to read), revision = current.revision + 1)
            }
            mail.copy(isRead = _readStates.value.values[id] ?: mail.isRead)
        }
    }

    override suspend fun markRead(generation: Long, mail: MailMessage, read: Boolean) = readWrites.withLock {
        markReadLocked(generation, mail, read)
    }

    override suspend fun markAllRead(generation: Long, mailboxId: Long): Int = readWrites.withLock {
        // Both connection modes provide the single-message read endpoint. Capture the
        // existing pages before writing, then issue sequential updates with no guessed batch API.
        val head = inbox(generation, mailboxId)
        val newestId = head.messages.maxOfOrNull(MailMessage::id) ?: return@withLock 0
        val messages = head.messages.associateByTo(linkedMapOf(), MailMessage::id)
        var offset = head.nextOffset
        while (offset != null) {
            val page = inbox(generation, mailboxId, offset)
            page.messages.filter { it.id <= newestId }.forEach { messages[it.id] = it }
            offset = page.nextOffset
        }
        control.withLock {
            checkGeneration(generation)
            val reads = messages.values.associate { mail -> mail.id to (pendingReads[mail.id] ?: mail.isRead) }
            _readStates.update { it.copy(values = it.values + reads, revision = it.revision + 1) }
        }
        var count = 0
        for (mail in messages.values) {
            checkGeneration(generation)
            if (_readStates.value.values[mail.id] ?: mail.isRead) continue
            markReadLocked(generation, mail, true)
            count++
        }
        count
    }

    private suspend fun markReadLocked(generation: Long, mail: MailMessage, read: Boolean) {
        val (credential, before) = control.withLock {
            checkGeneration(generation)
            val before = _readStates.value.values[mail.id] to pendingReads[mail.id]
            pendingReads[mail.id] = read
            _readStates.update { it.copy(values = it.values + (mail.id to read), revision = it.revision + 1) }
            key to before
        }
        try {
            withContext(dispatchers.io) { client.markRead(credential, mail.id, read) }
            checkGeneration(generation)
        } catch (error: Throwable) {
            if (error is MailException) invalidateExpired(generation, error)
            withContext(NonCancellable) {
                control.withLock {
                    if (_readStates.value.generation == generation) {
                        val (previousRead, previousPending) = before
                        if (previousPending == null) pendingReads.remove(mail.id) else pendingReads[mail.id] = previousPending
                        _readStates.update {
                            it.copy(
                                values = if (previousRead == null) it.values - mail.id else it.values + (mail.id to previousRead),
                                revision = it.revision + 1,
                            )
                        }
                    }
                }
            }
            throw error
        }
    }

    override suspend fun checkForNewMessages(): MailArrival? = mailPoll.withLock {
        initialize()
        val current = _connection.value
        if (!current.connected) return@withLock null
        val generation = current.generation
        val mailboxes = control.withLock {
            mailboxCatalog?.takeIf { it.generation == generation && clock.nowMillis() - it.fetchedAt in 0 until 300_000L }?.mailboxes
        } ?: mailboxes(generation)
        if (mailboxes.isEmpty()) {
            control.withLock {
                checkGeneration(generation)
                _inboxUpdates.value = null
            }
            return@withLock null
        }
        val selected = defaultMailboxId(generation)?.takeIf { id -> mailboxes.any { it.id == id } }
            ?: mailboxes.first().id.also { setDefaultMailboxId(generation, it) }
        val page = try {
            inbox(generation, selected)
        } catch (error: MailException) {
            if (error.error == MailError.NOT_FOUND) {
                control.withLock { if (_connection.value.generation == generation) mailboxCatalog = null }
            }
            throw error
        }
        val newestId = page.messages.maxOfOrNull(MailMessage::id) ?: 0L
        // Keep the cursor write in the credential transaction: an old request must never seed the
        // next account's baseline or notify for an inbox which is no longer the default.
        control.withLock cursor@{
            checkGeneration(generation)
            if (credentials.readDefaultMailboxId() != selected) return@cursor null
            val previous = credentials.readNotificationCursor(selected)
            if (previous == null) {
                credentials.saveNotificationCursor(selected, newestId)
                return@cursor null
            }
            if (newestId <= previous) return@cursor null
            credentials.saveNotificationCursor(selected, newestId)
            val count = page.messages.count { it.id > previous && !(_readStates.value.values[it.id] ?: it.isRead) }
            if (count == 0) return@cursor null
            MailArrival(selected, count, generation).also { _arrivals.tryEmit(it) }
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

    private fun checkAuthorization(id: Long) {
        val attempt = authorizationAttempt.value
        if (attempt?.id != id || attempt.connectionGeneration != _connection.value.generation) {
            throw CancellationException("Mail authorization changed")
        }
    }
}

private data class MailAuthorizationAttempt(val id: Long, val connectionGeneration: Long)

private data class MailboxCatalog(val generation: Long, val fetchedAt: Long, val mailboxes: List<Mailbox>)

private fun String.authModeOrNull(): MailAuthMode? = when {
    isBlank() -> null
    startsWith("web:") -> MailAuthMode.WEB_SESSION
    else -> MailAuthMode.API_KEY
}
