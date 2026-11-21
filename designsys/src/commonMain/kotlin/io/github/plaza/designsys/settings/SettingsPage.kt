package io.github.plaza.designsys.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.math.roundToInt

/**
 * A settings page described once and drawn by each platform's own list — Compose rows on Android and
 * the desktop, a system `UITableView` on iOS — see [AdaptiveSettingsPage].
 *
 * The description says what a reader can change and what it currently reads; how a section looks is
 * the renderer's business. That is what lets iOS use the real controls instead of drawings of them,
 * without a second copy of the page to keep in step.
 */
@Immutable
class SettingsPage(
    val title: String,
    val sections: List<SettingsSection>,
    /** A paragraph above the first section saying what the page is for. */
    val intro: String? = null,
)

@Immutable
class SettingsSection(
    /** Stable across recompositions, so that a renderer can tell an edit from a different page. */
    val key: String,
    val items: List<SettingsItem> = emptyList(),
    val header: String? = null,
    /** Small print under the section. A section with no items is its header and this note alone. */
    val footer: String? = null,
    val style: SettingsSectionStyle = SettingsSectionStyle.Grouped,
)

enum class SettingsSectionStyle {
    Grouped,

    /** The one switch the rest of the page depends on. Where the system draws lists, a plain group. */
    Prominent,

    /** A pointer off the page rather than a setting on it. Where the system draws lists, a plain row. */
    Callout,
}

/** One answer of a [SettingsItem.Choice]. */
@Immutable
class SettingsOption(
    val label: String,
    /** A colour the answer stands for — 已读标记's colours — drawn beside the label. */
    val swatch: Color? = null,
    /** False for an answer that exists but cannot be picked here, such as a refresh rate the screen lacks. */
    val enabled: Boolean = true,
)

/** How a [SettingsItem.Choice] shows its answers where the platform leaves it a choice. */
enum class SettingsChoicePresentation {
    /** A row with its answer as the second line; the answers open in a sheet with room for [SettingsItem.Choice.prompt]. */
    Sheet,

    /** A row whose answers open in a menu at its end. */
    Menu,

    /** Every answer side by side under the title — for two or three short ones that are changed often. */
    Segmented,
}

/** Where a row sits in its group, for a [SettingsItem.Custom] that draws its own rounded ends. */
@Immutable
class SettingsRowPosition(
    val first: Boolean,
    val last: Boolean,
)

/** Text drawn at the size a [SettingsItem.Slider] is choosing, so the reader sees the answer. */
@Immutable
class SettingsSample(
    val text: String,
    val fontSizeSp: Float,
)

/** The system keyboard a [SettingsItem.Text] field opens. */
enum class SettingsKeyboard {
    Text,
    Uri,
    Number,
}

@Immutable
sealed interface SettingsItem {
    /** Unique within its page; see [SettingsSection.key]. */
    val key: String
    val title: String

    /** Drawn by the Compose rows only: a system settings list carries no icons below its top level. */
    val icon: ImageVector?
    val enabled: Boolean

    /** Identifies the row to UI tests on both platforms: a Compose test tag, a UIKit accessibility identifier. */
    val testTag: String?

    @Immutable
    class Toggle(
        override val key: String,
        override val title: String,
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit,
        val description: String? = null,
        override val icon: ImageVector? = null,
        override val enabled: Boolean = true,
        override val testTag: String? = null,
    ) : SettingsItem

    /**
     * A single-line text entry. iOS renders it as a title label next to a real [UITextField];
     * [keyboard] picks the system keyboard layout, [errorMessage] renders as an error row below.
     */
    @Immutable
    class Text(
        override val key: String,
        override val title: String,
        val value: String,
        val onValueChange: (String) -> Unit,
        val placeholder: String? = null,
        val keyboard: SettingsKeyboard = SettingsKeyboard.Text,
        val errorMessage: String? = null,
        override val icon: ImageVector? = null,
        override val enabled: Boolean = true,
        override val testTag: String? = null,
    ) : SettingsItem

    /** One answer of several, each picked by its index into [options]. */
    @Immutable
    class Choice(
        override val key: String,
        override val title: String,
        val options: List<SettingsOption>,
        val selectedIndex: Int,
        val onSelect: (Int) -> Unit,
        /** What the question costs, said above the answers. */
        val prompt: String? = null,
        val presentation: SettingsChoicePresentation = SettingsChoicePresentation.Sheet,
        /** A line under the title explaining the question; a [SettingsChoicePresentation.Sheet] row shows the answer there instead. */
        val description: String? = null,
        override val icon: ImageVector? = null,
        override val enabled: Boolean = true,
        override val testTag: String? = null,
    ) : SettingsItem

    /**
     * A value picked along a range. [value] is what the thumb shows — the page keeps it while a drag is
     * under way — and [onValueChangeFinished] is when the page stores it, so a drag is one write.
     */
    @Immutable
    class Slider(
        override val key: String,
        override val title: String,
        val value: Float,
        val range: ClosedFloatingPointRange<Float>,
        /** Stops between the two ends, as Compose counts them; 0 for a continuous slider. */
        val steps: Int,
        val onValueChange: (Float) -> Unit,
        val onValueChangeFinished: () -> Unit,
        /** The value as the reader reads it — 「16」 — at the end of the title line. */
        val valueLabel: String? = null,
        val sample: SettingsSample? = null,
        val description: String? = null,
        override val icon: ImageVector? = null,
        override val enabled: Boolean = true,
        override val testTag: String? = null,
    ) : SettingsItem

    /** A row that opens something — another page, or somewhere off this one. */
    @Immutable
    class Link(
        override val key: String,
        override val title: String,
        val onClick: () -> Unit,
        val description: String? = null,
        /** What the destination currently holds — 「3 个」 — or, on a [SettingsSectionStyle.Callout], its verb. */
        val value: String? = null,
        /** Something new behind the row — an update — shown as a dot. */
        val badge: Boolean = false,
        /** Opens outside the app — a browser — rather than another page of it. */
        val external: Boolean = false,
        override val icon: ImageVector? = null,
        override val enabled: Boolean = true,
        override val testTag: String? = null,
    ) : SettingsItem

    /** A row that does something where it is — 清除缓存, 退出登录 — rather than opening a page. */
    @Immutable
    class Action(
        override val key: String,
        override val title: String,
        val onClick: () -> Unit,
        val description: String? = null,
        val value: String? = null,
        /** Drawn in the error colour: the action loses something. */
        val destructive: Boolean = false,
        /** The action is under way; the row shows progress and does not take another tap. */
        val loading: Boolean = false,
        override val icon: ImageVector? = null,
        override val enabled: Boolean = true,
        override val testTag: String? = null,
    ) : SettingsItem

    /**
     * A row that only reports — 签到状态 — with nothing to tap: no click, no chevron, no selection.
     * [value] is what it currently reads; where the system draws lists it sits at the row's end and
     * moves under the title when it is too long to share the line.
     */
    @Immutable
    class Info(
        override val key: String,
        override val title: String,
        val value: String? = null,
        val description: String? = null,
        override val icon: ImageVector? = null,
        override val enabled: Boolean = true,
        override val testTag: String? = null,
    ) : SettingsItem

    /**
     * A row the Compose list draws its own way, for the few whose Android look the shared rows cannot
     * reproduce — a preview card, a palette strip. [native] is the same setting as an ordinary item,
     * holding the same state and the same callbacks, and is what the system list draws.
     *
     * Use sparingly: every one is a row with two drawings. When the shared rows learn to draw it,
     * replace the custom row with [native].
     */
    @Immutable
    class Custom(
        val native: SettingsItem,
        val compose: @Composable (SettingsRowPosition) -> Unit,
    ) : SettingsItem {
        init {
            require(native !is Custom) { "A custom row's native item must be an ordinary one" }
        }

        override val key: String get() = native.key
        override val title: String get() = native.title
        override val icon: ImageVector? get() = native.icon
        override val enabled: Boolean get() = native.enabled
        override val testTag: String? get() = native.testTag
    }
}

/** The item the system list draws: a [SettingsItem.Custom]'s native half, or the item itself. */
val SettingsItem.nativeItem: SettingsItem
    get() = if (this is SettingsItem.Custom) native else this

/**
 * The part of a page a native list has to be rebuilt for: which rows exist, of which kind, under which
 * text. Anything outside it — a switch's state, a value, a description — is applied to the rows
 * already on screen, which is what keeps a switch's own animation and an open menu alive.
 */
internal data class SettingsSectionShape(
    val key: String,
    val header: String?,
    val footer: String?,
    val rows: List<String>,
)

internal fun SettingsItem.rowKind(): String =
    when (val item = nativeItem) {
        is SettingsItem.Toggle -> "toggle"
        is SettingsItem.Choice -> if (item.presentation == SettingsChoicePresentation.Segmented) "choice-segmented" else "choice"
        is SettingsItem.Slider -> "slider"
        is SettingsItem.Link -> if (item.external) "link-external" else "link"
        is SettingsItem.Text -> "text"
        is SettingsItem.Action -> "action"
        is SettingsItem.Info -> "info"
        is SettingsItem.Custom -> error("unreachable")
    }

internal fun SettingsPage.shape(): List<SettingsSectionShape> =
    listOfNotNull(intro?.let { SettingsSectionShape(key = "", header = null, footer = it, rows = emptyList()) }) +
        sections.map { section ->
            SettingsSectionShape(
                key = section.key,
                header = section.header,
                footer = section.footer,
                rows = section.items.map { item -> "${item.rowKind()}:${item.key}" },
            )
        }

/** Snaps [value] to the stops a Compose slider would offer for [steps] interior ones; continuous when there are none. */
internal fun snapToSteps(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
): Float {
    if (steps <= 0) return value.coerceIn(range.start, range.endInclusive)
    val stop = (range.endInclusive - range.start) / (steps + 1)
    val index = ((value - range.start) / stop).roundToInt().coerceIn(0, steps + 1)
    return range.start + index * stop
}
