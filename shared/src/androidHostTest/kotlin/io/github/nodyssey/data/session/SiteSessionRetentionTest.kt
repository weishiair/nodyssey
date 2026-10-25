package io.github.nodyssey.data.session

import io.github.nodyssey.core.ActiveSite
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.Site
import io.github.plaza.core.net.SessionCookies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteSessionRetentionTest {
    @Test
    fun `returning to either site restores its session without borrowing the other sites credentials`() {
        val cookies = FakeSessionCookieStore()
        cookies.setCookie(Site.NODESEEK.origin, "session=nodesession")
        cookies.setCookie(Site.NODESEEK.origin, "cf_clearance=nodeclearance")
        val original = SessionRepository(SessionCookies(NodeSeekSite.configFor(Site.NODESEEK), cookies)).peek()
        try {
            ActiveSite.install(Site.DEEPFLOOD)
            val df = SessionRepository(SessionCookies(NodeSeekSite.configFor(Site.DEEPFLOOD), cookies))
            assertFalse(df.peek().isSignedIn)
            assertFalse(df.peek().hasClearance)
            cookies.setCookie(Site.DEEPFLOOD.origin, "session=deepsession")
            cookies.setCookie(Site.DEEPFLOOD.origin, "cf_clearance=deepclearance")
            df.sync()
            val deepState = df.peek()
            repeat(3) {
                ActiveSite.install(Site.NODESEEK)
                val nsAgain = SessionRepository(SessionCookies(NodeSeekSite.configFor(Site.NODESEEK), cookies)).peek()
                assertEquals(original.fingerprint, nsAgain.fingerprint)
                assertTrue(nsAgain.isSignedIn)
                ActiveSite.install(Site.DEEPFLOOD)
                val dfAgain = SessionRepository(SessionCookies(NodeSeekSite.configFor(Site.DEEPFLOOD), cookies)).peek()
                assertEquals(deepState.fingerprint, dfAgain.fingerprint)
                assertTrue(dfAgain.isSignedIn)
                // Existing session readers stay bound to their site's cookie URLs, even after install changes.
                assertTrue(df.peek().isSignedIn)
                assertEquals(original.fingerprint, SessionCookies(NodeSeekSite.configFor(Site.NODESEEK), cookies).snapshot().fingerprint)
            }
        } finally {
            ActiveSite.install(Site.DEFAULT)
        }
    }
}
