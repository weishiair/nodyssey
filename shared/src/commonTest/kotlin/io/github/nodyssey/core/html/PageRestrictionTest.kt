package io.github.nodyssey.core.html

import io.github.nodyssey.core.NodeSeekSite
import io.github.plaza.core.net.ChallengeDetector
import io.github.plaza.core.net.SiteError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PageRestrictionTest {
    private val detector = ChallengeDetector(NodeSeekSite.CONFIG.markers)
    private val url = NodeSeekSite.BASE_URL + "/"

    @Test
    fun `a feed discussing permission errors is still readable`() {
        assertNull(detector.detect(Fixtures.load("feed-permission-discussion.html"), 200, emptyMap(), url))
    }

    @Test
    fun `quoted restrictions in posts and signatures do not block reading`() {
        restrictionMessages.forEach { message ->
            val html = Fixtures.load("post-permission-discussion.html").replace("__MESSAGE__", message)
            assertNull(detector.detect(html, 200, emptyMap(), url), message)
        }
    }

    @Test
    fun `a settings script or editable readme is not a restriction response`() {
        restrictionMessages.forEach { message ->
            val html = Fixtures.load("settings-permission-script.html").replace("__MESSAGE__", message)
            assertNull(detector.detect(html, 200, emptyMap(), url), message)
        }
    }

    @Test
    fun `real restrictions remain effective inside a normal forum frame`() {
        listOf(
            Triple("post-login-required.html", 200, SiteError.LoginRequired),
            Triple("post-level-required.html", 200, SiteError.LevelRequired(5)),
            Triple("post-private.html", 404, SiteError.PrivatePost),
        ).forEach { (fixture, status, error) ->
            assertEquals(error, detector.detect(Fixtures.load(fixture), status, emptyMap(), url), fixture)
        }
        assertEquals(
            SiteError.LoginRequired,
            detector.detect("""{"success":false,"message":"Wrong category"}""", 400, emptyMap(), url),
        )
    }

    private val restrictionMessages = listOf(
        "权限不足",
        "需要注册用户才能查看",
        "Wrong category",
        "查看本帖需要Lv5，您的权限不足😑，请赚取🍗升级您的用户等级",
        "本帖已经被用户设为私有，您没有阅读权限",
    )
}
