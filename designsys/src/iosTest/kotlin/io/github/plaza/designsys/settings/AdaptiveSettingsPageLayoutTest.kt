@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.plaza.designsys.settings

import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.theme.ReadableWidthSpec
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIColor
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies how [NativeSettingsPageHost] reacts when a live settings page rotates or resizes
 * between wide centered columns and compact widths, without pinning any design dimension.
 */
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
    fun `same host relayouts bar and table across rotation and resize`() {
        val host = NativeSettingsPageHost()
        val initialMargins = host.table.directionalLayoutMargins.useContents { leading to trailing }
        val wideSpec =
            ReadableWidthSpec(
                maxWidth = 700.dp,
                landscapeMaxWidth = 900.dp,
                horizontalGutter = 16.dp,
                gutterMinWidth = 600.dp,
                windowLandscape = true,
            )

        // 1. Wide landscape: bar and table centre together inside a full-width scroll backdrop,
        //    and the inset-grouped table keeps non-zero symmetric margins so its cards stay rounded.
        val landscapeWidth = 1100.0
        host.update(samplePage, onBack = {}, palette = samplePalette, readableSpec = wideSpec)
        host.view.setFrame(CGRectMake(0.0, 0.0, landscapeWidth, 800.0))
        host.view.layoutIfNeeded()

        val landscape = host.measureHorizontal()
        assertCenteredColumn(landscape, containerWidth = landscapeWidth)
        assertFalse(host.table.cellLayoutMarginsFollowReadableWidth)
        assertFalse(host.table.preservesSuperviewLayoutMargins)
        val wideMargins = host.table.directionalLayoutMargins.useContents { leading to trailing }
        assertTrue(wideMargins.first > 0.0 && wideMargins.second > 0.0)
        assertEquals(wideMargins.first, wideMargins.second, 0.5)

        // 2. Rotate the same host to portrait: column narrows dynamically and stays centred.
        val portraitWidth = 820.0
        host.update(samplePage, onBack = {}, palette = samplePalette, readableSpec = wideSpec.copy(windowLandscape = false))
        host.view.setFrame(CGRectMake(0.0, 0.0, portraitWidth, 1100.0))
        host.view.layoutIfNeeded()

        val portrait = host.measureHorizontal()
        assertCenteredColumn(portrait, containerWidth = portraitWidth)
        assertTrue(portrait.tableWidth < landscape.tableWidth)
        assertFalse(host.table.cellLayoutMarginsFollowReadableWidth)

        // 3. Resize the same host to a compact window: bar and table return to full width and
        //    hand layout margins back to UIKit's default readable-width handling.
        val compactWidth = 420.0
        host.update(samplePage, onBack = {}, palette = samplePalette, readableSpec = ReadableWidthSpec.Default)
        host.view.setFrame(CGRectMake(0.0, 0.0, compactWidth, 800.0))
        host.view.layoutIfNeeded()

        val compact = host.measureHorizontal()
        assertFullWidth(compact, containerWidth = compactWidth)
        assertTrue(host.table.cellLayoutMarginsFollowReadableWidth)
        assertTrue(host.table.preservesSuperviewLayoutMargins)
        val restoredMargins = host.table.directionalLayoutMargins.useContents { leading to trailing }
        assertEquals(initialMargins.first, restoredMargins.first, 0.5)
        assertEquals(initialMargins.second, restoredMargins.second, 0.5)

        // 4. Widen the same host again: centred layout and rounded-card margins re-engage cleanly.
        host.update(samplePage, onBack = {}, palette = samplePalette, readableSpec = wideSpec)
        host.view.setFrame(CGRectMake(0.0, 0.0, landscapeWidth, 800.0))
        host.view.layoutIfNeeded()

        val reopened = host.measureHorizontal()
        assertCenteredColumn(reopened, containerWidth = landscapeWidth)
        assertEquals(landscape.tableWidth, reopened.tableWidth, 0.5)
        assertFalse(host.table.cellLayoutMarginsFollowReadableWidth)
        assertFalse(host.table.preservesSuperviewLayoutMargins)

        host.dispose()
    }

    private data class HorizontalLayout(
        val barX: Double,
        val barWidth: Double,
        val tableX: Double,
        val tableWidth: Double,
        val backdropX: Double,
        val backdropWidth: Double,
    )

    private fun NativeSettingsPageHost.measureHorizontal(): HorizontalLayout {
        val (barX, barWidth) = bar.frame.useContents { origin.x to size.width }
        val (tableX, tableWidth) = table.frame.useContents { origin.x to size.width }
        val (backdropX, backdropWidth) = barBackdrop.frame.useContents { origin.x to size.width }
        return HorizontalLayout(barX, barWidth, tableX, tableWidth, backdropX, backdropWidth)
    }

    private fun assertCenteredColumn(layout: HorizontalLayout, containerWidth: Double) {
        assertEquals(0.0, layout.backdropX, 0.5)
        assertEquals(containerWidth, layout.backdropWidth, 0.5)
        assertTrue(layout.barX > 0.0)
        assertTrue(layout.barWidth < containerWidth)
        assertEquals(layout.barX, layout.tableX, 0.5)
        assertEquals(layout.barWidth, layout.tableWidth, 0.5)
        val trailingGap = containerWidth - (layout.tableX + layout.tableWidth)
        assertTrue(abs(layout.tableX - trailingGap) <= 0.5)
    }

    private fun assertFullWidth(layout: HorizontalLayout, containerWidth: Double) {
        assertEquals(0.0, layout.backdropX, 0.5)
        assertEquals(containerWidth, layout.backdropWidth, 0.5)
        assertEquals(0.0, layout.barX, 0.5)
        assertEquals(containerWidth, layout.barWidth, 0.5)
        assertEquals(0.0, layout.tableX, 0.5)
        assertEquals(containerWidth, layout.tableWidth, 0.5)
    }
}
