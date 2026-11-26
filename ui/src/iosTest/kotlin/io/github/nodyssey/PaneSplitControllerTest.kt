package io.github.nodyssey

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UINavigationController
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController
import platform.UIKit.removeFromParentViewController
import platform.UIKit.willMoveToParentViewController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Checks the UIKit containment and page transfers used by the iPad shell. */
class PaneSplitControllerTest {
    @Test
    fun `a page with a cleared parent but an outgoing navigation view can enter the split`() {
        val list = UIViewController(nibName = null, bundle = null)
        val previous = UIViewController(nibName = null, bundle = null)
        previous.addChildViewController(list)
        previous.view.addSubview(list.view)
        list.didMoveToParentViewController(previous)
        // A visible non-animated navigation replacement clears parent before its outgoing view.
        list.willMoveToParentViewController(null)
        list.removeFromParentViewController()
        assertNull(list.parentViewController)
        assertEquals(previous.view, list.view.superview)
        val split = PaneSplitController(UIViewController(nibName = null, bundle = null))

        split.showList(list)

        assertEquals(split, list.parentViewController)
        assertEquals(split.view, list.view.superview)
    }

    @Test
    fun `a new split has a root before the outer navigation controller displays it`() {
        val placeholder = UIViewController(nibName = null, bundle = null)
        val split = PaneSplitController(placeholder)
        val outer = UINavigationController(rootViewController = UIViewController(nibName = null, bundle = null))

        outer.loadViewIfNeeded()
        outer.setViewControllers(listOf(split), animated = false)
        split.loadViewIfNeeded()

        assertEquals(placeholder, split.details.viewControllers.single())
        assertEquals(split, split.details.parentViewController)
    }

    @Test
    fun `releasing a split frees its pages without emptying the nested navigation stack`() {
        val placeholder = UIViewController(nibName = null, bundle = null)
        val list = UIViewController(nibName = null, bundle = null)
        val detail = UIViewController(nibName = null, bundle = null)
        val split = PaneSplitController(placeholder)
        val outer = UINavigationController(rootViewController = split)
        outer.loadViewIfNeeded()
        split.showList(list)
        split.details.setViewControllers(listOf(detail), animated = false)

        split.releasePages()

        assertNull(list.parentViewController)
        assertNull(detail.parentViewController)
        assertEquals(placeholder, split.details.viewControllers.single())
        outer.setViewControllers(listOf(list), animated = false)
        assertEquals(outer, list.parentViewController)
    }

    @Test
    fun `pages can move between a navigation stack and the split and back`() {
        val list = UIViewController(nibName = null, bundle = null)
        val detail = UIViewController(nibName = null, bundle = null)
        val placeholder = UIViewController(nibName = null, bundle = null)
        val outer = UINavigationController(rootViewController = list)
        outer.loadViewIfNeeded()
        outer.setViewControllers(listOf(list, detail), animated = false)

        val split = PaneSplitController(placeholder)
        outer.setViewControllers(listOf(split), animated = false)
        split.showList(list)
        split.details.setViewControllers(listOf(detail), animated = false)
        assertEquals(split, list.parentViewController)
        assertEquals(split.details, detail.parentViewController)

        split.releasePages()
        outer.setViewControllers(listOf(list, detail), animated = false)
        assertEquals(outer, list.parentViewController)
        assertEquals(outer, detail.parentViewController)
    }

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun `squeezing keeps the list view at full width and translates details without relayout`() {
        val list = UIViewController(nibName = null, bundle = null)
        val placeholder = UIViewController(nibName = null, bundle = null)
        val split = PaneSplitController(placeholder)
        split.view.setFrame(CGRectMake(0.0, 0.0, 1194.0, 834.0))
        split.showList(list)
        split.beginSqueeze(offset = split.fullyOpenOffset)
        split.view.layoutIfNeeded()

        assertTrue(split.isSqueezing)
        assertEquals(1194.0, list.view.frame.useContents { size.width })
        assertEquals(834.0, split.details.view.transform.useContents { tx })
        // List stays below details so the opaque details pane covers its unused trailing area.
        val subviews = split.view.subviews
        assertTrue(subviews.indexOf(list.view) < subviews.indexOf(split.details.view))

        split.updateSqueezeOffset(240.0)
        assertEquals(1194.0, list.view.frame.useContents { size.width })
        assertEquals(240.0, split.details.view.transform.useContents { tx })

        split.endSqueeze()
        assertFalse(split.isSqueezing)
        assertEquals(0.0, split.details.view.transform.useContents { tx })
        assertEquals(LIST_PANE_WIDTH, list.view.frame.useContents { size.width })
    }
}
