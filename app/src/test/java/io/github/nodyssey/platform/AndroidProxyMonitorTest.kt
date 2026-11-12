package io.github.nodyssey.platform

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AndroidProxyMonitorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val shadow = shadowOf(connectivity)
    private val target = "https://www.nodeseek.com/"

    @Test
    fun `VPN capabilities update suspension without a request and release callbacks on shutdown`() = runTest {
        val network = requireNotNull(connectivity.activeNetwork)
        val normal = shadowOf(NetworkCapabilities()).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        shadow.setNetworkCapabilities(network, normal)
        val originalCallbacks = shadow.networkCallbacks.toSet()
        val application = shadowOf(context as android.app.Application)
        val originalReceivers = application.registeredReceivers.toSet()
        val owner = SupervisorJob(backgroundScope.coroutineContext[Job])
        val monitor = AndroidProxyMonitor(context, CoroutineScope(backgroundScope.coroutineContext + owner), target, Selector())
        runCurrent()
        val callback = (shadow.networkCallbacks - originalCallbacks).single()
        assertFalse(monitor.active.value)

        val vpn = shadowOf(NetworkCapabilities()).addTransportType(NetworkCapabilities.TRANSPORT_VPN)
        shadow.setNetworkCapabilities(network, vpn)
        callback.onCapabilitiesChanged(network, vpn)
        runCurrent()
        assertTrue(monitor.active.value)

        shadow.setNetworkCapabilities(network, normal)
        callback.onCapabilitiesChanged(network, normal)
        runCurrent()
        assertFalse(monitor.active.value)

        owner.cancel()
        runCurrent()
        assertEquals(originalCallbacks, shadow.networkCallbacks)
        assertEquals(originalReceivers, application.registeredReceivers.toSet())
    }

    @Test
    fun `system proxy changes follow the forum selector including a direct exclusion`() = runTest {
        val selector = Selector()
        val monitor = AndroidProxyMonitor(context, backgroundScope, target, selector)
        runCurrent()
        assertFalse(monitor.active.value)

        selector.route = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.example", 8080))
        context.sendBroadcast(Intent(android.net.Proxy.PROXY_CHANGE_ACTION))
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
        assertTrue(monitor.active.value)
        assertEquals(URI(target), selector.lastTarget)

        // A configured system proxy may exclude this host; DIRECT must not suspend DNS.
        selector.route = Proxy.NO_PROXY
        context.sendBroadcast(Intent(android.net.Proxy.PROXY_CHANGE_ACTION))
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
        assertFalse(monitor.active.value)
    }

    @Test
    fun `a live refresh observes a proxy before its broadcast arrives`() = runTest {
        val selector = Selector()
        val monitor = AndroidProxyMonitor(context, backgroundScope, target, selector)
        runCurrent()
        selector.route = Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("proxy.example", 1080))

        assertTrue(monitor.refresh())
        assertTrue(monitor.active.value)
    }

    private class Selector : ProxySelector() {
        var route: Proxy = Proxy.NO_PROXY
        var lastTarget: URI? = null

        override fun select(uri: URI): List<Proxy> {
            lastTarget = uri
            return listOf(route)
        }

        override fun connectFailed(uri: URI, socketAddress: SocketAddress, exception: IOException) = Unit
    }
}
