package io.github.nodyssey.data

import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.nodyssey.data.session.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReadingHistoryAccountsTest {
    private lateinit var database: NodeSeekDatabase
    private lateinit var accounts: ReadingHistoryAccounts
    private lateinit var repository: OfflineFirstPostRepository
    private lateinit var positions: RoomReadingPositionStore
    private val session = MutableStateFlow(SessionState())
    private val clock = MutableClock()

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        accounts = ReadingHistoryAccounts(database) { session.value }
        repository = OfflineFirstPostRepository(database, FakePostRemoteDataSource(), clock, historyAccounts = accounts)
        positions = RoomReadingPositionStore(database.readingPositionDao(), clock, accounts)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun login(uid: Long, fingerprint: Int) {
        session.value = SessionState(isSignedIn = true, fingerprint = fingerprint)
        repository.reconcileSession(true, fingerprint)
        accounts.identify(fingerprint, uid)
    }

    private suspend fun logout() {
        repository.clearSessionData()
        session.value = SessionState()
        repository.reconcileSession(false, 0)
    }

    private suspend fun read(post: Long, page: Int) {
        clock.advanceBy(1_000)
        repository.markThreadRead(post)
        positions.setReadingPosition(post, ReadingPosition(page, "#${page * 10}"))
    }

    private suspend fun ids() = repository.readHistory().first().map { it.postId }

    @Test
    fun `same uid restores history and position after logout and a new cookie`() = runTest {
        login(71, 101)
        read(42, 8)
        val original = repository.readHistory().first()
        logout()
        assertTrue(ids().isEmpty())
        assertNull(positions.readingPosition(42))

        login(71, 202)
        assertEquals(original, repository.readHistory().first())
        assertEquals(ReadingPosition(8, "#80"), positions.readingPosition(42))
    }

    @Test
    fun `two accounts and guest keep separate histories including the same thread`() = runTest {
        read(3, 1)
        login(71, 101)
        assertTrue(ids().isEmpty())
        read(42, 8)
        login(82, 202)
        assertTrue(ids().isEmpty())
        assertNull(positions.readingPosition(42))
        read(42, 2)
        read(99, 3)
        login(71, 303)
        assertEquals(listOf(42L), ids())
        assertEquals(8, positions.readingPosition(42)?.page)
        logout()
        assertEquals(listOf(3L), ids())
        login(82, 404)
        assertEquals(listOf(99L, 42L), ids())
        assertEquals(2, positions.readingPosition(42)?.page)
    }

    @Test
    fun `reads before uid identification merge without overwriting newer progress`() = runTest {
        login(71, 101)
        read(42, 8)
        database.readMarkDao().markRead(42, 20, clock.nowMillis())
        logout()
        session.value = SessionState(isSignedIn = true, fingerprint = 202)
        repository.reconcileSession(true, 202)
        read(99, 2)
        database.readMarkDao().markRead(42, 1, 1)
        positions.setReadingPosition(42, ReadingPosition(1))
        accounts.identify(202, 71)
        assertEquals(listOf(99L, 42L), ids())
        assertEquals(20, database.readMarkDao().find(42)?.lastSeenCommentCount)
        // The pending session's bookmark was written later, so its place wins.
        assertEquals(1, positions.readingPosition(42)?.page)
    }

    @Test
    fun `new pending read without metadata keeps the restored title`() = runTest {
        login(71, 101)
        database.readMarkDao().markRead(42, 20, 100, title = "remembered title", authorName = "reader")
        logout()
        session.value = SessionState(isSignedIn = true, fingerprint = 202)
        repository.reconcileSession(true, 202)
        database.readMarkDao().markRead(42, 1, 200)
        accounts.identify(202, 71)
        val entry = repository.readHistory().first().single()
        assertEquals("remembered title", entry.title)
        assertEquals("reader", entry.authorName)
        assertEquals(200L, entry.lastReadAtMillis)
        assertEquals(20, database.readMarkDao().find(42)?.lastSeenCommentCount)
    }

    @Test
    fun `late profile cannot restore a signed out or different account`() = runTest {
        login(71, 101)
        read(42, 8)
        logout()
        accounts.identify(101, 71)
        assertTrue(ids().isEmpty())
        login(82, 202)
        read(99, 2)
        accounts.identify(101, 71)
        assertEquals(listOf(99L), ids())
    }

    @Test
    fun `signout cannot reactivate old cookies while their removal is in flight`() = runTest {
        login(71, 101)
        read(42, 8)
        repository.clearSessionData()
        // The session state is deliberately still signed in until cookie removal completes.
        repository.reconcileSession(true, 101)
        accounts.identify(101, 71)
        assertTrue(ids().isEmpty())
        assertNull(positions.readingPosition(42))
    }

    @Test
    fun `inactive history survives recreating the account coordinator`() = runTest {
        login(71, 101)
        read(42, 8)
        logout()
        accounts = ReadingHistoryAccounts(database) { session.value }
        login(71, 202)
        assertEquals(listOf(42L), ids())
        assertEquals(8, positions.readingPosition(42)?.page)
    }

    @Test
    fun `clearing current history does not erase another account or resurrect old rows`() = runTest {
        login(71, 101)
        read(42, 8)
        login(82, 202)
        read(99, 2)
        repository.clearReadHistory()
        login(71, 303)
        assertEquals(listOf(42L), ids())
        login(82, 404)
        assertTrue(ids().isEmpty())
        assertNull(positions.readingPosition(99))
    }

    @Test
    fun `cache cleanup preserves history and undo cannot cross accounts`() = runTest {
        login(71, 101)
        read(42, 8)
        repository.clearCache(true, 101)
        val removed = repository.readHistory().first().single()
        assertEquals(42L, removed.postId)
        repository.removeFromHistory(42)
        login(82, 202)
        repository.restoreToHistory(removed)
        assertTrue(ids().isEmpty())
        assertNull(positions.readingPosition(42))
    }

    @Test
    fun `stale reconciliation uses the current cookies instead of a previous session`() = runTest {
        login(71, 101)
        read(42, 8)
        session.value = SessionState(isSignedIn = true, fingerprint = 202)
        repository.reconcileSession(true, 101)
        assertTrue(ids().isEmpty())
        accounts.identify(202, 82)
        assertTrue(ids().isEmpty())
    }
}
