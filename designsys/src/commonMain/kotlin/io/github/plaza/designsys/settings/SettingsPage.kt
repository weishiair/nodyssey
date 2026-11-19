package io.github.plaza.designsys.settings

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

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

@Immutable
sealed interface SettingsItem {
    /** Unique within its page; see [SettingsSection.key]. */
    val key: String
    val title: String

    /** Drawn by the Compose rows only: a system settings list carries no icons below its top level. */
    val icon: ImageVector?
    val enabled: Boolean

    @Immutable
    class Toggle(
        override val key: String,
        override val title: String,
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit,
        val description: String? = null,
        override val icon: ImageVector? = null,
        override val enabled: Boolean = true,
    ) : SettingsItem

    /** One answer of several, each picked by its index into [options]. */
    @Immutable
    class Choice(
        override val key: String,
        override val title: String,
        val options: List<String>,
        val selectedIndex: Int,
        val onSelect: (Int) -> Unit,
        /** What the question costs, said above the answers. */
        val prompt: String? = null,
        override val icon: ImageVector? = null,
        override val enabled: Boolean = true,
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
        override val icon: ImageVector? = null,
        override val enabled: Boolean = true,
    ) : SettingsItem
}

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

internal fun SettingsPage.shape(): List<SettingsSectionShape> =
    sections.map { section ->
        SettingsSectionShape(
            key = section.key,
            header = section.header,
            footer = section.footer,
            rows =
            section.items.map { item ->
                val kind =
                    when (item) {
                        is SettingsItem.Toggle -> "toggle"
                        is SettingsItem.Choice -> "choice"
                        is SettingsItem.Link -> "link"
                    }
                "$kind:${item.key}"
            },
        )
    }
