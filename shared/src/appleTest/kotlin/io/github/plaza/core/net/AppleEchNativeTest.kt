@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.plaza.core.net

import io.github.plaza.core.net.ech.nodyssey_network_cancel
import io.github.plaza.core.net.ech.nodyssey_network_free
import io.github.plaza.core.net.ech.nodyssey_network_start
import io.github.plaza.core.net.ech.nodyssey_network_take
import kotlinx.cinterop.toKString
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AppleEchNativeTest {
    @Test fun malformedInputIsRejectedAtTheNativeBoundary() {
        assertEquals(0uL, nodyssey_network_start("{}"))
    }

    @Test fun invalidSchemeCompletesWithSanitizedFactsAndNoNetwork() = runTest {
        val id = nodyssey_network_start("""{"url":"http://example.test/","method":"GET","headers":[],"settings":{"resolvers":[],"ipv6":false}}""")
        assertNotEquals(0uL, id)
        try {
            val result = withTimeout(10_000) {
                while (true) {
                    val pointer = nodyssey_network_take(id)
                    if (pointer != null) {
                        return@withTimeout try {
                            pointer.toKString()
                        } finally {
                            nodyssey_network_free(pointer)
                        }
                    }
                    // Real dispatcher: the Rust runtime is outside runTest's virtual clock.
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { delay(25) }
                }
                @Suppress("UNREACHABLE_CODE")
                error("unreachable")
            }
            assertTrue(result.contains("https_required"))
            assertTrue(result.contains("NotEstablished"))
            assertTrue(nodyssey_network_take(id) == null)
        } finally {
            nodyssey_network_cancel(id)
            nodyssey_network_cancel(id)
        }
    }
}
