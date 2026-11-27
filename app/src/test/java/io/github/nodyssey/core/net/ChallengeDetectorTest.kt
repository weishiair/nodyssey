package io.github.nodyssey.core.net

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.html.Fixtures
import io.github.plaza.core.net.ChallengeDetector
import io.github.plaza.core.net.SiteError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The detector itself lives in `:core` and knows nothing about NodeSeek. What this pins is the pair:
 * [NodeSeekSite.CONFIG]'s markers against pages captured from the site they were read off.
 */
class ChallengeDetectorTest {
    /**
     * The address the request was for. A challenge carries it back so the web view can be sent
     * somewhere that will actually ask — see [SiteError.Cloudflare]. Not the home page on purpose:
     * that is the one path NodeSeek's zone exempts, which is the bug this parameter exists for.
     */
    private companion object {
        const val URL = "https://www.nodeseek.com/categories/daily"
    }

    private val detector = ChallengeDetector(NodeSeekSite.CONFIG.markers)

    @Test
    fun `a real page is not a challenge`() {
        assertNull(detector.detect(Fixtures.load("page-1.html"), 200, emptyMap(), URL))
        assertNull(detector.detect(Fixtures.load("post-703863-1.html"), 200, emptyMap(), URL))
    }

    /**
     * The shape `/setting` actually comes back as — captured off the device on 2026-08-02: a 200
     * carrying the bootstrap, no content markup, and Cloudflare's own script inlined into it. It used
     * to be read as a challenge, which is why 联系方式 could never show an email.
     */
    @Test
    fun `a settings page carrying the bootstrap is not a challenge`() {
        val html =
            """
            <!DOCTYPE html> <html data-server-rendered="true"><head>
            <script src="/cdn-cgi/challenge-platform/scripts/jsd/main.js"></script>
            </head><body><div id="app"></div>
            <script id="temp-script" type="text/json">eyJ1c2VyIjp7fX0=</script>
            </body></html>
            """.trimIndent()
        assertNull(detector.detect(html, 200, emptyMap(), URL))
    }

    @Test
    fun `a cloudflare interstitial is detected`() {
        assertEquals(
            SiteError.Cloudflare(URL),
            detector.detect(Fixtures.load("cloudflare-challenge.html"), 403, emptyMap(), URL),
        )
    }

    /**
     * The origin's own error page, captured off the live site on 2026-09-13 (`/categories/carpool`,
     * trimmed of nginx's padding comments): nothing of NodeSeek's in it, and Cloudflare's detection
     * script injected into it the way it is into every body Cloudflare proxies. It used to read as a
     * challenge, which put 去验证 over a board that a retry would have opened — and the web view it
     * opened had nothing to solve, so the wall never came down.
     */
    @Test
    fun `an origin 503 carrying the detection script is the 503 it is, not a challenge`() {
        val html =
            """
            <html>
            <head><title>503 Service Temporarily Unavailable</title></head>
            <body>
            <center><h1>503 Service Temporarily Unavailable</h1></center>
            <hr><center>nginx</center>
            <script>(function(){function c(){var b=a.contentDocument||(a.contentWindow&&a.contentWindow.document);if(b){var d=b.createElement('script');d.innerHTML="window.__CF${'$'}cv${'$'}params={r:'a3a7c40d19b2fd32',t:'MTc4OTMwODk0NQ=='};var a=document.createElement('script');a.src='/cdn-cgi/challenge-platform/scripts/jsd/main.js';document.getElementsByTagName('head')[0].appendChild(a);";b.getElementsByTagName('head')[0].appendChild(d)}}if(document.body){var a=document.createElement('iframe');a.height=1;a.width=1;a.style.position='absolute';a.style.top=0;a.style.left=0;a.style.border='none';a.style.visibility='hidden';document.body.appendChild(a);if('loading'!==document.readyState)c();else if(window.addEventListener)document.addEventListener('DOMContentLoaded',c);else{var e=document.onreadystatechange||function(){};document.onreadystatechange=function(b){e(b);'loading'!==document.readyState&&(document.onreadystatechange=e,c())}}}})();</script><script type="module" src="https://static.cloudflareinsights.com/beacon.min.js/v31edd6df95cf4e85bb4c19e7a9bdbcba1788362987495" crossorigin="anonymous"></script>
            </body>
            </html>
            """.trimIndent()
        assertEquals(SiteError.Http(503), detector.detect(html, 503, emptyMap(), URL))
    }

    /**
     * Cloudflare's WAF block page, as its template ships (trimmed): a 403 with no `cf-mitigated`
     * header and nothing of the site's in it. It used to fall through to `HTTP 403` with 重试 as the
     * main action, which only asks the same refused question again; the web view is what the reader
     * needed, and it is what 在网页中打开 already proved could open the thread.
     */
    @Test
    fun `a cloudflare block page sends the reader to the web view`() {
        val html =
            """
            <html class="no-js" lang="en-US"><head><title>Attention Required! | Cloudflare</title>
            <link rel="stylesheet" id="cf_styles-css" href="/cdn-cgi/styles/cf.errors.css" /></head>
            <body><div id="cf-wrapper"><div id="cf-error-details" class="cf-error-details-wrapper">
            <div class="cf-wrapper cf-header cf-error-overview">
            <h1 data-translate="block_headline">Sorry, you have been blocked</h1>
            <h2 class="cf-subheadline"><span data-translate="unable_to_access">You are unable to access</span> nodeseek.com</h2>
            </div><h2 data-translate="blocked_why_headline">Why have I been blocked?</h2>
            <span data-translate="cf_ray">Cloudflare Ray ID:</span> <strong>a5e76d00cb405846</strong>
            </div></div></body></html>
            """.trimIndent()
        assertEquals(SiteError.Cloudflare(URL), detector.detect(html, 403, mapOf("Server" to "cloudflare"), URL))
    }

    /**
     * Cloudflare's 1015 page shares the 1xxx 「used Cloudflare to restrict access」 title with the
     * block pages, but arrives as a 429. Waiting is what clears it; a web view would only spend
     * another request against the same limit.
     */
    @Test
    fun `cloudflare's rate-limit page stays a throttle rather than a block`() {
        val html =
            """
            <html><head><title>Access denied | www.nodeseek.com used Cloudflare to restrict access</title></head>
            <body><div id="cf-error-details"><h1><span data-translate="error">Error</span><span>1015</span></h1>
            <h2 data-translate="error_desc">You are being rate limited</h2></div></body></html>
            """.trimIndent()
        assertEquals(SiteError.RateLimited, detector.detect(html, 429, emptyMap(), URL))
    }

    /** The same error-page wrapper frames the origin failing, which no web view can clear. */
    @Test
    fun `a cloudflare origin error is not a block`() {
        val html =
            """
            <html><head><title>www.nodeseek.com | 522: Connection timed out</title></head>
            <body><div id="cf-error-details" class="cf-error-details-wrapper">
            <h1><span class="cf-error-type" data-translate="error">Error</span><span class="cf-error-code">522</span></h1>
            </div></body></html>
            """.trimIndent()
        assertEquals(SiteError.Http(522), detector.detect(html, 522, emptyMap(), URL))
    }

    @Test
    fun `the cf-mitigated header alone is enough`() {
        assertEquals(
            SiteError.Cloudflare(URL),
            detector.detect("<html></html>", 200, mapOf("CF-Mitigated" to "challenge"), URL),
        )
    }

    /** 内版, signed out: JSON on an HTML route, and the one sentence that separates it from a 404. */
    @Test
    fun `a members-only board refused as JSON is a login wall`() {
        assertEquals(
            SiteError.LoginRequired,
            detector.detect("""{"success":false,"message":"Wrong category"}""", 400, emptyMap(), URL),
        )
    }

    @Test
    fun `a login wall is reported separately so the UI can offer sign-in`() {
        assertEquals(
            SiteError.LoginRequired,
            detector.detect(Fixtures.load("post-login-required.html"), 200, emptyMap(), URL),
        )
    }

    /**
     * The level wall says 权限不足 too, which is one of the login markers — so this pins the order as
     * much as the marker: read as a login wall, it offers 登录 to a reader who is already signed in.
     */
    @Test
    fun `a level wall is its own state and carries the level`() {
        assertEquals(
            SiteError.LevelRequired(requiredLevel = 5),
            detector.detect(Fixtures.load("post-level-required.html"), 200, emptyMap(), URL),
        )
    }

    /**
     * The captured page keeps the sentence in one text node, so this is the shape we do *not* have:
     * a re-render that wraps the number still has to classify. Restriction checks read visible text,
     * so inline markup no longer hides the level from the pattern.
     */
    @Test
    fun `a level wall whose number is wrapped in markup still carries its level`() {
        val html =
            """
            <html><body><div id="nsk-body"><h1>查看本帖需要<b>Lv5</b>，您的权限不足😑，
            请赚取🍗升级您的用户等级</h1></div></body></html>
            """.trimIndent()
        assertEquals(SiteError.LevelRequired(requiredLevel = 5), detector.detect(html, 200, emptyMap(), URL))
    }

    /**
     * 私有 shares no phrase with either wall — 阅读权限 is not 权限不足 — so what this pins is that the
     * sentence is consulted at all, and before the usable-page markers: the captured page carries
     * `id="nsk-body"`, which classifies as real content and would send an empty thread to the parsers.
     *
     * The 404 is the live status, and passing it in is the second half of the same point: a check
     * placed after the status handling would report HTTP 404 and lose the reason.
     */
    @Test
    fun `a private thread is its own state rather than an empty page`() {
        assertEquals(
            SiteError.PrivatePost,
            detector.detect(Fixtures.load("post-private.html"), 404, emptyMap(), URL),
        )
    }

    /** The wording is a whole clause because a thread *about* 私有 must still open. */
    @Test
    fun `a thread whose body discusses the feature is not a private thread`() {
        val html =
            """
            <html><body><div id="nsk-body"><div class="post-content">
            发帖的时候可以把阅读权限设为私有，只有自己能看
            </div></div></body></html>
            """.trimIndent()
        assertNull(detector.detect(html, 200, emptyMap(), URL))
    }
}
