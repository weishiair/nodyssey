package io.github.nodyssey.platform

import io.github.plaza.core.net.SessionCookieStore
import io.github.plaza.core.net.SiteConfig
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** OkHttp's normal cookie matching, backed only by this account's in-memory snapshot. */
internal class AndroidAttendanceCookies(snapshot: String, private val site: SiteConfig) :
    CookieJar,
    SessionCookieStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val records = mutableMapOf<Triple<String, String, String>, SavedCookie>()

    init {
        if (snapshot.isNotEmpty()) {
            json.decodeFromString<List<SavedCookie>>(snapshot).forEach { record ->
                require(record.url.toHttpUrl().host in site.sessionUrls.map { it.toHttpUrl().host })
                normalizeAccountCookie(record.url, record.header, record.hostOnly)?.let { normalized ->
                    Cookie.parse(normalized.url.toHttpUrl(), normalized.header)?.let { records[key(it)] = normalized }
                }
            }
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> = records.values.mapNotNull { record ->
        Cookie.parse(record.url.toHttpUrl(), record.header)?.takeIf { it.expiresAt > System.currentTimeMillis() && it.matches(url) && it.name !in site.unreplayableCookies }
    }.sortedByDescending { it.path.length }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val origin = site.sessionUrls.firstOrNull { it.toHttpUrl().host == url.host } ?: return
        cookies.forEach { cookie ->
            if (cookie.expiresAt <= System.currentTimeMillis()) {
                records.remove(key(cookie))
            } else {
                // OkHttp's Set-Cookie text omits Chromium's leading dot on real domain cookies.
                // Record scope explicitly so replay cannot confuse this with an old WebView export.
                records[key(cookie)] = SavedCookie(origin, cookie.toString(), if (cookie.persistent) cookie.expiresAt else null, cookie.hostOnly)
            }
        }
    }

    @Synchronized
    fun snapshot(): String = json.encodeToString(records.values.toList())

    override fun cookieHeader(url: String): String? = loadForRequest(url.toHttpUrl()).takeIf { it.isNotEmpty() }
        ?.joinToString("; ") { "${it.name}=${it.value}" }
    override fun setCookie(url: String, cookie: String) {
        Cookie.parse(url.toHttpUrl(), cookie)?.let { saveFromResponse(url.toHttpUrl(), listOf(it)) }
    }

    override suspend fun removeAll() {
        synchronized(this) { records.clear() }
    }
    override fun flush() = Unit
    private fun key(cookie: Cookie) = Triple(cookie.name, cookie.domain, cookie.path)
}
