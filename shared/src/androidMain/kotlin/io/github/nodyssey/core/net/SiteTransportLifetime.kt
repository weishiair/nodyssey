package io.github.nodyssey.core.net

import io.github.plaza.core.net.HttpRequest
import io.github.plaza.core.net.HttpResponse
import io.github.plaza.core.net.HttpTransport
import io.github.plaza.core.net.UploadProgress
import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/** A retired graph must neither start a request nor consume a late answer after switching sites. */
class SiteTransportLifetime {
    private val stopped = AtomicBoolean(false)
    val isActive: Boolean get() = !stopped.get()

    fun stop() {
        stopped.set(true)
    }

    fun checkActive() {
        if (!isActive) throw CancellationException("Site graph retired")
    }

    fun guard(delegate: HttpTransport): HttpTransport = object : HttpTransport {
        override suspend fun execute(request: HttpRequest, onUploadProgress: UploadProgress?): HttpResponse {
            checkActive()
            return delegate.execute(request, onUploadProgress).also { checkActive() }
        }
    }
}
