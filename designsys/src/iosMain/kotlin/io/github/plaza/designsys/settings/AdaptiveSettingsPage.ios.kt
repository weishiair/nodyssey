@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package io.github.plaza.designsys.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import io.github.plaza.designsys.component.NativeMenuFocus
import io.github.plaza.designsys.component.SfSymbolByIconName
import io.github.plaza.designsys.component.interfaceStyle
import io.github.plaza.designsys.component.releasesFocusForMenu
import io.github.plaza.designsys.component.toUIColor
import io.github.plaza.designsys.theme.LocalEinkMode
import io.github.plaza.designsys.theme.LocalPlazaDarkTheme
import io.github.plaza.designsys.theme.LocalPlazaFontScale
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.LocalReadableWidthSpec
import io.github.plaza.designsys.theme.ReadableWidthSpec
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGRectZero
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSIndexPath
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSValue
import platform.Foundation.setValue
import platform.UIKit.CGRectValue
import platform.UIKit.NSDirectionalEdgeInsetsMake
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.NSLineBreakByTruncatingTail
import platform.UIKit.NSLineBreakByWordWrapping
import platform.UIKit.NSTextAlignmentRight
import platform.UIKit.UIAccessibilityTraitStaticText
import platform.UIKit.UIAction
import platform.UIKit.UIActivityIndicatorView
import platform.UIKit.UIActivityIndicatorViewStyleMedium
import platform.UIKit.UIApplication
import platform.UIKit.UIBarPositionTopAttached
import platform.UIKit.UIBarPositioningProtocol
import platform.UIKit.UIBlurEffect
import platform.UIKit.UIBlurEffectStyle
import platform.UIKit.UIButton
import platform.UIKit.UIButtonConfiguration
import platform.UIKit.UIColor
import platform.UIKit.UIContentSizeCategoryIsAccessibilityCategory
import platform.UIKit.UIControlContentHorizontalAlignmentLeading
import platform.UIKit.UIControlEventEditingChanged
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlEventTouchUpOutside
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UIEdgeInsetsMake
import platform.UIKit.UIFont
import platform.UIKit.UIFontMetrics
import platform.UIKit.UIFontWeightSemibold
import platform.UIKit.UIImage
import platform.UIKit.UIImageRenderingMode
import platform.UIKit.UIImageView
import platform.UIKit.UIKeyboardFrameEndUserInfoKey
import platform.UIKit.UIKeyboardTypeDecimalPad
import platform.UIKit.UIKeyboardTypeDefault
import platform.UIKit.UIKeyboardTypeURL
import platform.UIKit.UIKeyboardWillChangeFrameNotification
import platform.UIKit.UILabel
import platform.UIKit.UILayoutConstraintAxisHorizontal
import platform.UIKit.UILayoutConstraintAxisVertical
import platform.UIKit.UILayoutPriorityRequired
import platform.UIKit.UIListContentConfiguration
import platform.UIKit.UIListContentTextAlignment
import platform.UIKit.UIMenu
import platform.UIKit.UIMenuElementAttributesDisabled
import platform.UIKit.UIMenuElementState
import platform.UIKit.UINavigationBar
import platform.UIKit.UINavigationBarAppearance
import platform.UIKit.UINavigationBarDelegateProtocol
import platform.UIKit.UINavigationItem
import platform.UIKit.UINavigationItemBackButtonDisplayModeMinimal
import platform.UIKit.UIReturnKeyType
import platform.UIKit.UIScrollView
import platform.UIKit.UISegmentedControl
import platform.UIKit.UISlider
import platform.UIKit.UIStackView
import platform.UIKit.UIStackViewAlignmentCenter
import platform.UIKit.UIStackViewAlignmentFirstBaseline
import platform.UIKit.UIStackViewAlignmentTop
import platform.UIKit.UISwitch
import platform.UIKit.UITableView
import platform.UIKit.UITableViewCell
import platform.UIKit.UITableViewCellAccessoryType
import platform.UIKit.UITableViewCellSelectionStyle
import platform.UIKit.UITableViewCellStyle
import platform.UIKit.UITableViewDataSourceProtocol
import platform.UIKit.UITableViewDelegateProtocol
import platform.UIKit.UITableViewScrollPosition
import platform.UIKit.UITableViewStyle
import platform.UIKit.UITextField
import platform.UIKit.UITextFieldDelegateProtocol
import platform.UIKit.UITextFieldViewMode
import platform.UIKit.UIView
import platform.UIKit.UIViewContentMode
import platform.UIKit.UIVisualEffectView
import platform.UIKit.labelColor
import platform.UIKit.row
import platform.UIKit.secondaryLabelColor
import platform.UIKit.section
import platform.UIKit.systemRedColor
import platform.UIKit.tertiaryLabelColor
import platform.darwin.NSInteger
import platform.darwin.NSObject
import kotlin.math.abs

// One view for the whole page, under the Compose canvas (not `placedAsOverlay`), so that whatever the
// shell draws over a page — the clipboard offer, the update reminder — still lands on top of it.
// UIKit takes every touch inside it: the table scrolls, highlights and opens menus by itself.
private val SettingsPageInterop =
    UIKitInteropProperties(
        interactionMode = UIKitInteropInteractionMode.NonCooperative,
        isNativeAccessibilityEnabled = true,
    )

actual val nativeSettingsPages: Boolean = true

@Composable
actual fun AdaptiveSettingsPage(
    page: SettingsPage,
    onBack: () -> Unit,
    modifier: Modifier,
    fallback: @Composable () -> Unit,
) {
    if (LocalEinkMode.current) {
        fallback()
        return
    }
    // Compose follows Dynamic Type through the density's font scale, so reading it is what brings a
    // change of text size back here; the category itself is the system's to say.
    val systemFontScale = LocalDensity.current.fontScale
    val largeText =
        remember(systemFontScale) {
            UIContentSizeCategoryIsAccessibilityCategory(UIApplication.sharedApplication.preferredContentSizeCategory)
        }
    val layers = LocalPlazaLayers.current
    val dark = LocalPlazaDarkTheme.current
    // In pure white, Compose separates white cards with shadows. Give the table a slight contrast instead.
    val pageColor =
        if (!dark && layers.page == layers.card) Color.Black.copy(alpha = 0.03f).compositeOver(layers.page) else layers.page
    val palette =
        NativeSettingsPalette(
            page = pageColor.toUIColor(),
            card = layers.card.toUIColor(),
            accent = MaterialTheme.colorScheme.primary.toUIColor(),
            dark = dark,
            scale = LocalPlazaFontScale.current.toDouble(),
            largeText = largeText,
        )
    val readableSpec = LocalReadableWidthSpec.current
    val host = remember { NativeSettingsPageHost() }
    UIKitView(
        factory = { host.view },
        // The interop view can receive a zero UIKit top inset even under the status bar.
        modifier =
        modifier
            .fillMaxSize()
            .background(pageColor)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
        properties = SettingsPageInterop,
        update = { host.update(page, onBack, palette, readableSpec) },
        onRelease = { host.dispose() },
    )
}

internal data class NativeSettingsPalette(
    val page: UIColor,
    val card: UIColor,
    val accent: UIColor,
    val dark: Boolean,
    val scale: Double,
    /** An accessibility text size: rows stack their controls under the question, as 设置 does. */
    val largeText: Boolean,
) {
    val text: UIColor get() = UIColor.labelColor
    val secondaryText: UIColor get() = UIColor.secondaryLabelColor
    val disabledText: UIColor get() = UIColor.tertiaryLabelColor

    fun iconTint(enabled: Boolean): UIColor = if (enabled) accent else disabledText
}

private fun NativeSettingsPalette.font(size: Double, semibold: Boolean = false): UIFont {
    val base =
        if (semibold) UIFont.systemFontOfSize(size * scale, weight = UIFontWeightSemibold) else UIFont.systemFontOfSize(size * scale)
    return UIFontMetrics.defaultMetrics.scaledFontForFont(base)
}

private class NativeSettingsSection(
    val header: String?,
    val footer: String?,
    val rows: List<NativeSettingsRow>,
)

/** What the table's data source and the bar's delegate read; it owns the cells and no other view. */
private class NativeSettingsState {
    var sections: List<NativeSettingsSection> = emptyList()
    var onBack: () -> Unit = {}
    var onScroll: (UIScrollView) -> Unit = {}

    fun row(indexPath: NSIndexPath): NativeSettingsRow = sections[indexPath.section.toInt()].rows[indexPath.row.toInt()]
}

internal class NativeSettingsView : UIView(frame = CGRectZero.readValue()) {
    var onBeforeLayout: (() -> Unit)? = null
    var onLayout: (() -> Unit)? = null

    override fun layoutSubviews() {
        onBeforeLayout?.invoke()
        super.layoutSubviews()
        onLayout?.invoke()
    }
}

internal class NativeSettingsPageHost {
    val view = NativeSettingsView()
    private val state = NativeSettingsState()
    private val source = NativeSettingsSource(state)
    private val barDelegate = NativeSettingsBarDelegate(state)
    internal val barBackdrop = UIVisualEffectView(effect = null)
    internal val bar = UINavigationBar()
    internal val table = UITableView(frame = CGRectZero.readValue(), style = UITableViewStyle.UITableViewStyleInsetGrouped)
    private val titleItem = UINavigationItem(title = "")
    private val rows = mutableMapOf<String, NativeSettingsRow>()
    private val focus = NativeMenuFocus()
    private val barLeadingConstraint = bar.leadingAnchor.constraintEqualToAnchor(view.leadingAnchor)
    private val barTrailingConstraint = bar.trailingAnchor.constraintEqualToAnchor(view.trailingAnchor)
    private val tableLeadingConstraint = table.leadingAnchor.constraintEqualToAnchor(view.leadingAnchor)
    private val tableTrailingConstraint = table.trailingAnchor.constraintEqualToAnchor(view.trailingAnchor)
    private val defaultTableMargins = table.directionalLayoutMargins
    private var shape: List<SettingsSectionShape>? = null
    private var palette: NativeSettingsPalette? = null
    private var readableSpec: ReadableWidthSpec = ReadableWidthSpec.Default
    private var showRowIcons = false
    private var scrolled = false
    private var keyboardObserver: Any? = null

    init {
        // A bar of its own rather than the navigation controller's: the shell keeps that one hidden,
        // because every Compose page draws its own. Two items give the system back button, chevron and
        // all; the tap is handed to the back stack in [NativeSettingsBarDelegate] instead of popping.
        val backItem = UINavigationItem(title = "").apply { backButtonDisplayMode = UINavigationItemBackButtonDisplayModeMinimal }
        bar.setItems(listOf(backItem, titleItem), animated = false)
        bar.delegate = barDelegate
        table.dataSource = source
        table.delegate = source
        table.cellLayoutMarginsFollowReadableWidth = true
        state.onScroll = ::onScroll
        barBackdrop.userInteractionEnabled = false
        view.addSubview(table)
        view.addSubview(barBackdrop)
        view.addSubview(bar)
        barBackdrop.translatesAutoresizingMaskIntoConstraints = false
        bar.translatesAutoresizingMaskIntoConstraints = false
        table.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activateConstraints(
            listOf(
                barBackdrop.topAnchor.constraintEqualToAnchor(view.topAnchor),
                barBackdrop.leadingAnchor.constraintEqualToAnchor(view.leadingAnchor),
                barBackdrop.trailingAnchor.constraintEqualToAnchor(view.trailingAnchor),
                barBackdrop.bottomAnchor.constraintEqualToAnchor(bar.bottomAnchor),
                bar.topAnchor.constraintEqualToAnchor(view.safeAreaLayoutGuide.topAnchor),
                barLeadingConstraint,
                barTrailingConstraint,
                table.topAnchor.constraintEqualToAnchor(bar.topAnchor),
                tableLeadingConstraint,
                tableTrailingConstraint,
                table.bottomAnchor.constraintEqualToAnchor(view.bottomAnchor),
            ),
        )
        view.onBeforeLayout = { updateColumnInsets(requestLayout = false) }
        view.onLayout = ::updateTopInset
        keyboardObserver =
            NSNotificationCenter.defaultCenter.addObserverForName(
                name = UIKeyboardWillChangeFrameNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) { note ->
                val end = (note?.userInfo?.get(UIKeyboardFrameEndUserInfoKey) as? NSValue)?.CGRectValue() ?: return@addObserverForName
                val keyboard = view.convertRect(end, fromView = null)
                val bottom =
                    view.bounds.useContents {
                        val kbTop = keyboard.useContents { this.origin.y }
                        maxOf(0.0, this.size.height - kbTop)
                    }
                table.contentInset = table.contentInset.useContents { UIEdgeInsetsMake(top, left, bottom, right) }
                table.scrollIndicatorInsets = table.scrollIndicatorInsets.useContents { UIEdgeInsetsMake(top, left, bottom, right) }
            }
    }

    fun update(
        page: SettingsPage,
        onBack: () -> Unit,
        palette: NativeSettingsPalette,
        readableSpec: ReadableWidthSpec,
    ) {
        state.onBack = onBack
        if (readableSpec != this.readableSpec) {
            this.readableSpec = readableSpec
            updateColumnInsets(requestLayout = true)
        }
        if (palette.largeText != this.palette?.largeText || page.showRowIconsOnIos != showRowIcons) {
            // Text size and leading icons determine the layout of rows with custom controls.
            showRowIcons = page.showRowIconsOnIos
            rows.clear()
            shape = null
        }
        if (palette != this.palette) {
            this.palette = palette
            view.overrideUserInterfaceStyle = interfaceStyle(palette.dark)
            view.backgroundColor = palette.page
            table.backgroundColor = palette.page
            bar.tintColor = palette.accent
            applyBarAppearance()
        }
        if (titleItem.title != page.title) titleItem.title = page.title

        val live = mutableSetOf<String>()
        var resized = false
        val sections =
            page.sections.map { section ->
                NativeSettingsSection(
                    header = section.header,
                    footer = section.footer,
                    rows =
                    section.items.map { listed ->
                        // A custom row is a Compose drawing; the system list draws its native half.
                        val item = listed.nativeItem
                        val id = "${listed.rowKind()}:${item.key}"
                        live += id
                        rows.getOrPut(id) { NativeSettingsRow(item, palette.largeText, focus, showRowIcons) }
                            .also { resized = it.update(item, palette) || resized }
                    },
                )
            }
        // The page's introduction as the footer of an empty first group: the system list's own place
        // for a paragraph of explanation, set in the same small print.
        state.sections = listOfNotNull(page.intro?.let { NativeSettingsSection(header = null, footer = it, rows = emptyList()) }) + sections
        rows.keys.retainAll(live)
        // Text rows chain their return keys in page order: Next until the last field, which resigns.
        val textRows = state.sections.flatMap { it.rows }.filter { it.isTextRow }
        textRows.forEachIndexed { index, row ->
            val next = textRows.getOrNull(index + 1)
            row.nextTextField = { next?.textField }
            row.textField?.returnKeyType =
                if (next != null) UIReturnKeyType.UIReturnKeyNext else UIReturnKeyType.UIReturnKeyDone
            row.scrollToMe = {
                table.indexPathForCell(row.cell)?.let { ip ->
                    table.scrollToRowAtIndexPath(ip, UITableViewScrollPosition.UITableViewScrollPositionMiddle, animated = true)
                }
            }
        }

        val nextShape = page.shape()
        if (nextShape != shape) {
            shape = nextShape
            table.reloadData()
        } else if (resized) {
            // A longer subtitle or a count with more digits: let the rows on screen take their new
            // heights without reloading them, which would cut a switch's animation short.
            UIView.performWithoutAnimation {
                table.beginUpdates()
                table.endUpdates()
            }
        }
    }

    fun dispose() {
        view.onBeforeLayout = null
        view.onLayout = null
        state.onScroll = {}
        keyboardObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        keyboardObserver = null
        focus.restore()
        table.dataSource = null
        table.delegate = null
        bar.delegate = null
        rows.clear()
        state.sections = emptyList()
    }

    private fun updateColumnInsets(requestLayout: Boolean) {
        val (width, height) = view.bounds.useContents { size.width to size.height }
        if (width <= 0.0 || height <= 0.0) return
        val (safeLeft, safeRight) = view.safeAreaInsets.useContents { left to right }
        val safeWidth = (width - safeLeft - safeRight).coerceAtLeast(0.0)
        val targetWidth = readableSpec.resolveWidth(safeWidth.dp, height.dp).value.toDouble()
        val side = ((safeWidth - targetWidth) / 2.0).coerceAtLeast(0.0)
        val centered = side > 0.5
        val leading = if (centered) safeLeft + side else 0.0
        val trailing = if (centered) -(safeRight + side) else 0.0
        val followReadable = !centered
        var changed = false
        if (abs(barLeadingConstraint.constant - leading) > 0.5) {
            barLeadingConstraint.constant = leading
            tableLeadingConstraint.constant = leading
            changed = true
        }
        if (abs(barTrailingConstraint.constant - trailing) > 0.5) {
            barTrailingConstraint.constant = trailing
            tableTrailingConstraint.constant = trailing
            changed = true
        }
        if (table.cellLayoutMarginsFollowReadableWidth != followReadable) {
            table.cellLayoutMarginsFollowReadableWidth = followReadable
            table.preservesSuperviewLayoutMargins = followReadable
            // Inset-grouped sections lose their rounded corners when superview margins shrink to
            // zero inside an inset frame; restore the standard 16pt gutter to match Compose cards.
            table.directionalLayoutMargins =
                if (centered) {
                    NSDirectionalEdgeInsetsMake(0.0, CENTERED_TABLE_GUTTER, 0.0, CENTERED_TABLE_GUTTER)
                } else {
                    defaultTableMargins
                }
            table.setNeedsLayout()
            changed = true
        }
        if (changed && requestLayout) view.setNeedsLayout()
    }

    private fun updateTopInset() {
        val height = bar.bounds.useContents { size.height }
        if (height == table.contentInset.useContents { top }) return
        val wasAtTop = table.contentOffset.useContents { y } <= -table.adjustedContentInset.useContents { top } + 0.5
        table.contentInset = table.contentInset.useContents { UIEdgeInsetsMake(height, left, bottom, right) }
        table.scrollIndicatorInsets = table.scrollIndicatorInsets.useContents { UIEdgeInsetsMake(height, left, bottom, right) }
        if (wasAtTop) {
            val top = table.adjustedContentInset.useContents { top }
            table.setContentOffset(CGPointMake(table.contentOffset.useContents { x }, -top), animated = false)
        }
    }

    /** Flush with the page at the top, the system bar material once rows pass under it — 设置's own behaviour. */
    private fun onScroll(scrollView: UIScrollView) {
        val offset = scrollView.contentOffset.useContents { y } + scrollView.adjustedContentInset.useContents { top }
        val next = offset > 0.5
        if (next != scrolled) {
            scrolled = next
            applyBarAppearance()
        }
    }

    private fun applyBarAppearance() {
        val palette = palette ?: return
        barBackdrop.effect =
            if (scrolled) {
                // Full-width chrome material so the bar stays centred over the column without a blur seam.
                UIBlurEffect.effectWithStyle(UIBlurEffectStyle.UIBlurEffectStyleSystemChromeMaterial)
            } else {
                null
            }
        val appearance =
            UINavigationBarAppearance().apply {
                configureWithTransparentBackground()
                titleTextAttributes =
                    mapOf(
                        NSFontAttributeName to palette.font(17.0, semibold = true),
                        NSForegroundColorAttributeName to palette.text,
                    )
            }
        bar.standardAppearance = appearance
        bar.scrollEdgeAppearance = appearance
    }
}

/** The widest a pop-up answer may grow beside its question, before the in-app text size. */
private const val PICKER_MAX_WIDTH = 170.0
private const val CENTERED_TABLE_GUTTER = 16.0
private const val ROW_ICON_SIZE = 22.0
private const val ROW_ICON_SPACING = 12.0

/** The newest description of a row. UIAction handlers read it at the tap rather than capturing an old one. */
private class NativeSettingsRowState(var item: SettingsItem)

private fun stack(views: List<UIView>, vertical: Boolean, spacing: Double): UIStackView =
    UIStackView(arrangedSubviews = views).apply {
        axis = if (vertical) UILayoutConstraintAxisVertical else UILayoutConstraintAxisHorizontal
        this.spacing = spacing
        translatesAutoresizingMaskIntoConstraints = false
    }

private fun multilineLabel(): UILabel =
    UILabel().apply {
        numberOfLines = 0
        adjustsFontForContentSizeCategory = true
    }

/**
 * Segments only while they fit: more than four answers, or an accessibility text size, and the answers
 * go to a menu the way 设置 itself moves a crowded picker into one.
 */
private fun SettingsItem.Choice.drawsSegments(largeText: Boolean): Boolean =
    presentation == SettingsChoicePresentation.Segmented && options.size <= 4 && !largeText

/**
 * One cell per row, kept for the life of the page rather than dequeued: a settings page has a dozen
 * rows, and a cell that is never recycled is one whose switch keeps animating and whose menu stays
 * open through an update.
 */
private class NativeSettingsRow(
    item: SettingsItem,
    largeText: Boolean,
    private val focus: NativeMenuFocus,
    private val showIcon: Boolean,
) {
    // UIAction retains this state, which owns no view; capturing the row would tie the cell to itself.
    private val state = NativeSettingsRowState(item)
    val cell = UITableViewCell(style = UITableViewCellStyle.UITableViewCellStyleDefault, reuseIdentifier = null)
    private var content: List<Any?>? = null
    private var menu: List<Any?>? = null

    private var toggle: UISwitch? = null
    private var picker: UIButton? = null
    private var segments: UISegmentedControl? = null
    private var slider: UISlider? = null
    private var titleLabel: UILabel? = null
    private var descriptionLabel: UILabel? = null
    private var valueLabel: UILabel? = null
    private var sampleLabel: UILabel? = null
    private var spinner: UIActivityIndicatorView? = null
    private var stackedPicker = false
    private var leadingIcon: UIImageView? = null
    private var symbolName: String? = null
    private var symbolImage: UIImage? = null

    init {
        // Read before any `apply`, where `state` would be a control's own UIControlState.
        val rowState = state
        when (item) {
            is SettingsItem.Toggle -> {
                toggle =
                    UISwitch().apply {
                        addAction(
                            UIAction.actionWithHandler { action ->
                                val toggle = rowState.item as? SettingsItem.Toggle ?: return@actionWithHandler
                                val on = (action?.sender as? UISwitch)?.on ?: !toggle.checked
                                if (toggle.enabled) toggle.onCheckedChange(on)
                            },
                            forControlEvents = UIControlEventValueChanged,
                        )
                    }
                cell.accessoryView = toggle
                cell.selectionStyle = UITableViewCellSelectionStyle.UITableViewCellSelectionStyleNone
            }

            is SettingsItem.Choice ->
                if (item.drawsSegments(largeText)) {
                    buildSegments(rowState)
                } else if (largeText) {
                    buildStackedPicker()
                } else {
                    // A pop-up button, as a system Form draws a menu-style picker: the answer beside the
                    // question and the choices in a menu over the page, its title saying what a choice costs.
                    picker =
                        UIButton.buttonWithConfiguration(UIButtonConfiguration.plainButtonConfiguration(), primaryAction = null).apply {
                            showsMenuAsPrimaryAction = true
                            changesSelectionAsPrimaryAction = true
                            releasesFocusForMenu(focus)
                        }
                    cell.accessoryView = picker
                    cell.selectionStyle = UITableViewCellSelectionStyle.UITableViewCellSelectionStyleNone
                }

            is SettingsItem.Slider -> buildSlider(rowState)

            is SettingsItem.Link ->
                if (!item.external && !item.badge) {
                    cell.accessoryType = UITableViewCellAccessoryType.UITableViewCellAccessoryDisclosureIndicator
                }

            is SettingsItem.Action -> spinner = UIActivityIndicatorView(activityIndicatorStyle = UIActivityIndicatorViewStyleMedium)

            is SettingsItem.Info -> {
                cell.selectionStyle = UITableViewCellSelectionStyle.UITableViewCellSelectionStyleNone
                // Read as text rather than as a button. Through key-value coding for the same reason as the
                // identifier below: the binding declares the property on the protocol, not on the cell.
                cell.setValue(NSNumber(unsignedLongLong = UIAccessibilityTraitStaticText), forKey = "accessibilityTraits")
            }

            is SettingsItem.Text -> buildTextField(rowState)

            is SettingsItem.Custom -> error("a custom row reaches the system list as its native item")
        }
        // Through key-value coding: UIView conforms to UIAccessibilityIdentification at runtime, but the
        // Kotlin binding declares the property only on the protocol, and the cast to it does not succeed.
        item.testTag?.let { tag -> cell.setValue(tag, forKey = "accessibilityIdentifier") }
    }

    private fun pinContent(content: UIView) {
        val body =
            if (showIcon) {
                val icon = UIImageView(image = null).apply {
                    contentMode = UIViewContentMode.UIViewContentModeScaleAspectFit
                    hidden = true
                    // The stack collapses the slot for rows without a symbol.
                    widthAnchor.constraintEqualToConstant(ROW_ICON_SIZE).apply { priority = 999f }.active = true
                    heightAnchor.constraintEqualToConstant(ROW_ICON_SIZE).active = true
                }
                leadingIcon = icon
                stack(listOf(icon, content), vertical = false, spacing = ROW_ICON_SPACING).apply {
                    alignment = UIStackViewAlignmentTop
                }
            } else {
                content
            }
        cell.contentView.addSubview(body)
        val margins = cell.contentView.layoutMarginsGuide
        NSLayoutConstraint.activateConstraints(
            listOf(
                body.leadingAnchor.constraintEqualToAnchor(margins.leadingAnchor),
                body.trailingAnchor.constraintEqualToAnchor(margins.trailingAnchor),
                body.topAnchor.constraintEqualToAnchor(margins.topAnchor, constant = 2.0),
                body.bottomAnchor.constraintEqualToAnchor(margins.bottomAnchor, constant = -2.0),
            ),
        )
    }

    var textField: UITextField? = null
        private set
    val isTextRow: Boolean get() = state.item is SettingsItem.Text
    private var lastPushedValue: String? = null

    /** The next [SettingsItem.Text] field in page order, for the return key's Next. */
    var nextTextField: (() -> UITextField?)? = null
    var scrollToMe: (() -> Unit)? = null

    private var errorLabel: UILabel? = null

    /** `UITextField.delegate` is weak — the row keeps its delegate for the cell's life. */
    private var fieldDelegate: TextFieldDelegate? = null

    private inner class TextFieldDelegate :
        NSObject(),
        UITextFieldDelegateProtocol {
        override fun textFieldDidBeginEditing(textField: UITextField) {
            // Compose's OverlayInputView holds first responder for hardware keys; editing takes it,
            // and [NativeMenuFocus.restore] hands it back so page menus keep their type-select.
            focus.releaseFor(textField)
            scrollToMe?.invoke()
        }

        override fun textFieldDidEndEditing(textField: UITextField) {
            focus.restore()
        }

        override fun textFieldShouldReturn(textField: UITextField): Boolean {
            val next = nextTextField?.invoke()
            if (next != null) next.becomeFirstResponder() else textField.resignFirstResponder()
            return true
        }
    }

    private fun buildTextField(rowState: NativeSettingsRowState) {
        val item = rowState.item as SettingsItem.Text
        val title =
            multilineLabel().apply {
                text = item.title
                font = UIFont.systemFontOfSize(17.0)
            }
        val fieldDelegate = TextFieldDelegate()
        this.fieldDelegate = fieldDelegate
        val field =
            UITextField().apply {
                delegate = fieldDelegate
                text = item.value
                placeholder = item.placeholder
                keyboardType =
                    when (item.keyboard) {
                        SettingsKeyboard.Uri -> UIKeyboardTypeURL
                        SettingsKeyboard.Number -> UIKeyboardTypeDecimalPad
                        SettingsKeyboard.Text -> UIKeyboardTypeDefault
                    }
                returnKeyType = UIReturnKeyType.UIReturnKeyDone
                clearButtonMode = UITextFieldViewMode.UITextFieldViewModeWhileEditing
                secureTextEntry = item.secure
                enabled = item.enabled
                textAlignment = NSTextAlignmentRight
                adjustsFontForContentSizeCategory = true
                font = UIFont.systemFontOfSize(17.0)
                setContentHuggingPriority(50.0f, UILayoutConstraintAxisHorizontal)
                setContentCompressionResistancePriority(751.0f, UILayoutConstraintAxisHorizontal)
                addAction(
                    UIAction.actionWithHandler { action ->
                        val current = rowState.item as? SettingsItem.Text ?: return@actionWithHandler
                        current.onValueChange((action?.sender as? UITextField)?.text ?: "")
                    },
                    forControlEvents = UIControlEventEditingChanged,
                )
            }
        val error =
            multilineLabel().apply {
                text = item.errorMessage
                hidden = item.errorMessage == null
                textColor = UIColor.systemRedColor
                font = UIFont.systemFontOfSize(13.0)
            }
        lastPushedValue = item.value
        textField = field
        titleLabel = title
        errorLabel = error
        pinContent(
            stack(
                listOf(stack(listOf(title, field), vertical = false, spacing = 8.0), error),
                vertical = true,
                spacing = 4.0,
            ),
        )
        cell.selectionStyle = UITableViewCellSelectionStyle.UITableViewCellSelectionStyleNone
    }

    private fun updateTextField(item: SettingsItem.Text, palette: NativeSettingsPalette): Boolean {
        pushModelValue(item)
        textField?.enabled = item.enabled
        titleLabel?.textColor = if (item.enabled) palette.text else palette.disabledText
        val label = errorLabel ?: return false
        val changed = label.hidden != (item.errorMessage == null)
        label.text = item.errorMessage
        label.hidden = item.errorMessage == null
        return changed
    }

    /** Model → field, only when the value differs and the user is not typing in it. */
    private fun pushModelValue(item: SettingsItem.Text) {
        if (item.value == lastPushedValue) return
        lastPushedValue = item.value
        val field = textField ?: return
        if (!field.isFirstResponder) field.text = item.value
    }

    private fun buildSegments(rowState: NativeSettingsRowState) {
        val title = multilineLabel()
        val description = multilineLabel()
        val control =
            UISegmentedControl().apply {
                addAction(
                    UIAction.actionWithHandler { action ->
                        val choice = rowState.item as? SettingsItem.Choice ?: return@actionWithHandler
                        val index = (action?.sender as? UISegmentedControl)?.selectedSegmentIndex?.toInt() ?: return@actionWithHandler
                        if (choice.enabled && index >= 0) choice.onSelect(index)
                    },
                    forControlEvents = UIControlEventValueChanged,
                )
            }
        titleLabel = title
        descriptionLabel = description
        segments = control
        pinContent(stack(listOf(title, description, control), vertical = true, spacing = 6.0))
        cell.selectionStyle = UITableViewCellSelectionStyle.UITableViewCellSelectionStyleNone
    }

    /**
     * The answer on a line of its own under the question. Beside it, at an accessibility size, either
     * the answer or the question would get no room — 设置 stacks its value rows the same way.
     */
    private fun buildStackedPicker() {
        val title = multilineLabel()
        val description = multilineLabel()
        val button =
            UIButton.buttonWithConfiguration(UIButtonConfiguration.plainButtonConfiguration(), primaryAction = null).apply {
                showsMenuAsPrimaryAction = true
                changesSelectionAsPrimaryAction = true
                contentHorizontalAlignment = UIControlContentHorizontalAlignmentLeading
                releasesFocusForMenu(focus)
            }
        titleLabel = title
        descriptionLabel = description
        picker = button
        stackedPicker = true
        pinContent(stack(listOf(title, description, button), vertical = true, spacing = 4.0))
        cell.selectionStyle = UITableViewCellSelectionStyle.UITableViewCellSelectionStyleNone
    }

    private fun buildSlider(rowState: NativeSettingsRowState) {
        val title = multilineLabel()
        val value =
            UILabel().apply {
                adjustsFontForContentSizeCategory = true
                setContentHuggingPriority(UILayoutPriorityRequired, forAxis = UILayoutConstraintAxisHorizontal)
                setContentCompressionResistancePriority(UILayoutPriorityRequired, forAxis = UILayoutConstraintAxisHorizontal)
            }
        val top = stack(listOf(title, value), vertical = false, spacing = 8.0).apply { alignment = UIStackViewAlignmentFirstBaseline }
        val description = multilineLabel()
        val control =
            UISlider().apply {
                addAction(
                    UIAction.actionWithHandler { action ->
                        val item = rowState.item as? SettingsItem.Slider ?: return@actionWithHandler
                        val raw = (action?.sender as? UISlider)?.value ?: return@actionWithHandler
                        if (item.enabled) item.onValueChange(snapToSteps(raw, item.range, item.steps))
                    },
                    forControlEvents = UIControlEventValueChanged,
                )
                addAction(
                    UIAction.actionWithHandler { action ->
                        val item = rowState.item as? SettingsItem.Slider ?: return@actionWithHandler
                        (action?.sender as? UISlider)?.let { it.setValue(snapToSteps(it.value, item.range, item.steps), animated = true) }
                        if (item.enabled) item.onValueChangeFinished()
                    },
                    forControlEvents = UIControlEventTouchUpInside or UIControlEventTouchUpOutside,
                )
            }
        val sample = multilineLabel()
        titleLabel = title
        valueLabel = value
        descriptionLabel = description
        slider = control
        sampleLabel = sample
        pinContent(stack(listOf(top, description, control, sample), vertical = true, spacing = 6.0))
        cell.selectionStyle = UITableViewCellSelectionStyle.UITableViewCellSelectionStyleNone
    }

    val selectable: Boolean
        get() =
            when (val item = state.item) {
                is SettingsItem.Link -> item.enabled
                is SettingsItem.Action -> item.enabled && !item.loading
                else -> false
            }

    fun select() {
        when (val item = state.item) {
            is SettingsItem.Link -> if (item.enabled) item.onClick()
            is SettingsItem.Action -> if (item.enabled && !item.loading) item.onClick()
            else -> Unit
        }
    }

    /** Applies [item] in place; true when the text changed and the row may need another height. */
    fun update(item: SettingsItem, palette: NativeSettingsPalette): Boolean {
        state.item = item
        cell.backgroundColor = palette.card
        val iconName = if (showIcon) item.icon?.name else null
        // This setting controls the avatar on search results.
        val nextSymbol = if (iconName == "PersonSearch") "person.crop.circle" else iconName?.let(SfSymbolByIconName::get)
        val iconChanged = nextSymbol != symbolName
        if (iconChanged) {
            symbolName = nextSymbol
            symbolImage = nextSymbol?.let { UIImage.systemImageNamed(it) }
            leadingIcon?.image = symbolImage
            leadingIcon?.hidden = symbolImage == null
        }
        leadingIcon?.tintColor = palette.iconTint(item.enabled)
        return updateContent(item, palette) || iconChanged
    }

    private fun updateContent(item: SettingsItem, palette: NativeSettingsPalette): Boolean {
        val textColor = if (item.enabled) palette.text else palette.disabledText
        val secondaryColor = if (item.enabled) palette.secondaryText else palette.disabledText
        when (item) {
            is SettingsItem.Toggle -> {
                toggle?.let { control ->
                    if (control.on != item.checked) control.setOn(item.checked, animated = true)
                    control.enabled = item.enabled
                    control.onTintColor = palette.accent
                }
                return listContent(item.title, item.description, null, item.enabled, palette)
            }

            is SettingsItem.Choice -> {
                if (segments != null) return updateSegments(item, palette, textColor, secondaryColor)
                val answerChanged = picker?.let { updatePicker(it, item, palette) } ?: false
                // Stacked, a longer answer can take another line.
                if (stackedPicker) return updateLabels(item.title, item.description, palette, textColor, secondaryColor) || answerChanged
                return listContent(item.title, item.description, null, item.enabled, palette)
            }

            is SettingsItem.Slider -> return updateSlider(item, palette, textColor, secondaryColor)

            is SettingsItem.Link -> {
                cell.accessoryView =
                    when {
                        item.external -> accessoryImage("arrow.up.forward", palette)
                        item.badge -> badgeAccessory(palette)
                        else -> null
                    }
                return listContent(item.title, item.description, item.value, item.enabled, palette)
            }

            is SettingsItem.Action -> {
                if (item.loading) {
                    spinner?.startAnimating()
                    cell.accessoryView = spinner
                } else {
                    spinner?.stopAnimating()
                    cell.accessoryView = null
                }
                cell.selectionStyle =
                    if (item.loading) UITableViewCellSelectionStyle.UITableViewCellSelectionStyleNone else UITableViewCellSelectionStyle.UITableViewCellSelectionStyleDefault
                return listContent(item.title, item.description, item.value, item.enabled, palette, destructive = item.destructive)
            }

            is SettingsItem.Info -> {
                cell.accessoryView = null
                return listContent(item.title, item.description, item.value, item.enabled, palette)
            }

            is SettingsItem.Text -> return updateTextField(item, palette)

            is SettingsItem.Custom -> error("a custom row reaches the system list as its native item")
        }
    }

    private fun listContent(
        title: String,
        description: String?,
        value: String?,
        enabled: Boolean,
        palette: NativeSettingsPalette,
        destructive: Boolean = false,
    ): Boolean {
        val nextContent = listOf(title, description, value, enabled, destructive, palette, symbolName)
        if (nextContent == content) return false
        content = nextContent
        val configuration =
            when {
                description != null -> UIListContentConfiguration.subtitleCellConfiguration()
                value != null -> UIListContentConfiguration.valueCellConfiguration()
                else -> UIListContentConfiguration.cellConfiguration()
            }
        configuration.text = title
        configuration.secondaryText = description ?: value
        symbolImage?.let {
            configuration.image = it
            configuration.imageProperties.tintColor = palette.iconTint(enabled)
            configuration.imageProperties.maximumSize = CGSizeMake(ROW_ICON_SIZE, ROW_ICON_SIZE)
            configuration.imageProperties.reservedLayoutSize = CGSizeMake(ROW_ICON_SIZE, 0.0)
            configuration.imageToTextPadding = ROW_ICON_SPACING
        }
        configuration.textProperties.font = palette.font(17.0)
        configuration.textProperties.color =
            when {
                !enabled -> palette.disabledText
                destructive -> UIColor.systemRedColor
                else -> palette.text
            }
        configuration.textProperties.adjustsFontForContentSizeCategory = true
        // A destructive action is centred, as 设置 sets 退出登录 apart from the rows above it.
        if (destructive && description == null && value == null) {
            configuration.textProperties.alignment = UIListContentTextAlignment.UIListContentTextAlignmentCenter
        }
        configuration.secondaryTextProperties.font = palette.font(if (description != null) 15.0 else 17.0)
        configuration.secondaryTextProperties.color = if (enabled) palette.secondaryText else palette.disabledText
        configuration.secondaryTextProperties.adjustsFontForContentSizeCategory = true
        cell.contentConfiguration = configuration
        return true
    }

    private fun updateSegments(
        item: SettingsItem.Choice,
        palette: NativeSettingsPalette,
        textColor: UIColor,
        secondaryColor: UIColor,
    ): Boolean {
        val control = segments ?: return false
        val labels = item.options.map { it.label }
        val current = (0 until control.numberOfSegments.toInt()).map { control.titleForSegmentAtIndex(it.toULong()) }
        if (current != labels) {
            control.removeAllSegments()
            labels.forEachIndexed { index, label -> control.insertSegmentWithTitle(label, atIndex = index.toULong(), animated = false) }
        }
        item.options.forEachIndexed { index, option -> control.setEnabled(option.enabled, forSegmentAtIndex = index.toULong()) }
        if (control.selectedSegmentIndex != item.selectedIndex.toLong()) control.selectedSegmentIndex = item.selectedIndex.toLong()
        control.enabled = item.enabled
        return updateLabels(item.title, item.description, palette, textColor, secondaryColor)
    }

    private fun updateSlider(
        item: SettingsItem.Slider,
        palette: NativeSettingsPalette,
        textColor: UIColor,
        secondaryColor: UIColor,
    ): Boolean {
        val control = slider ?: return false
        control.minimumValue = item.range.start
        control.maximumValue = item.range.endInclusive
        // The thumb is the reader's while a drag is under way; the page's value catches up after.
        if (!control.tracking && control.value != item.value) control.setValue(item.value, animated = false)
        control.enabled = item.enabled
        control.minimumTrackTintColor = palette.accent
        valueLabel?.text = item.valueLabel
        valueLabel?.font = palette.font(17.0)
        valueLabel?.textColor = secondaryColor
        var resized = updateLabels(item.title, item.description, palette, textColor, secondaryColor)
        sampleLabel?.let { label ->
            val sample = item.sample
            val nextText = sample?.text
            val nextSize = sample?.fontSizeSp?.toDouble()
            if (label.text != nextText || label.font.pointSize != nextSize) resized = true
            label.text = nextText
            label.hidden = sample == null
            label.textColor = secondaryColor
            if (nextSize != null) label.font = UIFont.systemFontOfSize(nextSize)
        }
        return resized
    }

    private fun updateLabels(
        title: String,
        description: String?,
        palette: NativeSettingsPalette,
        textColor: UIColor,
        secondaryColor: UIColor,
    ): Boolean {
        val resized = titleLabel?.text != title || descriptionLabel?.text != description
        titleLabel?.text = title
        titleLabel?.font = palette.font(17.0)
        titleLabel?.textColor = textColor
        descriptionLabel?.text = description
        descriptionLabel?.hidden = description == null
        descriptionLabel?.font = palette.font(15.0)
        descriptionLabel?.textColor = secondaryColor
        return resized
    }

    private fun updatePicker(
        button: UIButton,
        choice: SettingsItem.Choice,
        palette: NativeSettingsPalette,
    ): Boolean {
        button.enabled = choice.enabled
        val nextMenu = listOf(choice.options.map { listOf(it.label, it.swatch, it.enabled) }, choice.selectedIndex, choice.prompt, palette)
        if (nextMenu == menu) return false
        menu = nextMenu
        button.configuration =
            UIButtonConfiguration.plainButtonConfiguration().apply {
                baseForegroundColor = palette.secondaryText
                titleLineBreakMode = if (stackedPicker) NSLineBreakByWordWrapping else NSLineBreakByTruncatingTail
                titleTextAttributesTransformer = { attributes ->
                    attributes.orEmpty() + (NSFontAttributeName to palette.font(17.0))
                }
            }
        val rowState = state
        button.menu =
            UIMenu.menuWithTitle(
                title = choice.prompt.orEmpty(),
                children =
                choice.options.mapIndexed { index, option ->
                    val swatch = option.swatch?.let { colour ->
                        UIImage.systemImageNamed("circle.fill")?.imageWithTintColor(colour.toUIColor(), renderingMode = UIImageRenderingMode.UIImageRenderingModeAlwaysOriginal)
                    }
                    UIAction.actionWithTitle(option.label, image = swatch, identifier = null) {
                        (rowState.item as? SettingsItem.Choice)?.takeIf { it.enabled && it.options.getOrNull(index)?.enabled == true }?.onSelect?.invoke(index)
                    }.apply {
                        if (!option.enabled) attributes = UIMenuElementAttributesDisabled
                        state =
                            if (index == choice.selectedIndex) {
                                UIMenuElementState.UIMenuElementStateOn
                            } else {
                                UIMenuElementState.UIMenuElementStateOff
                            }
                    }
                },
            )
        // Stacked, the button is laid out by its stack; an accessory view is placed by its frame instead,
        // capped so that a long answer leaves the question room.
        if (stackedPicker) return true
        button.sizeToFit()
        val (width, height) = button.frame.useContents { size.width to size.height }
        val cap = PICKER_MAX_WIDTH * palette.scale
        if (width > cap) button.setFrame(CGRectMake(0.0, 0.0, cap, height))
        return true
    }

    private fun accessoryImage(symbol: String, palette: NativeSettingsPalette): UIView =
        UIImageView(image = UIImage.systemImageNamed(symbol)).apply {
            tintColor = palette.disabledText
            sizeToFit()
        }

    /** A red dot before the chevron: something new behind the row, the way 设置 marks a pending update. */
    private fun badgeAccessory(palette: NativeSettingsPalette): UIView {
        val dot =
            UIView(frame = CGRectMake(0.0, 0.0, 10.0, 10.0)).apply {
                backgroundColor = UIColor.systemRedColor
                layer.cornerRadius = 5.0
                translatesAutoresizingMaskIntoConstraints = false
                widthAnchor.constraintEqualToConstant(10.0).active = true
                heightAnchor.constraintEqualToConstant(10.0).active = true
            }
        val chevron =
            UIImageView(image = UIImage.systemImageNamed("chevron.forward")).apply {
                tintColor = palette.disabledText
                translatesAutoresizingMaskIntoConstraints = false
            }
        return stack(listOf(dot, chevron), vertical = false, spacing = 8.0).apply {
            alignment = UIStackViewAlignmentCenter
            translatesAutoresizingMaskIntoConstraints = true
            setFrame(CGRectMake(0.0, 0.0, 32.0, 20.0))
        }
    }
}

private class NativeSettingsSource(
    private val state: NativeSettingsState,
) : NSObject(),
    UITableViewDataSourceProtocol,
    UITableViewDelegateProtocol {
    override fun numberOfSectionsInTableView(tableView: UITableView): NSInteger = state.sections.size.toLong()

    @ObjCSignatureOverride
    override fun tableView(
        tableView: UITableView,
        numberOfRowsInSection: NSInteger,
    ): NSInteger = state.sections[numberOfRowsInSection.toInt()].rows.size.toLong()

    @ObjCSignatureOverride
    override fun tableView(
        tableView: UITableView,
        cellForRowAtIndexPath: NSIndexPath,
    ): UITableViewCell = state.row(cellForRowAtIndexPath).cell

    @ObjCSignatureOverride
    override fun tableView(
        tableView: UITableView,
        titleForHeaderInSection: NSInteger,
    ): String? = state.sections[titleForHeaderInSection.toInt()].header

    @ObjCSignatureOverride
    override fun tableView(
        tableView: UITableView,
        titleForFooterInSection: NSInteger,
    ): String? = state.sections[titleForFooterInSection.toInt()].footer

    @ObjCSignatureOverride
    override fun tableView(
        tableView: UITableView,
        shouldHighlightRowAtIndexPath: NSIndexPath,
    ): Boolean = state.row(shouldHighlightRowAtIndexPath).selectable

    @ObjCSignatureOverride
    override fun tableView(
        tableView: UITableView,
        didSelectRowAtIndexPath: NSIndexPath,
    ) {
        tableView.deselectRowAtIndexPath(didSelectRowAtIndexPath, animated = true)
        state.row(didSelectRowAtIndexPath).select()
    }

    override fun scrollViewDidScroll(scrollView: UIScrollView) = state.onScroll(scrollView)
}

private class NativeSettingsBarDelegate(
    private val state: NativeSettingsState,
) : NSObject(),
    UINavigationBarDelegateProtocol {
    /** The back stack pops the page; the bar keeps both items, ready for the next time it is shown. */
    @ObjCSignatureOverride
    override fun navigationBar(
        navigationBar: UINavigationBar,
        shouldPopItem: UINavigationItem,
    ): Boolean {
        state.onBack()
        return false
    }

    /** Lets the bar's background run up under the status bar, as a navigation controller's does. */
    override fun positionForBar(bar: UIBarPositioningProtocol): Long = UIBarPositionTopAttached
}
