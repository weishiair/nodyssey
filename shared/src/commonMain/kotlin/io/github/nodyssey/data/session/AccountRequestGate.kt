package io.github.nodyssey.data.session

import io.github.plaza.core.net.HttpRequest
import io.github.plaza.core.net.HttpResponse
import io.github.plaza.core.net.HttpTransport
import io.github.plaza.core.net.UploadProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Stops old requests before replacing cookies; only the transition's identity check may run. */
class AccountRequestGate {
    private val lock = Mutex()
    private val running = mutableSetOf<Job>()
    private var paused = false
    private val revision = MutableStateFlow(0L)
    val epoch: Long get() = revision.value

    fun guard(transport: HttpTransport): HttpTransport = object : HttpTransport {
        override suspend fun execute(request: HttpRequest, onUploadProgress: UploadProgress?): HttpResponse = this@AccountRequestGate.run {
            transport.execute(request, onUploadProgress)
        }
    }

    suspend fun <T> run(block: suspend () -> T): T = coroutineScope {
        val job = currentCoroutineContext().job
        val permitted = currentCoroutineContext()[TransitionAccess]?.gate === this@AccountRequestGate
        val started = lock.withLock {
            if (paused && !permitted) throw CancellationException("Account is changing")
            running += job
            epoch
        }
        try {
            val response = block()
            if (epoch != started) throw CancellationException("Account changed")
            response
        } finally {
            // Cancellation must not leave a job registered after its transport has stopped.
            withContext(NonCancellable) { lock.withLock { running -= job } }
        }
    }

    suspend fun pause() {
        val jobs = lock.withLock {
            paused = true
            revision.value += 1
            running.toList()
        }
        jobs.forEach { it.cancelAndJoin() }
    }

    suspend fun resume() = lock.withLock { paused = false }

    suspend fun <T> verify(block: suspend () -> T): T = withContext(TransitionAccess(this)) { block() }

    private class TransitionAccess(val gate: AccountRequestGate) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<TransitionAccess>
    }
}
