@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package io.github.plaza.designsys.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSIndexPath
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIAction
import platform.UIKit.UIBarPositionTopAttached
import platform.UIKit.UIBarPositioningProtocol
import platform.UIKit.UIButton
import platform.UIKit.UIButtonConfiguration
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UIFont
import platform.UIKit.UIFontMetrics
import platform.UIKit.UIFontWeightSemibold
import platform.UIKit.UIListContentConfiguration
import platform.UIKit.UIMenu
import platform.UIKit.UIMenuElementState
import platform.UIKit.UINavigationBar
import platform.UIKit.UINavigationBarAppearance
import platform.UIKit.UINavigationBarDelegateProtocol
import platform.UIKit.UINavigationItem
import platform.UIKit.UINavigationItemBackButtonDisplayModeMinimal
import platform.UIKit.UIScrollView
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

@Composable
actual fun AdaptiveSettingsPage(
    page: SettingsPage,
    onBack: () -> Unit,
    modifier: Modifier,
    fallback: @Composable () -> Unit,
) {
    val palette =
        NativeSettingsPalette(
            accent = MaterialTheme.colorScheme.primary.toUIColor(),
            dark = LocalPlazaDarkTheme.current,
            scale = LocalPlazaFontScale.current.toDouble(),
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
        state.sections =
            page.sections.map { section ->
                NativeSettingsSection(
                    header = section.header,
                    footer = section.footer,
                    rows =
                    section.items.map { item ->
                        val id = item.rowId()
                        live += id
                        rows.getOrPut(id) { NativeSettingsRow(item) }.also { resized = it.update(item, palette) || resized }
                    },
                )
            }
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

private fun SettingsItem.rowId(): String =
    when (this) {
        is SettingsItem.Toggle -> "toggle:$key"
        is SettingsItem.Choice -> "choice:$key"
        is SettingsItem.Link -> "link:$key"
    }

/** The newest description of a row. UIAction handlers read it at the tap rather than capturing an old one. */
private class NativeSettingsRowState(var item: SettingsItem)

/**
 * One cell per row, kept for the life of the page rather than dequeued: a settings page has a dozen
 * rows, and a cell that is never recycled is one whose switch keeps animating and whose menu stays
 * open through an update.
 */
private class NativeSettingsRow(item: SettingsItem) {
    // UIAction retains this state, which owns no view; capturing the row would tie the cell to itself.
    private val state = NativeSettingsRowState(item)
    val cell = UITableViewCell(style = UITableViewCellStyle.UITableViewCellStyleDefault, reuseIdentifier = null)
    private var content: List<Any?>? = null
    private var menu: List<Any?>? = null

    private val toggle: UISwitch? =
        (item as? SettingsItem.Toggle)?.let {
            // Read before `apply`, where `state` would be the switch's own UIControlState.
            val rowState = state
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
        }

    // A pop-up button, as a system Form draws a menu-style picker: the answer beside the question and
    // the choices in a menu over the page, with the menu's title saying what a choice costs.
    private val picker: UIButton? =
        (item as? SettingsItem.Choice)?.let {
            UIButton.buttonWithConfiguration(UIButtonConfiguration.plainButtonConfiguration(), primaryAction = null).apply {
                showsMenuAsPrimaryAction = true
                changesSelectionAsPrimaryAction = true
            }
        }

    init {
        cell.accessoryView = toggle ?: picker
        if (item is SettingsItem.Link) {
            cell.accessoryType = UITableViewCellAccessoryType.UITableViewCellAccessoryDisclosureIndicator
        } else {
            cell.selectionStyle = UITableViewCellSelectionStyle.UITableViewCellSelectionStyleNone
        }
    }

    val selectable: Boolean
        get() = (state.item as? SettingsItem.Link)?.enabled == true

    fun select() {
        (state.item as? SettingsItem.Link)?.takeIf { it.enabled }?.onClick?.invoke()
    }

    /** Applies [item] in place; true when the text changed and the row may need another height. */
    fun update(item: SettingsItem, palette: NativeSettingsPalette): Boolean {
        state.item = item
        cell.backgroundColor = palette.card
        toggle?.let { control ->
            val checked = (item as SettingsItem.Toggle).checked
            if (control.on != checked) control.setOn(checked, animated = true)
            control.enabled = item.enabled
            control.onTintColor = palette.accent
        }
        picker?.let { updatePicker(it, item as SettingsItem.Choice, palette) }

        val description = (item as? SettingsItem.Toggle)?.description ?: (item as? SettingsItem.Link)?.description
        val value = (item as? SettingsItem.Link)?.value
        val nextContent = listOf(item.title, description, value, item.enabled, palette)
        if (nextContent == content) return false
        content = nextContent
        val configuration =
            when {
                description != null -> UIListContentConfiguration.subtitleCellConfiguration()
                value != null -> UIListContentConfiguration.valueCellConfiguration()
                else -> UIListContentConfiguration.cellConfiguration()
            }
        val textColor = if (item.enabled) palette.text else palette.disabledText
        val secondaryColor = if (item.enabled) palette.secondaryText else palette.disabledText
        configuration.text = item.title
        configuration.secondaryText = description ?: value
        configuration.textProperties.font = palette.font(17.0)
        configuration.textProperties.color = textColor
        configuration.textProperties.adjustsFontForContentSizeCategory = true
        configuration.secondaryTextProperties.font = palette.font(if (description != null) 15.0 else 17.0)
        configuration.secondaryTextProperties.color = secondaryColor
        configuration.secondaryTextProperties.adjustsFontForContentSizeCategory = true
        cell.contentConfiguration = configuration
        return true
    }

    private fun updatePicker(
        button: UIButton,
        choice: SettingsItem.Choice,
        palette: NativeSettingsPalette,
    ) {
        button.enabled = choice.enabled
        val nextMenu = listOf(choice.options, choice.selectedIndex, choice.prompt, palette)
        if (nextMenu == menu) return
        menu = nextMenu
        button.configuration =
            UIButtonConfiguration.plainButtonConfiguration().apply {
                baseForegroundColor = palette.secondaryText
                titleTextAttributesTransformer = { attributes ->
                    attributes.orEmpty() + (NSFontAttributeName to palette.font(17.0))
                }
            }
        val rowState = state
        button.menu =
            UIMenu.menuWithTitle(
                title = choice.prompt.orEmpty(),
                children =
                choice.options.mapIndexed { index, label ->
                    UIAction.actionWithTitle(label, image = null, identifier = null) {
                        (rowState.item as? SettingsItem.Choice)?.takeIf { it.enabled }?.onSelect?.invoke(index)
                    }.apply {
                        state =
                            if (index == choice.selectedIndex) {
                                UIMenuElementState.UIMenuElementStateOn
                            } else {
                                UIMenuElementState.UIMenuElementStateOff
                            }
                    }
                },
            )
        // An accessory view is placed by its frame, not by constraints.
        button.sizeToFit()
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
