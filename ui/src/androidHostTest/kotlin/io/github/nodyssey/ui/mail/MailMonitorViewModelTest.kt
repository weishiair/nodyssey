package io.github.nodyssey.ui.mail

import io.github.nodyssey.data.mail.MailArrival
import io.github.nodyssey.data.mail.MailAuthMode
import io.github.nodyssey.data.mail.MailError
import io.github.nodyssey.data.mail.MailException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MailMonitorViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `foreground starts immediately once and API polling stops on pause then resumes immediately`() = runTest(dispatcher) {
        val repository = FakeMailRepository().apply { connection.value = connection.value.copy(authMode = MailAuthMode.API_KEY) }
        val monitor = MailMonitorViewModel(repository, io.github.plaza.core.AppClock { testScheduler.currentTime })
        monitor.startPolling(inboxVisible = true)
        monitor.startPolling(inboxVisible = true)
        runCurrent()
        assertEquals(1, repository.polls)
        advanceTimeBy(14_999)
        runCurrent()
        assertEquals(1, repository.polls)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, repository.polls)
        monitor.stopPolling()
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(2, repository.polls)
        monitor.startPolling(inboxVisible = true)
        runCurrent()
        assertEquals(3, repository.polls)
        monitor.stopPolling()
    }

    @Test
    fun `rate limiting backs off and a successful retry restores the API cadence`() = runTest(dispatcher) {
        val repository = FakeMailRepository().apply {
            connection.value = connection.value.copy(authMode = MailAuthMode.API_KEY)
            pollAnswer = { if (polls <= 1) throw MailException(MailError.RATE_LIMITED) }
        }
        val monitor = MailMonitorViewModel(repository, io.github.plaza.core.AppClock { testScheduler.currentTime })
        monitor.startPolling(inboxVisible = true)
        runCurrent()
        advanceTimeBy(59_999)
        runCurrent()
        assertEquals(1, repository.polls)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, repository.polls)
        advanceTimeBy(15_000)
        runCurrent()
        assertEquals(3, repository.polls)
        monitor.stopPolling()
    }

    @Test
    fun `changing the active screen cannot bypass a rate limit cooldown`() = runTest(dispatcher) {
        val repository = FakeMailRepository().apply {
            connection.value = connection.value.copy(authMode = MailAuthMode.API_KEY)
            pollAnswer = { throw MailException(MailError.RATE_LIMITED) }
        }
        val monitor = MailMonitorViewModel(repository, io.github.plaza.core.AppClock { testScheduler.currentTime })
        monitor.startPolling()
        runCurrent()
        assertEquals(1, repository.polls)
        advanceTimeBy(15_000)
        monitor.stopPolling()
        monitor.startPolling(inboxVisible = true)
        runCurrent()
        assertEquals(1, repository.polls)
        advanceTimeBy(44_999)
        runCurrent()
        assertEquals(1, repository.polls)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, repository.polls)
        monitor.stopPolling()
    }

    @Test
    fun `web mode polls less frequently and switching to API restarts immediately`() = runTest(dispatcher) {
        val repository = FakeMailRepository().apply { connection.value = connection.value.copy(authMode = MailAuthMode.WEB_SESSION) }
        val monitor = MailMonitorViewModel(repository, io.github.plaza.core.AppClock { testScheduler.currentTime })
        monitor.startPolling()
        runCurrent()
        advanceTimeBy(59_999)
        runCurrent()
        assertEquals(1, repository.polls)
        repository.connection.value = repository.connection.value.copy(generation = 2, authMode = MailAuthMode.API_KEY)
        runCurrent()
        assertEquals(2, repository.polls)
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(3, repository.polls)
        monitor.stopPolling()
    }

    @Test
    fun `worker arrivals alert in foreground and old accounts or paused arrivals cannot alert`() = runTest(dispatcher) {
        val repository = FakeMailRepository()
        val monitor = MailMonitorViewModel(repository, io.github.plaza.core.AppClock { testScheduler.currentTime })
        monitor.startPolling(inboxVisible = true)
        runCurrent()
        repository.arrivals.emit(MailArrival(7, 1, 1))
        runCurrent()
        val first = monitor.notice.value!!
        assertEquals(1, first.arrival.count)
        repository.arrivals.emit(MailArrival(7, 1, 1))
        runCurrent()
        monitor.dismissNotice(first.id)
        assertEquals(first.id + 1, monitor.notice.value!!.id)
        repository.connect("synthetic")
        runCurrent()
        assertNull(monitor.notice.value)
        repository.arrivals.emit(MailArrival(7, 1, 1))
        runCurrent()
        assertNull(monitor.notice.value)
        monitor.stopPolling()
        repository.arrivals.emit(MailArrival(7, 1, 2))
        runCurrent()
        assertNull(monitor.notice.value)
    }

    @Test
    fun `disconnected mailbox never polls and failed requests progressively reduce the load`() = runTest(dispatcher) {
        val repository = FakeMailRepository().apply { connection.value = connection.value.copy(connected = false) }
        val monitor = MailMonitorViewModel(repository, io.github.plaza.core.AppClock { testScheduler.currentTime })
        monitor.startPolling(inboxVisible = true)
        runCurrent()
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(0, repository.polls)
        repository.pollAnswer = { throw MailException(MailError.NETWORK) }
        repository.connection.value = repository.connection.value.copy(connected = true, authMode = MailAuthMode.API_KEY)
        runCurrent()
        assertEquals(1, repository.polls)
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(2, repository.polls)
        advanceTimeBy(59_999)
        runCurrent()
        assertEquals(2, repository.polls)
        monitor.stopPolling()
    }
}
