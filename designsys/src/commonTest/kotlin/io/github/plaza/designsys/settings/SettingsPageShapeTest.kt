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

    private fun single(item: SettingsItem, intro: String? = null) =
        SettingsPage(title = "设置", intro = intro, sections = listOf(SettingsSection(key = "appearance", items = listOf(item))))

    private fun themeChoice(presentation: SettingsChoicePresentation, selected: Int = 0) =
        SettingsItem.Choice(
            key = "theme-mode",
            title = "明暗",
            options = listOf(SettingsOption("跟随系统"), SettingsOption("浅色"), SettingsOption("深色")),
            selectedIndex = selected,
            onSelect = {},
            presentation = presentation,
        )

    @Test
    fun `picking another answer updates in place`() {
        assertEquals(
            single(themeChoice(SettingsChoicePresentation.Segmented, selected = 0)).shape(),
            single(themeChoice(SettingsChoicePresentation.Segmented, selected = 2)).shape(),
        )
    }

    @Test
    fun `segments and a menu are different cells, so switching between them reloads the table`() {
        assertNotEquals(
            single(themeChoice(SettingsChoicePresentation.Segmented)).shape(),
            single(themeChoice(SettingsChoicePresentation.Menu)).shape(),
        )
    }

    @Test
    fun `a custom row is laid out as its native item`() {
        // The system list draws the native half, so an Android-only drawing must not cost iOS a reload.
        val plain = SettingsItem.Toggle(key = "eink", title = "墨水屏模式", checked = false, onCheckedChange = {})
        assertEquals(single(plain).shape(), single(SettingsItem.Custom(native = plain) {}).shape())
    }

    @Test
    fun `an introduction appearing adds a group and reloads the table`() {
        val link = SettingsItem.Link(key = "licenses", title = "开源许可", onClick = {})
        assertNotEquals(single(link).shape(), single(link, intro = "本应用使用了以下开源软件。").shape())
    }

    @Test
    fun `a link that starts opening outside the app gets a different cell`() {
        val inside = SettingsItem.Link(key = "source", title = "源码", onClick = {})
        val outside = SettingsItem.Link(key = "source", title = "源码", onClick = {}, external = true)
        assertNotEquals(single(inside).shape(), single(outside).shape())
    }

    @Test
    fun `an info row that reads another value updates in place`() {
        val before = SettingsItem.Info(key = "attendance", title = "签到状态", value = "未签到")
        val after = SettingsItem.Info(key = "attendance", title = "签到状态", value = "已签到")
        assertEquals(single(before).shape(), single(after).shape())
    }

    @Test
    fun `an info row that gains a tap is a different cell`() {
        val info = SettingsItem.Info(key = "attendance", title = "签到状态", value = "已签到")
        val link = SettingsItem.Link(key = "attendance", title = "签到状态", value = "已签到", onClick = {})
        assertNotEquals(single(info).shape(), single(link).shape())
    }
}
