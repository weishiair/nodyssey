package io.github.nodyssey.data

import io.github.plaza.core.AppClock
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthorInfoCacheTest {
    private fun profile(uid: Long, level: Int = 4) = UserProfile(uid, "author", "", rank = level, createdAt = "2025-07-01")

    @Test
    fun `concurrent screens share UID requests and at most two profile lookups run`() = runTest {
        val calls = mutableListOf<Long>()
        var active = 0
        var maximum = 0
        val cache = AuthorInfoCache(AppClock { testScheduler.currentTime }) { uid ->
            calls += uid
            active++
            maximum = maxOf(maximum, active)
            delay(100)
            active--
            profile(uid)
        }
        val readers = (1L..12L).map { uid -> async { cache.get(uid) } } + List(4) { async { cache.get(1) } }
        readers.awaitAll()
        assertEquals((1L..12L).toSet(), calls.toSet())
        assertEquals(12, calls.size)
        assertEquals(2, maximum)
    }

    @Test
    fun `cache expires and keeps recently read users within its memory limit`() = runTest {
        var now = 0L
        val calls = mutableListOf<Long>()
        val cache = AuthorInfoCache(AppClock { now }) { uid ->
            calls += uid
            profile(uid, calls.size)
        }
        (1L..AuthorInfoCache.MAX_ENTRIES.toLong()).forEach { cache.get(it) }
        assertEquals(1, cache.get(1)?.level)
        cache.get(AuthorInfoCache.MAX_ENTRIES + 1L)
        assertEquals(1, cache.get(1)?.level)
        cache.get(2)
        assertEquals(2, calls.count { it == 2L })
        assertEquals(1, calls.count { it == 1L })
        now = AuthorInfoCache.CACHE_TTL_MILLIS
        assertTrue(cache.get(1)!!.level!! > 1)
        assertEquals(2, calls.count { it == 1L })
    }

    @Test
    fun `endpoint failure backs off across authors while successful cache entries stay readable`() = runTest {
        var now = 0L
        var fail = false
        val calls = mutableListOf<Long>()
        val cache = AuthorInfoCache(AppClock { now }) { uid ->
            calls += uid
            check(!fail)
            profile(uid)
        }
        assertEquals(4, cache.get(1)?.level)
        fail = true
        assertNull(cache.get(2))
        assertNull(cache.get(2))
        assertNull(cache.get(3))
        assertEquals(4, cache.get(1)?.level)
        assertEquals(listOf(1L, 2L), calls)
        now = AuthorInfoCache.FAILURE_BACKOFF_MILLIS
        fail = false
        assertEquals(4, cache.get(2)?.level)
        assertEquals(4, cache.get(3)?.level)
        assertEquals(listOf(1L, 2L, 2L, 3L), calls)
    }

    @Test
    fun `cancelling one screen neither poisons a waiting reader nor starts failure backoff`() = runTest {
        var calls = 0
        val cache = AuthorInfoCache(AppClock { 0L }) { uid ->
            calls++
            if (calls == 1) CompletableDeferred<Unit>().await()
            profile(uid)
        }
        val first = async { cache.get(7) }
        runCurrent()
        val second = async { cache.get(7) }
        runCurrent()
        assertEquals(1, calls)
        first.cancel()
        assertEquals(4, second.await()?.level)
        assertEquals(2, calls)
        assertEquals(4, cache.get(8)?.level)
        assertEquals(3, calls)
    }

    @Test
    fun `invalid and mismatched UIDs never show another user's account information`() = runTest {
        var calls = 0
        val cache = AuthorInfoCache(AppClock { 0L }) {
            calls++
            profile(9)
        }
        assertNull(cache.get(0))
        assertNull(cache.get(-1))
        assertEquals(0, calls)
        assertNull(cache.get(7))
        assertEquals(1, calls)
    }

    @Test
    fun `one deleted account does not pause lookups for other authors`() = runTest {
        val calls = mutableListOf<Long>()
        val cache = AuthorInfoCache(AppClock { 0L }) { uid ->
            calls += uid
            if (uid == 2L) throw SiteException(SiteError.Http(404))
            profile(uid)
        }
        assertNull(cache.get(2))
        assertEquals(4, cache.get(3)?.level)
        assertNull(cache.get(2))
        assertEquals(listOf(2L, 3L), calls)
    }
}
