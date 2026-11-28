package io.github.nodyssey.ui.composer

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UILayoutGuide
import platform.UIKit.UIView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class ReplySheetKeyboardPaddingTest {
    @Test
    fun `the editor gets its space back when the keyboard guide returns to the bottom`() {
        val sheet = UIView(frame = CGRectMake(0.0, 0.0, 600.0, 300.0))
        val guide = UILayoutGuide()
        sheet.addLayoutGuide(guide)
        val keyboardHeight = guide.heightAnchor.constraintEqualToConstant(200.0)
        NSLayoutConstraint.activateConstraints(
            listOf(
                guide.leadingAnchor.constraintEqualToAnchor(sheet.leadingAnchor),
                guide.trailingAnchor.constraintEqualToAnchor(sheet.trailingAnchor),
                guide.bottomAnchor.constraintEqualToAnchor(sheet.bottomAnchor),
                keyboardHeight,
            ),
        )

        var bottomInset: Double? = null
        val stopObserving = observeReplyKeyboardInset(sheet, guide) { bottomInset = it }
        try {
            sheet.layoutIfNeeded()
            assertEquals(200.0, bottomInset)

            keyboardHeight.constant = 0.0
            sheet.setNeedsLayout()
            sheet.layoutIfNeeded()
            assertEquals(
                0.0,
                bottomInset,
                "The guide returned a height of ${guide.layoutFrame.useContents { size.height }}",
            )
        } finally {
            stopObserving()
        }
        assertTrue(sheet.subviews.isEmpty())
    }
}
