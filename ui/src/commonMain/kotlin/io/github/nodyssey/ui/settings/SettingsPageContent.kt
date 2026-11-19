package io.github.nodyssey.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nodyssey.ui.common.PlazaSheet
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.plaza.designsys.component.ChoiceRow
import io.github.plaza.designsys.component.GroupedListItem
import io.github.plaza.designsys.component.GroupedListItemSwitch
import io.github.plaza.designsys.component.GroupedRow
import io.github.plaza.designsys.component.IconBadge
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.SectionLabel
import io.github.plaza.designsys.component.SectionNote
import io.github.plaza.designsys.component.groupedListItemColors
import io.github.plaza.designsys.component.groupedRowTitleStyle
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.settings.AdaptiveSettingsPage
import io.github.plaza.designsys.settings.SettingsItem
import io.github.plaza.designsys.settings.SettingsPage
import io.github.plaza.designsys.settings.SettingsSection
import io.github.plaza.designsys.settings.SettingsSectionStyle
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.stringResource

/**
 * A settings page from its description: the system list on iOS, this app's own cards and rows
 * everywhere else. A page written this way has one definition — the [SettingsPage] its screen builds.
 */
@Composable
internal fun SettingsPageScreen(
    page: SettingsPage,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AdaptiveSettingsPage(page = page, onBack = onBack, modifier = modifier) {
        ComposeSettingsPage(page = page, onBack = onBack, modifier = modifier)
    }
}

@Composable
private fun ComposeSettingsPage(
    page: SettingsPage,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val appBarState = rememberOneHandAppBarState()
    var openChoice by remember { mutableStateOf<String?>(null) }
    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        topBar = {
            OneHandTopAppBar(
                title = page.title,
                state = appBarState,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
            Modifier
                .padding(padding)
                .fillMaxSize()
                .readableWidth()
                .verticalScroll(rememberScrollState())
                .padding(SettingsPagePadding),
            verticalArrangement = Arrangement.spacedBy(SettingsItemGap),
        ) {
            page.sections.forEach { section ->
                SectionContent(section, onOpenChoice = { openChoice = it })
            }
        }
    }

    page.sections
        .flatMap { it.items }
        .filterIsInstance<SettingsItem.Choice>()
        .firstOrNull { it.key == openChoice }
        ?.let { choice -> ChoiceSheet(choice, onDismiss = { openChoice = null }) }
}

/** Siblings in the page's column rather than a column of their own, so every gap is [SettingsItemGap]. */
@Composable
private fun SectionContent(
    section: SettingsSection,
    onOpenChoice: (String) -> Unit,
) {
    section.header?.let { SectionLabel(it) }
    when (section.style) {
        SettingsSectionStyle.Prominent -> section.items.forEach { item -> ProminentItem(item, onOpenChoice) }

        SettingsSectionStyle.Callout -> section.items.forEach { item -> CalloutItem(item, onOpenChoice) }

        SettingsSectionStyle.Grouped ->
            if (section.items.isNotEmpty()) {
                SettingsGroup {
                    section.items.forEachIndexed { index, item ->
                        ItemRow(item, first = index == 0, last = index == section.items.lastIndex, onOpenChoice = onOpenChoice)
                    }
                }
            }
    }
    section.footer?.let { footer ->
        // Standing in for a group, the note reads as body text; under one, it is the group's small print.
        if (section.items.isEmpty()) {
            SectionNote(footer)
        } else {
            Text(
                footer,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xs),
            )
        }
    }
}

@Composable
private fun ItemRow(
    item: SettingsItem,
    first: Boolean,
    last: Boolean,
    onOpenChoice: (String) -> Unit,
) {
    when (item) {
        is SettingsItem.Toggle ->
            GroupedRow(
                title = item.title,
                subtitle = item.description,
                icon = item.icon,
                first = first,
                last = last,
                checked = item.checked,
                onCheckedChange = item.onCheckedChange,
                enabled = item.enabled,
                trailing = { GroupedListItemSwitch(checked = item.checked, enabled = item.enabled) },
            )

        is SettingsItem.Choice ->
            GroupedRow(
                title = item.title,
                subtitle = item.options.getOrNull(item.selectedIndex),
                icon = item.icon,
                first = first,
                last = last,
                enabled = item.enabled,
                onClick = { onOpenChoice(item.key) },
            )

        is SettingsItem.Link ->
            GroupedRow(
                title = item.title,
                subtitle = item.description ?: item.value,
                icon = item.icon,
                first = first,
                last = last,
                enabled = item.enabled,
                onClick = item.onClick,
            )
    }
}

/**
 * The master switch as 6d draws it: a group of its own in the primary container tone, larger type than
 * the rows under it, because everything else on the page is conditional on it.
 */
@Composable
private fun ProminentItem(
    item: SettingsItem,
    onOpenChoice: (String) -> Unit,
) {
    if (item !is SettingsItem.Toggle) {
        ItemRow(item, first = true, last = true, onOpenChoice = onOpenChoice)
        return
    }
    val scheme = MaterialTheme.colorScheme
    GroupedListItem(
        first = true,
        last = true,
        checked = item.checked,
        onCheckedChange = item.onCheckedChange,
        enabled = item.enabled,
        colors = groupedListItemColors(scheme.onPrimaryContainer, scheme.primaryContainer),
        headlineContent = {
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 24.sp),
            )
        },
        supportingContent =
        item.description?.let { description ->
            {
                Text(
                    description,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onPrimaryContainer,
                )
            }
        },
        trailingContent = { GroupedListItemSwitch(checked = item.checked, enabled = item.enabled) },
    )
}

/** A card rather than a row, because it points out of the page — 想要即时提醒？ */
@Composable
private fun CalloutItem(
    item: SettingsItem,
    onOpenChoice: (String) -> Unit,
) {
    if (item !is SettingsItem.Link) {
        ItemRow(item, first = true, last = true, onOpenChoice = onOpenChoice)
        return
    }
    val scheme = MaterialTheme.colorScheme
    LayerCard(onClick = item.onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item.icon?.let { icon ->
                IconBadge(containerColor = scheme.tertiaryContainer, contentColor = scheme.onTertiaryContainer) {
                    Icon(icon, contentDescription = null)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    item.title,
                    style = groupedRowTitleStyle().copy(fontWeight = FontWeight.SemiBold),
                )
                item.description?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            item.value?.let {
                Text(
                    it,
                    color = scheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/**
 * 6d2 — the answers as radio rows in a sheet, with the cost of a choice said above them.
 *
 * A pick applies and closes in the same tap: the sheet is one question with a few answers, and a
 * confirm button would make the reader answer it twice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceSheet(
    choice: SettingsItem.Choice,
    onDismiss: () -> Unit,
) {
    PlazaSheet(
        onDismiss = onDismiss,
        title = choice.title,
        subtitle = choice.prompt,
    ) {
        Column(
            modifier =
            Modifier
                .padding(horizontal = Spacing.xl)
                .padding(bottom = Spacing.lg)
                .navigationBarsPadding(),
        ) {
            Column(Modifier.selectableGroup()) {
                choice.options.forEachIndexed { index, label ->
                    val isSelected = index == choice.selectedIndex
                    ChoiceRow(
                        label = label,
                        selected = isSelected,
                        onSelect = {
                            choice.onSelect(index)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        textStyle =
                        groupedRowTitleStyle().copy(
                            fontSize = 16.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        // Wraps at a large text size, as the sheet's rows did before ChoiceRow.
                        maxLines = Int.MAX_VALUE,
                    )
                }
            }
        }
    }
}
