@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package io.github.plaza.core.net

import io.github.nodyssey.data.session.AccountCookieStore
import io.github.nodyssey.data.session.AccountNativeFailure
import io.github.nodyssey.data.session.InvalidAccountCookieSnapshot
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSHTTPCookieExpires
import platform.Foundation.NSHTTPCookieMaximumAge
import platform.Foundation.NSHTTPCookieStorage
import platform.Foundation.NSPropertyListSerialization
import platform.Foundation.NSPropertyListXMLFormat_v1_0
import platform.Foundation.NSURL
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create
import platform.Foundation.timeIntervalSince1970
import platform.WebKit.WKHTTPCookieStore
import platform.WebKit.WKWebsiteDataStore
import kotlin.coroutines.resume

/** Foundation cookie properties retain domain, path, expiry, Secure and HttpOnly on disk. */
class AppleAccountCookies(
    config: SiteConfig,
    private val storage: NSHTTPCookieStorage = NSHTTPCookieStorage.sharedHTTPCookieStorage,
    private val webKit: WKHTTPCookieStore? = WKWebsiteDataStore.defaultDataStore().httpCookieStore,
) : AccountCookieStore {
    private val host = requireNotNull(NSURL.URLWithString(config.baseUrl)?.host).removePrefix("www.")

    override suspend fun capture(): String = withContext(Dispatchers.Main) {
        encodeAccountCookies(mine())
    }

    override suspend fun replace(snapshot: String) = withContext(Dispatchers.Main) {
        val incoming = if (snapshot.isEmpty()) emptyList() else decodeAccountCookies(snapshot)
        if (incoming.any { !owns(it) }) throw InvalidAccountCookieSnapshot()
        val browser = suspendCancellableCoroutine<List<NSHTTPCookie>> { continuation ->
            if (webKit == null) continuation.resume(emptyList()) else webKit.getAllCookies { continuation.resume(it.orEmpty().filterIsInstance<NSHTTPCookie>()) }
        }
        browser.filter(::owns).forEach { cookie ->
            if (webKit != null) suspendCancellableCoroutine { continuation -> webKit.deleteCookie(cookie) { continuation.resume(Unit) } }
        }
        mine().forEach(storage::deleteCookie)
        incoming.forEach { cookie ->
            val expiry = cookie.expiresDate
            if (expiry == null || expiry.timeIntervalSince1970 > NSDate().timeIntervalSince1970) {
                storage.setCookie(cookie)
                if (webKit != null) suspendCancellableCoroutine { continuation -> webKit.setCookie(cookie) { continuation.resume(Unit) } }
            }
        }
    }

    private fun mine(): List<NSHTTPCookie> = storage.cookies.orEmpty().filterIsInstance<NSHTTPCookie>().filter(::owns)

    private fun owns(cookie: NSHTTPCookie): Boolean = cookie.domain.trimStart('.').let { it == host || it.endsWith(".$host") }
}

internal fun encodeAccountCookies(cookies: List<NSHTTPCookie>): String {
    val properties = cookies.mapNotNull(::savedCookieProperties)
    val data = memScoped {
        val failure = alloc<ObjCObjectVar<NSError?>>()
        failure.value = null
        // Foundation's binary writer keys its reference table by Objective-C object identity.
        // Kotlin 2.4.20 recreates bridge wrappers on repeated reads, yielding self-references
        // instead of cookie children. XML is Foundation's format without object references.
        // Reconsider binary only when the Native round-trip test passes with the pinned bridge.
        NSPropertyListSerialization.dataWithPropertyList(
            properties,
            format = NSPropertyListXMLFormat_v1_0,
            options = 0u,
            error = failure.ptr,
        ) ?: throw AccountNativeFailure(failure.value?.domain.orEmpty(), failure.value?.code?.toLong())
    }
    return data.base64EncodedStringWithOptions(0u)
}

internal fun decodeAccountCookies(snapshot: String): List<NSHTTPCookie> {
    val data = NSData.create(base64EncodedString = snapshot, options = 0u) ?: throw InvalidAccountCookieSnapshot()
    val root = memScoped {
        val failure = alloc<ObjCObjectVar<NSError?>>()
        failure.value = null
        NSPropertyListSerialization.propertyListWithData(data, options = 0u, format = null, error = failure.ptr)
            ?: throw InvalidAccountCookieSnapshot(AccountNativeFailure(failure.value?.domain.orEmpty(), failure.value?.code?.toLong()))
    }
    val properties = root as? List<*> ?: throw InvalidAccountCookieSnapshot()
    return properties.map { raw ->
        @Suppress("UNCHECKED_CAST")
        val item = raw as? Map<Any?, *> ?: throw InvalidAccountCookieSnapshot()
        NSHTTPCookie.cookieWithProperties(item) ?: throw InvalidAccountCookieSnapshot()
    }
}

/** Relative Max-Age must not start over when Foundation constructs a saved cookie later. */
internal fun savedCookieProperties(cookie: NSHTTPCookie): Map<Any?, Any?>? =
    cookie.properties?.mapValues { (_, value) ->
        // OriginURL and CommentURL also accept NSString, which a property list can represent.
        if (value is NSURL) value.absoluteString.orEmpty() else value
    }?.toMutableMap()?.apply {
        remove(NSHTTPCookieMaximumAge)
        cookie.expiresDate?.let { this[NSHTTPCookieExpires] = it }
    }
