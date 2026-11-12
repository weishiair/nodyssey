@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.nodyssey

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.plaza.designsys.component.PlatformTopAppBar
import io.github.plaza.designsys.component.PlatformTopAppBarHost
import io.github.plaza.designsys.component.toUIKitBarButton
import platform.Foundation.NSOperationQueue
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIAction
import platform.UIKit.UIButton
import platform.UIKit.UIButtonConfiguration
import platform.UIKit.UIColor
import platform.UIKit.UINavigationController
import platform.UIKit.UISearchBar
import platform.UIKit.UISearchBarDelegateProtocol
import platform.UIKit.UISearchController
import platform.UIKit.UISearchResultsUpdatingProtocol
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController
import platform.UIKit.navigationItem
import platform.UIKit.systemBackgroundColor
import platform.darwin.NSObject

/** Owns the UIKit bar and starts the shared screen below it. */
internal class NativeChromeViewController(
    private val makeContent: (NativeChromeViewController) -> UIViewController,
) : UIViewController(nibName = null, bundle = null) {
    val barHost = PlatformTopAppBarHost(::updateBar)
    var usesNativeBar by mutableStateOf(true)
        private set
    private val content by lazy { makeContent(this) }
    private var contentSafeTop: NSLayoutConstraint? = null
    private var contentEdgeTop: NSLayoutConstraint? = null
    private var needsSearchFocus = false
    private val searchDelegate = object : NSObject(), UISearchResultsUpdatingProtocol, UISearchBarDelegateProtocol {
        override fun updateSearchResultsForSearchController(searchController: UISearchController) {
            val search = barHost.current?.search ?: return
            val text = searchController.searchBar.text.orEmpty()
            if (text != search.query) search.onQueryChange(text)
        }

        override fun searchBarSearchButtonClicked(searchBar: UISearchBar) {
            barHost.current?.search?.onSubmit?.invoke()
            searchBar.resignFirstResponder()
        }
    }
    private val searchController by lazy {
        UISearchController(searchResultsController = null).apply {
            obscuresBackgroundDuringPresentation = false
            hidesNavigationBarDuringPresentation = false
            automaticallyShowsCancelButton = false
            searchResultsUpdater = searchDelegate
            searchBar.delegate = searchDelegate
        }
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        definesPresentationContext = true
        view.backgroundColor = UIColor.systemBackgroundColor
        addChildViewController(content)
        content.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(content.view)
        contentSafeTop = content.view.topAnchor.constraintEqualToAnchor(view.safeAreaLayoutGuide.topAnchor)
        contentEdgeTop = content.view.topAnchor.constraintEqualToAnchor(view.topAnchor)
        NSLayoutConstraint.activateConstraints(
            listOf(
                content.view.leadingAnchor.constraintEqualToAnchor(view.leadingAnchor),
                content.view.trailingAnchor.constraintEqualToAnchor(view.trailingAnchor),
                content.view.bottomAnchor.constraintEqualToAnchor(view.bottomAnchor),
            ),
        )
        content.didMoveToParentViewController(this)
        updateEligibility()
        updateContentTop()
    }

    override fun viewWillAppear(animated: Boolean) {
        super.viewWillAppear(animated)
        updateEligibility()
        updateVisibility(animated)
    }

    override fun viewDidAppear(animated: Boolean) {
        super.viewDidAppear(animated)
        focusSearchIfNeeded()
    }

    fun updateEligibility() {
        // Both iPad panes own a navigation controller, as does each phone tab.
        usesNativeBar = parentViewController is UINavigationController
        updateContentTop()
    }

    private fun updateContentTop() {
        // Shared headers may use zero Scaffold insets because the header owns them. Once that
        // header is a UINavigationBar, constrain the child below UIKit's safe area instead.
        // Keep the full bottom edge: Compose still accounts for the tab bar and keyboard there.
        val nativeHeader = usesNativeBar && barHost.current != null
        if (nativeHeader) {
            contentEdgeTop?.active = false
            contentSafeTop?.active = true
        } else {
            contentSafeTop?.active = false
            contentEdgeTop?.active = true
        }
    }

    private fun updateBar(bar: PlatformTopAppBar?) {
        val hadSearch = navigationItem.searchController != null
        navigationItem.title = bar?.title
        navigationItem.titleView = bar?.titleAction?.let { openTitle ->
            UIButton.buttonWithConfiguration(
                UIButtonConfiguration.plainButtonConfiguration(),
                UIAction.actionWithTitle(bar.title, null, null) { openTitle() },
            )
        }
        navigationItem.prompt = bar?.subtitle
        // The screen owns back semantics, including when a split detail should have no button.
        navigationItem.setHidesBackButton(true, animated = false)
        navigationItem.leftBarButtonItem = bar?.navigation?.toUIKitBarButton()
        // UIKit lays the first right item at the trailing edge; keep the screen's reading order.
        navigationItem.rightBarButtonItems = bar?.actions?.asReversed()?.map { it.toUIKitBarButton() }
        navigationItem.searchController = if (bar?.search != null) searchController else null
        bar?.search?.let { search ->
            if (searchController.searchBar.text.orEmpty() != search.query) searchController.searchBar.text = search.query
            searchController.searchBar.placeholder = search.placeholder
            navigationItem.hidesSearchBarWhenScrolling = false
            if (!hadSearch) needsSearchFocus = search.autoFocus
        }
        updateVisibility(animated = false)
        updateContentTop()
        NSOperationQueue.mainQueue.addOperationWithBlock { focusSearchIfNeeded() }
    }

    private fun focusSearchIfNeeded() {
        if (!needsSearchFocus || barHost.current?.search == null || view.window == null) return
        needsSearchFocus = false
        searchController.searchBar.becomeFirstResponder()
    }

    private fun updateVisibility(animated: Boolean) {
        val navigation = parentViewController as? UINavigationController ?: return
        if (navigation.topViewController !== this) return
        navigation.updateChromeFor(this, animated)
    }
}

internal fun UINavigationController.updateChromeFor(controller: UIViewController, animated: Boolean) {
    val chrome = controller as? NativeChromeViewController
    chrome?.updateEligibility()
    setNavigationBarHidden(chrome == null || !chrome.usesNativeBar || chrome.barHost.current == null, animated)
}
