@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.nodyssey

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.plaza.designsys.component.PlatformTopAppBar
import io.github.plaza.designsys.component.PlatformTopAppBarHost
import io.github.plaza.designsys.component.toUIKitBarButton
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAction
import platform.UIKit.UIButton
import platform.UIKit.UIButtonConfiguration
import platform.UIKit.UINavigationController
import platform.UIKit.UISearchBar
import platform.UIKit.UISearchBarDelegateProtocol
import platform.UIKit.UISearchController
import platform.UIKit.UISearchResultsUpdatingProtocol
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController
import platform.UIKit.navigationItem
import platform.darwin.NSObject

/** Owns the UIKit bar while its child owns the shared screen and its safe-area insets. */
internal class NativeChromeViewController(
    private val makeContent: (NativeChromeViewController) -> UIViewController,
) : UIViewController(nibName = null, bundle = null) {
    val barHost = PlatformTopAppBarHost(::updateBar)
    var usesNativeBar by mutableStateOf(true)
        private set
    private val content by lazy { makeContent(this) }
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
        addChildViewController(content)
        view.addSubview(content.view)
        content.didMoveToParentViewController(this)
        updateEligibility()
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

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        content.view.setFrame(view.bounds)
    }

    fun updateEligibility() {
        // Both iPad panes own a navigation controller, as does each phone tab.
        usesNativeBar = parentViewController is UINavigationController
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
