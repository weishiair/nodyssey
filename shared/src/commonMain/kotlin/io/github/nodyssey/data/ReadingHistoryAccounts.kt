package io.github.nodyssey.data

import io.github.nodyssey.data.local.AccountReadMarkEntity
import io.github.nodyssey.data.local.AccountReadingPositionEntity
import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.nodyssey.data.local.ReadingHistoryAccountEntity
import io.github.nodyssey.data.local.writeTransaction
import io.github.nodyssey.data.session.SessionState

/**
 * Keeps the active history tables as the feed's SSOT while parking inactive accounts by UID.
 *
 * Switching the active tables and their owner is one Room transaction: observers cannot see another
 * account's marks in between. A cookie fingerprint only names a pending session, never a person.
 * The profile fetched by the app identifies that session before any UID history is restored.
 */
class ReadingHistoryAccounts(
    private val database: NodeSeekDatabase,
    private val currentSession: (() -> SessionState)? = null,
) {
    private var signingOutFingerprint: Int? = null
    private val accounts get() = database.readingHistoryAccountDao()
    private val marks get() = database.readMarkDao()
    private val positions get() = database.readingPositionDao()

    suspend fun <T> withCurrent(block: suspend () -> T): T = database.writeTransaction {
        currentSession?.invoke()?.let { activate(it.isSignedIn, it.fingerprint) }
        block()
    }

    suspend fun activateSession(isSignedIn: Boolean, fingerprint: Int) = database.writeTransaction {
        val current = currentSession?.invoke()
        activate(current?.isSignedIn ?: isSignedIn, current?.fingerprint ?: fingerprint)
        checkNotNull(accounts.active())
    }

    /** Cookies are removed after content; do not let an in-flight reader reactivate them meanwhile. */
    suspend fun signOut() = database.writeTransaction {
        signingOutFingerprint = currentSession?.invoke()?.takeIf { it.isSignedIn }?.fingerprint
        switchTo(ReadingHistoryAccountEntity(ownerKey = GUEST, authenticated = false, fingerprint = 0))
    }

    suspend fun currentOwner(): String = accounts.active()?.ownerKey ?: GUEST

    /** Ignores a late profile response from cookies that are no longer active. */
    suspend fun identify(fingerprint: Int, uid: Long) = withCurrent {
        if (uid <= 0) return@withCurrent
        val previous = accounts.active() ?: return@withCurrent
        if (!previous.authenticated || previous.fingerprint != fingerprint) return@withCurrent
        val owner = "uid:$uid"
        if (previous.ownerKey == owner) return@withCurrent
        val pending = previous.ownerKey.startsWith("session:")
        val pendingMarks = if (pending) marks.all() else emptyList()
        val pendingPositions = if (pending) positions.all() else emptyList()
        switchTo(previous.copy(ownerKey = owner))
        // A thread opened while identification was in flight is part of this session too. Keep the
        // newest snapshot and the largest seen count; an older read must not undo newer progress.
        for (mark in pendingMarks) {
            val saved = marks.find(mark.postId)
            val latest = if (saved != null && saved.lastReadAtMillis > mark.lastReadAtMillis) saved else mark
            marks.upsert(
                latest.copy(
                    lastSeenCommentCount = maxOf(saved?.lastSeenCommentCount ?: 0, mark.lastSeenCommentCount),
                    title = latest.title ?: saved?.title ?: mark.title,
                    authorName = latest.authorName ?: saved?.authorName ?: mark.authorName,
                    authorUid = latest.authorUid ?: saved?.authorUid ?: mark.authorUid,
                    categoryTitle = latest.categoryTitle ?: saved?.categoryTitle ?: mark.categoryTitle,
                    commentCount = latest.commentCount ?: saved?.commentCount ?: mark.commentCount,
                ),
            )
        }
        for (position in pendingPositions) {
            val saved = positions.find(position.postId)
            if (saved == null || position.updatedAtMillis >= saved.updatedAtMillis) positions.upsert(position)
        }
        if (pending) {
            accounts.deleteMarks(previous.ownerKey)
            accounts.deletePositions(previous.ownerKey)
        }
    }

    private suspend fun activate(isSignedIn: Boolean, fingerprint: Int) {
        if (!isSignedIn || fingerprint != signingOutFingerprint) signingOutFingerprint = null
        if (isSignedIn && fingerprint == signingOutFingerprint) return
        val previous = accounts.active()
        if (previous != null && previous.authenticated == isSignedIn &&
            (!isSignedIn || previous.fingerprint == fingerprint)
        ) {
            return
        }
        val owner = if (isSignedIn) {
            database.profileDao().find(fingerprint)?.uid?.let { "uid:$it" } ?: "session:$fingerprint"
        } else {
            GUEST
        }
        switchTo(ReadingHistoryAccountEntity(ownerKey = owner, authenticated = isSignedIn, fingerprint = fingerprint))
    }

    private suspend fun switchTo(next: ReadingHistoryAccountEntity) {
        val previous = accounts.active() ?: ReadingHistoryAccountEntity(ownerKey = GUEST, authenticated = false, fingerprint = 0)
        if (previous.ownerKey != next.ownerKey) {
            accounts.deleteMarks(previous.ownerKey)
            accounts.deletePositions(previous.ownerKey)
            accounts.saveMarks(marks.all().map { AccountReadMarkEntity(previous.ownerKey, it) })
            accounts.savePositions(positions.all().map { AccountReadingPositionEntity(previous.ownerKey, it) })
            marks.clearAll()
            positions.clearAll()
            accounts.marks(next.ownerKey).forEach { marks.upsert(it.mark) }
            accounts.positions(next.ownerKey).forEach { positions.upsert(it.position) }
            // The active tables now own these rows, so clearing history cannot resurrect an old copy.
            accounts.deleteMarks(next.ownerKey)
            accounts.deletePositions(next.ownerKey)
        }
        accounts.setActive(next)
    }

    private companion object {
        const val GUEST = "guest"
    }
}
