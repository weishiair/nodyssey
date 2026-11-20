package io.github.nodyssey.ui.postlist

import io.github.nodyssey.core.Site
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteMenuActionsTest {
    @Test
    fun `each site menu action dispatches its Site value and marks only the active site selected`() {
        for (active in Site.entries) {
            val picked = mutableListOf<Site>()
            val actions = siteMenuActions(
                active = active,
                signedInLabel = "Signed in · alice",
                nextLaunchLabel = "Applies on next launch",
                restartsApp = false,
                onSelect = { picked += it },
            )

            assertEquals(Site.entries.map { it.displayName }, actions.map { it.label })
            assertEquals(Site.entries.map { it == active }, actions.map { it.selected })
            assertTrue(actions.all { it.enabled })

            actions.forEach { it.onClick() }
            assertEquals(Site.entries, picked)
        }
    }

    @Test
    fun `subtitles join domain, current-site sign-in, and deferred-switch note with middle dot`() {
        val deferredSignedIn = siteMenuActions(
            active = Site.NODESEEK,
            signedInLabel = "Signed in · alice",
            nextLaunchLabel = "Applies on next launch",
            restartsApp = false,
            onSelect = {},
        )
        assertEquals(
            listOf(
                "nodeseek.com · Signed in · alice",
                "deepflood.com · Applies on next launch",
            ),
            deferredSignedIn.map { it.subtitle },
        )

        val deferredSignedOut = siteMenuActions(
            active = Site.DEEPFLOOD,
            signedInLabel = null,
            nextLaunchLabel = "Applies on next launch",
            restartsApp = false,
            onSelect = {},
        )
        assertEquals(
            listOf(
                "nodeseek.com · Applies on next launch",
                "deepflood.com",
            ),
            deferredSignedOut.map { it.subtitle },
        )

        val immediateSignedIn = siteMenuActions(
            active = Site.NODESEEK,
            signedInLabel = "Signed in · alice",
            nextLaunchLabel = "Applies on next launch",
            restartsApp = true,
            onSelect = {},
        )
        assertEquals(
            listOf(
                "nodeseek.com · Signed in · alice",
                "deepflood.com",
            ),
            immediateSignedIn.map { it.subtitle },
        )
    }
}
