@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    kotlin.experimental.ExperimentalNativeApi::class,
)

package io.github.plaza.designsys.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.viewinterop.remeasureRequester
import androidx.compose.ui.viewinterop.rememberUIKitInteropRemeasureRequester
import io.github.plaza.designsys.nativeglass.plaza_glass_button_configuration
import io.github.plaza.designsys.nativeglass.plaza_glass_effect
import io.github.plaza.designsys.nativeglass.plaza_set_glass_capsule
import io.github.plaza.designsys.theme.LocalPlazaDarkTheme
import io.github.plaza.designsys.theme.LocalPlazaFontScale
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.nativeGlass
import platform.UIKit.NSDirectionalRectEdgeLeading
import platform.UIKit.NSDirectionalRectEdgeTop
import platform.UIKit.NSDirectionalRectEdgeTrailing
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.NSLineBreakByTruncatingTail
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.UIAccessibilityAnnouncementNotification
import platform.UIKit.UIAccessibilityPostNotification
import platform.UIKit.UIAccessibilityTraitButton
import platform.UIKit.UIAccessibilityTraitSelected
import platform.UIKit.UIAction
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIButton
import platform.UIKit.UIButtonConfiguration
import platform.UIKit.UIButtonConfigurationCornerStyleCapsule
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
import platform.UIKit.UIMenuElementAttributesDestructive
import platform.UIKit.UIMenuElementAttributesDisabled
import platform.UIKit.UIMenuElementState
import platform.UIKit.UINavigationBarAppearance
import platform.UIKit.UINavigationItem
import platform.UIKit.UIStackView
import platform.UIKit.UIStackViewDistributionFillEqually
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIView
import platform.UIKit.UIVisualEffect
import platform.UIKit.UIVisualEffectView
import platform.UIKit.accessibilityLabel
import platform.UIKit.accessibilityTraits
import platform.UIKit.accessibilityValue
import platform.UIKit.labelColor
import platform.UIKit.systemRedColor
import kotlin.native.ref.WeakReference

// Overlay placement is essential: the default UIKitView cuts a hole in the Metal canvas, so native
// glass would sample an empty rectangle instead of the Compose content scrolling behind the button.
private val NativeGlassInterop = UIKitInteropProperties(
    interactionMode = UIKitInteropInteractionMode.NonCooperative,
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
        modifier = modifier.remeasureRequester(remeasure).clipNativeToCompose(host.view),
        properties = NativeGlassInterop,
        update = {
            host.update(action, accent.toUIColor(), dark, scale)
            remeasure.requestRemeasure()
        },
        onRelease = { host.dispose() },
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
        modifier = modifier.remeasureRequester(remeasure).clipNativeToCompose(host.view),
        properties = NativeGlassInterop,
        update = {
            host.update(actions, foreground.toUIColor(), surface.toUIColor(), dark, scale)
            remeasure.requestRemeasure()
        },
        onRelease = { host.dispose() },
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
    fallback: @Composable () -> Unit,
) {
    if (!LocalPlazaLayers.current.nativeGlass) {
        fallback()
        return
    }
    val dark = LocalPlazaDarkTheme.current
    val accent = MaterialTheme.colorScheme.primary
    val scale = LocalPlazaFontScale.current
    val currentBack by rememberUpdatedState(back)
    val currentActions by rememberUpdatedState(actions)
    val currentTitleAction by rememberUpdatedState(titleAction)
    val foreground = MaterialTheme.colorScheme.onSurface
    val focus = remember { NativeMenuFocus() }
    UIKitView(
        factory = {
            FocusReleasingNavigationBar(focus).apply {
                // Compose is not a UIScrollView, so automatic scroll-edge tracking cannot choose
                // the background. Use the standard system material in both bar appearances.
                val appearance = UINavigationBarAppearance().apply { configureWithDefaultBackground() }
                standardAppearance = appearance
                scrollEdgeAppearance = appearance
            }
        },
        modifier = modifier,
        properties = NativeGlassInterop,
        update = { bar ->
            bar.overrideUserInterfaceStyle = interfaceStyle(dark)
            bar.tintColor = accent.toUIColor()
            bar.titleTextAttributes = mapOf(
                platform.UIKit.NSFontAttributeName to
                    UIFontMetrics.defaultMetrics.scaledFontForFont(UIFont.systemFontOfSize(17.0 * scale)),
            )
            val item = UINavigationItem(title = title)
            // UIKit's own iOS 26 subtitle; this bar only runs where native glass does, which is 26+.
            item.subtitle = subtitle
            item.leftBarButtonItem = back?.barItem { currentBack }
            val anchor = WeakReference<UIView>(bar)
            item.rightBarButtonItems = actions.asReversed().map { action ->
                action.barItem(anchor) { currentActions.firstOrNull { it.label == action.label } }
            }
            if (titleAction != null) {
                item.titleView = UIButton.buttonWithConfiguration(
                    UIButtonConfiguration.plainButtonConfiguration().apply {
                        this.title = title
                        baseForegroundColor = foreground.toUIColor()
                        titleLineBreakMode = NSLineBreakByTruncatingTail
                        titleTextAttributesTransformer = { attributes ->
                            attributes.orEmpty() + (
                                platform.UIKit.NSFontAttributeName to
                                    UIFontMetrics.defaultMetrics.scaledFontForFont(UIFont.systemFontOfSize(17.0 * scale))
                                )
                        }
                    },
                    UIAction.actionWithHandler { currentTitleAction?.onClick?.invoke() },
                ).apply {
                    accessibilityLabel = titleAction.label
                    titleLabel?.numberOfLines = 1
                    titleLabel?.lineBreakMode = NSLineBreakByTruncatingTail
                    setContentCompressionResistancePriority(UILayoutPriorityDefaultLow, forAxis = UILayoutConstraintAxisHorizontal)
                }
            }
            bar.setItems(listOf(item), animated = false)
        },
        onRelease = {
            focus.restore()
            NativeMenuShield.lower()
        },
    )
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
        modifier = modifier.remeasureRequester(remeasure).clipNativeToCompose(host.view),
        properties = NativeGlassInterop,
        update = {
            host.update(message, action, dismissAction, accent.toUIColor(), dark, scale)
            remeasure.requestRemeasure()
        },
        onRelease = { host.dispose() },
    )
}

/** The contents of one tab in a native glass capsule positioned by its Compose host. */
data class NativeGlassTab(
    val label: String,
    val symbol: String,
    val selected: Boolean,
    val badge: Int?,
    val onClick: () -> Unit,
)

@Composable
fun NativeGlassTabBar(tabs: List<NativeGlassTab>, modifier: Modifier = Modifier) {
    val dark = LocalPlazaDarkTheme.current
    val colors = MaterialTheme.colorScheme
    val tint = LocalPlazaLayers.current.raised.copy(alpha = if (dark) 0.50f else 0.65f)
    val host = remember(tabs.size) { NativeGlassTabBarHost(tabs.size) }
    UIKitView(
        factory = { host.view },
        modifier = modifier.clipNativeToCompose(host.view),
        properties = NativeGlassInterop,
        update = {
            host.view.overrideUserInterfaceStyle = interfaceStyle(dark)
            host.view.effect = glassEffect(interactive = true, tint = tint.toUIColor())
            host.buttons.zip(tabs).forEach { (button, tab) ->
                button.update(
                    tab,
                    foreground = (if (tab.selected) colors.primary else colors.onSurface).toUIColor(),
                    selection = colors.secondaryContainer.copy(alpha = if (dark) 0.56f else 0.68f).toUIColor(),
                    badgeColor = colors.error.toUIColor(),
                    badgeTextColor = colors.onError.toUIColor(),
                )
            }
        },
    )
}

private class NativeGlassTabBarHost(size: Int) {
    val view = glassView(interactive = true)
    val buttons = List(size) { NativeGlassTabButton() }

    init {
        plaza_set_glass_capsule(view, maximumRadius = 0.0)
        val stack = UIStackView().apply {
            axis = UILayoutConstraintAxisHorizontal
            distribution = UIStackViewDistributionFillEqually
            spacing = 4.0
            buttons.forEach { addArrangedSubview(it.view) }
        }
        pin(stack, view.contentView, 4.0)
    }
}

private class NativeGlassTabButton {
    private val action = NativeGlassActionState(GlassAction(label = "", onClick = {}))
    val view = UIButton.buttonWithConfiguration(UIButtonConfiguration.plainButtonConfiguration(), action.primaryAction())
    private val badge = UILabel().apply {
        userInteractionEnabled = false
        textAlignment = NSTextAlignmentCenter
        font = UIFont.systemFontOfSize(10.0, weight = platform.UIKit.UIFontWeightSemibold)
        layer.cornerRadius = 8.0
        clipsToBounds = true
        translatesAutoresizingMaskIntoConstraints = false
    }

    init {
        view.addSubview(badge)
        NSLayoutConstraint.activateConstraints(
            listOf(
                badge.heightAnchor.constraintEqualToConstant(16.0),
                badge.widthAnchor.constraintGreaterThanOrEqualToConstant(22.0),
                badge.trailingAnchor.constraintEqualToAnchor(view.trailingAnchor, constant = -4.0),
                badge.topAnchor.constraintEqualToAnchor(view.topAnchor, constant = 2.0),
            ),
        )
    }

    fun update(tab: NativeGlassTab, foreground: UIColor, selection: UIColor, badgeColor: UIColor, badgeTextColor: UIColor) {
        action.action = GlassAction(tab.label, tab.onClick)
        view.accessibilityLabel = tab.label
        view.accessibilityTraits = UIAccessibilityTraitButton or (if (tab.selected) UIAccessibilityTraitSelected else 0uL)
        view.accessibilityValue = tab.badge?.toString()
        view.configuration = UIButtonConfiguration.plainButtonConfiguration().apply {
            title = tab.label
            titleLineBreakMode = NSLineBreakByTruncatingTail
            image = UIImage.systemImageNamed(tab.symbol)
            imagePlacement = NSDirectionalRectEdgeTop
            imagePadding = 2.0
            preferredSymbolConfigurationForImage = UIImageSymbolConfiguration.configurationWithPointSize(
                20.0,
                weight = platform.UIKit.UIImageSymbolWeightRegular,
            )
            titleTextAttributesTransformer = { attributes ->
                attributes.orEmpty() + (
                    platform.UIKit.NSFontAttributeName to
                        UIFont.systemFontOfSize(11.0, weight = platform.UIKit.UIFontWeightSemibold)
                    )
            }
            cornerStyle = UIButtonConfigurationCornerStyleCapsule
            baseForegroundColor = foreground
            if (tab.selected) background = background.apply { backgroundColor = selection }
        }
        view.titleLabel?.numberOfLines = 1
        badge.hidden = tab.badge == null
        badge.text = tab.badge?.coerceAtMost(99)?.toString()
        badge.backgroundColor = badgeColor
        badge.textColor = badgeTextColor
    }
}

/** Keeps one UIButton while recomposition replaces its action, disabled state and menu. */
internal class NativeGlassButtonHost(initial: GlassAction, private val grouped: Boolean = false) {
    private val state = NativeGlassActionState(initial)
    private val focus = NativeMenuFocus()
    val view: UIButton = UIButton.buttonWithConfiguration(
        UIButtonConfiguration.plainButtonConfiguration(),
        state.primaryAction(),
    )
    private val badge = UIView().apply {
        userInteractionEnabled = false
        backgroundColor = UIColor.systemRedColor
        layer.cornerRadius = BADGE_SIZE / 2
        hidden = true
        translatesAutoresizingMaskIntoConstraints = false
    }

    init {
        view.releasesFocusForMenu(focus)
        view.addSubview(badge)
        NSLayoutConstraint.activateConstraints(
            listOf(
                badge.widthAnchor.constraintEqualToConstant(BADGE_SIZE),
                badge.heightAnchor.constraintEqualToConstant(BADGE_SIZE),
                badge.trailingAnchor.constraintEqualToAnchor(view.trailingAnchor, constant = -6.0),
                badge.topAnchor.constraintEqualToAnchor(view.topAnchor, constant = 6.0),
            ),
        )
    }

    // The menu is the button's own, so it may only reach back to the button weakly.
    private val anchor = WeakReference<UIView>(view)

    fun dispose() {
        focus.restore()
        NativeMenuShield.lower()
    }

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
            // A titled button that opens a menu is a pull-down: a small chevron after the text.
            val pullDown = action.menu.isNotEmpty() && action.title != null
            imagePlacement = if (pullDown) NSDirectionalRectEdgeTrailing else NSDirectionalRectEdgeLeading
            // SF Symbols otherwise keep their large intrinsic size rather than following the text
            // transformers below; this is especially visible in a narrow vertical page rail.
            preferredSymbolConfigurationForImage = UIImageSymbolConfiguration.configurationWithPointSize(
                (
                    if (pullDown) {
                        13.0
                    } else if (action.compact) {
                        16.0
                    } else if (grouped) {
                        17.0
                    } else {
                        17.0
                    }
                    ) * scale,
                weight = if (pullDown) platform.UIKit.UIImageSymbolWeightSemibold else platform.UIKit.UIImageSymbolWeightRegular,
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
        view.menu = action.menu.takeIf { it.isNotEmpty() }?.let { menu ->
            UIMenu.menuWithTitle(
                title = action.menuTitle.orEmpty(),
                children = listOf(NativeMenuShield.element(anchor::get)) + menu.map { item ->
                    item.uiAction {
                        // The menu may still be open when an async operation disables or changes an item.
                        currentState.action.takeIf { it.enabled && !it.loading }
                            ?.menu?.firstOrNull { it.label == item.label && it.enabled }?.onClick?.invoke()
                    }
                },
            )
        }
        view.showsMenuAsPrimaryAction = action.menu.isNotEmpty()
        badge.hidden = !action.badge
        // A configuration change can insert its background above earlier subviews.
        view.bringSubviewToFront(badge)
        view.titleLabel?.numberOfLines = 1
        view.subtitleLabel?.numberOfLines = 1
        view.invalidateIntrinsicContentSize()
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
            val host = buttons.removeAt(buttons.lastIndex)
            host.dispose()
            stack.removeArrangedSubview(host.view)
            host.view.removeFromSuperview()
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

    fun dispose() = buttons.forEach { it.dispose() }
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
                it.dispose()
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

    fun dispose() = buttons.forEach { it.dispose() }
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

private fun GlassAction.barItem(
    anchor: WeakReference<UIView>? = null,
    current: () -> GlassAction?,
): UIBarButtonItem {
    val nativeMenu = menu.takeIf { it.isNotEmpty() }?.let { items ->
        UIMenu.menuWithTitle(
            title = menuTitle.orEmpty(),
            children = listOfNotNull(anchor?.let { NativeMenuShield.element(it::get) }) + items.map { item ->
                item.uiAction { current()?.menu?.firstOrNull { it.label == item.label && it.enabled }?.onClick?.invoke() }
            },
        )
    }
    return if (nativeMenu != null) {
        UIBarButtonItem(image = symbol?.let { UIImage.systemImageNamed(it) }, menu = nativeMenu)
    } else {
        UIBarButtonItem(primaryAction = UIAction.actionWithTitle(label, symbol?.let { UIImage.systemImageNamed(it) }, null) { current()?.takeIf { it.enabled && !it.loading }?.onClick?.invoke() })
    }.apply {
        enabled = this@barItem.enabled
        accessibilityLabel = label
    }
}

private fun GlassMenuAction.uiAction(onSelect: () -> Unit): UIAction =
    UIAction.actionWithTitle(label, symbol?.let { UIImage.systemImageNamed(it) }, null) {
        NativeMenuShield.lower()
        onSelect()
    }.apply {
        this@uiAction.subtitle?.let { subtitle = it }
        attributes = (if (enabled) 0uL else UIMenuElementAttributesDisabled) or
            (if (destructive) UIMenuElementAttributesDestructive else 0uL)
        state = if (selected) UIMenuElementState.UIMenuElementStateOn else UIMenuElementState.UIMenuElementStateOff
    }

private const val BADGE_SIZE = 8.0

internal fun Color.toUIColor(): UIColor = UIColor(red = red.toDouble(), green = green.toDouble(), blue = blue.toDouble(), alpha = alpha.toDouble())

internal fun interfaceStyle(dark: Boolean): UIUserInterfaceStyle =
    if (dark) UIUserInterfaceStyle.UIUserInterfaceStyleDark else UIUserInterfaceStyle.UIUserInterfaceStyleLight
