package io.github.nodyssey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.settings.ThemeMode
import io.github.nodyssey.data.settings.UserSettings
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.assets.PendingAttendance
import io.github.nodyssey.ui.common.LocalOpenNetworkCheck
import io.github.nodyssey.ui.compare.LocalOpenReportCompare
import io.github.nodyssey.ui.composer.PendingComposerShare
import io.github.nodyssey.ui.navigation.PaneGroup
import io.github.nodyssey.ui.navigation.PaneKind
import io.github.nodyssey.ui.navigation.StackMirror
import io.github.nodyssey.ui.navigation.TopLevelDestination
import io.github.nodyssey.ui.navigation.WindowLayoutDispatcher
import io.github.nodyssey.ui.navigation.paneGroups
import io.github.nodyssey.ui.notifications.NotificationsViewModel
import io.github.nodyssey.ui.onboarding.OnboardingScreen
import io.github.nodyssey.ui.postlist.HomeFeedStates
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.about_privacy
import io.github.nodyssey.ui.resources.about_rss
import io.github.nodyssey.ui.resources.about_site
import io.github.nodyssey.ui.resources.app_name
import io.github.nodyssey.ui.settings.rememberAppLinkHandlingEnabled
import io.github.nodyssey.ui.settings.rememberAppLinkSettingsLauncher
import io.github.plaza.core.runCatchingExceptCancellation
import io.github.plaza.designsys.theme.LocalOneHandMode
import io.github.plaza.designsys.theme.LocalOneHandModeAvailable
import io.github.plaza.designsys.theme.LocalPlazaLayers
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.compose.resources.getString
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIDevice
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UIGestureRecognizerDelegateProtocol
import platform.UIKit.UIImage
import platform.UIKit.UIModalPresentationFullScreen
import platform.UIKit.UINavigationController
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UITabBarController
import platform.UIKit.UITabBarControllerDelegateProtocol
import platform.UIKit.UITabBarItem
import platform.UIKit.UIUserInterfaceIdiomPad
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController
import platform.UIKit.hidesBottomBarWhenPushed
import platform.UIKit.modalInPresentation
import platform.UIKit.removeFromParentViewController
import platform.UIKit.tabBarItem
import platform.UIKit.transitionCoordinator
import platform.UIKit.willMoveToParentViewController
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * The iOS app: a system tab bar and a system navigation stack per tab, with one Compose screen in
 * each pushed controller.
 *
 * What this buys over the single `ComposeUIViewController` the app used to be is everything iOS
 * draws for a native app and Compose can only imitate — the Liquid Glass tab bar, the push and pop
 * transitions, the edge swipe and iOS 26's swipe-anywhere back, all of it behaving the way every
 * other app on the phone does.
 *
 * **The back stacks are still the truth.** Every screen navigates the way it does on Android, with
 * `backStack.add` and `removeLastOrNull` on the [NavBackStack] its entry was built against — no entry
 * file knows this shell exists. A [StackMirror] per tab turns each change into the controllers its
 * `UINavigationController` should hold, and the one move UIKit makes on its own, popping, is written
 * back from the navigation delegate.
 *
 * What the Android root has and this does not:
 * - **Exit through 首页.** A system tab bar does not pop into another tab, and nobody on iOS expects
 *   it to.
 * - **The shared-element flight from a feed row into a thread.** It needs both ends in one
 *   composition, and each screen here is its own. `LocalThreadTransition` is simply never provided,
 *   which the screens already handle — it is what a two-pane window and 墨水屏模式 look like too.
 * - **Hide-on-scroll for the tab bar.** The bar is glass over the content rather than a slab under
 *   it, which is the reason to hide one.
 */
suspend fun nativeShellViewController(container: AppContainer): UIViewController {
    // Read before anything is drawn, for the reason `NodysseyRoot` takes `initialSettings`: the first
    // frame of every screen would otherwise go out in the factory colours.
    val settings = container.settingsRepository.settings.first()
    // Resolved once rather than per composition. Entry lambdas are not composable, and on this
    // platform a change of 语言 takes effect at the next launch anyway — see `AppLanguageControl`.
    val strings =
        ShellStrings(
            siteTitle = container.appVersion.label.ifBlank { getString(Res.string.app_name) },
            aboutSiteTitle = getString(Res.string.about_site),
            privacyTitle = getString(Res.string.about_privacy),
            rssLabel = getString(Res.string.about_rss),
            tabTitles = TopLevelDestination.entries.associateWith { getString(it.label) },
        )
    container.accountRepository?.awaitReady()
    return AccountNativeHost(container, settings, strings).root
}

/** Keeps one UIKit root while replacing every account-owned controller and ViewModel beneath it. */
@OptIn(ExperimentalForeignApi::class)
private class AccountNativeHost(
    private val container: AppContainer,
    private val settings: UserSettings,
    private val strings: ShellStrings,
) {
    val root = UIViewController()
    private val scope = MainScope()
    private var shell: NativeShell? = null
    private var child: UIViewController? = null
    private var identity: Any? = null
    private var detach: (() -> Unit)? = null

    init {
        val accounts = container.accountRepository
        if (accounts == null) {
            val next = NativeShell(container, settings, strings)
            shell = next
            replace(next.root)
        } else {
            scope.launch {
                accounts.state.collect { state ->
                    if (!state.ready || state.busy) {
                        if (shell != null || child == null) {
                            replace(ComposeUIViewController { NodysseySiteSwitchPlaceholder(container, settings) })
                            shell?.awaitTransitions()
                            shell?.close()
                            shell = null
                            detach?.invoke()
                            detach = null
                            identity = null
                        }
                    } else {
                        val nextIdentity = Triple(state.navigationKey, state.adding, state.revision)
                        if (identity != nextIdentity) {
                            shell?.awaitTransitions()
                            val latest = accounts.state.value
                            if (latest.busy || Triple(latest.navigationKey, latest.adding, latest.revision) != nextIdentity) return@collect
                            shell?.close()
                            detach?.invoke()
                            val next = NativeShell(container, settings, strings)
                            shell = next
                            identity = nextIdentity
                            replace(next.root)
                            detach = accounts.attachUi(next::close)
                        }
                    }
                }
            }
        }
    }

    private fun replace(next: UIViewController) {
        child?.let { previous ->
            previous.willMoveToParentViewController(null)
            previous.view.removeFromSuperview()
            previous.removeFromParentViewController()
        }
        root.addChildViewController(next)
        next.view.setFrame(root.view.bounds)
        next.view.autoresizingMask = platform.UIKit.UIViewAutoresizingFlexibleWidth or platform.UIKit.UIViewAutoresizingFlexibleHeight
        root.view.addSubview(next.view)
        next.didMoveToParentViewController(root)
        child = next
    }
}

private class ShellStrings(
    val siteTitle: String,
    val aboutSiteTitle: String,
    val privacyTitle: String,
    val rssLabel: String,
    val tabTitles: Map<TopLevelDestination, String>,
)

private class NativeShell(
    private val container: AppContainer,
    initialSettings: UserSettings,
    strings: ShellStrings,
) {
    private val scope = MainScope()

    /** The newest settings, so a screen pushed later starts in today's colours rather than launch's. */
    private var settings: UserSettings = initialSettings

    /**
     * What `MainNavigation` keeps in its own composition, kept here because no composition here
     * outlives the screens: the notifications view model behind both the tab badge and the 通知
     * screen, and 首页's list positions.
     */
    private val shellStore = ViewModelStore()
    private val notificationsViewModel: NotificationsViewModel =
        ViewModelProvider.create(shellStore, NotificationsViewModel.factory(container))[NotificationsViewModel::class]
    private val homeFeedStates = HomeFeedStates()
    private val pendingComposerShare = PendingComposerShare()
    private val pendingAttendance = PendingAttendance()
    private var homeReselectRequests by mutableIntStateOf(0)
    private var notificationsScrollToTopRequests by mutableIntStateOf(0)

    private val stacks: Map<TopLevelDestination, NavBackStack<NavKey>> =
        TopLevelDestination.entries.associateWith { destination ->
            NavBackStack<NavKey>(destination.key).apply {
                val accounts = container.accountRepository?.state?.value
                if (destination == TopLevelDestination.PROFILE) {
                    if (accounts?.adding == true || (accounts?.revision ?: 0) > 0) add(AccountsKey)
                    if (accounts?.adding == true) add(SignInKey)
                }
            }
        }

    private var listDetailExpanded by mutableStateOf(false)
    val root: UITabBarController = ShellTabBarController(onWidth = ::onWindowWidth)

    private val navigationControllers: Map<TopLevelDestination, UINavigationController> =
        TopLevelDestination.entries.associateWith { destination ->
            UINavigationController().apply {
                // Every screen still draws its own app bar, back arrow included; the system one
                // would be a second bar above it.
                setNavigationBarHidden(true, animated = false)
                tabBarItem =
                    UITabBarItem(
                        title = strings.tabTitles.getValue(destination),
                        image = UIImage.systemImageNamed(destination.symbol),
                        selectedImage = UIImage.systemImageNamed("${destination.symbol}.fill"),
                    )
            }
        }

    private val dependencies =
        NavigationDependencies(
            container = container,
            siteTitle = strings.siteTitle,
            aboutSiteTitle = strings.aboutSiteTitle,
            privacyTitle = strings.privacyTitle,
            rssLabel = strings.rssLabel,
            uriHandler = SafariUriHandler,
            // The same gate `MainNavigation` puts in front of the browser.
            openExternalUrl = { url ->
                if (NodeSeekSite.isExternalWebUrl(url) || NodeSeekSite.isMailUrl(url)) {
                    runCatching { SafariUriHandler.openUri(url) }
                }
            },
            notificationsViewModel = notificationsViewModel,
            homeFeedStates = homeFeedStates,
            homeReselectRequests = { homeReselectRequests },
            notificationsScrollToTopRequests = { notificationsScrollToTopRequests },
            isListDetailExpanded = { listDetailExpanded },
            onTabBarHiddenByScroll = {},
            selectTab = { root.selectedIndex = it.ordinal.toULong() },
            scope = scope,
            pendingComposerShare = pendingComposerShare,
            pendingAttendance = pendingAttendance,
        )

    private val providers: Map<TopLevelDestination, (NavKey) -> NavEntry<NavKey>> =
        TopLevelDestination.entries.associateWith { destinationProvider(stacks.getValue(it), dependencies) }

    /** Controllers popped but possibly still animating out; see [flushReleases]. */
    private val pendingRelease = mutableListOf<Page>()

    private val mirrors: Map<TopLevelDestination, StackMirror<NavKey, Page>> =
        TopLevelDestination.entries.associateWith { destination ->
            StackMirror(
                create = { _, key -> page(destination, key) },
                release = { pendingRelease += it },
            )
        }

    /** Tabs waiting for a transition, preserving whether the request also regroups their pages. */
    private val deferredSyncs = mutableMapOf<TopLevelDestination, Boolean>()

    /** Each tab's pairs, by the page in their left half. */
    private val splits: Map<TopLevelDestination, MutableMap<Page, Split>> =
        TopLevelDestination.entries.associateWith { mutableMapOf() }

    /** How each tab's stack is drawn right now: what a pop UIKit made is counted against. */
    private val groups: MutableMap<TopLevelDestination, List<PaneGroup>> =
        TopLevelDestination.entries.associateWithTo(mutableMapOf()) { emptyList() }

    /** The width mode actually installed in each tab, which may lag a resize during navigation. */
    private val drawnExpanded = TopLevelDestination.entries.associateWithTo(mutableMapOf()) { false }

    /**
     * Set while [sync] hands UIKit new controllers. A navigation controller may report the change
     * back through its delegate before the call returns, and that is the shell's own change, not a
     * pop to write back.
     */
    private var syncing = false

    // UIKit holds delegates weakly; these fields are what keeps them alive.
    private val tabBarDelegate = TabBarDelegate()
    private val navigationDelegates = TopLevelDestination.entries.associateWith { NavigationDelegate(it) }
    private val popGestureDelegates = TopLevelDestination.entries.associateWith { PopGestureDelegate(it) }
    private var foregroundObserver: Any? = null
    private var closed = false

    suspend fun awaitTransitions() {
        navigationControllers.values.forEach { controller ->
            controller.transitionCoordinator?.let { coordinator ->
                suspendCancellableCoroutine<Unit> { continuation ->
                    val finish = { if (continuation.isActive) continuation.resume(Unit) }
                    if (!coordinator.animateAlongsideTransition(animation = null, completion = { finish() })) finish()
                }
            }
        }
    }

    fun close() {
        if (closed) return
        closed = true
        scope.cancel()
        foregroundObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        splits.values.forEach { pairs ->
            pairs.values.forEach { it.empty() }
            pairs.clear()
        }
        mirrors.values.forEach { it.reconcile(emptyList()) }
        navigationControllers.values.forEach { it.setViewControllers(emptyList<UIViewController>(), animated = false) }
        flushReleases()
        shellStore.clear()
    }

    init {
        scope.launch {
            container.sessionRepository.state.collectLatest { session ->
                container.postRepository.reconcileSession(session.isSignedIn, session.fingerprint)
                if (session.isSignedIn) {
                    runCatchingExceptCancellation {
                        val profile = container.profileRepository.profile()
                        container.accountRepository?.remember(profile, session.fingerprint)
                    }
                }
            }
        }
        scope.launch {
            IosLotteryNavigation.requests.collect { request ->
                if (request != null) {
                    root.selectedIndex = TopLevelDestination.HOME.ordinal.toULong()
                    stacks.getValue(TopLevelDestination.HOME).add(PostDetailKey(request.postId))
                    IosLotteryNavigation.handled(request)
                }
            }
        }
        root.delegate = tabBarDelegate
        root.setViewControllers(TopLevelDestination.entries.map { navigationControllers.getValue(it) }, animated = false)
        TopLevelDestination.entries.forEach { destination ->
            val navigationController = navigationControllers.getValue(destination)
            navigationController.delegate = navigationDelegates.getValue(destination)
            // With the bar hidden UIKit switches both swipes off — the edge one and iOS 26's
            // swipe-anywhere one — on the theory that a screen without a back button has nothing to go
            // back to. These screens draw their own. The swipe-anywhere recognizer is iOS 26 API on a
            // target that starts at 16, hence the check; Compose Multiplatform already knows to stop
            // it when a drag belongs to something scrolling sideways inside the page.
            //
            // The view first: UIKit hands both recognizers back to its own delegate when it loads a
            // navigation controller's view, and a tab's view loads the first time the tab is shown.
            // Set before that, the delegate held for 首页 only — every screen under 通知 and 我的
            // went back by its arrow alone.
            navigationController.loadViewIfNeeded()
            val popDelegate = popGestureDelegates.getValue(destination)
            navigationController.interactivePopGestureRecognizer?.delegate = popDelegate
            if (navigationController.hasContentPopGesture()) {
                navigationController.interactiveContentPopGestureRecognizer?.delegate = popDelegate
            }
            sync(destination)
            scope.launch {
                snapshotFlow { stacks.getValue(destination).toList() }.collect { sync(destination) }
            }
        }
        observeSettings()
        observeNotifications()
        container.accountRepository?.state?.value?.let { accounts ->
            if (accounts.adding || accounts.revision > 0) root.selectedIndex = TopLevelDestination.PROFILE.ordinal.toULong()
        }
    }

    /** One screen: a Compose controller showing [key]'s entry, and the view models it owns. */
    private class Page(val key: NavKey, val controller: UIViewController, val owner: PageOwner)

    private class PageOwner : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    private fun page(destination: TopLevelDestination, key: NavKey): Page {
        val stack = stacks.getValue(destination)
        val entry = providers.getValue(destination)(key)
        val owner = PageOwner()
        val controller =
            ComposeUIViewController(
                configure = {
                    // On the pages that lift themselves over the keyboard, Compose must not lift
                    // them a second time — see [makesRoomForKeyboard].
                    if (key.makesRoomForKeyboard()) onFocusBehavior = OnFocusBehavior.DoNothing
                },
            ) {
                NodysseyChrome(container, settings) {
                    DeviceLocals {
                        CompositionLocalProvider(
                            // The page's own store rather than the controller's default one, so that a
                            // view model lives exactly as long as its entry is on the stack — see
                            // [flushReleases] — and not as long as UIKit keeps the controller around.
                            LocalViewModelStoreOwner provides owner,
                            // 网络自检 from any screen's network-error state — see [LocalOpenNetworkCheck].
                            LocalOpenNetworkCheck provides { stack.add(NetworkCheckKey) },
                            // 测评对比 from any report card's 对比 (n), onto this page's tab.
                            LocalOpenReportCompare provides { stack.add(ReportCompareKey) },
                        ) {
                            entry.Content()
                        }
                    }
                }
            }
        return Page(key, controller, owner)
    }

    /**
     * Hands [destination]'s navigation controller the screens its stack now calls for.
     *
     * [relayout] is a change of window width rather than of the stack: the same pages, grouped
     * again without a navigation animation. It must still wait for UIKit's transition: a rotation
     * animates the existing Compose scenes, which cannot be moved to another container mid-flight.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun sync(
        destination: TopLevelDestination,
        relayout: Boolean = false,
    ) {
        val navigationController = navigationControllers.getValue(destination)
        val splits = splits.getValue(destination)
        val needsRelayout = relayout || deferredSyncs[destination] == true
        // `setViewControllers` during a push or a swipe is how UIKit ends up with a bar for one
        // screen over the content of another. The navigation delegates run this again once the
        // transition has settled.
        val coordinator = navigationController.transitionCoordinator
            ?: splits.values.firstNotNullOfOrNull { it.details.transitionCoordinator }
        if (coordinator != null) {
            val alreadyWaiting = destination in deferredSyncs
            deferredSyncs[destination] = needsRelayout
            if (!alreadyWaiting) {
                val resume = { NSOperationQueue.mainQueue.addOperationWithBlock { settle(destination) } }
                // A rotation has no navigation didShow callback. Its coordinator must drain the
                // pending request too, after UIKit has finished all of its completion callbacks.
                if (!coordinator.animateAlongsideTransition(animation = null, completion = { resume() })) resume()
            }
            return
        }
        deferredSyncs.remove(destination)
        val mirror = mirrors.getValue(destination)
        val keys = stacks.getValue(destination).toList()
        val pages = mirror.reconcile(keys) ?: if (needsRelayout) mirror.views else return
        val layout = paneGroups(keys, listDetailExpanded, ::paneKindOf)
        val onScreen = navigationController.view.window != null
        val animated = onScreen && !needsRelayout

        syncing = true
        try {
            groups[destination] = layout
            drawnExpanded[destination] = listDetailExpanded
            // A page goes into its new container only once it has left the old one: UIKit will not
            // give a controller two parents. Pairs no longer drawn give their pages back first.
            val lists = layout.filterIsInstance<PaneGroup.Split>().map { pages[it.listIndex] }.toSet()
            splits.keys.filter { it !in lists }.forEach { splits.remove(it)?.empty() }
            val controllers =
                layout.map { group ->
                    when (group) {
                        // The tab bar belongs to the tab roots, as on Android: a thread or a settings
                        // page pushed over 首页 is full-screen.
                        is PaneGroup.Single ->
                            pages[group.index].controller.also { it.hidesBottomBarWhenPushed = group.index > 0 }

                        // A pair keeps it, since the list it was opened from is still on screen.
                        is PaneGroup.Split ->
                            splits.getOrPut(pages[group.listIndex]) { Split(destination, pages[group.listIndex]) }.controller
                    }
                }
            navigationController.setViewControllers(controllers, animated = animated)
            layout.filterIsInstance<PaneGroup.Split>().forEach { group ->
                val details = pages.subList(group.listIndex + 1, group.listIndex + 1 + group.detailCount)
                splits.getValue(pages[group.listIndex]).show(
                    details.map { it.controller.also { controller -> controller.hidesBottomBarWhenPushed = false } },
                    animated = animated,
                )
            }
        } finally {
            syncing = false
        }
        updateTabBar()
        if (!onScreen || (navigationController.transitionCoordinator == null && splits.values.all { it.details.transitionCoordinator == null })) flushReleases()
    }

    /**
     * iPad's tab bar floats at the top, and `hidesBottomBarWhenPushed` does not reach it: an image, a
     * web page or 设置 pushed over a tab would sit under 首页 / 通知 / 我的. So on iPad the shell
     * hides it for the screens the iPhone hides it for — anything full-window above a tab root —
     * and keeps it over a pair, whose list is still the tab's own screen.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun updateTabBar() {
        if (!isPad || !root.respondsToSelector(NSSelectorFromString("setTabBarHidden:animated:"))) return
        val destination = TopLevelDestination.entries.getOrNull(root.selectedIndex.toInt()) ?: return
        val top = groups.getValue(destination).lastOrNull()
        val hidden = top is PaneGroup.Single && top.index > 0
        if (root.tabBarHidden != hidden) root.setTabBarHidden(hidden, animated = root.view.window != null)
    }

    private fun onWindowWidth(width: Double) {
        val expanded = isPad && width >= LIST_DETAIL_MIN_WIDTH
        if (expanded == listDetailExpanded) return
        listDetailExpanded = expanded
        TopLevelDestination.entries.forEach { sync(it, relayout = true) }
    }

    /**
     * UIKit has popped [destination] down to the screens standing for its first [remaining] entries;
     * remove those old entries without erasing newer navigation deferred during the transition.
     * Zero dropped is the common case — a change the shell made itself, or a cancelled swipe.
     */
    private fun writeBack(
        destination: TopLevelDestination,
        remaining: Int,
    ) {
        val mirror = mirrors.getValue(destination)
        val stack = stacks.getValue(destination)
        val dropped = mirror.platformPopped(remaining, stack)
        if (dropped == 0) return
        // settle reconciles navigation deferred during the pop against these surviving pages.
        groups[destination] = paneGroups(mirror.views.map { it.key }, drawnExpanded.getValue(destination), ::paneKindOf)
        val splits = splits.getValue(destination)
        val views = mirror.views.toSet()
        splits.keys.filter { it !in views }.forEach { splits.remove(it)?.empty() }
        updateTabBar()
        if (destination == TopLevelDestination.PROFILE && stack.lastOrNull() == AccountsKey) {
            container.accountRepository?.cancelIfAbandoned()
        }
    }

    /** What every transition ends with: released pages cleared, and a change that had to wait made. */
    private fun settle(destination: TopLevelDestination) {
        flushReleases()
        deferredSyncs.remove(destination)?.let { relayout -> sync(destination, relayout) }
    }

    /** One list and its details, side by side; see [PaneSplitController]. */
    private inner class Split(
        destination: TopLevelDestination,
        val list: Page,
    ) {
        /** The right half before anything has been opened in it, as on Android. */
        private val placeholder =
            ComposeUIViewController {
                NodysseyChrome(container, settings) {
                    DeviceLocals {
                        // On the page colour, as every screen's `Scaffold` is; Android's pane scaffold
                        // paints it there, and here nothing else would.
                        Box(Modifier.fillMaxSize().background(LocalPlazaLayers.current.page)) {
                            EmptyDetailPane(emptyDetailTextOf(list.key))
                        }
                    }
                }
            }

        // The placeholder is already the navigation root before UIKit sees this nested controller.
        val controller = PaneSplitController(placeholder)
        val details = controller.details

        // UIKit holds delegates weakly; these fields are what keeps them alive.
        private val navigationDelegate = DetailsDelegate(destination, this)
        private val popGestureDelegate = DetailsPopGestureDelegate(details)

        init {
            details.delegate = navigationDelegate
            // The view first, for the reason the tabs' own controllers load theirs first.
            details.loadViewIfNeeded()
            details.interactivePopGestureRecognizer?.delegate = popGestureDelegate
            if (details.hasContentPopGesture()) {
                details.interactiveContentPopGestureRecognizer?.delegate = popGestureDelegate
            }
        }

        /** How many details are on the right, the placeholder not counted. */
        val detailCount: Int get() = details.viewControllers.count { it != placeholder }

        fun show(
            pages: List<UIViewController>,
            animated: Boolean,
        ) {
            controller.showList(list.controller)
            val target = pages.ifEmpty { listOf(placeholder) }
            val current = details.viewControllers
            if (current == target) return
            // A push or a pop when the two share their first page. Otherwise it is a swap — out of
            // the placeholder, or a row of the list replacing what was open — and a swap does not
            // slide: there is nothing to go back to, and Mail does not slide either.
            val sameRoot = current.isNotEmpty() && current.first() == target.first()
            // Pages released by the outer stack can still have their views in its hierarchy.
            target.filter { it !in current }.forEach { it.view.removeFromSuperview() }
            details.setViewControllers(target, animated = animated && sameRoot)
        }

        /** Gives the pages back, to be drawn full-window or released. */
        fun empty() {
            controller.releasePages()
        }
    }

    /**
     * Clears the view models of every page that has left its stack.
     *
     * Not at the moment it leaves: a popped page is still on screen for the length of the pop, and a
     * view model cleared under it cancels whatever it is showing. The navigation delegate calls this
     * when the transition is over, and [sync] calls it at once for a tab that is not on screen.
     */
    private fun flushReleases() {
        pendingRelease.forEach { it.owner.viewModelStore.clear() }
        pendingRelease.clear()
    }

    private fun observeSettings() {
        scope.launch {
            container.settingsRepository.settings.collect { latest ->
                settings = latest
                // The bars UIKit draws follow the app's own light/dark answer, not only the OS's, the
                // way `NodysseyRoot` makes the status bar follow it on Android.
                root.overrideUserInterfaceStyle =
                    when {
                        latest.einkMode -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
                        latest.themeMode == ThemeMode.LIGHT -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
                        latest.themeMode == ThemeMode.DARK -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
                        else -> UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
                    }
            }
        }
        scope.launch {
            container.settingsRepository.settings
                .map { it.onboardingSeen }
                .distinctUntilChanged()
                .collect { seen -> if (seen) dismissOnboarding() else presentOnboarding() }
        }
    }

    private var onboarding: UIViewController? = null

    /**
     * 新手引导, presented over the tabs rather than drawn over them.
     *
     * Watched rather than shown once at launch for the reason the Android root overlays it instead of
     * swapping it in: 再看一次引导 on 使用帮助 clears the flag from deep in a stack, and the guide has
     * to come back over that stack without taking it away.
     */
    private suspend fun presentOnboarding() {
        if (onboarding != null) return
        // Presenting needs a controller already in a window, and the shell is installed as the
        // window's root only after this object has been built and handed back.
        while (root.view.window == null) delay(16)
        val controller =
            ComposeUIViewController {
                NodysseyChrome(container, settings) {
                    DeviceLocals {
                        OnboardingScreen(
                            onFinish = { scope.launch { container.settingsRepository.setOnboardingSeen(true) } },
                            appLinksEnabled = rememberAppLinkHandlingEnabled(),
                            onOpenAppLinkSettings = rememberAppLinkSettingsLauncher(),
                        )
                    }
                }
            }
        controller.modalPresentationStyle = UIModalPresentationFullScreen
        controller.modalInPresentation = true
        onboarding = controller
        root.presentViewController(controller, animated = false, completion = null)
    }

    private fun dismissOnboarding() {
        val controller = onboarding ?: return
        onboarding = null
        controller.dismissViewControllerAnimated(true, completion = null)
    }

    private fun observeNotifications() {
        scope.launch {
            notificationsViewModel.uiState
                .map { it.counts.all }
                .distinctUntilChanged()
                .collect { unread ->
                    navigationControllers.getValue(TopLevelDestination.NOTIFICATIONS).tabBarItem.badgeValue =
                        unread.takeIf { it > 0 }?.toString()
                }
        }
        // The Android root refreshes the badge on every resume, because a count grown stale overnight
        // is most visibly wrong the moment the app comes back. This is that resume.
        notificationsViewModel.refreshIfStale()
        foregroundObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ -> notificationsViewModel.refreshIfStale() }
    }

    private fun destinationOf(controller: UIViewController?): TopLevelDestination? =
        navigationControllers.entries.firstOrNull { it.value == controller }?.key

    private inner class TabBarDelegate :
        NSObject(),
        UITabBarControllerDelegateProtocol {
        /*
         * Tapping the tab that is already showing. Deeper in its stack, UIKit pops to the root by
         * itself and the navigation delegate writes that back. At the root, it is the same request as
         * on Android: 首页 back to the top and reloaded, 通知 back to the top, 我的 nothing.
         */
        override fun tabBarController(
            tabBarController: UITabBarController,
            shouldSelectViewController: UIViewController,
        ): Boolean {
            val destination = destinationOf(shouldSelectViewController) ?: return true
            if (shouldSelectViewController == tabBarController.selectedViewController &&
                stacks.getValue(destination).size == 1
            ) {
                when (destination) {
                    TopLevelDestination.HOME -> homeReselectRequests++
                    TopLevelDestination.NOTIFICATIONS -> notificationsScrollToTopRequests++
                    TopLevelDestination.PROFILE -> Unit
                }
            }
            scope.launch { updateTabBar() }
            return true
        }
    }

    private inner class NavigationDelegate(
        private val destination: TopLevelDestination,
    ) : NSObject(),
        UINavigationControllerDelegateProtocol {
        /*
         * Called when a transition has finished, whichever side started it. After a pop UIKit made —
         * a swipe, the tab bar's pop-to-root — the controller holds fewer screens than the mirror,
         * and the difference comes off the back stack. After a change this shell made the counts
         * already agree and nothing is written. A swipe that was let go of halfway lands here too,
         * with nothing popped.
         */
        override fun navigationController(
            navigationController: UINavigationController,
            didShowViewController: UIViewController,
            animated: Boolean,
        ) {
            if (syncing) return
            // A pair is one controller here and several entries on the stack.
            val shown = navigationController.viewControllers.size
            writeBack(destination, groups.getValue(destination).take(shown).sumOf { it.size })
            settle(destination)
        }
    }

    private inner class DetailsDelegate(
        private val destination: TopLevelDestination,
        private val split: Split,
    ) : NSObject(),
        UINavigationControllerDelegateProtocol {
        /*
         * [NavigationDelegate] for the right half of a pair: a swipe there pops a detail, and the back
         * stack loses that one. Only the pair on top of its tab can be swiped, so the entries it
         * stands for are the last ones on the stack.
         */
        override fun navigationController(
            navigationController: UINavigationController,
            didShowViewController: UIViewController,
            animated: Boolean,
        ) {
            if (syncing) return
            val layout = groups.getValue(destination)
            val top = layout.lastOrNull() as? PaneGroup.Split
            if (top != null && mirrors.getValue(destination).views.getOrNull(top.listIndex) === split.list) {
                writeBack(destination, layout.dropLast(1).sumOf { it.size } + 1 + split.detailCount)
            }
            settle(destination)
        }
    }

    private inner class PopGestureDelegate(
        private val destination: TopLevelDestination,
    ) : NSObject(),
        UIGestureRecognizerDelegateProtocol {
        /*
         * The two conditions UIKit's own delegate checks, which this one replaces: a screen to go
         * back to, and no push or pop still animating — a swipe is not let start a pop on top of a
         * transition that has not finished.
         */
        @OptIn(ExperimentalForeignApi::class)
        override fun gestureRecognizerShouldBegin(gestureRecognizer: UIGestureRecognizer): Boolean {
            val navigationController = navigationControllers.getValue(destination)
            // Over the right half of a pair, a swipe belongs to its details while they have one to
            // go back to; the pair itself goes only once they do not.
            val split = splits.getValue(destination).values.firstOrNull { it.controller == navigationController.topViewController }
            if (split != null &&
                split.details.viewControllers.size > 1 &&
                gestureRecognizer.locationInView(split.details.view).useContents { x >= 0.0 }
            ) {
                return false
            }
            return navigationController.viewControllers.size > 1 &&
                navigationController.transitionCoordinator == null
        }
    }

    /** [PopGestureDelegate]'s two conditions, for the right half of a pair. */
    private class DetailsPopGestureDelegate(
        private val details: UINavigationController,
    ) : NSObject(),
        UIGestureRecognizerDelegateProtocol {
        override fun gestureRecognizerShouldBegin(gestureRecognizer: UIGestureRecognizer): Boolean =
            details.viewControllers.size > 1 && details.transitionCoordinator == null
    }
}

/**
 * The tab controller, reporting its width on every layout: what [NativeShell] decides one pane or two
 * by. Its own bounds rather than the screen's, because in Split View and Stage Manager the window is
 * not the screen.
 */
@OptIn(ExperimentalForeignApi::class)
private class ShellTabBarController(
    private val onWidth: (Double) -> Unit,
) : UITabBarController(nibName = null, bundle = null) {
    private val layoutUpdates = WindowLayoutDispatcher(
        post = { update -> NSOperationQueue.mainQueue.addOperationWithBlock { update() } },
        onLayout = { onWidth(view.bounds.useContents { size.width }) },
    )

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        val coordinator = transitionCoordinator
        if (coordinator == null) {
            layoutUpdates.request()
        } else {
            layoutUpdates.request { onFinished ->
                coordinator.animateAlongsideTransition(animation = null, completion = { onFinished() })
            }
        }
    }
}

/**
 * Whether this is an iPad: a question about the hardware rather than the window, asked only where
 * the hardware is the answer — the hand holding it, and where UIKit puts the tab bar.
 */
private val isPad: Boolean = UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad

/** What only the device can say, for every composition the shell makes; see [LocalOneHandModeAvailable]. */
@Composable
private fun DeviceLocals(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier) {
        if (isPad) {
            CompositionLocalProvider(
                LocalOneHandMode provides false,
                LocalOneHandModeAvailable provides false,
                content = content,
            )
        } else {
            content()
        }
    }
}

/**
 * Where Android's `calculatePaneScaffoldDirective` starts drawing two panes: Material's expanded
 * width class. An iPad in landscape and the larger ones in portrait are past it; an 11-inch or
 * smaller iPad in portrait, and half of one in Split View, keep one screen at a time.
 */
private const val LIST_DETAIL_MIN_WIDTH = 840.0

private fun paneKindOf(key: NavKey): PaneKind =
    when (paneRoleOf(key)) {
        PaneRole.LIST -> PaneKind.LIST
        PaneRole.DETAIL -> PaneKind.DETAIL
        null -> PaneKind.NONE
    }

/** Whether this system has iOS 26's swipe-anywhere back, which a target starting at 16 cannot assume. */
@OptIn(ExperimentalForeignApi::class)
private fun UINavigationController.hasContentPopGesture(): Boolean =
    respondsToSelector(NSSelectorFromString("interactiveContentPopGestureRecognizer"))

/** The SF Symbol for a tab; its selected state is the `.fill` variant, as Material's is the filled icon. */
private val TopLevelDestination.symbol: String
    get() =
        when (this) {
            TopLevelDestination.HOME -> "house"
            TopLevelDestination.NOTIFICATIONS -> "bell"
            TopLevelDestination.PROFILE -> "person"
        }

/**
 * The pages whose screens make room for the keyboard themselves — `imePadding`, or `:designsys`'s
 * `paddingWithKeyboard`, which is the same insets consumed a `Scaffold` later.
 *
 * On those, Compose's default must be off. Left on, it shifts the whole page to keep the focused
 * field above the keyboard, and the two adjustments chase each other: `OffsetToFocusedRect`
 * re-measures whenever the padding moves the field, the padding moves it again, and the main thread
 * spins in layout until the watchdog kills the app (`0x8BADF00D`) — the freezes reported in 发帖 and
 * 编辑资料. Every other page keeps the default, because it is the only thing keeping their fields in
 * view: a search box or a settings form there has no padding of its own to fall back on. A screen
 * that gains `imePadding` belongs on this list.
 */
private fun NavKey.makesRoomForKeyboard(): Boolean =
    this is PostComposerKey ||
        this is PostDetailKey ||
        this is AccountProfileFieldsKey ||
        this is AccountSecurityKey ||
        this is SignInKey ||
        this is MessageThreadKey ||
        this is DohSettingsKey ||
        this is StickerAddKey ||
        this is ThemeSettingsKey

/**
 * Safari, which is what the platform `UriHandler` is on this side — the same one `BrowserLinks.ios`
 * hands back inside a composition, needed here outside of one.
 */
private object SafariUriHandler : UriHandler {
    override fun openUri(uri: String) {
        val url = NSURL.URLWithString(uri) ?: throw IllegalArgumentException("Not a URL: $uri")
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }
}
