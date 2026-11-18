package io.github.nodyssey.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.nodyssey.ui.common.MediumButton
import io.github.nodyssey.ui.common.MediumButtonStyle
import io.github.nodyssey.ui.common.SelectableMenuItem
import io.github.nodyssey.ui.common.bottomWithFloatingBar
import io.github.plaza.designsys.component.AdaptiveGlassTopBar
import io.github.plaza.designsys.component.AdaptiveNativeMenu
import io.github.plaza.designsys.component.AdaptiveSegmentedControl
import io.github.plaza.designsys.component.ChoiceSegments
import io.github.plaza.designsys.component.GlassAction
import io.github.plaza.designsys.component.GlassMenuAction
import io.github.plaza.designsys.component.GroupCard
import io.github.plaza.designsys.component.GroupedListItem
import io.github.plaza.designsys.component.GroupedRow
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.OneHandAppBarState
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaFieldDefaults
import io.github.plaza.designsys.component.ReadableTopBar
import io.github.plaza.designsys.component.groupedRowTitleStyle
import io.github.plaza.designsys.component.rememberFitsSegmentedControl
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.nativeChrome

internal val SettingsTopBarHeight = 48.dp

/**
 * Page backdrop for settings screens: keeps Material's page colour on Android/JVM and 墨水屏, while
 * falling back to `inset` on iOS when `page == card` (浅色「纯白」）so shadowless groups remain distinct.
 */
@Composable
internal fun settingsPageColor(): Color {
    val layers = LocalPlazaLayers.current
    return if (layers.nativeChrome && layers.page == layers.card) layers.inset else layers.page
}

/**
 * Scroll padding below the top bar that preserves [SettingsPagePadding] plus dynamic bottom safe area.
 */
@Composable
internal fun settingsScrollPadding(scaffoldPadding: PaddingValues): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = SettingsPagePadding.calculateStartPadding(layoutDirection),
        top = SettingsPagePadding.calculateTopPadding(),
        end = SettingsPagePadding.calculateEndPadding(layoutDirection),
        bottom = SettingsPagePadding.calculateBottomPadding() + scaffoldPadding.bottomWithFloatingBar(),
    )
}

/**
 * Unified top bar for the settings flow: reuses [AdaptiveGlassTopBar] on iOS with [OneHandTopAppBar]
 * unchanged on Android and desktop JVM.
 */
@Composable
internal fun SettingsTopBar(
    title: String,
    onBack: () -> Unit,
    backContentDescription: String,
    appBarState: OneHandAppBarState,
    actions: List<GlassAction> = emptyList(),
    fallbackActions: @Composable RowScope.() -> Unit = {},
) {
    val layers = LocalPlazaLayers.current
    if (!layers.nativeChrome) {
        OneHandTopAppBar(
            title = title,
            state = appBarState,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = backContentDescription,
                    )
                }
            },
            actions = fallbackActions,
        )
        return
    }
    val pageColor = settingsPageColor()
    ReadableTopBar { windowInsets ->
        AdaptiveGlassTopBar(
            title = title,
            titleAction = null,
            back = GlassAction(
                label = backContentDescription,
                onClick = onBack,
                symbol = "chevron.backward",
            ),
            actions = actions,
            containerColor = pageColor,
            modifier =
            Modifier
                .fillMaxWidth()
                .background(pageColor)
                .windowInsetsPadding(windowInsets)
                .height(SettingsTopBarHeight),
            fallback = {},
        )
    }
}

/**
 * One group: a single [GroupCard] around whatever it holds. Rows, but also the blocks that are not
 * rows — 主题's source picker and preset grid, 网络自检's report lines — which have no card of their own.
 */
@Composable
internal fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    GroupCard(
        modifier = modifier,
        elevated = !LocalPlazaLayers.current.nativeChrome,
        content = content,
    )
}

/** What a settings page scrolls inside: the 12dp card gutter the rest of the layer system uses. */
internal val SettingsPagePadding =
    PaddingValues(start = LayerPageGutter, end = LayerPageGutter, top = 4.dp, bottom = 24.dp)

/** Between one card and the next, and between a section label and its card. */
internal val SettingsItemGap = 8.dp

/** A row whose control needs its own line — a slider, a segmented button, a preview block. */
@Composable
internal fun SettingsBlock(
    title: String,
    top: Boolean = false,
    subtitle: String? = null,
    /** The control's current reading, in the primary colour at the end of the title line — 「16」. */
    value: String? = null,
    /**
     * Dims the label, and only the label.
     *
     * The control below it is the caller's — every one of them takes its own `enabled`, and dimming
     * the whole block on top of that would multiply the two and leave a segmented button too faint to
     * read as anything at all.
     */
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    GroupedListItem(
        first = top,
        last = false,
        headlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA),
            ) {
                Text(title, style = groupedRowTitleStyle(), modifier = Modifier.weight(1f))
                value?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        // The control rides in the supporting slot, under the title and at the text's indent, which is
        // where Material puts a row's second line — here it is a slider or a segmented button.
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                subtitle?.let {
                    Text(it, modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA))
                }
                Column(Modifier.padding(top = 2.dp), content = content)
            }
        },
    )
}

/**
 * A short one-of-a-few setting selector: keeps Material [ChoiceSegments] inside [SettingsBlock] on
 * Android/JVM, and uses [AdaptiveSegmentedControl] on iOS when all labels fit without clipping — or
 * drops cleanly to a [SettingsMenuRow] when translations or large text sizes overflow.
 */
@Composable
internal fun <T> SettingsChoiceSection(
    title: String,
    choices: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    top: Boolean = false,
    last: Boolean = false,
    subtitle: String? = null,
    enabled: Boolean = true,
    menuIcon: ImageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
    nativeChrome: Boolean = LocalPlazaLayers.current.nativeChrome,
) {
    if (!nativeChrome) {
        SettingsBlock(title = title, top = top, subtitle = subtitle, enabled = enabled) {
            ChoiceSegments(
                labels = choices.map { it.second },
                selectedIndex = choices.indexOfFirst { it.first == selected },
                onSelect = { onSelect(choices[it].first) },
                enabled = enabled,
            )
        }
        return
    }
    val labels = choices.map { it.second }
    val selectedIndex = choices.indexOfFirst { it.first == selected }
    val selectedLabel = choices.getOrNull(selectedIndex)?.second
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val availableWidth = (maxWidth - 32.dp).coerceAtLeast(0.dp)
        val fitsSegments =
            rememberFitsSegmentedControl(
                labels = labels,
                availableWidth = availableWidth,
                density = density,
            )
        if (fitsSegments) {
            SettingsBlock(title = title, top = top, subtitle = subtitle, enabled = enabled) {
                AdaptiveSegmentedControl(
                    labels = labels,
                    selectedIndex = selectedIndex,
                    onSelectIndex = { onSelect(choices[it].first) },
                    enabled = enabled,
                    label = title,
                ) {
                    ChoiceSegments(
                        labels = labels,
                        selectedIndex = selectedIndex,
                        onSelect = { onSelect(choices[it].first) },
                        enabled = enabled,
                    )
                }
            }
        } else {
            SettingsMenuRow(
                icon = icon,
                title = title,
                subtitle = subtitle,
                choices = choices,
                selected = selected,
                onSelect = onSelect,
                first = top,
                last = last,
                enabled = enabled,
                menuIcon = menuIcon,
                value = selectedLabel,
            )
        }
    }
}

/**
 * A row whose choices open in a menu rather than as rows of their own — 语言, 色彩风格 — with its
 * answer on the second line or in [value] beside the trailing icon.
 *
 * The menu hangs off the trailing icon and nothing wider, because a `DropdownMenu` is anchored to its
 * *parent* layout node — `Popup` reads `parentLayoutCoordinates`, not the position of its own
 * zero-sized node. Put it a level up and the anchor becomes the whole row, so the menu opens at the
 * row's bottom left however the enclosing box is aligned; with the icon as its parent, the menu ends
 * tucked under the control that opened it.
 */
@Composable
internal fun <T> SettingsMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    choices: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    first: Boolean = false,
    last: Boolean = false,
    enabled: Boolean = true,
    menuIcon: ImageVector = Icons.Default.ArrowDropDown,
    value: String? = null,
    isChoiceEnabled: (T) -> Boolean = { true },
) {
    val layers = LocalPlazaLayers.current
    if (layers.nativeChrome) {
        val selectedLabel = value ?: choices.firstOrNull { it.first == selected }?.second
        val accessibilityLabel =
            if (subtitle != null && selectedLabel != null && subtitle.startsWith(selectedLabel)) {
                "$title, $subtitle"
            } else {
                listOfNotNull(
                    title,
                    selectedLabel,
                    subtitle?.takeIf { it.isNotBlank() },
                ).joinToString(", ")
            }
        AdaptiveNativeMenu(
            label = accessibilityLabel,
            actions = choices.map { (choice, label) ->
                GlassMenuAction(
                    label = label,
                    onClick = { onSelect(choice) },
                    enabled = isChoiceEnabled(choice),
                    selected = choice == selected,
                )
            },
            enabled = enabled,
            cooperativeInScroll = true,
            fallback = { _, _ -> },
        ) {
            GroupedRow(
                icon = icon,
                title = title,
                subtitle = subtitle,
                value = value,
                first = first,
                last = last,
                enabled = enabled,
                onClick = if (enabled) {
                    {}
                } else {
                    null
                },
                showChevron = false,
                trailing = { Icon(menuIcon, contentDescription = null) },
            )
        }
        return
    }
    var expanded by remember { mutableStateOf(false) }
    GroupedRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        value = value,
        first = first,
        last = last,
        enabled = enabled,
        onClick = { expanded = true },
        showChevron = false,
        trailing = {
            Box {
                Icon(menuIcon, contentDescription = null)
                DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
                    choices.forEachIndexed { index, (choice, label) ->
                        SelectableMenuItem(
                            selected = choice == selected,
                            index = index,
                            count = choices.size,
                            text = { Text(label) },
                            enabled = isChoiceEnabled(choice),
                            onClick = {
                                expanded = false
                                onSelect(choice)
                            },
                        )
                    }
                }
            }
        },
    )
}

/**
 * What a [SettingsBlock]'s label dims to while its control is disabled — Material's own disabled
 * content alpha, so a block reads the same as the disabled rows around it.
 */
internal const val DISABLED_ALPHA = 0.38f

/** A text field on a settings card: one line unless told otherwise, its label inside, in the kit's in-card field style. */
@Composable
internal fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textStyle: TextStyle = LocalTextStyle.current,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = singleLine,
        isError = isError,
        textStyle = textStyle,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        supportingText = supportingText?.let { { Text(it) } },
        shape = PlazaFieldDefaults.shape,
        colors = PlazaFieldDefaults.colors(inCard = true),
    )
}

/**
 * 测试 and 保存, side by side as two pills: the tonal one checks, the filled one commits. Shared by
 * 代理 and 加密 DNS, whose two actions are the same pair.
 */
@Composable
internal fun SettingsTestSaveButtons(
    testLabel: String,
    saveLabel: String,
    testing: Boolean,
    enabled: Boolean,
    onTest: () -> Unit,
    onSave: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        MediumButton(
            onClick = onTest,
            style = MediumButtonStyle.Tonal,
            enabled = enabled && !testing,
            busy = testing,
            modifier = Modifier.weight(1f),
        ) {
            Text(testLabel)
        }
        MediumButton(onClick = onSave, enabled = enabled, modifier = Modifier.weight(1f)) {
            Text(saveLabel)
        }
    }
}
