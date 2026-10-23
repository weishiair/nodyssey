package io.github.nodyssey.ui.login

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnstileNavigationPolicyTest {
    @Test
    fun `verification frames can navigate after the initial document without replacing it`() {
        val policy = TurnstileNavigationPolicy()

        assertTrue(policy.allowsNavigation(isMainFrame = true))
        repeat(3) { assertTrue(policy.allowsNavigation(isMainFrame = false)) }
        assertFalse(policy.allowsNavigation(isMainFrame = true))
        assertTrue(policy.allowsNavigation(isMainFrame = false))
    }

    @Test
    fun `a child frame or popup cannot spend the initial document allowance`() {
        val policy = TurnstileNavigationPolicy()

        assertTrue(policy.allowsNavigation(isMainFrame = false))
        assertFalse(policy.allowsNavigation(isMainFrame = null))
        assertTrue(policy.allowsNavigation(isMainFrame = true))
        assertFalse(policy.allowsNavigation(isMainFrame = true))
    }

    @Test
    fun `Android permits challenge frames but never a replacement document`() {
        val policy = TurnstileNavigationPolicy(allowInitialDocument = false)

        assertFalse(policy.allowsNavigation(isMainFrame = true))
        assertTrue(policy.allowsNavigation(isMainFrame = false))
        assertFalse(policy.allowsNavigation(isMainFrame = null))
        assertFalse(policy.allowsNavigation(isMainFrame = true))
    }
}
