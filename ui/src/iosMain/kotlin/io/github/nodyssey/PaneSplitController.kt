package io.github.nodyssey

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIColor
import platform.UIKit.UINavigationController
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController
import platform.UIKit.removeFromParentViewController
import platform.UIKit.separatorColor
import platform.UIKit.willMoveToParentViewController

/**
 * A list and its details side by side: the iPad's answer to `ListDetailSceneStrategy`.
 *
 * The list is one page, held as a child; the details are a [UINavigationController] of their own, so
 * opening one thread from another pushes it in the right half and a swipe there pops it, the way
 * Mail and Notes behave. The shell owns both — this only lays them out.
 *
 * A fixed-width list rather than a share of the window: it is a column of titles, and past the
 * width of a phone it gains nothing but white space, which the thread beside it could use instead.
 */
@OptIn(ExperimentalForeignApi::class)
internal class PaneSplitController(
    private val placeholder: UIViewController,
) : UIViewController(nibName = null, bundle = null) {
    // Keep a valid placeholder root while pages move between navigation controllers.
    val details = UINavigationController(rootViewController = placeholder).apply {
        setNavigationBarHidden(true, animated = false)
    }

    private val divider = UIView()
    private var list: UIViewController? = null

    override fun viewDidLoad() {
        super.viewDidLoad()
        adopt(details)
        divider.backgroundColor = UIColor.separatorColor
        view.addSubview(divider)
    }

    /** Puts [controller] in the left half, or empties it so the page can be shown somewhere else. */
    fun showList(controller: UIViewController?) {
        if (list == controller) return
        list?.let {
            it.willMoveToParentViewController(null)
            it.view.removeFromSuperview()
            it.removeFromParentViewController()
        }
        list = controller
        if (controller != null) adopt(controller)
        view.setNeedsLayout()
    }

    /** Releases pages before this pair leaves the outer navigation controller. */
    fun releasePages() {
        // This pair is discarded. Suppress delegate write-back while its pages are detached.
        val released =
            details.viewControllers
                .filterIsInstance<UIViewController>()
                .filter { it != placeholder }
        details.delegate = null
        details.setViewControllers(listOf(placeholder), animated = false)
        released.forEach { it.view.removeFromSuperview() }
        showList(null)
    }

    private fun adopt(child: UIViewController) {
        // A visible navigation stack clears parent before removing its outgoing view.
        // UIKit also checks that view hierarchy when adopting a child, so detach the view
        // first even when parentViewController is already null.
        child.view.removeFromSuperview()
        addChildViewController(child)
        view.addSubview(child.view)
        child.didMoveToParentViewController(this)
    }

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        val scale = view.traitCollection.displayScale.takeIf { it > 0.0 } ?: 2.0
        val hairline = 1.0 / scale
        view.bounds.useContents {
            val height = size.height
            list?.view?.setFrame(CGRectMake(0.0, 0.0, LIST_PANE_WIDTH, height))
            divider.setFrame(CGRectMake(LIST_PANE_WIDTH, 0.0, hairline, height))
            details.view.setFrame(CGRectMake(LIST_PANE_WIDTH + hairline, 0.0, size.width - LIST_PANE_WIDTH - hairline, height))
        }
    }

    // UISplitViewController cannot be pushed onto a UINavigationController (Apple's documented
    // restriction). These pairs are entries in the tab's existing navigation stack, so use public
    // child-controller containment here. Replace this container if the shell moves to a split root.
    // Keep UIKit's safe-area insets; do not inspect its floating tab bar's internal view hierarchy.
}

/** Material's default list pane width. */
private const val LIST_PANE_WIDTH = 360.0
