package io.github.nodyssey.ui.mail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.mail.MailArrival
import io.github.nodyssey.data.mail.MailAuthMode
import io.github.nodyssey.data.mail.MailError
import io.github.nodyssey.data.mail.MailRepository
import io.github.nodyssey.di.AppContainer
import io.github.plaza.core.AppClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MailNotice(val id: Long, val arrival: MailArrival)

/** One foreground monitor at the navigation root, independent of forum sign-in and the active tab. */
class MailMonitorViewModel(private val repository: MailRepository, private val clock: AppClock = AppClock.System) : ViewModel() {
    private var pollJob: Job? = null
    private var foreground = false
    private var noticeId = 0L
    private var retryGeneration = 0L
    private var failures = 0
    private var retryNotBefore = 0L
    private val _notice = MutableStateFlow<MailNotice?>(null)
    val notice = _notice.asStateFlow()

    init {
        viewModelScope.launch {
            repository.arrivals.collect { arrival ->
                if (foreground && repository.connection.value.let { it.connected && it.generation == arrival.generation }) {
                    _notice.value = MailNotice(++noticeId, arrival)
                }
            }
        }
        viewModelScope.launch {
            repository.connection.collect { _notice.value = null }
        }
    }

    fun startPolling(inboxVisible: Boolean = false) {
        if (pollJob?.isActive == true) return
        foreground = true
        pollJob = viewModelScope.launch {
            while (isActive) {
                try {
                    repository.initialize()
                    break
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    delay(60_000)
                }
            }
            repository.connection.collectLatest { connection ->
                if (!connection.connected) return@collectLatest
                if (retryGeneration != connection.generation) {
                    retryGeneration = connection.generation
                    failures = 0
                    retryNotBefore = 0L
                }
                while (isActive) {
                    // Changing tabs or resuming must not bypass a service's rate-limit cooldown.
                    val remaining = (retryNotBefore - clock.nowMillis()).coerceIn(0L, 300_000L)
                    if (remaining > 0) delay(remaining)
                    var error: MailError? = null
                    try {
                        repository.checkForNewMessages()
                        failures = 0
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        error = failure.mailError()
                        failures = (failures + 1).coerceAtMost(5)
                    }
                    val interval = mailPollDelay(connection.authMode, failures, error, inboxVisible)
                    retryNotBefore = if (error == null) 0 else clock.nowMillis() + interval
                    delay(interval)
                }
            }
        }
    }

    fun stopPolling() {
        foreground = false
        pollJob?.cancel()
        pollJob = null
        _notice.value = null
    }

    fun dismissNotice(id: Long) {
        if (_notice.value?.id == id) _notice.value = null
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { MailMonitorViewModel(container.mailRepository, container.clock) }
        }
    }
}

internal fun mailPollDelay(mode: MailAuthMode?, failures: Int, error: MailError?, inboxVisible: Boolean): Long {
    val base = when {
        mode != MailAuthMode.API_KEY -> 60_000L
        inboxVisible -> 15_000L
        else -> 30_000L
    }
    return when (error) {
        MailError.CHALLENGE, MailError.PERMISSION, MailError.UNAUTHORIZED -> 300_000L
        MailError.RATE_LIMITED -> (base * (1L shl failures)).coerceIn(60_000L, 300_000L)
        else -> (base * (1L shl failures)).coerceAtMost(300_000L)
    }
}
