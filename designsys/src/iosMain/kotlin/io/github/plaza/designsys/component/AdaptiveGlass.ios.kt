@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package io.github.plaza.designsys.component

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.viewinterop.remeasureRequester
import androidx.compose.ui.viewinterop.rememberUIKitInteropRemeasureRequester
import io.github.plaza.designsys.nativeglass.plaza_configure_bar_item_style
import io.github.plaza.designsys.nativeglass.plaza_glass_button_configuration
import io.github.plaza.designsys.nativeglass.plaza_glass_effect
import io.github.plaza.designsys.nativeglass.plaza_make_bar_spinner
import io.github.plaza.designsys.nativeglass.plaza_make_subtitle_view
import io.github.plaza.designsys.nativeglass.plaza_set_bar_item_selected
import io.github.plaza.designsys.nativeglass.plaza_set_glass_capsule
import io.github.plaza.designsys.nativeglass.plaza_update_subtitle_view
import io.github.plaza.designsys.theme.LocalPlazaDarkTheme
import io.github.plaza.designsys.theme.LocalPlazaFontScale
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.nativeChrome
import io.github.plaza.designsys.theme.nativeGlass
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.NSLineBreakByTruncatingTail
import platform.UIKit.UIAccessibilityAnnouncementNotification
import platform.UIKit.UIAccessibilityPostNotification
import platform.UIKit.UIAction
import platform.UIKit.UIActivityIndicatorView
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIButton
import platform.UIKit.UIButtonConfiguration
import platform.UIKit.UIButtonConfigurationSize
import platform.UIKit.UIButtonConfigurationTitleAlignment
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIFontMetrics
import platform.UIKit.UIImage
import platform.UIKit.UIImageSymbolConfiguration
import platform.UIKit.UILabel
import platform.UIKit.UILayoutConstraintAxisHorizontal
import platform.UIKit.UILayoutConstraintAxisVertical
import platform.UIKit.UILayoutPriorityDefaultLow
import platform.UIKit.UILayoutPriorityRequired
import platform.UIKit.UIMenu
import platform.UIKit.UIMenuElementAttributesDisabled
import platform.UIKit.UIMenuElementState
import platform.UIKit.UINavigationBar
import platform.UIKit.UINavigationBarAppearance
import platform.UIKit.UINavigationItem
import platform.UIKit.UIStackView
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIView
import platform.UIKit.UIVisualEffect
import platform.UIKit.UIVisualEffectView
import platform.UIKit.accessibilityLabel
import platform.UIKit.labelColor

// Overlay placement is essential: the default UIKitView cuts a hole in the Metal canvas, so native
// glass would sample an empty rectangle instead of the Compose content scrolling behind the button.
private val NativeGlassInterop = UIKitInteropProperties(
    interactionMode = UIKitInteropInteractionMode.NonCooperative,
    isNativeAccessibilityEnabled = true,
    placedAsOverlay = true,
)

private val CooperativeOverlayInterop = UIKitInteropProperties(
    interactionMode = UIKitInteropInteractionMode.Cooperative(),
    isNativeAccessibilityEnabled = true,
    placedAsOverlay = true,
)

@Composable
actual fun AdaptiveGlassButton(action: GlassAction, modifier: Modifier, fallback: @Composable () -> Unit) {
    if (!LocalPlazaLayers.current.nativeGlass || (action.symbol == null && action.title == null)) {
        fallback()
        return
    }
    val dark = LocalPlazaDarkTheme.current
    val accent = MaterialTheme.colorScheme.primary
    val scale = LocalPlazaFontScale.current
    val host = remember { NativeGlassButtonHost(action) }
    val remeasure = rememberUIKitInteropRemeasureRequester()
    UIKitView(
        factory = { host.view },
        modifier = modifier.remeasureRequester(remeasure),
        properties = NativeGlassInterop,
        update = {
            host.update(action, accent.toUIColor(), dark, scale)
            remeasure.requestRemeasure()
        },
    )
}

@Composable
actual fun AdaptiveGlassToolbar(
    actions: List<GlassAction>,
    modifier: Modifier,
    vertical: Boolean,
    fallback: @Composable () -> Unit,
) {
    if (!LocalPlazaLayers.current.nativeGlass) {
        fallback()
        return
    }
    val dark = LocalPlazaDarkTheme.current
    val scale = LocalPlazaFontScale.current
    val host = remember(vertical) { NativeGlassToolbarHost(vertical) }
    val foreground = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = LocalPlazaLayers.current.raised.copy(alpha = if (dark) 0.50f else 0.65f)
    val remeasure = rememberUIKitInteropRemeasureRequester()
    UIKitView(
        factory = { host.view },
        modifier = modifier.remeasureRequester(remeasure),
        properties = NativeGlassInterop,
        update = {
            host.update(actions, foreground.toUIColor(), surface.toUIColor(), dark, scale)
            remeasure.requestRemeasure()
        },
    )
}

@Composable
actual fun AdaptiveGlassTopBar(
    title: String,
    titleAction: GlassAction?,
    back: GlassAction?,
    actions: List<GlassAction>,
    modifier: Modifier,
    subtitle: String?,
    containerColor: Color,
    fallback: @Composable () -> Unit,
) {
    val layers = LocalPlazaLayers.current
    if (!layers.nativeChrome) {
        fallback()
        return
    }
    val dark = LocalPlazaDarkTheme.current
    val accent = MaterialTheme.colorScheme.primary
    val scale = LocalPlazaFontScale.current
    val foreground = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val background = if (containerColor.isSpecified) containerColor else layers.page
    val host = remember { NativeTopBarHost() }
    UIKitView(
        factory = { host.bar },
        modifier = modifier,
        properties = NativeGlassInterop,
        update = {
            host.update(
                title = title,
                subtitle = subtitle,
                titleAction = titleAction,
                back = back,
                actions = actions,
                accent = accent.toUIColor(),
                foreground = foreground.toUIColor(),
                mutedForeground = muted.toUIColor(),
                background = background,
                nativeGlass = layers.nativeGlass,
                dark = dark,
                scale = scale,
            )
        },
    )
}

@Composable
actual fun AdaptiveNativeMenu(
    label: String,
    actions: List<GlassMenuAction>,
    modifier: Modifier,
    enabled: Boolean,
    cooperativeInScroll: Boolean,
    fallback: @Composable (expanded: Boolean, onDismiss: () -> Unit) -> Unit,
    trigger: @Composable (onClick: () -> Unit) -> Unit,
) {
    if (!LocalPlazaLayers.current.nativeChrome) {
        var expanded by remember { mutableStateOf(false) }
        Box(modifier) {
            trigger { if (enabled) expanded = true }
            fallback(expanded && enabled) { expanded = false }
        }
        return
    }
    val dark = LocalPlazaDarkTheme.current
    val host = remember { NativeMenuOverlayHost() }
    val active = enabled && actions.isNotEmpty()
    Box(modifier) {
        Box(if (active) Modifier.clearAndSetSemantics {} else Modifier) {
            trigger {}
        }
        if (active) {
            UIKitView(
                factory = { host.view },
                modifier = Modifier.matchParentSize(),
                properties = if (cooperativeInScroll) CooperativeOverlayInterop else NativeGlassInterop,
                update = { host.update(label, actions, dark, enabled = active) },
                onRelease = { host.dispose() },
            )
        }
    }
}

@Composable
actual fun AdaptiveGlassNotice(
    message: String,
    action: GlassAction?,
    dismissAction: GlassAction?,
    modifier: Modifier,
    fallback: @Composable () -> Unit,
) {
    if (!LocalPlazaLayers.current.nativeGlass) {
        fallback()
        return
    }
    val dark = LocalPlazaDarkTheme.current
    val accent = MaterialTheme.colorScheme.primary
    val scale = LocalPlazaFontScale.current
    val host = remember { NativeGlassNoticeHost() }
    LaunchedEffect(message) { UIAccessibilityPostNotification(UIAccessibilityAnnouncementNotification, message) }
    val remeasure = rememberUIKitInteropRemeasureRequester()
    UIKitView(
        factory = { host.view },
        modifier = modifier.remeasureRequester(remeasure),
        properties = NativeGlassInterop,
        update = {
            host.update(message, action, dismissAction, accent.toUIColor(), dark, scale)
            remeasure.requestRemeasure()
        },
    )
}

/** Keeps one UIButton while recomposition replaces its action, disabled state and menu. */
internal class NativeGlassButtonHost(initial: GlassAction, private val grouped: Boolean = false) {
    private val state = NativeGlassActionState(initial)
    val view: UIButton = UIButton.buttonWithConfiguration(
        UIButtonConfiguration.plainButtonConfiguration(),
        state.primaryAction(),
    )

    fun update(action: GlassAction, accent: UIColor, dark: Boolean, scale: Float) {
        state.action = action
        view.overrideUserInterfaceStyle = interfaceStyle(dark)
        view.tintColor = accent
        view.enabled = action.enabled && !action.loading
        view.accessibilityLabel = action.label
        view.configuration = when {
            grouped -> UIButtonConfiguration.plainButtonConfiguration()
            else -> checkNotNull(plaza_glass_button_configuration(action.prominent))
        }.apply {
            if (action.compact) buttonSize = UIButtonConfigurationSize.UIButtonConfigurationSizeSmall
            title = action.title
            subtitle = action.subtitle
            image = action.symbol?.let { UIImage.systemImageNamed(it) }
            // SF Symbols otherwise keep their large intrinsic size rather than following the text
            // transformers below; this is especially visible in a narrow vertical page rail.
            preferredSymbolConfigurationForImage = UIImageSymbolConfiguration.configurationWithPointSize(
                (
                    if (action.compact) {
                        16.0
                    } else if (grouped) {
                        17.0
                    } else {
                        20.0
                    }
                    ) * scale,
                weight = platform.UIKit.UIImageSymbolWeightRegular,
            )
            titleLineBreakMode = NSLineBreakByTruncatingTail
            subtitleLineBreakMode = NSLineBreakByTruncatingTail
            titleAlignment = UIButtonConfigurationTitleAlignment.UIButtonConfigurationTitleAlignmentCenter
            imagePadding = 6.0
            showsActivityIndicator = action.loading
            subtitleTextAttributesTransformer = { attributes ->
                attributes.orEmpty() + (
                    platform.UIKit.NSFontAttributeName to
                        UIFontMetrics.defaultMetrics.scaledFontForFont(UIFont.systemFontOfSize(10.0 * scale))
                    )
            }
            if (action.prominent) baseBackgroundColor = accent
            titleTextAttributesTransformer = { attributes ->
                val font = if (action.compact) {
                    UIFont.systemFontOfSize(13.0 * scale, weight = platform.UIKit.UIFontWeightSemibold)
                } else {
                    UIFont.systemFontOfSize(15.0 * scale)
                }
                attributes.orEmpty() + (platform.UIKit.NSFontAttributeName to UIFontMetrics.defaultMetrics.scaledFontForFont(font))
            }
        }
        val currentState = state
        val nextMenuSignature = action.menu.signatures()
        if (nextMenuSignature != lastMenuSignature) {
            lastMenuSignature = nextMenuSignature
            view.menu = action.menu.takeIf { it.isNotEmpty() }?.let { menu ->
                buildNativeMenu(menu) {
                    // The menu may still be open when an async operation disables or changes an item.
                    currentState.action.takeIf { it.enabled && !it.loading }?.menu.orEmpty()
                }
            }
        }
        view.showsMenuAsPrimaryAction = action.menu.isNotEmpty()
        view.titleLabel?.numberOfLines = 1
        view.subtitleLabel?.numberOfLines = 1
        view.invalidateIntrinsicContentSize()
    }

    private var lastMenuSignature: List<MenuActionSignature> = emptyList()
}

/** Keeps one UIBarButtonItem + UINavigationItem alive across recompositions so open menus are never interrupted. */
internal class NativeTopBarHost {
    val bar: UINavigationBar = UINavigationBar()
    internal val item: UINavigationItem = UINavigationItem(title = "")
    private var lastNativeGlass: Boolean? = null
    private var lastBackground: Color? = null
    private var lastDark: Boolean? = null

    private var leftHost: NativeBarItemHost? = null
    private var rightHosts: List<NativeBarItemHost> = emptyList()

    private val titleActionState = NativeGlassActionState(GlassAction("", {}))
    private var titleButton: UIButton? = null
    private var subtitleView: UIStackView? = null

    init {
        bar.setItems(listOf(item), animated = false)
    }

    fun update(
        title: String,
        subtitle: String?,
        titleAction: GlassAction?,
        back: GlassAction?,
        actions: List<GlassAction>,
        accent: UIColor,
        foreground: UIColor,
        mutedForeground: UIColor,
        background: Color,
        nativeGlass: Boolean,
        dark: Boolean,
        scale: Float,
    ) {
        if (nativeGlass != lastNativeGlass || background != lastBackground || dark != lastDark) {
            lastNativeGlass = nativeGlass
            lastBackground = background
            lastDark = dark
            val appearance = UINavigationBarAppearance().apply {
                if (nativeGlass) {
                    // Compose is not a UIScrollView, so automatic scroll-edge tracking cannot choose
                    // the background. Use the standard system material in both bar appearances.
                    configureWithDefaultBackground()
                } else {
                    configureWithOpaqueBackground()
                    backgroundColor = background.toUIColor()
                    shadowColor = UIColor.clearColor
                }
            }
            bar.standardAppearance = appearance
            bar.scrollEdgeAppearance = appearance
        }
        bar.overrideUserInterfaceStyle = interfaceStyle(dark)
        bar.tintColor = accent
        val scaledTitleFont = UIFontMetrics.defaultMetrics.scaledFontForFont(
            UIFont.systemFontOfSize(17.0 * scale, weight = platform.UIKit.UIFontWeightSemibold),
        )
        bar.titleTextAttributes = mapOf(
            platform.UIKit.NSFontAttributeName to scaledTitleFont,
            NSForegroundColorAttributeName to foreground,
        )

        updateTitle(
            title = title,
            subtitle = subtitle,
            titleAction = titleAction,
            foreground = foreground,
            mutedForeground = mutedForeground,
            scale = scale,
        )
        updateLeft(back = back, accent = accent, foreground = foreground, scale = scale)
        updateRight(actions = actions, accent = accent, foreground = foreground, scale = scale)
    }

    private fun updateTitle(
        title: String,
        subtitle: String?,
        titleAction: GlassAction?,
        foreground: UIColor,
        mutedForeground: UIColor,
        scale: Float,
    ) {
        if (titleAction != null) {
            item.title = title
            titleActionState.action = titleAction
            val button = titleButton ?: createTitleButton().also { titleButton = it }
            button.accessibilityLabel = titleAction.label
            button.configuration = UIButtonConfiguration.plainButtonConfiguration().apply {
                this.title = title
                baseForegroundColor = foreground
                titleLineBreakMode = NSLineBreakByTruncatingTail
                titleTextAttributesTransformer = { attributes ->
                    attributes.orEmpty() + (
                        platform.UIKit.NSFontAttributeName to
                            UIFontMetrics.defaultMetrics.scaledFontForFont(UIFont.systemFontOfSize(17.0 * scale))
                        )
                }
            }
            if (item.titleView !== button) item.titleView = button
            return
        }
        if (subtitle != null) {
            item.title = title
            val stack = subtitleView ?: plaza_make_subtitle_view().also { subtitleView = it }
            val titleFont = UIFontMetrics.defaultMetrics.scaledFontForFont(
                UIFont.systemFontOfSize(15.0 * scale, weight = platform.UIKit.UIFontWeightSemibold),
            )
            val subtitleFont = UIFontMetrics.defaultMetrics.scaledFontForFont(
                UIFont.systemFontOfSize(11.0 * scale),
            )
            plaza_update_subtitle_view(
                stack = stack,
                title = title,
                subtitle = subtitle,
                titleColor = foreground,
                subtitleColor = mutedForeground,
                titleFont = titleFont,
                subtitleFont = subtitleFont,
            )
            if (item.titleView !== stack) item.titleView = stack
            return
        }
        if (item.titleView != null) item.titleView = null
        if (item.title != title) item.title = title
    }

    private fun createTitleButton(): UIButton {
        val state = titleActionState
        return UIButton.buttonWithConfiguration(
            UIButtonConfiguration.plainButtonConfiguration(),
            UIAction.actionWithHandler {
                state.action.takeIf { it.enabled && !it.loading }?.onClick?.invoke()
            },
        ).apply {
            titleLabel?.numberOfLines = 1
            titleLabel?.lineBreakMode = NSLineBreakByTruncatingTail
            setContentCompressionResistancePriority(UILayoutPriorityDefaultLow, forAxis = UILayoutConstraintAxisHorizontal)
        }
    }

    private fun updateLeft(back: GlassAction?, accent: UIColor, foreground: UIColor, scale: Float) {
        if (back == null) {
            if (leftHost != null) {
                leftHost = null
                item.leftBarButtonItem = null
            }
            return
        }
        val existing = leftHost
        if (existing != null && existing.canReuse(back)) {
            existing.update(back, accent, foreground)
        } else {
            val host = NativeBarItemHost(back).also { it.update(back, accent, foreground) }
            leftHost = host
            item.leftBarButtonItem = host.barItem
        }
    }

    private fun updateRight(actions: List<GlassAction>, accent: UIColor, foreground: UIColor, scale: Float) {
        val reversed = actions.asReversed()
        val reusable = rightHosts.size == reversed.size &&
            rightHosts.zip(reversed).all { (host, action) -> host.canReuse(action) }
        if (reusable) {
            rightHosts.zip(reversed).forEach { (host, action) -> host.update(action, accent, foreground) }
            return
        }
        rightHosts = reversed.map { action ->
            NativeBarItemHost(action).also { it.update(action, accent, foreground) }
        }
        item.rightBarButtonItems = rightHosts.map { it.barItem }
    }
}

private enum class BarItemKind { LOADING, MENU, PLAIN }

private fun GlassAction.barItemKind(): BarItemKind = when {
    loading -> BarItemKind.LOADING
    menu.isNotEmpty() -> BarItemKind.MENU
    else -> BarItemKind.PLAIN
}

private class NativeBarItemHost(initial: GlassAction) {
    private val kind = initial.barItemKind()
    private val state = NativeGlassActionState(initial)
    private var spinner: UIActivityIndicatorView? = null
    private var lastMenuSignature: List<MenuActionSignature> = emptyList()

    val barItem: UIBarButtonItem = when (kind) {
        BarItemKind.LOADING -> {
            val indicator = plaza_make_bar_spinner(null)
            spinner = indicator
            UIBarButtonItem(customView = indicator)
        }

        BarItemKind.MENU ->
            if (initial.symbol != null) {
                UIBarButtonItem(image = UIImage.systemImageNamed(initial.symbol), menu = null)
            } else {
                UIBarButtonItem(title = initial.title ?: initial.label, menu = null)
            }

        BarItemKind.PLAIN ->
            UIBarButtonItem(primaryAction = state.primaryAction())
    }

    fun canReuse(next: GlassAction): Boolean {
        if (next.barItemKind() != kind) return false
        if (kind == BarItemKind.MENU) {
            return (next.symbol != null) == (state.action.symbol != null)
        }
        return true
    }

    fun update(action: GlassAction, accent: UIColor, foreground: UIColor) {
        state.action = action
        barItem.accessibilityLabel = action.label
        when (kind) {
            BarItemKind.LOADING -> {
                spinner?.color = foreground
            }

            BarItemKind.MENU -> {
                barItem.enabled = action.enabled
                barItem.title = action.title
                barItem.image = action.symbol?.let { UIImage.systemImageNamed(it) }
                val signature = action.menu.signatures()
                if (signature != lastMenuSignature) {
                    lastMenuSignature = signature
                    val currentState = state
                    barItem.menu = buildNativeMenu(action.menu) {
                        currentState.action.takeIf { it.enabled && !it.loading }?.menu.orEmpty()
                    }
                }
            }

            BarItemKind.PLAIN -> {
                barItem.enabled = action.enabled
                barItem.title = action.title
                barItem.image = action.symbol?.let { UIImage.systemImageNamed(it) }
                barItem.tintColor = if (action.enabled) accent else null
                plaza_configure_bar_item_style(barItem, action.prominent)
                plaza_set_bar_item_selected(barItem, action.selected)
            }
        }
    }
}

// UIMenu/UIAction retain this state, which owns no UIView; capturing the host would form an ARC cycle.
private class MenuOverlayState(
    var actions: List<GlassMenuAction> = emptyList(),
    var enabled: Boolean = true,
)

internal class NativeMenuOverlayHost {
    private val state = MenuOverlayState()
    private var lastSignature: List<MenuActionSignature> = emptyList()
    val view: UIButton = UIButton.buttonWithConfiguration(
        UIButtonConfiguration.plainButtonConfiguration(),
        UIAction.actionWithHandler {},
    ).apply {
        showsMenuAsPrimaryAction = true
    }

    fun update(
        label: String,
        nextActions: List<GlassMenuAction>,
        dark: Boolean,
        enabled: Boolean = true,
    ) {
        state.enabled = enabled
        state.actions = nextActions
        view.overrideUserInterfaceStyle = interfaceStyle(dark)
        view.accessibilityLabel = label
        view.enabled = enabled && nextActions.isNotEmpty()
        val signature = nextActions.signatures()
        if (signature != lastSignature) {
            lastSignature = signature
            val currentState = state
            view.menu = nextActions.takeIf { it.isNotEmpty() }?.let { menu ->
                buildNativeMenu(menu) { if (currentState.enabled) currentState.actions else emptyList() }
            }
        }
    }

    fun dispose() {
        state.enabled = false
        state.actions = emptyList()
    }
}

// UIAction retains this state, which owns no UIView; capturing the host would form an ARC cycle.
private class NativeGlassActionState(var action: GlassAction) {
    fun primaryAction(): UIAction =
        UIAction.actionWithHandler { if (action.enabled && !action.loading) action.onClick() }
}

private class NativeGlassToolbarHost(vertical: Boolean) {
    val view = glassView(interactive = true)
    private val stack = UIStackView().apply {
        axis = if (vertical) UILayoutConstraintAxisVertical else UILayoutConstraintAxisHorizontal
        spacing = 0.0
    }
    private val buttons = mutableListOf<NativeGlassButtonHost>()

    init {
        // UIToolbar only lays out horizontally. A UIStackView inside the official glass effect is
        // needed for the vertical page rail; remove this host if UIKit adds a vertical toolbar.
        plaza_set_glass_capsule(view, maximumRadius = 0.0)
        pin(stack, view.contentView, 4.0)
    }

    fun update(actions: List<GlassAction>, foreground: UIColor, surface: UIColor, dark: Boolean, scale: Float) {
        view.overrideUserInterfaceStyle = interfaceStyle(dark)
        view.effect = glassEffect(interactive = true, tint = surface)
        while (buttons.size > actions.size) {
            val button = buttons.removeAt(buttons.lastIndex).view
            stack.removeArrangedSubview(button)
            button.removeFromSuperview()
        }
        actions.forEachIndexed { index, action ->
            val host = buttons.getOrNull(index) ?: NativeGlassButtonHost(action, grouped = true).also {
                buttons += it
                stack.addArrangedSubview(it.view)
                it.view.heightAnchor.constraintGreaterThanOrEqualToConstant(44.0).active = true
                it.view.widthAnchor.constraintGreaterThanOrEqualToConstant(44.0).active = true
            }
            host.update(action, foreground, dark, scale)
        }
    }
}

private class NativeGlassNoticeHost {
    val view = glassView()
    private val label = UILabel().apply {
        numberOfLines = 0
        adjustsFontForContentSizeCategory = true
        setContentCompressionResistancePriority(UILayoutPriorityDefaultLow, forAxis = UILayoutConstraintAxisHorizontal)
    }
    private val stack = UIStackView().apply {
        axis = UILayoutConstraintAxisHorizontal
        spacing = 8.0
        addArrangedSubview(label)
    }
    private var buttons = emptyList<NativeGlassButtonHost>()

    init {
        plaza_set_glass_capsule(view, maximumRadius = 20.0)
        pin(stack, view.contentView, 12.0)
    }

    fun update(message: String, action: GlassAction?, dismiss: GlassAction?, accent: UIColor, dark: Boolean, scale: Float) {
        view.overrideUserInterfaceStyle = interfaceStyle(dark)
        label.text = message
        label.textColor = UIColor.labelColor
        label.font = UIFontMetrics.defaultMetrics.scaledFontForFont(UIFont.systemFontOfSize(14.0 * scale))
        val actions = listOfNotNull(action, dismiss)
        if (buttons.size != actions.size) {
            buttons.forEach {
                stack.removeArrangedSubview(it.view)
                it.view.removeFromSuperview()
            }
            buttons = actions.map {
                NativeGlassButtonHost(it, grouped = true).also { button ->
                    stack.addArrangedSubview(button.view)
                    button.view.setContentCompressionResistancePriority(UILayoutPriorityRequired, forAxis = UILayoutConstraintAxisHorizontal)
                }
            }
        }
        buttons.zip(actions).forEach { (button, spec) -> button.update(spec, accent, dark, scale) }
    }
}

private fun glassView(interactive: Boolean = false): UIVisualEffectView =
    UIVisualEffectView(effect = glassEffect(interactive))

private fun glassEffect(interactive: Boolean, tint: UIColor? = null): UIVisualEffect =
    checkNotNull(plaza_glass_effect(interactive, tint))

private fun pin(child: UIView, parent: UIView, inset: Double) {
    parent.addSubview(child)
    child.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activateConstraints(
        listOf(
            child.leadingAnchor.constraintEqualToAnchor(parent.leadingAnchor, constant = inset),
            child.trailingAnchor.constraintEqualToAnchor(parent.trailingAnchor, constant = -inset),
            child.topAnchor.constraintEqualToAnchor(parent.topAnchor, constant = inset),
            child.bottomAnchor.constraintEqualToAnchor(parent.bottomAnchor, constant = -inset),
        ),
    )
}

private data class MenuActionSignature(
    val label: String,
    val symbol: String?,
    val enabled: Boolean,
    val selected: Boolean,
)

private fun List<GlassMenuAction>.signatures(): List<MenuActionSignature> =
    map { MenuActionSignature(it.label, it.symbol, it.enabled, it.selected) }

internal fun resolveNativeMenuAction(
    current: List<GlassMenuAction>,
    index: Int,
    label: String,
): GlassMenuAction? {
    val target =
        current.getOrNull(index)?.takeIf { it.label == label }
            ?: current.firstOrNull { it.label == label }
    return target?.takeIf { it.enabled }
}

private fun buildNativeMenu(
    items: List<GlassMenuAction>,
    currentItems: () -> List<GlassMenuAction>,
): UIMenu = UIMenu.menuWithChildren(
    items.mapIndexed { index, item ->
        item.uiAction {
            resolveNativeMenuAction(currentItems(), index, item.label)?.onClick?.invoke()
        }
    },
)

private fun GlassMenuAction.uiAction(onSelect: () -> Unit): UIAction =
    UIAction.actionWithTitle(label, symbol?.let { UIImage.systemImageNamed(it) }, null) { onSelect() }.apply {
        if (!enabled) attributes = UIMenuElementAttributesDisabled
        state = if (selected) UIMenuElementState.UIMenuElementStateOn else UIMenuElementState.UIMenuElementStateOff
    }

internal fun Color.toUIColor(): UIColor =
    UIColor(red = red.toDouble(), green = green.toDouble(), blue = blue.toDouble(), alpha = alpha.toDouble())

internal fun interfaceStyle(dark: Boolean): UIUserInterfaceStyle =
    if (dark) UIUserInterfaceStyle.UIUserInterfaceStyleDark else UIUserInterfaceStyle.UIUserInterfaceStyleLight
