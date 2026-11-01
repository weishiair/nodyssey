package io.github.nodyssey.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class RoleBadgeLogicTest {

    @Test
    fun `truncation folds the tail into a count but never drops a punishment badge`() {
        val (shown, folded) = visibleRoleBadges(listOf("服主", "管理", "Dev", "骗子"))
        assertEquals(listOf("骗子", "服主", "管理"), shown)
        assertEquals(1, folded)
    }

    @Test
    fun `temporary suspension stays visible ahead of staff and unknown roles`() {
        val (shown, folded) = visibleRoleBadges(listOf("服主", "管理", "Dev", "临时禁言", "楼主"))

        assertEquals(listOf("楼主", "临时禁言", "服主"), shown)
        assertEquals(2, folded)
    }
}
