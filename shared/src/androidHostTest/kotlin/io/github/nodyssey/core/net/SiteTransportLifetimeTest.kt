package io.github.nodyssey.core.net

import io.github.plaza.core.net.HttpRequest
import io.github.plaza.core.net.HttpResponse
import io.github.plaza.core.net.HttpTransport
import io.github.plaza.core.net.UploadProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteTransportLifetimeTest {
    @Test
    fun `an answer arriving after a site switch cannot reach the old repository`() = runTest {
        val answer = CompletableDeferred<Unit>()
        val lifetime = SiteTransportLifetime()
        var sent = false
        val transport = lifetime.guard(object : HttpTransport {
            override suspend fun execute(request: HttpRequest, onUploadProgress: UploadProgress?): HttpResponse {
                sent = true
                answer.await()
                return HttpResponse(200, request.url, emptyMap(), "old site body")
            }
        })
        val call = async { runCatching { transport.execute(HttpRequest("https://www.nodeseek.com/")) } }
        runCurrent()
        assertTrue(sent)
        lifetime.stop()
        answer.complete(Unit)
        assertTrue(call.await().exceptionOrNull() is CancellationException)
    }

    @Test
    fun `a retired graph cannot start another request when the active site changes back`() = runTest {
        val lifetime = SiteTransportLifetime()
        var sent = false
        val transport = lifetime.guard(object : HttpTransport {
            override suspend fun execute(request: HttpRequest, onUploadProgress: UploadProgress?): HttpResponse {
                sent = true
                return HttpResponse(200, request.url, emptyMap(), "body")
            }
        })
        lifetime.stop()
        repeat(2) {
            val result = runCatching { transport.execute(HttpRequest("https://www.deepflood.com/")) }
            assertTrue(result.exceptionOrNull() is CancellationException)
        }
        assertFalse(sent)
        assertTrue(SiteTransportLifetime().isActive)
    }
}
