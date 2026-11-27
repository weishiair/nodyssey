@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.plaza.designsys.settings

import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.theme.ReadableWidthSpec
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdaptiveSettingsPageLayoutTest {
    private val samplePage =
        SettingsPage(
            title = "账号设置",
            sections =
            listOf(
                SettingsSection(
                    key = "account",
                    items = listOf(SettingsItem.Link(key = "profile", title = "个人信息", onClick = {})),
                ),
            ),
        )

    private val samplePalette =
        NativeSettingsPalette(
            page = UIColor.blackColor,
            card = UIColor.darkGrayColor,
            accent = UIColor.blueColor,
            dark = true,
            scale = 1.0,
            largeText = false,
        )

    @Test
    fun `ipad landscape full page aligns bar and table to 960pt column`() {
        val host = NativeSettingsPageHost()
        val spec =
            ReadableWidthSpec(
                maxWidth = 720.dp,
                landscapeMaxWidth = 960.dp,
                horizontalGutter = 16.dp,
                gutterMinWidth = 600.dp,
                windowLandscape = true,
            )
        host.update(samplePage, onBack = {}, palette = samplePalette, readableSpec = spec)
        host.view.setFrame(CGRectMake(0.0, 0.0, 1180.0, 800.0))
        host.view.layoutIfNeeded()

        val (barX, barW) = host.bar.frame.useContents { origin.x to size.width }
        val (tableX, tableW) = host.table.frame.useContents { origin.x to size.width }
        val (backdropX, backdropW) = host.barBackdrop.frame.useContents { origin.x to size.width }
        assertEquals(110.0, barX, 0.5)
        assertEquals(960.0, barW, 0.5)
        assertEquals(110.0, tableX, 0.5)
        assertEquals(960.0, tableW, 0.5)
        assertEquals(0.0, backdropX, 0.5)
        assertEquals(1180.0, backdropW, 0.5)
        assertFalse(host.table.cellLayoutMarginsFollowReadableWidth)
        assertFalse(host.table.preservesSuperviewLayoutMargins)
        val (marginLead, marginTrail) = host.table.directionalLayoutMargins.useContents { leading to trailing }
        assertEquals(16.0, marginLead, 0.5)
        assertEquals(16.0, marginTrail, 0.5)
        host.dispose()
    }

    @Test
    fun `ipad portrait full page narrows bar and table to 720pt column`() {
        val host = NativeSettingsPageHost()
        val spec =
            ReadableWidthSpec(
                maxWidth = 720.dp,
                landscapeMaxWidth = 960.dp,
                horizontalGutter = 16.dp,
                gutterMinWidth = 600.dp,
                windowLandscape = false,
            )
        host.update(samplePage, onBack = {}, palette = samplePalette, readableSpec = spec)
        host.view.setFrame(CGRectMake(0.0, 0.0, 820.0, 1160.0))
        host.view.layoutIfNeeded()

        val (barX, barW) = host.bar.frame.useContents { origin.x to size.width }
        val (tableX, tableW) = host.table.frame.useContents { origin.x to size.width }
        assertEquals(50.0, barX, 0.5)
        assertEquals(720.0, barW, 0.5)
        assertEquals(50.0, tableX, 0.5)
        assertEquals(720.0, tableW, 0.5)
        assertFalse(host.table.cellLayoutMarginsFollowReadableWidth)
        host.dispose()
    }

    @Test
    fun `compact phone keeps full width bar and system readable width table`() {
        val host = NativeSettingsPageHost()
        host.update(samplePage, onBack = {}, palette = samplePalette, readableSpec = ReadableWidthSpec.Default)
        host.view.setFrame(CGRectMake(0.0, 0.0, 393.0, 800.0))
        host.view.layoutIfNeeded()

        val (barX, barW) = host.bar.frame.useContents { origin.x to size.width }
        val (tableX, tableW) = host.table.frame.useContents { origin.x to size.width }
        assertEquals(0.0, barX, 0.5)
        assertEquals(393.0, barW, 0.5)
        assertEquals(0.0, tableX, 0.5)
        assertEquals(393.0, tableW, 0.5)
        assertTrue(host.table.cellLayoutMarginsFollowReadableWidth)
        host.dispose()
    }
}
