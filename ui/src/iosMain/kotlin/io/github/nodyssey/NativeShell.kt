package io.github.nodyssey

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReusableContent
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.settings.PadLayoutMode
import io.github.nodyssey.data.settings.ThemeMode
import io.github.nodyssey.data.settings.UserSettings
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.assets.PendingAttendance
import io.github.nodyssey.ui.common.ClipboardPost
import io.github.nodyssey.ui.common.IosClipboardPostReader
import io.github.nodyssey.ui.common.ListFabCentreAboveSafeArea
import io.github.nodyssey.ui.common.LocalBottomBarSafeAreaOverlap
import io.github.nodyssey.ui.common.LocalFixedBottomBarHeight
import io.github.nodyssey.ui.common.LocalIosPad
import io.github.nodyssey.ui.common.LocalIosPadFullPage
import io.github.nodyssey.ui.common.LocalIosPadLandscape
import io.github.nodyssey.ui.common.LocalIosPadSqueezing
import io.github.nodyssey.ui.common.LocalOpenNetworkCheck
import io.github.nodyssey.ui.common.bottomBarInset
import io.github.nodyssey.ui.common.clipboardPostToOffer
import io.github.nodyssey.ui.compare.LocalOpenReportCompare
import io.github.nodyssey.ui.composer.PendingComposerShare
import io.github.nodyssey.ui.navigation.PaneGroup
import io.github.nodyssey.ui.navigation.PaneKind
import io.github.nodyssey.ui.navigation.StackMirror
import io.github.nodyssey.ui.navigation.TopLevelDestination
import io.github.nodyssey.ui.navigation.WindowLayoutDispatcher
import io.github.nodyssey.ui.navigation.paneGroups
import io.github.nodyssey.ui.navigation.resolvesIosListDetail
import io.github.nodyssey.ui.notifications.NotificationsViewModel
import io.github.nodyssey.ui.onboarding.OnboardingScreen
import io.github.nodyssey.ui.postlist.HomeFeedStates
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.about_privacy
import io.github.nodyssey.ui.resources.about_rss
import io.github.nodyssey.ui.resources.about_site
import io.github.nodyssey.ui.resources.app_name
import io.github.nodyssey.ui.resources.clipboard_post_open
import io.github.nodyssey.ui.resources.clipboard_post_prompt
import io.github.nodyssey.ui.settings.UpdateReminderDialog
import io.github.nodyssey.ui.settings.UpdateReminderViewModel
import io.github.nodyssey.ui.settings.rememberAppLinkHandlingEnabled
import io.github.nodyssey.ui.settings.rememberAppLinkSettingsLauncher
import io.github.plaza.core.runCatchingExceptCancellation
import io.github.plaza.designsys.theme.LocalOneHandMode
import io.github.plaza.designsys.theme.LocalOneHandModeAvailable
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.nativeGlassAvailable
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSURL
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIColor
import platform.UIKit.UIDevice
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UIGestureRecognizerDelegateProtocol
import platform.UIKit.UIImage
import platform.UIKit.UIModalPresentationFullScreen
import platform.UIKit.UINavigationController
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UITabBarAppearance
import platform.UIKit.UITabBarController
import platform.UIKit.UITabBarControllerDelegateProtocol
import platform.UIKit.UITabBarItem
import platform.UIKit.UIUserInterfaceIdiomPad
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIView
import platform.UIKit.UIViewAnimationOptionCurveEaseOut
import platform.UIKit.UIViewController
import platform.UIKit.accessibilityElementsHidden
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
import kotlin.math.PI

/**
 * The iOS app: one system navigation stack per tab, with one Compose screen in each pushed
 * controller. UIKit owns push/pop transitions, the edge swipe and iOS 26's swipe-anywhere back.
 * UIKit also lays out the system tabs on older iOS versions and regular-width iPads. Floating
 * tabs live inside each root page and share a user-selected baseline with the thread actions.
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
    private val strings: ShellStrings,
) {
    private val scope = MainScope()

    /** The newest settings, so a screen pushed later starts in today's colours rather than launch's. */
    private var settings: UserSettings by mutableStateOf(initialSettings)

    /**
     * What `MainNavigation` keeps in its own composition, kept here because no composition here
     * outlives the screens: the notifications view model behind both the tab badge and the 通知
     * screen, and 首页's list positions.
     */
    private val shellStore = ViewModelStore()
    private val notificationsViewModel: NotificationsViewModel =
        ViewModelProvider.create(shellStore, NotificationsViewModel.factory(container))[NotificationsViewModel::class]
    private val updateReminderViewModel: UpdateReminderViewModel =
        ViewModelProvider.create(shellStore, UpdateReminderViewModel.factory(container))[UpdateReminderViewModel::class]
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
    private var windowLandscape by mutableStateOf<Boolean?>(null)
    private var windowWidth = 0.0
    private var windowHeight = 0.0

    // The system tab bar's height, measured after layout; the reading controls share its band.
    private var fixedBottomBarHeight by mutableStateOf<Dp?>(49.dp)

    // UIKit's own tab bar on every iOS version, iOS 26's liquid glass included. Its position is the
    // system's, which is why iOS offers no 底部栏位置 setting (see ThemeSettingsScreen).
    private val systemTabs = ShellTabBarController(onLayout = ::onSystemTabLayout)
    val root: UIViewController = systemTabs
    private var selectedTab = TopLevelDestination.HOME

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
            selectTab = ::selectTab,
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

    /** The root-pane squeeze still playing, if any; see [PaneSqueezeRequest]. */
    private var activeSqueeze: Pair<Page, PaneSqueezeRequest>? = null

    // UIKit holds delegates weakly; these fields are what keeps them alive.
    private val tabBarDelegate = TabBarDelegate()
    private val navigationDelegates = TopLevelDestination.entries.associateWith { NavigationDelegate(it) }
    private val popGestureDelegates = TopLevelDestination.entries.associateWith { PopGestureDelegate(it) }
    private var foregroundObserver: Any? = null
    private var clipboardCheckJob: Job? = null
    private var clipboardOffer by mutableStateOf<ClipboardPost?>(null)
    private var activePageState by mutableStateOf<PageState?>(null)
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
        finishSqueeze()
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
                    selectTab(TopLevelDestination.HOME)
                    stacks.getValue(TopLevelDestination.HOME).add(PostDetailKey(request.postId))
                    IosLotteryNavigation.handled(request)
                }
            }
        }
        systemTabs.delegate = tabBarDelegate
        systemTabs.setViewControllers(TopLevelDestination.entries.map { navigationControllers.getValue(it) }, animated = false)
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
            if (accounts.adding || accounts.revision > 0) selectTab(TopLevelDestination.PROFILE)
        }
        refreshActivePage()
        checkClipboardPost()
    }

    /**
     * Compose-observed state for a [Page], kept separate from [Page.controller] so the controller's
     * Compose lambda never retains its own `UIViewController` across the ObjC/Kotlin boundary.
     */
    private class PageState {
        /**
         * Temporary width in UIKit points while a root-pane squeeze animates in Compose's layout
         * phase so the backing UIView / CAMetalLayer keeps a fixed width. [Double.NaN] at rest.
         */
        var squeezeWidthPt by mutableDoubleStateOf(Double.NaN)

        /** Active Compose-clock squeeze hosted on this page, if any. */
        var squeezeRequest by mutableStateOf<PaneSqueezeRequest?>(null)

        /** Set once the page has left its navigation stack so its Compose tree and UIKitViews tear down. */
        var released by mutableStateOf(false)
    }

    /** One screen: a Compose controller showing [key]'s entry, and the view models it owns. */
    private class Page(
        val key: NavKey,
        val controller: UIViewController,
        val owner: PageOwner,
        val state: PageState,
    )

    private class PageOwner : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    private fun page(destination: TopLevelDestination, key: NavKey): Page {
        val stack = stacks.getValue(destination)
        val entry = providers.getValue(destination)(key)
        val owner = PageOwner()
        val state = PageState()
        val controller =
            ComposeUIViewController(
                configure = {
                    // On the pages that lift themselves over the keyboard, Compose must not lift
                    // them a second time — see [makesRoomForKeyboard].
                    if (key.makesRoomForKeyboard()) onFocusBehavior = OnFocusBehavior.DoNothing
                },
            ) {
                if (!state.released) {
                    NodysseyChrome(container, settings) { storedSettings ->
                        DeviceLocals(fixedBottomBarHeight, windowLandscape) {
                            CompositionLocalProvider(
                                // The page's own store rather than the controller's default one, so that a
                                // view model lives exactly as long as its entry is on the stack — see
                                // [flushReleases] — and not as long as UIKit keeps the controller around.
                                LocalViewModelStoreOwner provides owner,
                                // 网络自检 from any screen's network-error state — see [LocalOpenNetworkCheck].
                                LocalOpenNetworkCheck provides { stack.add(NetworkCheckKey) },
                                // 测评对比 from any report card's 对比 (n), onto this page's tab.
                                LocalOpenReportCompare provides { stack.add(ReportCompareKey) },
                                LocalIosPadFullPage provides (isPad && (storedSettings ?: settings).padLayoutMode == PadLayoutMode.SINGLE),
                                // Read during TabScaffold layout so narrow-gutter interpolation only runs mid-squeeze.
                                LocalIosPadSqueezing provides { !state.squeezeWidthPt.isNaN() },
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .layout { measurable, constraints ->
                                            val squeezeWidth = state.squeezeWidthPt
                                            val targetWidth =
                                                if (!squeezeWidth.isNaN()) {
                                                    squeezeWidth.dp.roundToPx().coerceIn(0, constraints.maxWidth)
                                                } else {
                                                    constraints.maxWidth
                                                }
                                            val placeable =
                                                measurable.measure(
                                                    constraints.copy(minWidth = targetWidth, maxWidth = targetWidth),
                                                )
                                            layout(constraints.maxWidth, constraints.maxHeight) {
                                                placeable.placeRelative(0, 0)
                                            }
                                        },
                                ) {
                                    // The system tab bar's selected item wears the app's accent, which only
                                    // the composed theme knows (seed colours, dynamic colour).
                                    val accent = MaterialTheme.colorScheme.primary
                                    SideEffect {
                                        if (activePageState === state) {
                                            systemTabs.tabBar.tintColor = UIColor.colorWithRed(
                                                accent.red.toDouble(),
                                                accent.green.toDouble(),
                                                accent.blue.toDouble(),
                                                accent.alpha.toDouble(),
                                            )
                                        }
                                    }
                                    entry.Content()
                                    PaneSqueezeRunner(state)
                                    ClipboardPostSnackbarOverlay(
                                        modifier = Modifier,
                                        active = activePageState === state,
                                        offer = clipboardOffer,
                                        onHandled = ::handleClipboardOffer,
                                    )
                                    val reminder by updateReminderViewModel.launchReminder.collectAsStateWithLifecycle()
                                    if (activePageState === state && settings.onboardingSeen && onboarding == null) {
                                        reminder?.let { release ->
                                            UpdateReminderDialog(
                                                release = release,
                                                onDownload = {
                                                    updateReminderViewModel.acceptReminder()
                                                    SafariUriHandler.openUri(release.htmlUrl)
                                                },
                                                onPostpone = updateReminderViewModel::postponeReminder,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        return Page(key, controller, owner, state)
    }

    /**
     * Hands [destination]'s navigation controller the screens its stack now calls for.
     *
     * [relayout] is a change of window size rather than of the stack: the same pages, grouped
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
        // The regrouped layout is read before reconcile: a collapse defers the UIKit swap until
        // the squeeze has played out, and reconcile must not run before the pair is swapped for real.
        val layout = paneGroups(keys, listDetailExpanded, ::paneKindOf)
        val onScreen = navigationController.view.window != null
        // The tab's list controller moves between navigation stacks when the first detail opens or
        // closes. As on resize, UIKit must finish that transfer before drawing the reused page, so
        // the regroup itself stays instant — [PaneSqueeze] plays the motion inside the pair, the
        // list re-laying out at each intermediate width as the detail pushes it from the right.
        val wasRootSplit = groups.getValue(destination).firstOrNull() is PaneGroup.Split
        val isRootSplit = layout.firstOrNull() is PaneGroup.Split
        val animated = onScreen && !needsRelayout && wasRootSplit == isRootSplit
        val currentRootSplit =
            splits.values.firstOrNull { it.controller == navigationController.viewControllers.firstOrNull() }
        val activeForTab = activeSqueeze?.second?.takeIf { it.destination == destination }
        // Keep the current in-flight offset when reversing or retargeting an active root squeeze
        // on this tab so rapid open -> back -> open transitions smoothly reverse in place instead
        // of snapping off-screen or short-circuiting when mirror.reconcile returns null.
        val canReverseInPlace =
            activeForTab != null &&
                onScreen &&
                !needsRelayout &&
                wasRootSplit &&
                currentRootSplit != null &&
                !UIAccessibilityIsReduceMotionEnabled()
        val interruptedOffset =
            if (canReverseInPlace) {
                val offset = currentRootSplit.controller.detailsOffset
                cancelSqueezeInPlace()
                offset
            } else {
                if (activeForTab != null || needsRelayout) finishSqueeze()
                null
            }
        if (
            onScreen && !needsRelayout && wasRootSplit && !isRootSplit &&
            !UIAccessibilityIsReduceMotionEnabled()
        ) {
            // Closing the pair plays first: the details slide off the right edge while the list
            // widens back in Compose's layout pass over a fixed full-width Metal surface. The
            // actual UIKit swap happens on a second pass where the pair is already fully open.
            val startOffset =
                when {
                    interruptedOffset != null -> interruptedOffset
                    currentRootSplit != null && !currentRootSplit.controller.isSqueezing && currentRootSplit.controller.detailsOffset == 0.0 -> 0.0
                    else -> null
                }
            if (currentRootSplit != null && startOffset != null) {
                val fullOpenOffset = currentRootSplit.controller.fullyOpenOffset
                if (fullOpenOffset > startOffset) {
                    val listPage = currentRootSplit.list
                    // Constrain Compose before expanding the UIView once behind the opaque
                    // details pane, avoiding per-frame Metal resizes.
                    listPage.state.squeezeWidthPt = LIST_PANE_WIDTH + startOffset
                    currentRootSplit.controller.beginSqueeze(offset = startOffset, layoutNow = !currentRootSplit.controller.isSqueezing)
                    lateinit var request: PaneSqueezeRequest
                    request =
                        PaneSqueezeRequest(
                            destination = destination,
                            from = startOffset,
                            to = fullOpenOffset,
                            isClosing = true,
                            onTick = { offset ->
                                listPage.state.squeezeWidthPt = LIST_PANE_WIDTH + offset
                            },
                            onPresent = { offset ->
                                currentRootSplit.controller.updateSqueezeOffset(offset.coerceIn(0.0, fullOpenOffset))
                            },
                            onEnd = {
                                if (activeSqueeze?.second === request) activeSqueeze = null
                                currentRootSplit.controller.updateSqueezeOffset(fullOpenOffset)
                                listPage.state.squeezeWidthPt = Double.NaN
                                sync(destination)
                            },
                            onCancel = {
                                currentRootSplit.controller.updateSqueezeOffset(fullOpenOffset)
                                listPage.state.squeezeWidthPt = Double.NaN
                            },
                        )
                    startSqueeze(listPage, request)
                    return
                }
            }
        }
        val reconciled = mirror.reconcile(keys)
        if (reconciled == null && !needsRelayout) {
            // Re-opening the same detail during a closing squeeze leaves mirror.keys unchanged, so
            // reconcile returns null even though the split is still offset toward the right edge.
            if (isRootSplit && currentRootSplit != null && (currentRootSplit.controller.isSqueezing || currentRootSplit.controller.detailsOffset != 0.0)) {
                val listPage = currentRootSplit.list
                val resumeFrom = interruptedOffset ?: currentRootSplit.controller.detailsOffset
                if (onScreen && resumeFrom > 0.0 && !UIAccessibilityIsReduceMotionEnabled()) {
                    listPage.state.squeezeWidthPt = LIST_PANE_WIDTH + resumeFrom
                    currentRootSplit.controller.beginSqueeze(offset = resumeFrom, layoutNow = !currentRootSplit.controller.isSqueezing)
                    startOpeningSqueeze(destination, listPage, currentRootSplit, resumeFrom)
                } else {
                    listPage.state.squeezeWidthPt = Double.NaN
                    currentRootSplit.controller.endSqueeze()
                }
            }
            return
        }
        val pages = reconciled ?: mirror.views
        val openingRootSplit =
            onScreen && !needsRelayout && !wasRootSplit && isRootSplit && !UIAccessibilityIsReduceMotionEnabled()
        val openingOffset =
            if (openingRootSplit) {
                navigationController.view.bounds.useContents { (size.width - LIST_PANE_WIDTH).coerceAtLeast(0.0) }
            } else {
                0.0
            }

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
                            pages[group.index].controller.also {
                                it.hidesBottomBarWhenPushed = group.index > 0
                            }

                        // A pair keeps it, since the list it was opened from is still on screen.
                        is PaneGroup.Split ->
                            splits.getOrPut(pages[group.listIndex]) { Split(destination, pages[group.listIndex]) }.controller
                    }
                }
            if (openingRootSplit && openingOffset > 0.0) {
                // Enter squeeze mode before UIKit lays out the newly mounted pair so the reused
                // list controller never shrinks to 360pt and back to full width on frame zero.
                val rootGroup = layout.first() as PaneGroup.Split
                val listPage = pages[rootGroup.listIndex]
                val rootSplit = splits.getValue(listPage)
                listPage.state.squeezeWidthPt = LIST_PANE_WIDTH + openingOffset
                rootSplit.controller.beginSqueeze(offset = openingOffset)
            }
            if (navigationController.viewControllers != controllers) {
                navigationController.setViewControllers(controllers, animated = animated)
            }
            layout.filterIsInstance<PaneGroup.Split>().forEach { group ->
                val details = pages.subList(group.listIndex + 1, group.listIndex + 1 + group.detailCount)
                splits.getValue(pages[group.listIndex]).show(
                    details.map { it.controller.also { controller -> controller.hidesBottomBarWhenPushed = false } },
                    animated = animated && interruptedOffset == null,
                )
            }
        } finally {
            syncing = false
        }
        (layout.firstOrNull() as? PaneGroup.Split)?.let { group ->
            val listPage = pages[group.listIndex]
            val rootSplit = splits.getValue(listPage)
            when {
                openingRootSplit && openingOffset > 0.0 -> {
                    // Lay out the pair once with details shifted off the right edge and list still
                    // spanning the window, then start the Compose-clock spring from that width.
                    navigationController.view.layoutIfNeeded()
                    val from = rootSplit.controller.fullyOpenOffset
                    if (from > 0.0) {
                        rootSplit.controller.updateSqueezeOffset(from)
                        listPage.state.squeezeWidthPt = LIST_PANE_WIDTH + from
                        startOpeningSqueeze(destination, listPage, rootSplit, from)
                    } else {
                        listPage.state.squeezeWidthPt = Double.NaN
                        rootSplit.controller.endSqueeze()
                    }
                }

                interruptedOffset != null && interruptedOffset > 0.0 -> {
                    // Another thread was selected while the split was mid-squeeze; slide cleanly
                    // from the current offset back to resting width.
                    listPage.state.squeezeWidthPt = LIST_PANE_WIDTH + interruptedOffset
                    rootSplit.controller.beginSqueeze(offset = interruptedOffset, layoutNow = !rootSplit.controller.isSqueezing)
                    startOpeningSqueeze(destination, listPage, rootSplit, interruptedOffset)
                }

                rootSplit.controller.isSqueezing || rootSplit.controller.detailsOffset != 0.0 -> {
                    // A pair that stayed the root through an interrupted collapse returns to rest.
                    listPage.state.squeezeWidthPt = Double.NaN
                    rootSplit.controller.endSqueeze()
                }
            }
        }
        updateTabBar()
        refreshActivePage()
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
        val tabs = systemTabs
        if (!isPad || !tabs.respondsToSelector(NSSelectorFromString("setTabBarHidden:animated:"))) return
        val top = groups.getValue(selectedTab).lastOrNull()
        val hidden = top is PaneGroup.Single && top.index > 0
        if (tabs.tabBarHidden != hidden) tabs.setTabBarHidden(hidden, animated = tabs.view.window != null)
    }

    private fun onSystemTabLayout(width: Double, height: Double, bottomBarHeight: Double?) {
        if (bottomBarHeight != null) fixedBottomBarHeight = bottomBarHeight.dp
        if (width > 0.0 && height > 0.0) {
            windowWidth = width
            windowHeight = height
            windowLandscape = width > height
        }
        refreshListDetailExpanded()
    }

    private fun refreshListDetailExpanded() {
        val expanded = resolvesIosListDetail(isPad, windowWidth, windowHeight, settings.padLayoutMode)
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
        refreshActivePage()
        if (destination == TopLevelDestination.PROFILE && stack.lastOrNull() == AccountsKey) {
            container.accountRepository?.cancelIfAbandoned()
        }
    }

    /** What every transition ends with: released pages cleared, and a change that had to wait made. */
    private fun settle(destination: TopLevelDestination) {
        flushReleases()
        deferredSyncs.remove(destination)?.let { relayout -> sync(destination, relayout) }
    }

    /** Built outside [Split] so its Compose lambda does not retain [Split] across ObjC/Kotlin. */
    private fun emptyDetailPlaceholder(listKey: NavKey): UIViewController {
        val text = emptyDetailTextOf(listKey)
        return ComposeUIViewController {
            NodysseyChrome(container, settings) {
                DeviceLocals(fixedBottomBarHeight, windowLandscape) {
                    // On the page colour, as every screen's `Scaffold` is; Android's pane scaffold
                    // paints it there, and here nothing else would.
                    Box(Modifier.fillMaxSize().background(LocalPlazaLayers.current.page)) {
                        EmptyDetailPane(text)
                    }
                }
            }
        }
    }

    /** One list and its details, side by side; see [PaneSplitController]. */
    private inner class Split(
        destination: TopLevelDestination,
        val list: Page,
    ) {
        /** The right half before anything has been opened in it, as on Android. */
        private val placeholder = emptyDetailPlaceholder(list.key)

        // The placeholder is already the navigation root before UIKit sees this nested controller.
        val controller = PaneSplitController(placeholder)
        val details = controller.details

        // UIKit holds delegates weakly; these fields are what keeps them alive.
        private val navigationDelegate = DetailsDelegate(destination, this)
        private val popGestureDelegate = DetailsPopGestureDelegate(details)

        /** The picture of the outgoing detail still fading over its replacement. */
        private var swapOverlay: UIView? = null

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

        @OptIn(ExperimentalForeignApi::class)
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
            // slide: there is nothing to go back to, and Mail does not slide either. A swap on
            // screen cross-fades a snapshot of the outgoing detail instead.
            val sameRoot = current.isNotEmpty() && current.first() == target.first()
            val fading = animated && !sameRoot && controller.view.window != null
            val old = if (fading) details.view.snapshotViewAfterScreenUpdates(false) else null
            // Pages released by the outer stack can still have their views in its hierarchy.
            target.filter { it !in current }.forEach { it.view.removeFromSuperview() }
            details.setViewControllers(target, animated = animated && sameRoot)
            swapOverlay?.removeFromSuperview()
            swapOverlay = null
            if (old != null) {
                old.userInteractionEnabled = false
                old.setFrame(details.view.frame)
                controller.view.addSubview(old)
                swapOverlay = old
                UIView.animateWithDuration(
                    duration = 0.2,
                    delay = 0.0,
                    options = UIViewAnimationOptionCurveEaseOut,
                    animations = { old.alpha = 0.0 },
                    completion = {
                        old.removeFromSuperview()
                        if (swapOverlay === old) swapOverlay = null
                    },
                )
            }
        }

        /** Gives the pages back, to be drawn full-window or released. */
        fun empty() {
            list.state.squeezeWidthPt = Double.NaN
            swapOverlay?.removeFromSuperview()
            swapOverlay = null
            details.interactivePopGestureRecognizer?.delegate = null
            if (details.hasContentPopGesture()) {
                details.interactiveContentPopGestureRecognizer?.delegate = null
            }
            controller.releasePages()
        }
    }

    private fun startOpeningSqueeze(
        destination: TopLevelDestination,
        listPage: Page,
        rootSplit: Split,
        from: Double,
    ) {
        lateinit var request: PaneSqueezeRequest
        request =
            PaneSqueezeRequest(
                destination = destination,
                from = from,
                to = 0.0,
                isClosing = false,
                onTick = { offset ->
                    listPage.state.squeezeWidthPt = LIST_PANE_WIDTH + offset
                },
                onPresent = { offset ->
                    val maxOffset = rootSplit.controller.fullyOpenOffset.coerceAtLeast(from)
                    rootSplit.controller.updateSqueezeOffset(offset.coerceIn(0.0, maxOffset))
                },
                onEnd = {
                    if (activeSqueeze?.second === request) activeSqueeze = null
                    listPage.state.squeezeWidthPt = Double.NaN
                    rootSplit.controller.endSqueeze()
                },
                onCancel = {
                    listPage.state.squeezeWidthPt = Double.NaN
                    rootSplit.controller.endSqueeze()
                },
            )
        startSqueeze(listPage, request)
    }

    private fun startSqueeze(
        page: Page,
        request: PaneSqueezeRequest,
    ) {
        activeSqueeze = page to request
        page.state.squeezeRequest = request
    }

    /** Stops the running spring without snapping its visual offset so a reversed squeeze can continue in place. */
    private fun cancelSqueezeInPlace() {
        val (page, request) = activeSqueeze ?: return
        activeSqueeze = null
        request.finished = true
        request.pendingFrames.clear()
        if (page.state.squeezeRequest === request) page.state.squeezeRequest = null
    }

    private fun finishSqueeze() {
        val (page, request) = activeSqueeze ?: return
        activeSqueeze = null
        if (!request.finished) {
            request.finished = true
            request.pendingFrames.clear()
            if (page.state.squeezeRequest === request) page.state.squeezeRequest = null
            request.onCancel()
        }
    }

    private fun finishSqueezeForTabSwitch(newTab: TopLevelDestination) {
        val current = activeSqueeze ?: return
        if (current.second.destination == newTab) return
        val closingDestination = current.second.destination.takeIf { current.second.isClosing }
        finishSqueeze()
        if (closingDestination != null) sync(closingDestination)
    }

    private class SqueezeFrame(
        val seq: Int,
        val offset: Double,
        val finalCommit: CompletableDeferred<Unit>? = null,
    )

    /**
     * Request for a Compose-clock root-pane squeeze hosted on the list [Page].
     *
     * Running the spring inside the list page's Compose frame clock (`Animatable`) drives the
     * list's layout width while a reused 1×1 invisible [UIKitView]'s `onReset` callback runs
     * directly inside Compose's `InteropSyncTransaction.performTransaction()` — the exact Core
     * Animation transaction that presents that Metal frame — so both per-frame details translation
     * and final-frame completion stay locked to the presented Metal drawable without waiting for
     * `layoutSubviews()` or guessing frame counts.
     */
    private class PaneSqueezeRequest(
        val destination: TopLevelDestination,
        val from: Double,
        val to: Double,
        val isClosing: Boolean,
        val onTick: (Double) -> Unit,
        val onPresent: (Double) -> Unit,
        val onEnd: () -> Unit,
        val onCancel: () -> Unit,
    ) {
        var finished = false
        val pendingFrames = ArrayDeque<SqueezeFrame>()
    }

    /**
     * Isolated host for [PaneSqueezeRequest] so updating squeeze state never recomposes the
     * screen content beside it.
     */
    @OptIn(ExperimentalComposeUiApi::class, ExperimentalForeignApi::class)
    @Composable
    private fun PaneSqueezeRunner(state: PageState) {
        val request = state.squeezeRequest ?: return
        key(request) {
            var frame by remember { mutableStateOf(SqueezeFrame(seq = 0, offset = request.from)) }
            var lastEnqueuedSeq by remember { mutableIntStateOf(0) }
            val currentFrame = frame
            ReusableContent(currentFrame.seq) {
                UIKitView(
                    factory = {
                        UIView(frame = CGRectZero.readValue()).apply {
                            alpha = 0.0
                            backgroundColor = UIColor.clearColor
                            userInteractionEnabled = false
                            accessibilityElementsHidden = true
                        }
                    },
                    modifier = Modifier.size(1.dp),
                    properties = SqueezeSyncInterop,
                    onReset = {
                        request.pendingFrames.removeFirstOrNull()?.let { committed ->
                            if (!request.finished) {
                                request.onPresent(committed.offset)
                                committed.finalCommit?.complete(Unit)
                            }
                        }
                    },
                    onRelease = { request.pendingFrames.clear() },
                )
            }
            // One FIFO entry per ReusableContent key change matches LayoutNode.onReuse() 1-to-1
            // (initial mount seq=0 and same-seq recompositions do not invoke onReuse).
            SideEffect {
                if (currentFrame.seq != lastEnqueuedSeq) {
                    lastEnqueuedSeq = currentFrame.seq
                    request.pendingFrames.addLast(currentFrame)
                }
            }
            LaunchedEffect(request) {
                if (request.finished) return@LaunchedEffect
                // Critically damped spring, response 0.36s: ω = 2π / 0.36, stiffness = ω².
                val omega = (2.0 * PI / 0.36).toFloat()
                val animatable = Animatable(request.from.toFloat())
                var seq = 0
                try {
                    animatable.animateTo(
                        targetValue = request.to.toFloat(),
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = omega * omega,
                            visibilityThreshold = 1.0f,
                        ),
                    ) {
                        if (!request.finished) {
                            val offset = value.toDouble()
                            request.onTick(offset)
                            frame = SqueezeFrame(seq = ++seq, offset = offset)
                        }
                    }
                    if (!request.finished) {
                        val finalCommit = CompletableDeferred<Unit>()
                        request.onTick(request.to)
                        frame = SqueezeFrame(seq = ++seq, offset = request.to, finalCommit = finalCommit)
                        // Await the exact onDisplay() commit callback for the resting-width Metal
                        // frame; bounded only so an off-window view can never stall cleanup.
                        withTimeoutOrNull(FINAL_SQUEEZE_COMMIT_TIMEOUT_MS) { finalCommit.await() }
                    }
                    if (!request.finished) {
                        request.finished = true
                        request.onEnd()
                    }
                } finally {
                    request.pendingFrames.clear()
                    if (state.squeezeRequest === request) {
                        state.squeezeRequest = null
                    }
                }
            }
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
        pendingRelease.forEach {
            it.state.squeezeRequest = null
            it.state.squeezeWidthPt = Double.NaN
            it.state.released = true
            it.owner.viewModelStore.clear()
        }
        pendingRelease.clear()
    }

    private fun observeSettings() {
        scope.launch {
            container.settingsRepository.settings.collect { latest ->
                settings = latest
                refreshListDetailExpanded()
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

    private var onboarding by mutableStateOf<UIViewController?>(null)

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
                    DeviceLocals(fixedBottomBarHeight) {
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
        controller.dismissViewControllerAnimated(true) {
            onboarding = null
            checkClipboardPost()
        }
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
        ) { _ ->
            notificationsViewModel.refreshIfStale()
            checkClipboardPost()
        }
    }

    private fun selectTab(destination: TopLevelDestination) {
        finishSqueezeForTabSwitch(destination)
        selectedTab = destination
        systemTabs.selectedIndex = destination.ordinal.toULong()
        updateTabBar()
        refreshActivePage()
    }

    private fun reselectTab(destination: TopLevelDestination) {
        when (destination) {
            TopLevelDestination.HOME -> homeReselectRequests++
            TopLevelDestination.NOTIFICATIONS -> notificationsScrollToTopRequests++
            TopLevelDestination.PROFILE -> Unit
        }
    }

    private fun refreshActivePage() {
        activePageState = mirrors.getValue(selectedTab).views.lastOrNull()?.state
    }

    private fun checkClipboardPost() {
        if (clipboardCheckJob?.isActive == true) return
        clipboardCheckJob =
            scope.launch {
                while (root.view.window == null) delay(16)
                val latest = container.settingsRepository.settings.first()
                // Not over the first-run guide, which is drawn above all of this and would hide the prompt.
                if (!latest.clipboardPostPrompt || !latest.onboardingSeen || onboarding != null) return@launch
                val text = IosClipboardPostReader.readNewUrlText() ?: return@launch
                val post = clipboardPostToOffer(text, container.settingsRepository.lastClipboardPostLink())
                    ?: return@launch
                container.settingsRepository.setLastClipboardPostLink(post.url)
                clipboardOffer = post
            }
    }

    private fun handleClipboardOffer(
        offer: ClipboardPost,
        open: Boolean,
    ) {
        if (clipboardOffer != offer) return
        clipboardOffer = null
        if (open) {
            stacks.getValue(selectedTab).add(PostDetailKey(offer.postId, page = offer.page))
        }
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
        @ObjCSignatureOverride
        override fun tabBarController(
            tabBarController: UITabBarController,
            shouldSelectViewController: UIViewController,
        ): Boolean {
            val destination = destinationOf(shouldSelectViewController) ?: return true
            if (shouldSelectViewController == tabBarController.selectedViewController &&
                stacks.getValue(destination).size == 1
            ) {
                reselectTab(destination)
            }
            return true
        }

        @ObjCSignatureOverride
        override fun tabBarController(
            tabBarController: UITabBarController,
            didSelectViewController: UIViewController,
        ) {
            val destination = destinationOf(didSelectViewController) ?: return
            finishSqueezeForTabSwitch(destination)
            selectedTab = destination
            updateTabBar()
            refreshActivePage()
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
        @ObjCSignatureOverride
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
        @ObjCSignatureOverride
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
            if (stacks.getValue(destination).lastOrNull() is MailComposeKey) return false
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

private const val FINAL_SQUEEZE_COMMIT_TIMEOUT_MS = 250L

@OptIn(ExperimentalComposeUiApi::class)
private val SqueezeSyncInterop = UIKitInteropProperties(
    interactionMode = UIKitInteropInteractionMode.NonCooperative,
    isNativeAccessibilityEnabled = false,
    placedAsOverlay = true,
)

/**
 * Reports the window size and the visible tab bar height after layout, so the shell can choose its
 * panes and the reading controls can share the tab bar's band.
 */
@OptIn(ExperimentalForeignApi::class)
private class ShellTabBarController(
    onLayout: (width: Double, height: Double, bottomBarHeight: Double?) -> Unit,
) : UITabBarController(nibName = null, bundle = null) {
    init {
        // iOS 26 floats its glass bar over the content and needs nothing from here. Earlier systems
        // switch to a clear bar whenever they think the content is scrolled to its edge, and they
        // cannot see a Compose list scroll, so the bar would stay clear over the posts. Keep the
        // system's blurred bar in both states instead — what those systems show mid-scroll.
        if (!nativeGlassAvailable) {
            val appearance = UITabBarAppearance().apply { configureWithDefaultBackground() }
            tabBar.standardAppearance = appearance
            tabBar.scrollEdgeAppearance = appearance
        }
    }

    private val layoutUpdates = WindowLayoutDispatcher(
        post = { update -> NSOperationQueue.mainQueue.addOperationWithBlock { update() } },
        onLayout = {
            view.bounds.useContents {
                onLayout(
                    size.width,
                    size.height,
                    bottomBarHeight(),
                )
            }
        },
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

    // Ignore the hidden bar's offscreen frame and keep the last visible height while reading.
    // A bar along the bottom is measured from its top edge down to where the reading controls'
    // band ends, so both share a centre line: iOS 26's capsule fills the bar's frame from the
    // top but reaches only part of the way into the safe area, which the frame spans to the edge.
    // An iPad's tabs float at the top (see `updateTabBar`) and this bottom bar stays hidden, so
    // there is nothing down there to line up with: the band is centred where the list's 发帖
    // button is instead, and a thread's reading controls share a line with the list beside it.
    private fun bottomBarHeight(): Double? {
        val bottom = view.bounds.useContents { size.height }
        val safeBottom = view.safeAreaInsets.useContents { this.bottom }
        val bandBottom = bottomBarInset(safeBottom.dp, bottomBarSafeAreaOverlap).value.toDouble()
        if (isPad && respondsToSelector(NSSelectorFromString("setTabBarHidden:animated:"))) {
            return 2 * (safeBottom + ListFabCentreAboveSafeArea.value - bandBottom)
        }
        if (tabBar.hidden) return null
        return tabBar.frame.useContents {
            val alongBottom = origin.y + size.height >= bottom - 0.5
            val height = if (alongBottom) bottom - origin.y - bandBottom else size.height - safeBottom
            height.takeIf { it > 0 && origin.y + size.height <= bottom }
        }
    }
}

/**
 * Whether this is an iPad: a question about the hardware rather than the window, asked only where
 * the hardware is the answer — the hand holding it, and where UIKit puts the tab bar.
 */
private val isPad: Boolean = UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad

/**
 * How far iOS 26's floating tab bar reaches into the bottom safe area: its capsule ends 21pt above
 * the edge of a 34pt home-indicator inset. The reading controls end on the same line.
 */
private val FloatingTabSafeAreaOverlap = 13.dp

private val bottomBarSafeAreaOverlap: Dp
    get() = if (nativeGlassAvailable) FloatingTabSafeAreaOverlap else 0.dp

@Composable
private fun DeviceLocals(
    fixedBottomBarHeight: Dp?,
    windowLandscape: Boolean? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalFixedBottomBarHeight provides fixedBottomBarHeight,
        LocalBottomBarSafeAreaOverlap provides bottomBarSafeAreaOverlap,
    ) {
        Box(modifier) {
            if (isPad) {
                CompositionLocalProvider(
                    LocalIosPad provides true,
                    LocalIosPadLandscape provides windowLandscape,
                    LocalOneHandMode provides false,
                    LocalOneHandModeAvailable provides false,
                    content = content,
                )
            } else {
                content()
            }
        }
    }
}

private fun paneKindOf(key: NavKey): PaneKind =
    when (paneRoleOf(key)) {
        PaneRole.LIST -> if (key.opensSplitOnSelection()) PaneKind.LIST_WHEN_DETAIL else PaneKind.LIST
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
        this is MailComposeKey ||
        this is DohSettingsKey ||
        this is StickerAddKey ||
        this is ThemeSettingsKey ||
        this is VpsCalculatorKey

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

@Composable
private fun BoxScope.ClipboardPostSnackbarOverlay(
    modifier: Modifier,
    active: Boolean,
    offer: ClipboardPost?,
    onHandled: (ClipboardPost, open: Boolean) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardPrompt = stringResource(Res.string.clipboard_post_prompt)
    val clipboardOpen = stringResource(Res.string.clipboard_post_open)
    LaunchedEffect(active, offer) {
        if (!active || offer == null) return@LaunchedEffect
        val result =
            snackbarHostState.showSnackbar(
                message = clipboardPrompt,
                actionLabel = clipboardOpen,
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
        onHandled(offer, result == SnackbarResult.ActionPerformed)
    }
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .then(modifier),
    )
}
