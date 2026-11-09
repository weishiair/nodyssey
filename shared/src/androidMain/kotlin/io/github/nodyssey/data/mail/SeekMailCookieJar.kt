package io.github.nodyssey.data.mail

import io.github.nodyssey.core.SeekMailSite
import io.github.plaza.core.net.SessionCookieStore
import io.github.plaza.core.net.WebViewCookieJar
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** A mail redirect can never pull another origin's cookies out of the shared browser store. */
class SeekMailCookieJar(store: SessionCookieStore) : CookieJar {
    private val bridge = WebViewCookieJar(store)

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        if (SeekMailSite.isMailUrl(url.toString())) bridge.loadForRequest(url) else emptyList()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (SeekMailSite.isMailUrl(url.toString())) bridge.saveFromResponse(url, cookies)
    }
}
