package io.github.plaza.designsys.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The roles a system alert gives its buttons: cancel leads, a destructive confirm is marked
 * destructive and never made the preferred answer, and a lone confirm is never preferred either —
 * with no alternative on screen there is nothing for bold to weigh against.
 */
class AlertButtonsTest {
    @Test
    fun `a destructive confirm is marked destructive and never preferred`() {
        val buttons = alertButtons("Delete", "Cancel", destructive = true)

        assertEquals(2, buttons.size)
        assertEquals(AlertButtonRole.Cancel, buttons[0].role)
        assertFalse(buttons[0].confirms)
        assertEquals(AlertButtonRole.Destructive, buttons[1].role)
        assertTrue(buttons[1].confirms)
        assertFalse(buttons[1].preferred)
    }

    @Test
    fun `a safe confirm beside a cancel is the preferred action`() {
        val buttons = alertButtons("Save", "Cancel", destructive = false)

        assertEquals(2, buttons.size)
        assertEquals(AlertButtonRole.Cancel, buttons[0].role)
        assertEquals(AlertButtonRole.Default, buttons[1].role)
        assertTrue(buttons[1].preferred)
    }

    @Test
    fun `a confirm on its own is not preferred`() {
        val buttons = alertButtons("OK", dismissLabel = null, destructive = false)

        assertEquals(1, buttons.size)
        assertTrue(buttons[0].confirms)
        assertFalse(buttons[0].preferred)
    }
}
