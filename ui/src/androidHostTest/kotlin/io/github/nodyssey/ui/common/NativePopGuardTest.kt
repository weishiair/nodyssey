package io.github.nodyssey.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativePopGuardTest {
    @Test
    fun `empty guard allows pop`() {
        val guard = PagePopGuard()
        assertTrue(guard.shouldAllowPop())
    }

    @Test
    fun `blocking guard denies pop and invokes its callback`() {
        val guard = PagePopGuard()
        val token = Any()
        var blocked = true
        var attempts = 0
        guard.register(token, blocked = { blocked }, onBlockedAttempt = { attempts++ })

        assertFalse(guard.shouldAllowPop())
        assertEquals(1, attempts)

        blocked = false
        assertTrue(guard.shouldAllowPop())
        assertEquals(1, attempts)
    }

    @Test
    fun `newest registered guard takes precedence and unregisters cleanly`() {
        val guard = PagePopGuard()
        val outer = Any()
        val inner = Any()
        var outerAttempts = 0
        var innerAttempts = 0

        guard.register(outer, blocked = { true }, onBlockedAttempt = { outerAttempts++ })
        guard.register(inner, blocked = { true }, onBlockedAttempt = { innerAttempts++ })

        assertFalse(guard.shouldAllowPop())
        assertEquals(0, outerAttempts)
        assertEquals(1, innerAttempts)

        guard.unregister(inner)
        assertFalse(guard.shouldAllowPop())
        assertEquals(1, outerAttempts)

        guard.unregister(outer)
        assertTrue(guard.shouldAllowPop())
    }
}
