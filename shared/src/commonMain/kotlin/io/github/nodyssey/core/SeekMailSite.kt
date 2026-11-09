package io.github.nodyssey.core

import io.github.plaza.core.net.WebUrl

/** Mail's authorization scope, deliberately separate from the forum WebView's host policy. */
object SeekMailSite {
    const val ORIGIN = "https://seek.li"
    const val LOGIN = "$ORIGIN/login"
    const val NODESEEK_AUTHORIZATION_CHALLENGE = "https://www.nodeseek.com/api/cAuth?target=NodeMailDev"

    fun isMailUrl(value: String): Boolean = trustedHost(value) == "seek.li"

    fun isAuthorizationUrl(value: String): Boolean =
        trustedHost(value) in setOf("seek.li", "www.nodeseek.com", "nodeseek.com")

    private fun trustedHost(value: String): String? = WebUrl.parse(value)?.takeIf {
        it.scheme == "https" && it.userInfo == null && (it.port == -1 || it.port == 443)
    }?.host?.lowercase()
}
