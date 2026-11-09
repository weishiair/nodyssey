package io.github.nodyssey.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SeekMailSiteTest {
    @Test
    fun `mail authorization accepts only HTTPS mail and NodeSeek origins`() {
        assertTrue(SeekMailSite.isAuthorizationUrl("https://seek.li/login"))
        assertTrue(SeekMailSite.isAuthorizationUrl("https://www.nodeseek.com/connect?target=NodeMailDev"))
        assertFalse(SeekMailSite.isMailUrl("https://www.nodeseek.com/"))
        for (url in listOf(
            "http://seek.li/login",
            "https://seek.li.evil.example/login",
            "https://seek.li@evil.example/login",
            "https://evil.example@seek.li/login",
            "https://seek.li:8443/login",
            "https://www.deepflood.com/login",
            "javascript:alert(1)",
        )) {
            assertFalse(SeekMailSite.isAuthorizationUrl(url), url)
        }
        assertFalse(NodeSeekSite.isTrustedWebViewUrl("https://seek.li/login"))
    }
}
