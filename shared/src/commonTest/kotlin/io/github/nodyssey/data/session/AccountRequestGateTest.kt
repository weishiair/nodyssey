package io.github.nodyssey.data.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AccountRequestGateTest {
    // Bug: a worker can repopulate A's cache after the account switch has cleared it for B.
    @Test
    fun `changing accounts joins cancellation cleanup and rejects new account work until resumed`() = runTest {
        val gate = AccountRequestGate()
        val started = CompletableDeferred<Unit>()
        var finished = false
        val old = async {
            gate.run {
                try {
                    started.complete(Unit)
                    awaitCancellation()
                } finally {
                    finished = true
                }
            }
        }
        started.await()
        gate.pause()
        assertTrue(old.isCancelled)
        assertTrue(finished)
        var entered = false
        val blocked = async { gate.run { entered = true } }
        runCurrent()
        assertTrue(blocked.isCancelled)
        assertFalse(entered)
        assertEquals("checked", gate.verify { gate.run { "checked" } })
        gate.resume()
        assertEquals("new account", gate.run { "new account" })
    }
}
