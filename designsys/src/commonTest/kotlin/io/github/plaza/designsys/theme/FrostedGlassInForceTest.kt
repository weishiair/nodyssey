package io.github.plaza.designsys.theme

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The 毛玻璃质感 rule every platform resolves the same way: where the system draws Liquid Glass the
 * reader's stored choice stops mattering, and 墨水屏 still beats everything.
 */
class FrostedGlassInForceTest {
    @Test
    fun `system controls turn it on regardless of the stored choice`() {
        assertEquals(true, frostedGlassInForce(setting = false, einkMode = false, systemControls = true))
        assertEquals(true, frostedGlassInForce(setting = true, einkMode = false, systemControls = true))
    }

    @Test
    fun `elsewhere it is the stored choice and eink always wins`() {
        assertEquals(false, frostedGlassInForce(setting = true, einkMode = true, systemControls = true))
        assertEquals(false, frostedGlassInForce(setting = false, einkMode = false, systemControls = false))
        assertEquals(true, frostedGlassInForce(setting = true, einkMode = false, systemControls = false))
    }
}
