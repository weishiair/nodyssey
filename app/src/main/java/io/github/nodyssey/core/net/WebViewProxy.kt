package io.github.nodyssey.core.net

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import io.github.nodyssey.data.proxy.ProxySettings
import io.github.nodyssey.data.proxy.ProxyType
import io.github.nodyssey.ui.login.LocalWebViewNetworkState
import io.github.nodyssey.ui.login.WebViewNetworkState
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import androidx.webkit.ProxyConfig as WebKitProxyConfig

/** The override is process-wide, so its owner survives Activity recreation and site switches. */
internal class AppWebViewProxy(scope: CoroutineScope, settings: ProxySettings) {
    private val mutableState = MutableStateFlow(WebViewNetworkState.Applying)
    val state = mutableState.asStateFlow()

    init {
        scope.launch(Dispatchers.Main.immediate) {
            var tunnel: WebViewProxyTunnel? = null
            val supported = runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE) }.getOrDefault(false)
            try {
                settings.config.distinctUntilChanged().collect { config ->
                    mutableState.value = WebViewNetworkState.Applying
                    tunnel?.close()
                    tunnel = null
                    if (config.enabled && (!config.isUsable || !supported)) {
                        mutableState.value = WebViewNetworkState.Unavailable
                        return@collect
                    }
                    val applied = runCatchingExceptCancellation {
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                            val controller = ProxyController.getInstance()
                            if (config.enabled) {
                                val rule = if (config.username.isBlank()) {
                                    val scheme = if (config.type == ProxyType.SOCKS) "socks5" else "http"
                                    val host = if (':' in config.host) "[${config.host}]" else config.host
                                    "$scheme://$host:${config.port}"
                                } else {
                                    val activeTunnel = withContext(Dispatchers.IO) { WebViewProxyTunnel(config) }
                                    tunnel = activeTunnel
                                    "http://127.0.0.1:${activeTunnel.port}"
                                }
                                val proxy = WebKitProxyConfig.Builder().addProxyRule(rule).build()
                                awaitProxyChange { executor, done -> controller.setProxyOverride(proxy, executor, done) }
                            } else {
                                awaitProxyChange { executor, done -> controller.clearProxyOverride(executor, done) }
                            }
                        }
                    }.isSuccess
                    mutableState.value = if (applied) WebViewNetworkState.Ready else WebViewNetworkState.Unavailable
                }
            } finally {
                tunnel?.close()
            }
        }
    }
}

/** Shared by the sign-in page, its popups and the native form's Turnstile widget. */
@Composable
internal fun ProvideWebViewProxy(proxy: AppWebViewProxy, content: @Composable () -> Unit) {
    val state by proxy.state.collectAsState()
    CompositionLocalProvider(LocalWebViewNetworkState provides state, content = content)
}

private suspend fun awaitProxyChange(change: (Executor, Runnable) -> Unit) {
    suspendCancellableCoroutine { continuation ->
        change(Executor { it.run() }, Runnable { continuation.resume(Unit) })
    }
}
