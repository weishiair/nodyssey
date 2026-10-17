package io.github.nodyssey.core.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Dns
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Adapts the app's suspending resolver chain to OkHttp's cancellable DNS call contract. */
internal class AppDnsCall(
    private val scope: CoroutineScope,
    override val request: Dns.Request,
    private val resolve: suspend () -> List<Dns.Record>,
) : Dns.Call {
    private val enqueued = AtomicBoolean()
    private val completed = AtomicBoolean()
    private val canceled = AtomicBoolean()

    @Volatile
    private var job: Job? = null

    override fun enqueue(callback: Dns.Callback) {
        check(enqueued.compareAndSet(false, true)) { "Already enqueued" }
        val worker = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val records = resolve()
                if (completed.compareAndSet(false, true)) {
                    if (canceled.get()) {
                        callback.onFailure(this@AppDnsCall, IOException("Canceled"))
                    } else {
                        callback.onRecords(this@AppDnsCall, true, records)
                    }
                }
            } catch (failure: IOException) {
                if (completed.compareAndSet(false, true)) callback.onFailure(this@AppDnsCall, failure)
            }
        }
        job = worker
        worker.invokeOnCompletion { cause ->
            if (cause != null && completed.compareAndSet(false, true)) {
                if (cause is CancellationException) canceled.set(true)
                callback.onFailure(this, IOException("DNS lookup did not complete", cause))
            }
        }
        if (canceled.get()) worker.cancel()
        worker.start()
    }

    override fun cancel() {
        if (completed.get()) return
        canceled.set(true)
        job?.cancel()
    }

    override fun isCanceled(): Boolean = canceled.get()
}

/** Keep service metadata as well as addresses, and cancel the actual DoH requests with the caller. */
internal suspend fun Dns.Call.awaitRecords(): List<Dns.Record> = suspendCancellableCoroutine { continuation ->
    val answer = mutableListOf<Dns.Record>()
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Dns.Callback {
        override fun onRecords(call: Dns.Call, last: Boolean, records: List<Dns.Record>) {
            // DNS callbacks are serial, so this buffer needs no additional synchronization.
            if (!continuation.isActive) return
            answer += records
            if (last) {
                if (answer.any { it is Dns.Record.IpAddress }) {
                    continuation.resume(answer.toList())
                } else {
                    continuation.resumeWithException(UnknownHostException(request.hostname))
                }
            }
        }

        override fun onFailure(call: Dns.Call, e: IOException) {
            if (!continuation.isActive) return
            // OkHttp resolves A, AAAA and HTTPS independently. Match its execute() contract:
            // an optional query failing must not discard usable addresses and ECH from this server.
            if (answer.any { it is Dns.Record.IpAddress }) {
                continuation.resume(answer.toList())
            } else {
                continuation.resumeWithException(e)
            }
        }
    })
}
