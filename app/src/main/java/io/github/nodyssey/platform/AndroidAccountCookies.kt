package io.github.nodyssey.platform

import android.webkit.CookieManager
import androidx.webkit.CookieManagerCompat
import androidx.webkit.WebViewFeature
import io.github.nodyssey.data.session.AccountCookieStore
import io.github.plaza.core.net.SiteConfig
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Uses WebKit's attribute-preserving API; getCookie() cannot preserve expiry or HttpOnly. */
class AndroidAccountCookies internal constructor(
    config: SiteConfig,
    private val browser: AccountCookieBrowser,
    private val nowMillis: () -> Long,
) : AccountCookieStore {
    constructor(
        config: SiteConfig,
        manager: CookieManager = CookieManager.getInstance(),
        nowMillis: () -> Long = System::currentTimeMillis,
    ) : this(config, WebViewAccountCookieBrowser(manager), nowMillis)

    private val urls = config.sessionUrls.flatMap { listOf(it, it.trimEnd('/') + "/api/") }.distinct()
    private val sessionNames = config.sessionCookieNames.toSet()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun capture(): String {
        checkSupported()
        return json.encodeToString(existingCookies())
    }

    private fun existingCookies(): List<SavedCookie> =
        urls.flatMap { url ->
            browser.headers(url).mapNotNull { header -> normalizeAccountCookie(url, header) }
        }.distinctBy { record ->
            val parsed = checkNotNull(Cookie.parse(record.url.toHttpUrl(), record.header))
            Triple(parsed.name, parsed.domain, parsed.path) to parsed.hostOnly
        }

    override suspend fun replace(snapshot: String) {
        if (snapshot.isEmpty() && !browser.supportsCookieInfo()) {
            // Old WebViews expose names but not scope. Expire each possible path and domain.
            val expired = buildList {
                urls.forEach { url ->
                    val host = url.toHttpUrl().host.removePrefix("www.")
                    browser.cookie(url).orEmpty().split(';').map { it.trim().substringBefore('=') }.filter { it.isNotEmpty() }.forEach { name ->
                        listOf("/", "/api/").forEach { path ->
                            add(url to "$name=; Max-Age=0; Path=$path; Secure")
                            add(url to "$name=; Max-Age=0; Path=$path; Domain=$host; Secure")
                        }
                    }
                }
            }
            withContext(NonCancellable) {
                writeAll(expired, requireAccepted = false)
                browser.flush()
                checkSessionCleared()
            }
            currentCoroutineContext().ensureActive()
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
        val deletions = existingCookies().map { record ->
            val cookie = checkNotNull(Cookie.parse(record.url.toHttpUrl(), record.header))
            val expired = buildString {
                append(cookie.name).append("=; Max-Age=0; Path=").append(cookie.path)
                if (!cookie.hostOnly) append("; Domain=").append(cookie.domain)
                if (cookie.secure) append("; Secure")
            }
            record.url to expired
        }
        withContext(NonCancellable) {
            writeAll(deletions)
            // Accepted deletions can still miss the original login if their scope differs.
            checkSessionCleared()
            val valid = incoming.filter { it.expiresAtMillis == null || it.expiresAtMillis > nowMillis() }
            writeAll(valid.map { it.url to it.header })
            browser.flush()
        }
        currentCoroutineContext().ensureActive()
    }

    private fun checkSessionCleared() {
        check(
            urls.none { url ->
                browser.cookie(url).orEmpty().split(';').any { pair ->
                    pair.trim().substringBefore('=').trim() in sessionNames && pair.substringAfter('=', "").isNotBlank()
                }
            },
        ) { "WebView could not clear the active login" }
    }

    private suspend fun writeAll(records: List<Pair<String, String>>, requireAccepted: Boolean = true) = coroutineScope {
        // Wait for every issued callback, including siblings of a rejected write, before rollback.
        val accepted = records.map { (url, header) -> async { browser.write(url, header) } }.awaitAll()
        check(!requireAccepted || accepted.all { it }) { "WebView refused a saved cookie" }
    }

    private fun checkSupported() {
        if (!browser.supportsCookieInfo()) {
            throw UnsupportedOperationException(UNSUPPORTED_WEBVIEW)
        }
    }
}

private const val UNSUPPORTED_WEBVIEW = "Account switching requires an updated Android System WebView"

internal interface AccountCookieBrowser {
    fun supportsCookieInfo(): Boolean
    fun headers(url: String): List<String>
    fun cookie(url: String): String?
    suspend fun write(url: String, header: String): Boolean
    fun flush()
}

private class WebViewAccountCookieBrowser(private val manager: CookieManager) : AccountCookieBrowser {
    override fun supportsCookieInfo() = WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)
    override fun headers(url: String): List<String> = if (WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)) {
        CookieManagerCompat.getCookieInfo(manager, url)
    } else {
        throw UnsupportedOperationException(UNSUPPORTED_WEBVIEW)
    }
    override fun cookie(url: String): String? = manager.getCookie(url)
    override suspend fun write(url: String, header: String): Boolean = suspendCoroutine { continuation ->
        manager.setCookie(url, header) { accepted -> continuation.resume(accepted) }
    }
    override fun flush() = manager.flush()
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
