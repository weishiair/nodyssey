@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.plaza.core.net

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFPropertyListCreateWithData
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFPropertyListImmutable
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppleSystemProxyTest {
    @Test
    fun `a scoped tunnel without a proxy does not suspend DoH`() = withSettings(
        """
        <key>__SCOPED__</key><dict><key>utun0</key><dict>
        <key>ExceptionsList</key><array><string>*.local</string></array>
        </dict></dict>
        """,
    ) { settings ->
        assertFalse(systemProxyAppliesToUrl(FORUM, settings))
    }

    @Test
    fun `a configured HTTPS proxy suspends only URLs outside its exclusions`() = withSettings(
        """
        <key>HTTPSEnable</key><integer>1</integer>
        <key>HTTPSProxy</key><string>127.0.0.1</string><key>HTTPSPort</key><integer>7890</integer>
        <key>ExceptionsList</key><array><string>*.example.com</string></array>
        """,
    ) { settings ->
        assertFalse(systemProxyAppliesToUrl(FORUM, settings))
        assertTrue(systemProxyAppliesToUrl("https://outside.test/", settings))
    }

    @Test
    fun `a saved proxy address with its switch off does not suspend DoH`() = withSettings(
        """
        <key>HTTPSEnable</key><integer>0</integer>
        <key>HTTPSProxy</key><string>127.0.0.1</string><key>HTTPSPort</key><integer>7890</integer>
        """,
    ) { settings ->
        assertFalse(systemProxyAppliesToUrl(FORUM, settings))
    }

    @Test
    fun `an enabled PAC configuration suspends without executing its script`() = withSettings(
        """
        <key>ProxyAutoConfigEnable</key><integer>1</integer>
        <key>ProxyAutoConfigURLString</key><string>https://proxy.example.test/proxy.pac</string>
        """,
    ) { settings ->
        assertTrue(systemProxyAppliesToUrl(FORUM, settings))
    }

    private fun withSettings(entries: String, check: (CFDictionaryRef) -> Unit) {
        val xml = """<?xml version="1.0" encoding="UTF-8"?><plist version="1.0"><dict>$entries</dict></plist>"""
        val bytes = xml.encodeToByteArray()
        val data = bytes.usePinned { CFDataCreate(null, it.addressOf(0).reinterpret(), bytes.size.toLong()) }
        requireNotNull(data)
        val settings = try {
            CFPropertyListCreateWithData(null, data, kCFPropertyListImmutable, null, null)
        } finally {
            CFRelease(data)
        }
        requireNotNull(settings)
        try {
            check(settings.reinterpret())
        } finally {
            CFRelease(settings)
        }
    }

    private companion object {
        const val FORUM = "https://forum.example.com/thread"
    }
}
