package io.github.nodyssey

import platform.UIKit.UINavigationController
import platform.UIKit.UIViewController
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

/** UIKit throws when a visible nested navigation controller has no root page. */
class PaneSplitControllerTest {
    @Test
    fun `a new split has a root before the outer navigation controller displays it`() {
        val placeholder = UIViewController(nibName = null, bundle = null)
        val split = PaneSplitController(placeholder)
        val outer = UINavigationController(rootViewController = UIViewController(nibName = null, bundle = null))

        outer.loadViewIfNeeded()
        outer.setViewControllers(listOf(split), animated = false)
        split.loadViewIfNeeded()

        assertSame(placeholder, split.details.viewControllers.single())
        assertSame(split, split.details.parentViewController)
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
        assertSame(placeholder, split.details.viewControllers.single())
        outer.setViewControllers(listOf(list), animated = false)
        assertSame(outer, list.parentViewController)
    }
}
