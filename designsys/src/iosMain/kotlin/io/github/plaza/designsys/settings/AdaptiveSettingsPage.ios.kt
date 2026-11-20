@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package io.github.plaza.designsys.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import io.github.plaza.designsys.component.interfaceStyle
import io.github.plaza.designsys.component.toUIColor
import io.github.plaza.designsys.theme.LocalPlazaDarkTheme
import io.github.plaza.designsys.theme.LocalPlazaFontScale
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSIndexPath
import platform.Foundation.NSNumber
import platform.Foundation.setValue
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.NSLineBreakByTruncatingTail
import platform.UIKit.NSLineBreakByWordWrapping
import platform.UIKit.UIAccessibilityTraitStaticText
import platform.UIKit.UIAction
import platform.UIKit.UIActivityIndicatorView
import platform.UIKit.UIActivityIndicatorViewStyleMedium
import platform.UIKit.UIApplication
import platform.UIKit.UIBarPositionTopAttached
import platform.UIKit.UIBarPositioningProtocol
import platform.UIKit.UIButton
import platform.UIKit.UIButtonConfiguration
import platform.UIKit.UIColor
import platform.UIKit.UIContentSizeCategoryIsAccessibilityCategory
import platform.UIKit.UIControlContentHorizontalAlignmentLeading
import platform.UIKit.UIControlEventMenuActionTriggered
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlEventTouchUpOutside
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UIFont
import platform.UIKit.UIFontMetrics
import platform.UIKit.UIFontWeightSemibold
import platform.UIKit.UIImage
import platform.UIKit.UIImageRenderingMode
import platform.UIKit.UIImageView
import platform.UIKit.UIKeyInputProtocol
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
import platform.UIKit.UIResponder
import platform.UIKit.UIScrollView
import platform.UIKit.UISegmentedControl
import platform.UIKit.UISlider
import platform.UIKit.UIStackView
import platform.UIKit.UIStackViewAlignmentCenter
import platform.UIKit.UIStackViewAlignmentFirstBaseline
import platform.UIKit.UISwitch
import platform.UIKit.UITableView
import platform.UIKit.UITableViewCell
import platform.UIKit.UITableViewCellAccessoryType
import platform.UIKit.UITableViewCellSelectionStyle
import platform.UIKit.UITableViewCellStyle
import platform.UIKit.UITableViewDataSourceProtocol
import platform.UIKit.UITableViewDelegateProtocol
import platform.UIKit.UITableViewStyle
import platform.UIKit.UIView
import platform.UIKit.labelColor
import platform.UIKit.row
import platform.UIKit.secondaryLabelColor
import platform.UIKit.secondarySystemGroupedBackgroundColor
import platform.UIKit.section
import platform.UIKit.systemGroupedBackgroundColor
import platform.UIKit.systemRedColor
import platform.UIKit.tertiaryLabelColor
import platform.darwin.NSInteger
import platform.darwin.NSObject

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
    // Compose follows Dynamic Type through the density's font scale, so reading it is what brings a
    // change of text size back here; the category itself is the system's to say.
    val systemFontScale = LocalDensity.current.fontScale
    val largeText =
        remember(systemFontScale) {
            UIContentSizeCategoryIsAccessibilityCategory(UIApplication.sharedApplication.preferredContentSizeCategory)
        }
    val palette =
        NativeSettingsPalette(
            accent = MaterialTheme.colorScheme.primary.toUIColor(),
            dark = LocalPlazaDarkTheme.current,
            scale = LocalPlazaFontScale.current.toDouble(),
            largeText = largeText,
        )
    val host = remember { NativeSettingsPageHost() }
    UIKitView(
        factory = { host.view },
        modifier = modifier.fillMaxSize(),
        properties = SettingsPageInterop,
        update = { host.update(page, onBack, palette) },
        onRelease = { host.dispose() },
    )
}

/**
 * What of the app's theme reaches UIKit: the accent, light or dark, and the in-app text size.
 *
 * The surfaces are the system's grouped colours rather than [io.github.plaza.designsys.theme.PlazaLayers]:
 * the light layers put white cards on a page one step off white and separate them by shadow, which a
 * table does not draw, so the groups vanished into the page. System grey under white is what an
 * inset-grouped list is read by, and it follows dark mode through the interface style.
 */
private data class NativeSettingsPalette(
    val accent: UIColor,
    val dark: Boolean,
    val scale: Double,
    /** An accessibility text size: rows stack their controls under the question, as 设置 does. */
    val largeText: Boolean,
) {
    val page: UIColor get() = UIColor.systemGroupedBackgroundColor
    val card: UIColor get() = UIColor.secondarySystemGroupedBackgroundColor
    val text: UIColor get() = UIColor.labelColor
    val secondaryText: UIColor get() = UIColor.secondaryLabelColor
    val disabledText: UIColor get() = UIColor.tertiaryLabelColor
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

private class NativeSettingsPageHost {
    val view = UIView()
    private val state = NativeSettingsState()
    private val source = NativeSettingsSource(state)
    private val barDelegate = NativeSettingsBarDelegate(state)
    private val bar = UINavigationBar()
    private val table = UITableView(frame = CGRectZero.readValue(), style = UITableViewStyle.UITableViewStyleInsetGrouped)
    private val titleItem = UINavigationItem(title = "")
    private val rows = mutableMapOf<String, NativeSettingsRow>()
    private val focus = NativeSettingsFocus()
    private var shape: List<SettingsSectionShape>? = null
    private var palette: NativeSettingsPalette? = null
    private var scrolled = false

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
        view.addSubview(table)
        view.addSubview(bar)
        bar.translatesAutoresizingMaskIntoConstraints = false
        table.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activateConstraints(
            listOf(
                bar.topAnchor.constraintEqualToAnchor(view.safeAreaLayoutGuide.topAnchor),
                bar.leadingAnchor.constraintEqualToAnchor(view.leadingAnchor),
                bar.trailingAnchor.constraintEqualToAnchor(view.trailingAnchor),
                table.topAnchor.constraintEqualToAnchor(bar.bottomAnchor),
                table.leadingAnchor.constraintEqualToAnchor(view.leadingAnchor),
                table.trailingAnchor.constraintEqualToAnchor(view.trailingAnchor),
                table.bottomAnchor.constraintEqualToAnchor(view.bottomAnchor),
            ),
        )
    }

    fun update(page: SettingsPage, onBack: () -> Unit, palette: NativeSettingsPalette) {
        state.onBack = onBack
        if (palette.largeText != this.palette?.largeText) {
            // Rows are laid out for a text size when they are built; a different one builds them again.
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
                        rows.getOrPut(id) { NativeSettingsRow(item, palette.largeText, focus) }.also { resized = it.update(item, palette) || resized }
                    },
                )
            }
        // The page's introduction as the footer of an empty first group: the system list's own place
        // for a paragraph of explanation, set in the same small print.
        state.sections = listOfNotNull(page.intro?.let { NativeSettingsSection(header = null, footer = it, rows = emptyList()) }) + sections
        rows.keys.retainAll(live)

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
        focus.restore()
        table.dataSource = null
        table.delegate = null
        bar.delegate = null
        rows.clear()
        state.sections = emptyList()
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
        val appearance =
            UINavigationBarAppearance().apply {
                if (scrolled) configureWithDefaultBackground() else configureWithTransparentBackground()
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

/** The newest description of a row. UIAction handlers read it at the tap rather than capturing an old one. */
private class NativeSettingsRowState(var item: SettingsItem)

private fun stack(views: List<UIView>, vertical: Boolean, spacing: Double): UIStackView =
    UIStackView(arrangedSubviews = views).apply {
        axis = if (vertical) UILayoutConstraintAxisVertical else UILayoutConstraintAxisHorizontal
        this.spacing = spacing
        translatesAutoresizingMaskIntoConstraints = false
    }

private fun UITableViewCell.pinToMargins(content: UIView) {
    contentView.addSubview(content)
    val margins = contentView.layoutMarginsGuide
    NSLayoutConstraint.activateConstraints(
        listOf(
            content.leadingAnchor.constraintEqualToAnchor(margins.leadingAnchor),
            content.trailingAnchor.constraintEqualToAnchor(margins.trailingAnchor),
            content.topAnchor.constraintEqualToAnchor(margins.topAnchor, constant = 2.0),
            content.bottomAnchor.constraintEqualToAnchor(margins.bottomAnchor, constant = -2.0),
        ),
    )
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
    private val focus: NativeSettingsFocus,
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

            is SettingsItem.Custom -> error("a custom row reaches the system list as its native item")
        }
        // Through key-value coding: UIView conforms to UIAccessibilityIdentification at runtime, but the
        // Kotlin binding declares the property only on the protocol, and the cast to it does not succeed.
        item.testTag?.let { tag -> cell.setValue(tag, forKey = "accessibilityIdentifier") }
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
        cell.pinToMargins(stack(listOf(title, description, control), vertical = true, spacing = 6.0))
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
        cell.pinToMargins(stack(listOf(title, description, button), vertical = true, spacing = 4.0))
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
        cell.pinToMargins(stack(listOf(top, description, control, sample), vertical = true, spacing = 6.0))
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
        val nextContent = listOf(title, description, value, enabled, destructive, palette)
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

/**
 * Keeps a pop-up menu from bringing up the software keyboard.
 *
 * With a hardware keyboard attached — or one the simulator believes is, as a remote session's does,
 * connecting and disconnecting under every keystroke — a menu turns on type-select
 * (`_UITypeSelectKeyInput`) against whatever holds first responder. Under Compose that is its
 * `OverlayInputView`, held for hardware key events and never released; type-select makes UIKit treat
 * it as needing a keyboard, and when the hardware one reads as disconnected the software one comes up.
 * A plain UIKit page has no first responder there and shows none, so the menu is opened from that same
 * state: the non-text responder is released as the menu is triggered, and handed back when the page
 * leaves so Compose keeps its key events. Compose takes the focus back on its own only when its window
 * or its layers change, neither of which a menu does.
 *
 * Remove when Compose Multiplatform stops holding first responder for hardware keys, or lets an
 * interop view opt out of it.
 */
private class NativeSettingsFocus {
    private var released: UIResponder? = null

    fun releaseFor(view: UIView) {
        val window = view.window ?: return
        val current = firstResponder(window) ?: return
        // A text field keeps its keyboard: that one is asked for.
        if (current is UIKeyInputProtocol) return
        if (current.resignFirstResponder()) released = current
    }

    fun restore() {
        val responder = released ?: return
        released = null
        val window = (responder as? UIView)?.window ?: return
        if (firstResponder(window) == null) responder.becomeFirstResponder()
    }

    private fun firstResponder(view: UIView): UIView? {
        if (view.isFirstResponder()) return view
        return view.subviews.firstNotNullOfOrNull { firstResponder(it as UIView) }
    }
}

private fun UIButton.releasesFocusForMenu(focus: NativeSettingsFocus) {
    // The handler holds the focus object and not the button: UIAction retains what it captures.
    addAction(
        UIAction.actionWithHandler { action -> (action?.sender as? UIView)?.let(focus::releaseFor) },
        forControlEvents = UIControlEventMenuActionTriggered,
    )
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
