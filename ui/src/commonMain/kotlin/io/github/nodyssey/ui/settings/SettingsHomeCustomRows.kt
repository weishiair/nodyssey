package io.github.nodyssey.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.FeedPost
import io.github.nodyssey.data.settings.UserSettings
import io.github.nodyssey.model.PostSummary
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.settings_search_avatar_preview
import io.github.nodyssey.ui.resources.settings_sticker_preview_caption
import io.github.nodyssey.ui.resources.settings_theme
import io.github.nodyssey.ui.resources.settings_theme_eink_hint
import io.github.nodyssey.ui.richtext.PostRichContent
import io.github.nodyssey.ui.search.SearchPostCard
import io.github.nodyssey.ui.settings.theme.ThemeSummaryDot
import io.github.nodyssey.ui.settings.theme.themeSummary
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode
import io.github.plaza.designsys.component.GroupedRow
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.richtext.LocalStickerSizing
import io.github.plaza.designsys.richtext.StickerSizing
import io.github.plaza.designsys.settings.SettingsChoicePresentation
import io.github.plaza.designsys.settings.SettingsItem
import io.github.plaza.designsys.settings.SettingsOption
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.Spacing
import org.jetbrains.compose.resources.stringResource

internal fun <T> settingsChoice(
    key: String,
    title: String,
    choices: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    presentation: SettingsChoicePresentation,
    description: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isChoiceEnabled: (T) -> Boolean = { true },
): SettingsItem.Choice =
    SettingsItem.Choice(
        key = key,
        title = title,
        options = choices.map { (value, label) -> SettingsOption(label, enabled = isChoiceEnabled(value)) },
        selectedIndex = choices.indexOfFirst { it.first == selected },
        onSelect = { onSelect(choices[it].first) },
        presentation = presentation,
        description = description,
        icon = icon,
        enabled = enabled,
    )

/** Keep the chevron and, where present, the trailing setting value in the Compose row. */
internal fun SettingsItem.Choice.withChevronMenu(subtitle: String? = description, value: String? = null): SettingsItem =
    SettingsItem.Custom(this) { position ->
        SettingsMenuRow(
            icon = icon,
            title = title,
            subtitle = subtitle,
            choices = options.mapIndexed { index, option -> index to option.label },
            selected = selectedIndex,
            onSelect = onSelect,
            first = position.first,
            last = position.last,
            enabled = enabled,
            menuIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            value = value,
            isChoiceEnabled = { options[it].enabled },
        )
    }

@Composable
internal fun themeSettingsLink(settings: UserSettings, onClick: () -> Unit): SettingsItem {
    val link = SettingsItem.Link(
        key = "theme",
        title = stringResource(Res.string.settings_theme),
        description = if (settings.einkMode) stringResource(Res.string.settings_theme_eink_hint) else themeSummary(settings),
        onClick = onClick,
        icon = PlazaIcons.Palette,
        enabled = !settings.einkMode,
    )
    return SettingsItem.Custom(link) { position ->
        GroupedRow(
            icon = link.icon,
            title = link.title,
            subtitle = link.description,
            first = position.first,
            last = position.last,
            enabled = link.enabled,
            onClick = link.onClick,
            trailing = { ThemeSummaryDot(settings = settings, enabled = link.enabled) },
        )
    }
}

/** Compose keeps the live previews drawn by the corresponding content renderer. */
internal fun SettingsItem.Slider.withPreview(preview: @Composable () -> Unit): SettingsItem =
    SettingsItem.Custom(this) { position ->
        SettingsBlock(title = title, top = position.first, value = valueLabel) {
            SettingsSlider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = range,
                steps = steps,
                modifier = testTag?.let { Modifier.testTag(it) } ?: Modifier,
            )
            preview()
        }
    }

/**
 * What 表情大小 buys, drawn by the renderer it changes.
 *
 * The same [PostRichContent] the thread uses, handed a line with two of the site's stickers in it,
 * under the size being dragged rather than the size stored — so the sample answers "how big next to
 * my body text" while the thumb is still moving. The stickers are fetched like any other: already in
 * Coil's cache for anyone who has opened a thread, a pair of fallback squares at the right size for
 * anyone who has not, which still shows the sizing this control is about.
 */
@Composable
internal fun StickerSizePreview(sizeSp: Int) {
    Surface(
        color = LocalPlazaLayers.current.inset,
        shape = MaterialTheme.shapes.medium,
    ) {
        CompositionLocalProvider(
            LocalStickerSizing provides StickerSizing(uniform = true, uniformSize = sizeSp.sp),
        ) {
            PostRichContent(
                nodes = stickerPreviewNodes(),
                onLinkClick = {},
                onImageClick = {},
                // Nothing here is worth copying, and a selection handle inside a settings row is a
                // way to lose the drag that was aimed at the slider.
                selectable = false,
                modifier = Modifier.padding(Spacing.md),
            )
        }
    }
}

@Composable
private fun stickerPreviewNodes(): List<RichNode> {
    val caption = stringResource(Res.string.settings_sticker_preview_caption)
    return remember(caption) {
        listOf(
            RichNode.Paragraph(
                listOf(
                    InlineNode.Text(caption),
                    InlineNode.Sticker(
                        url = NodeSeekSite.stickerUrl(group = "ac", code = "01", extension = "png"),
                        alt = "ac01",
                    ),
                    InlineNode.Sticker(
                        url = NodeSeekSite.stickerUrl(group = "yct", code = "001", extension = "gif"),
                        alt = "yct001",
                    ),
                ),
            ),
        )
    }
}

/**
 * A result card at the size being dragged, rather than a number beside a label.
 *
 * It is the screen's own [SearchPostCard], so the preview cannot drift from the thing it is
 * previewing — and the title beside the face answers the question the slider actually asks: how much
 * of a result the face costs.
 */
@Composable
internal fun SearchAvatarPreview(sizeDp: Int) {
    val title = stringResource(Res.string.settings_search_avatar_preview)
    Surface(
        color = LocalPlazaLayers.current.inset,
        shape = MaterialTheme.shapes.medium,
    ) {
        SearchPostCard(
            post = remember(title) { searchAvatarPreviewPost(title) },
            highlight = null,
            onClick = {},
            avatarSize = sizeDp.dp,
            modifier = Modifier.padding(Spacing.md),
        )
    }
}

private fun searchAvatarPreviewPost(title: String) =
    FeedPost(
        summary =
        PostSummary(
            postId = 0,
            title = title,
            authorName = "nodyssey",
            authorUid = 0,
            avatarUrl = null,
            // No board on purpose: the question is the face against the title, and a tag would
            // take width away from the thing being judged.
            categoryTitle = null,
            categorySlug = null,
            viewCount = null,
            commentCount = 3,
            lastActiveText = "1d",
            lastActiveTitle = null,
            isPinned = false,
            isLocked = false,
        ),
        isRead = false,
        newCommentCount = 0,
    )
