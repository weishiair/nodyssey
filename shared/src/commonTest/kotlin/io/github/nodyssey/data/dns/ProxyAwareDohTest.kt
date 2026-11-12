package io.github.nodyssey.data.dns

import io.github.nodyssey.data.proxy.ProxyConfig
import io.github.nodyssey.data.proxy.ProxyScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProxyAwareDohTest {
    private class Settings(initial: DohConfig = DohConfig()) : DohSettings {
        override val config = MutableStateFlow(initial)
        var writes = 0

        override suspend fun save(config: DohConfig) {
            writes++
            this.config.value = config
        }

        override suspend fun setEnabled(enabled: Boolean) = save(config.value.copy(enabled = enabled))
    }

    @Test
    fun `a VPN suspends native and system DNS without overwriting the saved choice`() = runTest {
        val saved = DohConfig(includeIPv6 = false, servers = listOf(DohServer.preset(DohProvider.GOOGLE, checked = true)))
        val settings = Settings(saved)
        val vpn = MutableStateFlow(false)
        val applied = mutableListOf<DohConfig>()
        val policy = ProxyAwareDoh(backgroundScope, settings, MutableStateFlow(ProxyConfig()), vpn, { vpn.value }, { applied += it })
        runCurrent()
        assertEquals(saved, applied.last())

        // No native request: a browser opened after the tunnel connects must still lose app DoH.
        vpn.value = true
        runCurrent()
        assertFalse(applied.last().enabled)
        assertTrue(policy.pausedForProxy.value)
        assertEquals(saved, settings.config.value)
        assertEquals(0, settings.writes)

        vpn.value = false
        runCurrent()
        assertEquals(saved, applied.last())
        assertFalse(policy.pausedForProxy.value)
    }

    @Test
    fun `a request samples a new VPN before the monitor publishes it`() = runTest {
        val settings = Settings()
        val applied = mutableListOf<DohConfig>()
        var connected = false
        val policy = ProxyAwareDoh(backgroundScope, settings, MutableStateFlow(ProxyConfig()), MutableStateFlow(false), { connected }, { applied += it })
        runCurrent()

        connected = true
        assertFalse(policy.currentConfig().enabled)
        assertFalse(applied.last().enabled)
        assertTrue(settings.config.value.enabled)

        // A queued settings notification must sample the same live VPN, not re-enable stale DNS.
        settings.config.value = settings.config.value.copy(includeIPv6 = false)
        runCurrent()
        assertFalse(applied.last().enabled)
        assertFalse(applied.last().includeIPv6)
    }

    @Test
    fun `a usable app proxy suspends DNS and removing it restores the latest settings`() = runTest {
        val settings = Settings()
        val proxy = MutableStateFlow(ProxyConfig(enabled = true, host = "localhost", port = 1080, scope = ProxyScope.FORUM_ONLY))
        val applied = mutableListOf<DohConfig>()
        val policy = ProxyAwareDoh(backgroundScope, settings, proxy, MutableStateFlow(false), { false }, { applied += it })
        runCurrent()
        assertFalse(applied.last().enabled)

        val edited = DohConfig(servers = listOf(DohServer.preset(DohProvider.ALIDNS, checked = true)))
        settings.save(edited)
        runCurrent()
        assertFalse(policy.currentConfig().enabled)
        proxy.value = proxy.value.copy(enabled = false)
        runCurrent()
        assertEquals(edited, applied.last())
        assertEquals(1, settings.writes)
    }

    @Test
    fun `disconnecting a VPN keeps an explicitly disabled preference off`() = runTest {
        val settings = Settings(DohConfig(enabled = false))
        val vpn = MutableStateFlow(true)
        val applied = mutableListOf<DohConfig>()
        val policy = ProxyAwareDoh(backgroundScope, settings, MutableStateFlow(ProxyConfig()), vpn, { vpn.value }, { applied += it })
        runCurrent()
        vpn.value = false
        runCurrent()
        assertFalse(policy.currentConfig().enabled)
        assertFalse(applied.last().enabled)
        assertEquals(0, settings.writes)
    }

    @Test
    fun `an incomplete proxy form does not disable DNS`() = runTest {
        val applied = mutableListOf<DohConfig>()
        val policy = ProxyAwareDoh(
            backgroundScope,
            Settings(),
            MutableStateFlow(ProxyConfig(enabled = true, host = "", port = 0)),
            MutableStateFlow(false),
            { false },
            { applied += it },
        )
        runCurrent()
        assertTrue(policy.currentConfig().enabled)
        assertFalse(policy.pausedForProxy.value)
    }
}
