package io.github.plaza.designsys.image

import coil3.annotation.ExperimentalCoilApi
import coil3.network.NetworkClient
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse

/**
 * Guards clients that have no HTTP cache of their own, such as the iOS byte transport.
 *
 * Coil 3.6.3's NetworkFetcher expresses disabled network reads as `only-if-cached`, but still calls
 * NetworkClient after a disk-cache miss. URLSession does not enforce that header. Answer with the
 * standard unsatisfiable-cache response before opening a connection. Remove this guard when
 * NetworkFetcher itself rejects network access for a cache-only miss.
 */
@OptIn(ExperimentalCoilApi::class)
class NetworkCachePolicyClient(private val delegate: NetworkClient) : NetworkClient {
    override suspend fun <T> executeRequest(request: NetworkRequest, block: suspend (NetworkResponse) -> T): T {
        val cacheOnly = request.headers["Cache-Control"].orEmpty().split(',').any { it.trim().equals("only-if-cached", ignoreCase = true) }
        return if (cacheOnly) block(NetworkResponse(code = 504)) else delegate.executeRequest(request, block)
    }
}
