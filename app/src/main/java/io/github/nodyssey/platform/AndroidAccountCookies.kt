package io.github.nodyssey.platform

import android.webkit.CookieManager
import androidx.webkit.CookieManagerCompat
import androidx.webkit.WebViewFeature
import io.github.nodyssey.data.session.AccountCookieStore
import io.github.plaza.core.net.SiteConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume

/** Uses WebKit's attribute-preserving API; getCookie() cannot preserve expiry or HttpOnly. */
class AndroidAccountCookies(
    config: SiteConfig,
    private val manager: CookieManager = CookieManager.getInstance(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : AccountCookieStore {
    private val urls = config.sessionUrls.flatMap { listOf(it, it.trimEnd('/') + "/api/") }.distinct()
    private val sessionNames = config.sessionCookieNames.toSet()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun capture(): String {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)) {
            throw UnsupportedOperationException("Account switching requires an updated Android System WebView")
        }
        val records = urls.flatMap { url ->
            val headers = if (WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)) {
                CookieManagerCompat.getCookieInfo(manager, url)
            } else {
                throw UnsupportedOperationException("Account switching requires an updated Android System WebView")
            }
            headers.mapNotNull { header ->
                normalizeAccountCookie(url, header)
            }
        }.distinctBy { record ->
            val parsed = checkNotNull(Cookie.parse(record.url.toHttpUrl(), record.header))
            Triple(parsed.name, parsed.domain, parsed.path) to parsed.hostOnly
        }
        return json.encodeToString(records)
    }

    override suspend fun replace(snapshot: String) {
        if (snapshot.isEmpty() && !WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)) {
            // Basic sign-out must remain possible on old WebViews. Only getCookieInfo can safely
            // round-trip attributes, so adding/switching accounts still requires that feature.
            urls.forEach { url ->
                manager.getCookie(url).orEmpty().split(';').map { it.trim().substringBefore('=') }.filter { it.isNotEmpty() }.forEach { name ->
                    val host = url.toHttpUrl().host.removePrefix("www.")
                    listOf("/", "/api/").forEach { path ->
                        write(url, "$name=; Max-Age=0; Path=$path; Secure", requireAccepted = false)
                        write(url, "$name=; Max-Age=0; Path=$path; Domain=$host; Secure", requireAccepted = false)
                    }
                }
            }
            manager.flush()
            checkSessionCleared()
            return
        }
        checkSupported()
        val incoming = if (snapshot.isEmpty()) {
            emptyList()
        } else {
            json.decodeFromString<List<SavedCookie>>(snapshot).map { record ->
                require(record.url in urls) { "Cookie belongs to another site" }
                checkNotNull(normalizeAccountCookie(record.url, record.header, record.hostOnly))
            }
        }
        val existing = json.decodeFromString<List<SavedCookie>>(capture())
        existing.forEach { record ->
            val cookie = checkNotNull(Cookie.parse(record.url.toHttpUrl(), record.header))
            val expired = buildString {
                append(cookie.name).append("=; Max-Age=0; Path=").append(cookie.path)
                if (!cookie.hostOnly) append("; Domain=").append(cookie.domain)
                if (cookie.secure) append("; Secure")
            }
            write(record.url, expired)
        }
        // A successful setCookie callback only means the deletion was accepted, not that it
        // targeted the original login. Never mix a surviving login with an incoming account.
        checkSessionCleared()
        incoming.filter { it.expiresAtMillis == null || it.expiresAtMillis > nowMillis() }.forEach { record ->
            require(record.url in urls) { "Cookie belongs to another site" }
            write(record.url, record.header)
        }
        manager.flush()
    }

    private fun checkSessionCleared() {
        check(
            urls.none { url ->
                manager.getCookie(url).orEmpty().split(';').any { pair ->
                    pair.trim().substringBefore('=').trim() in sessionNames && pair.substringAfter('=', "").isNotBlank()
                }
            },
        ) { "WebView could not clear the active login" }
    }

    private suspend fun write(url: String, header: String, requireAccepted: Boolean = true) = suspendCancellableCoroutine { continuation ->
        manager.setCookie(url, header) { accepted -> continuation.resume(accepted) }
    }.also { check(it || !requireAccepted) { "WebView refused a saved cookie" } }

    private fun checkSupported() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)) {
            throw UnsupportedOperationException("Account switching requires an updated Android System WebView")
        }
    }
}

@Serializable
internal data class SavedCookie(val url: String, val header: String, val expiresAtMillis: Long?, val hostOnly: Boolean? = null)

internal fun normalizeAccountCookie(url: String, header: String, hostOnly: Boolean? = null): SavedCookie? {
    val origin = url.toHttpUrl()
    // GET_COOKIE_INFO uses Chromium's BuildCookieAttributesLine: Domain is present even for
    // host-only cookies. Only a leading dot means a domain cookie in that exported format.
    // Strip a host-only Domain before parsing it as Set-Cookie, or deletion targets a different
    // cookie and replay broadens its scope. Keep real domain cookies' leading dots intact.
    val exported = header.split(';').filterIndexed { index, attribute ->
        index == 0 || !(
            attribute.substringBefore('=').trim().equals("Domain", ignoreCase = true) &&
                (hostOnly ?: attribute.substringAfter('=', "").trim().equals(origin.host, ignoreCase = true))
            )
    }.joinToString(";")
    val parsed = Cookie.parse(origin, exported) ?: return null
    // Max-Age is relative. Replaying it days later would extend a saved login's life.
    val attributes = exported.split(';').filterNot { it.trim().startsWith("Max-Age=", ignoreCase = true) }
    val absolute = if (exported.split(';').any { it.trim().startsWith("Max-Age=", ignoreCase = true) }) {
        attributes.filterNot { it.trim().startsWith("Expires=", ignoreCase = true) }.joinToString(";") +
            "; Expires=" + DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(parsed.expiresAt).atZone(ZoneOffset.UTC))
    } else {
        exported
    }
    return SavedCookie(url, absolute, if (parsed.persistent) parsed.expiresAt else null, parsed.hostOnly)
}
