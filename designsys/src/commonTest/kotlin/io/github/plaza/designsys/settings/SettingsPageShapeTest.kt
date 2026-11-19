package io.github.plaza.designsys.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * [shape] decides whether the iOS table reloads or updates its rows in place. Reloading on every
 * update cuts a switch's animation short and closes an open menu; *not* reloading when the rows
 * changed hands UITableView a row count it did not expect, which it answers by throwing.
 */
class SettingsPageShapeTest {
    private fun page(
        wifiOnly: Boolean = false,
        keywordCount: String = "无",
        enabled: Boolean = true,
        withQuietHours: Boolean = false,
        header: String = "检查",
    ) = SettingsPage(
        title = "通知提醒",
        sections =
        listOf(
            SettingsSection(
                key = "check",
                header = header,
                items =
                listOfNotNull(
                    SettingsItem.Toggle(key = "wifi-only", title = "仅 Wi-Fi", checked = wifiOnly, onCheckedChange = {}, enabled = enabled),
                    SettingsItem.Toggle(key = "quiet-hours", title = "夜间免打扰", checked = false, onCheckedChange = {})
                        .takeIf { withQuietHours },
                    SettingsItem.Link(key = "keywords", title = "提醒关键词", value = keywordCount, onClick = {}),
                ),
            ),
        ),
    )

    @Test
    fun `flipping a switch, disabling a row or changing a count updates in place`() {
        assertEquals(page().shape(), page(wifiOnly = true, keywordCount = "3 个", enabled = false).shape())
    }

    @Test
    fun `a row appearing reloads the table`() {
        assertNotEquals(page().shape(), page(withQuietHours = true).shape())
    }

    @Test
    fun `new header text reloads the table, which is the only way UIKit redraws one`() {
        assertNotEquals(page().shape(), page(header = "Check").shape())
    }

    @Test
    fun `a row that changes kind under the same key reloads the table`() {
        val asLink =
            SettingsPage(
                title = "通知提醒",
                sections =
                listOf(
                    SettingsSection(
                        key = "check",
                        header = "检查",
                        items =
                        listOf(
                            SettingsItem.Link(key = "wifi-only", title = "仅 Wi-Fi", onClick = {}),
                            SettingsItem.Link(key = "keywords", title = "提醒关键词", value = "无", onClick = {}),
                        ),
                    ),
                ),
            )

        assertNotEquals(page().shape(), asLink.shape())
    }
}
