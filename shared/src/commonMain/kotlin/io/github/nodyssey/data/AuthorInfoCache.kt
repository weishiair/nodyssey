package io.github.nodyssey.data

import io.github.plaza.core.AppClock
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Public account facts only; balances, follow state and session data never enter this cache. */
data class AuthorInfo(val level: Int?, val createdAt: String?)

/**
 * A process-local, bounded cache for the small badges beside visible reply authors.
 *
 * Locks per UID fold concurrent readers into one request. Two permits leave room for actual thread
 * loads, and a failed endpoint pauses new lookups across UIDs instead of retrying on every scroll.
 * A cancelled reader releases its UID lock so another screen can retry normally.
 */
internal class AuthorInfoCache(
    private val clock: AppClock,
    private val fetch: suspend (Long) -> UserProfile,
) {
    private class Request(val mutex: Mutex = Mutex(), var readers: Int = 0)

    private data class Entry(val info: AuthorInfo?, val savedAt: Long)

    private val mutex = Mutex()
    private val permits = Semaphore(2)
    private val entries = linkedMapOf<Long, Entry>()
    private val requests = mutableMapOf<Long, Request>()
    private var retryAfter = Long.MIN_VALUE

    suspend fun get(uid: Long): AuthorInfo? {
        if (uid <= 0) return null
        val request = mutex.withLock { requests.getOrPut(uid) { Request() }.also { it.readers++ } }
        try {
            return request.mutex.withLock {
                val cached = mutex.withLock { freshEntry(uid) }
                if (cached != null) return@withLock cached.info
                permits.withPermit {
                    val paused = mutex.withLock { clock.nowMillis() < retryAfter }
                    if (paused) return@withPermit null
                    val result = runCatchingExceptCancellation {
                        val profile = fetch(uid)
                        check(profile.uid == uid)
                        AuthorInfo(profile.rank?.takeIf { it >= 0 }, profile.createdAt?.takeIf(String::isNotBlank))
                    }
                    mutex.withLock {
                        val now = clock.nowMillis()
                        val error = (result.exceptionOrNull() as? SiteException)?.error
                        val missingAccount = error is SiteError.Http && error.statusCode in setOf(404, 410)
                        if (result.isFailure && !missingAccount) retryAfter = now + FAILURE_BACKOFF_MILLIS
                        entries.remove(uid)
                        entries[uid] = Entry(result.getOrNull(), now)
                        while (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
                    }
                    result.getOrNull()
                }
            }
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    if (--request.readers == 0) requests.remove(uid)
                }
            }
        }
    }

    /** Called with [mutex] held. Touches fresh entries to keep the most recently read accounts. */
    private fun freshEntry(uid: Long): Entry? {
        val entry = entries.remove(uid) ?: return null
        val ttl = if (entry.info == null) FAILURE_BACKOFF_MILLIS else CACHE_TTL_MILLIS
        if (clock.nowMillis() - entry.savedAt !in 0 until ttl) return null
        entries[uid] = entry
        return entry
    }

    companion object {
        internal const val MAX_ENTRIES = 256
        internal const val CACHE_TTL_MILLIS = 30 * 60_000L
        internal const val FAILURE_BACKOFF_MILLIS = 60_000L
    }
}
