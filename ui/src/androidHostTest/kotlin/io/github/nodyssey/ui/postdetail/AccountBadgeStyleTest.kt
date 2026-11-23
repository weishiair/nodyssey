package io.github.nodyssey.ui.postdetail

import io.github.nodyssey.data.settings.AccountBadgeStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountBadgeStyleTest {
    @Test
    fun `an unchosen style keeps what the glass switch gave before the update`() {
        // Android, or iOS 16–25: the reader's own switch decided.
        assertEquals(AccountBadgeStyle.VIVID, inForce(stored = null, frostedGlass = false, systemGlass = false))
        assertEquals(AccountBadgeStyle.NATURAL, inForce(stored = null, frostedGlass = true, systemGlass = false))
        // iOS 26+: the glass was forced on, whatever the hidden switch held.
        assertEquals(AccountBadgeStyle.NATURAL, inForce(stored = null, frostedGlass = false, systemGlass = true))
        // 墨水屏 never had the glass.
        assertEquals(AccountBadgeStyle.VIVID, inForce(stored = null, frostedGlass = true, systemGlass = true, einkMode = true))
    }

    @Test
    fun `a chosen style wins over the glass`() {
        assertEquals(AccountBadgeStyle.VIVID, inForce(AccountBadgeStyle.VIVID, frostedGlass = true, systemGlass = true))
        assertEquals(AccountBadgeStyle.NATURAL, inForce(AccountBadgeStyle.NATURAL, frostedGlass = false, systemGlass = false))
    }

    private fun inForce(
        stored: AccountBadgeStyle?,
        frostedGlass: Boolean,
        systemGlass: Boolean,
        einkMode: Boolean = false,
    ) = accountBadgeStyleInForce(stored, frostedGlass, einkMode, systemGlass)
}
