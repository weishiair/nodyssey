package io.github.nodyssey.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI

/** Watches the app's default network and the platform proxy selected for the forum URL. */
internal class AndroidProxyMonitor(
    context: Context,
    scope: CoroutineScope,
    url: String,
    private val platform: ProxySelector? = ProxySelector.getDefault(),
) {
    private val appContext = context.applicationContext
    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
    private val target = URI(url)
    private val detected = MutableStateFlow(detect())
    val active = detected.asStateFlow()

    init {
        callbackFlow {
            val callback = object : ConnectivityManager.NetworkCallback() {
                // Query from the collector, after delivery; synchronous reads in onAvailable can
                // precede the network's capabilities and link properties.
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    trySend(Unit)
                }

                override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                    trySend(Unit)
                }

                override fun onLost(network: Network) {
                    trySend(Unit)
                }
            }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    trySend(Unit)
                }
            }
            connectivity?.registerDefaultNetworkCallback(callback)
            try {
                ContextCompat.registerReceiver(appContext, receiver, IntentFilter(android.net.Proxy.PROXY_CHANGE_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
                trySend(Unit)
                awaitClose { appContext.unregisterReceiver(receiver) }
            } finally {
                connectivity?.unregisterNetworkCallback(callback)
            }
        }.conflate().onEach { refresh() }.launchIn(scope)
    }

    /** DNS also samples live state so a lookup cannot overtake a queued network notification. */
    fun refresh(): Boolean = detect().also { detected.value = it }

    private fun detect(): Boolean {
        val network = connectivity?.activeNetwork
        if (network != null && connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) return true
        // Use the same platform selector as AppProxySelector, including host exclusions and PAC.
        return platform?.select(target)?.any { it.type() != Proxy.Type.DIRECT } == true
    }
}
