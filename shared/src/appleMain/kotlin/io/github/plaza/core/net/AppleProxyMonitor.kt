@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.plaza.core.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.CFNetwork.CFNetworkCopySystemProxySettings
import platform.CoreFoundation.CFRelease

/**
 * Samples CFNetwork's proxy configuration for the forum URL, respecting proxy exclusions.
 * Tunnel interfaces alone cannot establish that a request uses a proxy: iOS may keep them alive
 * without a user-enabled proxy. Only an applicable system proxy or the separately handled in-app
 * proxy suspends DoH; VPN-only routing remains the system's responsibility.
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

    private fun detect(): Boolean {
        val settings = CFNetworkCopySystemProxySettings() ?: return false
        return try {
            systemProxyAppliesToUrl(url, settings)
        } finally {
            CFRelease(settings)
        }
    }
}
