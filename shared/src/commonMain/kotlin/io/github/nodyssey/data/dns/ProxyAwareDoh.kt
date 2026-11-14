package io.github.nodyssey.data.dns

import io.github.nodyssey.data.proxy.ProxyConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Suspends app-owned DNS while a proxy is present without overwriting the user's saved choice. */
class ProxyAwareDoh(
    scope: CoroutineScope,
    private val settings: DohSettings,
    private val appProxy: Flow<ProxyConfig>,
    externalProxy: StateFlow<Boolean>,
    private val refreshExternalProxy: () -> Boolean,
    private val applyConfig: suspend (DohConfig) -> Unit,
) {
    private val mutex = Mutex()
    private val paused = MutableStateFlow(externalProxy.value)
    val pausedForProxy: StateFlow<Boolean> = paused.asStateFlow()

    init {
        combine(settings.config, appProxy, externalProxy) { _, _, _ -> Unit }
            // Re-read inside the lock: a queued notification must not restore an obsolete route
            // after a request has already observed a newly connected proxy.
            .onEach { currentConfig() }
            .launchIn(scope)
    }

    /** Also samples the platform before each request, covering a change between monitor updates. */
    suspend fun currentConfig(): DohConfig = mutex.withLock {
        val saved = settings.config.first()
        val proxy = appProxy.first()
        val suspended = refreshExternalProxy() || (proxy.enabled && proxy.isUsable)
        val effective = if (suspended) saved.copy(enabled = false) else saved
        // Apply the system DNS context before returning a route to the HTTP caller.
        applyConfig(effective)
        paused.value = suspended
        effective
    }
}
