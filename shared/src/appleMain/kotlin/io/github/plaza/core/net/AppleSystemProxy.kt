@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.plaza.core.net

import kotlinx.cinterop.reinterpret
import platform.CFNetwork.CFNetworkCopyProxiesForURL
import platform.CFNetwork.kCFProxyTypeKey
import platform.CFNetwork.kCFProxyTypeNone
import platform.CoreFoundation.CFArrayGetCount
import platform.CoreFoundation.CFArrayGetValueAtIndex
import platform.CoreFoundation.CFDictionaryGetValue
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCompare
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFURLCreateWithString
import platform.CoreFoundation.kCFStringEncodingUTF8

/** Resolve proxy exclusions for this URL; a tunnel interface alone is not evidence of a proxy. */
internal fun systemProxyAppliesToUrl(url: String, settings: CFDictionaryRef): Boolean {
    val text = CFStringCreateWithCString(null, url, kCFStringEncodingUTF8) ?: return false
    val target = try {
        CFURLCreateWithString(null, text, null)
    } finally {
        CFRelease(text)
    } ?: return false
    val proxies = try {
        CFNetworkCopyProxiesForURL(target, settings)
    } finally {
        CFRelease(target)
    } ?: return false
    return try {
        (0 until CFArrayGetCount(proxies)).any { index ->
            val proxy: CFDictionaryRef = CFArrayGetValueAtIndex(proxies, index)?.reinterpret() ?: return@any false
            val type = CFDictionaryGetValue(proxy, kCFProxyTypeKey)
            // A PAC entry is configured even before its JavaScript is evaluated. Do not fetch or
            // execute PAC code merely to enable a settings control.
            type != null && CFStringCompare(type.reinterpret(), kCFProxyTypeNone, 0u) != 0L
        }
    } finally {
        CFRelease(proxies)
    }
}
