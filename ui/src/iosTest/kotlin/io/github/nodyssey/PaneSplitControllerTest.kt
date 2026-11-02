package io.github.nodyssey

import platform.UIKit.UINavigationController
import platform.UIKit.UIViewController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Checks the UIKit containment and page transfers used by the iPad shell. */
class PaneSplitControllerTest {
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
}
