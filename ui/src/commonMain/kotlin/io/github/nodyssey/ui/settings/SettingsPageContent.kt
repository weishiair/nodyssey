package io.github.nodyssey.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nodyssey.ui.common.PlazaSheet
import io.github.nodyssey.ui.common.UpdateDot
import io.github.nodyssey.ui.common.describedAsLoading
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.plaza.designsys.component.ChoiceRow
import io.github.plaza.designsys.component.ChoiceSegments
import io.github.plaza.designsys.component.GroupedListItem
import io.github.plaza.designsys.component.GroupedListItemSwitch
import io.github.plaza.designsys.component.GroupedRow
import io.github.plaza.designsys.component.IconBadge
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.PlazaSnackbarHost
import io.github.plaza.designsys.component.PlazaSpinner
import io.github.plaza.designsys.component.SectionLabel
import io.github.plaza.designsys.component.SectionNote
import io.github.plaza.designsys.component.groupedListItemColors
import io.github.plaza.designsys.component.groupedRowTitleStyle
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.settings.AdaptiveSettingsPage
import io.github.plaza.designsys.settings.SettingsChoicePresentation
import io.github.plaza.designsys.settings.SettingsItem
import io.github.plaza.designsys.settings.SettingsPage
import io.github.plaza.designsys.settings.SettingsRowPosition
import io.github.plaza.designsys.settings.SettingsSection
import io.github.plaza.designsys.settings.SettingsSectionStyle
import io.github.plaza.designsys.settings.nativeItem
import io.github.plaza.designsys.settings.nativeSettingsPages
import io.github.plaza.designsys.theme.PostBody
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.stringResource

/**
 * How the Compose list lays a page out — the part of a page's Android look that is not in its
 * [SettingsPage]. The system list on iOS has its own spacing and ignores this.
 *
 * Most pages are [Standard]. The others exist so that a page whose spacing predates the shared rows
 * keeps it exactly when it moves onto them; they are not a menu to pick new looks from.
 */
internal class SettingsPageLayout(
    val padding: PaddingValues,
    val gap: Dp,
    val footerPadding: PaddingValues = PaddingValues(horizontal = Spacing.xs),
    /** Row icons at 20dp in `onSurfaceVariant` rather than a list item's own size and colour. */
    val mutedIcons: Boolean = false,
) {
    companion object {
        val Standard = SettingsPageLayout(padding = SettingsPagePadding, gap = SettingsItemGap)

        /** 账号设置: wider gutters, a 12dp gap between groups, small muted icons. */
        val Account =
            SettingsPageLayout(
                padding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                gap = Spacing.md,
                mutedIcons = true,
            )

        /** 开源许可: the card gutter, a 12dp gap, and its closing note set a little further off. */
        val Licenses =
            SettingsPageLayout(
                padding = PaddingValues(horizontal = LayerPageGutter, vertical = Spacing.sm),
                gap = Spacing.md,
                footerPadding = PaddingValues(horizontal = Spacing.xs, vertical = Spacing.sm),
            )
    }
}

/**
 * A settings page from its description: the system list on iOS, this app's own cards and rows
 * everywhere else. A page written this way has one definition — the [SettingsPage] its screen builds.
 */
@Composable
internal fun SettingsPageScreen(
    page: SettingsPage,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    layout: SettingsPageLayout = SettingsPageLayout.Standard,
    snackbarHostState: SnackbarHostState? = null,
) {
    if (snackbarHostState == null || !nativeSettingsPages) {
        AdaptiveSettingsPage(page = page, onBack = onBack, modifier = modifier) {
            ComposeSettingsPage(page = page, onBack = onBack, modifier = modifier, layout = layout, snackbarHostState = snackbarHostState)
        }
        return
    }
    // The system list has no snackbar of its own; the Compose one sits over it, the way the shell's
    // own notices sit over every page.
    Box(modifier.fillMaxSize()) {
        AdaptiveSettingsPage(page = page, onBack = onBack, modifier = Modifier.fillMaxSize()) {}
        PlazaSnackbarHost(
            snackbarHostState,
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        )
    }
}

@Composable
private fun ComposeSettingsPage(
    page: SettingsPage,
    onBack: () -> Unit,
    modifier: Modifier,
    layout: SettingsPageLayout,
    snackbarHostState: SnackbarHostState?,
) {
    val appBarState = rememberOneHandAppBarState()
    var openChoice by remember { mutableStateOf<String?>(null) }
    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        snackbarHost = { snackbarHostState?.let { PlazaSnackbarHost(it) } },
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
                .padding(layout.padding),
            verticalArrangement = Arrangement.spacedBy(layout.gap),
        ) {
            page.intro?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            page.sections.forEach { section ->
                SectionContent(section, layout, onOpenChoice = { openChoice = it })
            }
        }
    }

    page.sections
        .flatMap { it.items }
        .map { it.nativeItem }
        .filterIsInstance<SettingsItem.Choice>()
        .firstOrNull { it.key == openChoice }
        ?.let { choice -> ChoiceSheet(choice, onDismiss = { openChoice = null }) }
}

/** Siblings in the page's column rather than a column of their own, so every gap is the layout's. */
@Composable
private fun SectionContent(
    section: SettingsSection,
    layout: SettingsPageLayout,
    onOpenChoice: (String) -> Unit,
) {
    section.header?.let { SectionLabel(it) }
    when (section.style) {
        SettingsSectionStyle.Prominent -> section.items.forEach { item -> ProminentItem(item, layout, onOpenChoice) }

        SettingsSectionStyle.Callout -> section.items.forEach { item -> CalloutItem(item, layout, onOpenChoice) }

        SettingsSectionStyle.Grouped ->
            if (section.items.isNotEmpty()) {
                SettingsGroup {
                    section.items.forEachIndexed { index, item ->
                        ItemRow(
                            item,
                            first = index == 0,
                            last = index == section.items.lastIndex,
                            layout = layout,
                            onOpenChoice = onOpenChoice,
                        )
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
                modifier = Modifier.padding(layout.footerPadding),
            )
        }
    }
}

/** The leading slot for [icon] under [layout]: the list item's own icon, or a small muted one. */
private fun leadingIcon(
    icon: ImageVector?,
    layout: SettingsPageLayout,
    tint: Color? = null,
): (@Composable () -> Unit)? =
    icon?.let {
        if (layout.mutedIcons) {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            { Icon(it, contentDescription = null) }
        }
    }

private fun Modifier.tagged(tag: String?): Modifier = tag?.let { testTag(it) } ?: this

@Composable
private fun ItemRow(
    item: SettingsItem,
    first: Boolean,
    last: Boolean,
    layout: SettingsPageLayout,
    onOpenChoice: (String) -> Unit,
) {
    when (item) {
        is SettingsItem.Custom -> item.compose(SettingsRowPosition(first = first, last = last))

        is SettingsItem.Toggle ->
            GroupedRow(
                title = item.title,
                subtitle = item.description,
                leading = leadingIcon(item.icon, layout),
                first = first,
                last = last,
                checked = item.checked,
                onCheckedChange = item.onCheckedChange,
                enabled = item.enabled,
                modifier = Modifier.tagged(item.testTag),
                trailing = { GroupedListItemSwitch(checked = item.checked, enabled = item.enabled) },
            )

        is SettingsItem.Choice -> ChoiceItemRow(item, first, last, layout, onOpenChoice)

        is SettingsItem.Slider ->
            // A block, not a row: the slider needs the line to itself. Never the group's last card —
            // `SettingsBlock` keeps its square bottom edge, as every slider on these pages always had.
            SettingsBlock(
                title = item.title,
                top = first,
                subtitle = item.description,
                value = item.valueLabel,
                enabled = item.enabled,
            ) {
                SettingsSlider(
                    value = item.value,
                    onValueChange = item.onValueChange,
                    onValueChangeFinished = item.onValueChangeFinished,
                    valueRange = item.range,
                    steps = item.steps,
                    modifier = Modifier.tagged(item.testTag),
                )
                item.sample?.let { sample ->
                    // At the size being chosen and at the post body's own line height for that size,
                    // so the sample answers "how will a post read" while the thumb is still moving.
                    Text(
                        sample.text,
                        style =
                        PostBody.copy(
                            fontSize = sample.fontSizeSp.sp,
                            lineHeight = (sample.fontSizeSp * PostBody.lineHeight.value / PostBody.fontSize.value).sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

        is SettingsItem.Link ->
            GroupedRow(
                title = item.title,
                subtitle = item.description ?: item.value,
                leading = leadingIcon(item.icon, layout),
                first = first,
                last = last,
                enabled = item.enabled,
                onClick = item.onClick,
                modifier = Modifier.tagged(item.testTag),
                showChevron = !item.external,
                trailing =
                when {
                    item.external -> {
                        {
                            Icon(
                                PlazaIcons.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(Spacing.md),
                            )
                        }
                    }

                    item.badge -> {
                        { UpdateDot() }
                    }

                    else -> null
                },
            )

        is SettingsItem.Action -> {
            val error = MaterialTheme.colorScheme.error
            GroupedRow(
                title = item.title,
                subtitle = item.description ?: item.value,
                leading = leadingIcon(item.icon, layout, tint = if (item.destructive) error else null),
                first = first,
                last = last,
                enabled = item.enabled,
                onClick = { if (!item.loading) item.onClick() },
                modifier = Modifier.tagged(item.testTag),
                contentColor = if (item.destructive) error else Color.Unspecified,
                showChevron = false,
                trailing =
                if (item.loading) {
                    { PlazaSpinner(Modifier.describedAsLoading(), size = 22.dp) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun ChoiceItemRow(
    item: SettingsItem.Choice,
    first: Boolean,
    last: Boolean,
    layout: SettingsPageLayout,
    onOpenChoice: (String) -> Unit,
) {
    when (item.presentation) {
        SettingsChoicePresentation.Sheet ->
            GroupedRow(
                title = item.title,
                subtitle = item.options.getOrNull(item.selectedIndex)?.label,
                leading = leadingIcon(item.icon, layout),
                first = first,
                last = last,
                enabled = item.enabled,
                onClick = { onOpenChoice(item.key) },
                modifier = Modifier.tagged(item.testTag),
            )

        SettingsChoicePresentation.Menu ->
            SettingsMenuRow(
                icon = item.icon,
                title = item.title,
                subtitle = item.description ?: item.options.getOrNull(item.selectedIndex)?.label,
                choices = item.options.indices.map { it to item.options[it].label },
                selected = item.selectedIndex,
                onSelect = item.onSelect,
                modifier = Modifier.tagged(item.testTag),
                first = first,
                last = last,
                enabled = item.enabled,
                isChoiceEnabled = { item.options[it].enabled },
            )

        SettingsChoicePresentation.Segmented ->
            // As the slider block: the segments take the line under the title, and the block never
            // takes the group's rounded bottom edge.
            SettingsBlock(
                title = item.title,
                top = first,
                subtitle = item.description,
                enabled = item.enabled,
            ) {
                ChoiceSegments(
                    labels = item.options.map { it.label },
                    selectedIndex = item.selectedIndex,
                    onSelect = item.onSelect,
                    enabled = item.enabled,
                    modifier = Modifier.tagged(item.testTag),
                )
            }
    }
}

/**
 * The master switch as 6d draws it: a group of its own in the primary container tone, larger type than
 * the rows under it, because everything else on the page is conditional on it.
 */
@Composable
private fun ProminentItem(
    item: SettingsItem,
    layout: SettingsPageLayout,
    onOpenChoice: (String) -> Unit,
) {
    if (item !is SettingsItem.Toggle) {
        ItemRow(item, first = true, last = true, layout = layout, onOpenChoice = onOpenChoice)
        return
    }
    val scheme = MaterialTheme.colorScheme
    GroupedListItem(
        first = true,
        last = true,
        checked = item.checked,
        onCheckedChange = item.onCheckedChange,
        enabled = item.enabled,
        modifier = Modifier.tagged(item.testTag),
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
    layout: SettingsPageLayout,
    onOpenChoice: (String) -> Unit,
) {
    if (item !is SettingsItem.Link) {
        ItemRow(item, first = true, last = true, layout = layout, onOpenChoice = onOpenChoice)
        return
    }
    val scheme = MaterialTheme.colorScheme
    LayerCard(onClick = item.onClick, modifier = Modifier.fillMaxWidth().tagged(item.testTag)) {
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
                choice.options.forEachIndexed { index, option ->
                    val isSelected = index == choice.selectedIndex
                    ChoiceRow(
                        label = option.label,
                        selected = isSelected,
                        onSelect = {
                            if (option.enabled) {
                                choice.onSelect(index)
                                onDismiss()
                            }
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
