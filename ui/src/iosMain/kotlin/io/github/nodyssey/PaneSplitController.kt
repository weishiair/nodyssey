package io.github.nodyssey

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGAffineTransformMakeTranslation
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

    /**
     * Whether a root-pane squeeze is currently playing. While active, [list]'s UIView stays at
     * full container width so Skiko/Metal does not reallocate its CAMetalLayer drawable every
     * frame; Compose narrows the list column in its layout pass while [details] and [divider]
     * translate via GPU transforms.
     */
    var isSqueezing = false
        private set

    /**
     * How far the details pane sits right of its resting position, in points.
     */
    var detailsOffset = 0.0
        private set

    /** The offset at which the details pane is just off the right edge and the list spans the window. */
    val fullyOpenOffset: Double
        get() = view.bounds.useContents { (size.width - LIST_PANE_WIDTH).coerceAtLeast(0.0) }

    /**
     * Enters squeeze mode with [details] and [divider] shifted right by [offset] points.
     * Pass [layoutNow] = true when expanding an already-mounted split before a closing squeeze
     * so the one-time full-width frame update happens behind the opaque details pane.
     */
    fun beginSqueeze(
        offset: Double,
        layoutNow: Boolean = false,
    ) {
        val modeChanged = !isSqueezing
        isSqueezing = true
        detailsOffset = offset
        if (modeChanged) {
            view.setNeedsLayout()
            if (layoutNow) view.layoutIfNeeded()
        }
        applyDetailsShift(offset)
    }

    /** Updates the details translation during an active squeeze without a UIKit layout pass. */
    fun updateSqueezeOffset(offset: Double) {
        if (detailsOffset == offset) return
        detailsOffset = offset
        applyDetailsShift(offset)
    }

    /** Leaves squeeze mode and restores [list] to its resting [LIST_PANE_WIDTH] frame. */
    fun endSqueeze() {
        val changed = isSqueezing || detailsOffset != 0.0
        isSqueezing = false
        detailsOffset = 0.0
        applyDetailsShift(0.0)
        if (changed) {
            view.setNeedsLayout()
            view.layoutIfNeeded()
        }
    }

    private fun applyDetailsShift(offset: Double) {
        val shift = CGAffineTransformMakeTranslation(offset, 0.0)
        divider.transform = shift
        details.view.transform = shift
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        view.clipsToBounds = true
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
        if (controller != null) adopt(controller, belowDetails = true)
        view.setNeedsLayout()
    }

    /** Releases pages before this pair leaves the outer navigation controller. */
    fun releasePages() {
        // This pair is discarded. Suppress delegate write-back while its pages are detached.
        val released =
            details.viewControllers
                .filterIsInstance<UIViewController>()
                .filter { it != placeholder }
        isSqueezing = false
        detailsOffset = 0.0
        applyDetailsShift(0.0)
        details.delegate = null
        details.setViewControllers(listOf(placeholder), animated = false)
        released.forEach { it.view.removeFromSuperview() }
        showList(null)
    }

    private fun adopt(
        child: UIViewController,
        belowDetails: Boolean = false,
    ) {
        // A visible navigation stack clears parent before removing its outgoing view.
        // UIKit also checks that view hierarchy when adopting a child, so detach the view
        // first even when parentViewController is already null.
        child.view.removeFromSuperview()
        addChildViewController(child)
        if (belowDetails && details.view.superview == view) {
            view.insertSubview(child.view, belowSubview = details.view)
        } else {
            view.addSubview(child.view)
        }
        child.didMoveToParentViewController(this)
    }

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        val scale = view.traitCollection.displayScale.takeIf { it > 0.0 } ?: 2.0
        val hairline = 1.0 / scale
        val shiftOffset = if (isSqueezing) detailsOffset else 0.0
        // Reset transforms before assigning frames; UIKit's frame is undefined under non-identity transforms.
        applyDetailsShift(0.0)
        view.bounds.useContents {
            val height = size.height
            val listWidth = if (isSqueezing) size.width else LIST_PANE_WIDTH
            list?.view?.setFrame(CGRectMake(0.0, 0.0, listWidth, height))
            divider.setFrame(CGRectMake(LIST_PANE_WIDTH, 0.0, hairline, height))
            details.view.setFrame(
                CGRectMake(
                    LIST_PANE_WIDTH + hairline,
                    0.0,
                    (size.width - LIST_PANE_WIDTH - hairline).coerceAtLeast(0.0),
                    height,
                ),
            )
        }
        if (shiftOffset != 0.0) applyDetailsShift(shiftOffset)
    }

    // UISplitViewController cannot be pushed onto a UINavigationController (Apple's documented
    // restriction). These pairs are entries in the tab's existing navigation stack, so use public
    // child-controller containment here. Replace this container if the shell moves to a split root.
    // Keep UIKit's safe-area insets; do not inspect its floating tab bar's internal view hierarchy.
}

/** Material's default list pane width. */
internal const val LIST_PANE_WIDTH = 360.0
