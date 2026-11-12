@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.plaza.core.net

import io.github.plaza.core.net.dns.plaza_network_proxy_active
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Public CFNetwork proxy settings plus live, addressed VPN tunnel interfaces. Apple has no public
 * API that identifies another app's VPN or proves a particular request traverses it. Tunnel names
 * are therefore a conservative signal, not a claim about the proxy app or its per-app rules.
 * Remove that heuristic if Apple exposes the actual VPN route through a supported API.
 *
 * NWPathMonitor cannot report HTTP-proxy-only changes. Poll local state while the owning scope is
 * alive, and refresh synchronously before a native request so it need not wait for the next tick.
 */
class AppleProxyMonitor(scope: CoroutineScope, private val url: String) {
    private val detected = MutableStateFlow(detect())
    val active: StateFlow<Boolean> = detected.asStateFlow()

    init {
        scope.launch {
            while (isActive) {
                refresh()
                delay(1_000)
            }
        }
    }

    fun refresh(): Boolean = detect().also { detected.value = it }

    private fun detect(): Boolean = plaza_network_proxy_active(url) != 0
}
