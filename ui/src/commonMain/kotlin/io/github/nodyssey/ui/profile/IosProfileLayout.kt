package io.github.nodyssey.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nodyssey.ui.common.LevelProgressLine
import io.github.nodyssey.ui.common.MediumButton
import io.github.nodyssey.ui.common.bottomWithFloatingBar
import io.github.nodyssey.ui.common.siteName
import io.github.nodyssey.ui.common.withoutBottom
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.accounts_switch
import io.github.nodyssey.ui.resources.accounts_title
import io.github.nodyssey.ui.resources.assets_signed_in
import io.github.nodyssey.ui.resources.assets_signing_in
import io.github.nodyssey.ui.resources.lottery_title
import io.github.nodyssey.ui.resources.mail_title
import io.github.nodyssey.ui.resources.mail_unread
import io.github.nodyssey.ui.resources.profile_attendance_board
import io.github.nodyssey.ui.resources.profile_attendance_checking
import io.github.nodyssey.ui.resources.profile_attendance_done
import io.github.nodyssey.ui.resources.profile_attendance_modes
import io.github.nodyssey.ui.resources.profile_attendance_title
import io.github.nodyssey.ui.resources.profile_chicken
import io.github.nodyssey.ui.resources.profile_guest_section
import io.github.nodyssey.ui.resources.profile_level
import io.github.nodyssey.ui.resources.profile_level_unknown
import io.github.nodyssey.ui.resources.profile_section_community
import io.github.nodyssey.ui.resources.profile_section_content
import io.github.nodyssey.ui.resources.profile_section_messages_events
import io.github.nodyssey.ui.resources.profile_section_settings_support
import io.github.nodyssey.ui.resources.profile_sign_in
import io.github.nodyssey.ui.resources.profile_sign_in_hint
import io.github.nodyssey.ui.resources.profile_signed_out_body
import io.github.nodyssey.ui.resources.profile_signed_out_title
import io.github.nodyssey.ui.resources.profile_space
import io.github.nodyssey.ui.resources.profile_stars
import io.github.nodyssey.ui.resources.profile_tile_about
import io.github.nodyssey.ui.resources.profile_tile_about_community
import io.github.nodyssey.ui.resources.profile_tile_account
import io.github.nodyssey.ui.resources.profile_tile_award
import io.github.nodyssey.ui.resources.profile_tile_block
import io.github.nodyssey.ui.resources.profile_tile_collections
import io.github.nodyssey.ui.resources.profile_tile_comments
import io.github.nodyssey.ui.resources.profile_tile_followers
import io.github.nodyssey.ui.resources.profile_tile_following
import io.github.nodyssey.ui.resources.profile_tile_friends
import io.github.nodyssey.ui.resources.profile_tile_history
import io.github.nodyssey.ui.resources.profile_tile_invite
import io.github.nodyssey.ui.resources.profile_tile_lucky
import io.github.nodyssey.ui.resources.profile_tile_notifications
import io.github.nodyssey.ui.resources.profile_tile_providers
import io.github.nodyssey.ui.resources.profile_tile_ruling
import io.github.nodyssey.ui.resources.profile_tile_theme
import io.github.nodyssey.ui.resources.profile_tile_topics
import io.github.nodyssey.ui.resources.profile_tools
import io.github.plaza.designsys.component.GroupCard
import io.github.plaza.designsys.component.GroupedRow
import io.github.plaza.designsys.component.IconBadge
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.SectionLabel
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.Sizes
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import io.github.plaza.designsys.theme.nativeChrome
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.stringResource

internal val ProfileTopBarHeight = 48.dp

/**
 * Keeps shadowless iOS grouped cards separated from the backdrop in Pure White light mode where
 * `layers.page == layers.card`, matching settings screens.
 */
@Composable
internal fun profilePageColor(): Color {
    val layers = LocalPlazaLayers.current
    return if (layers.nativeChrome && layers.page == layers.card) layers.inset else layers.page
}

internal data class IosProfileMetric(
    val label: String,
    val value: String,
    val onClick: () -> Unit,
)

/**
 * Signed-in 我的 screen for iOS: identity & quiet assets header, focused daily check-in card,
 * and grouped destination rows preserving every destination, unread badge, and wide-screen split.
 */
@Composable
internal fun IosProfileSignedInContent(
    state: ProfileUiState,
    destinations: ProfileDestinations,
    hasUnreadLotteryResults: Boolean,
    hasUnreadMail: Boolean,
    onAttendance: () -> Unit,
    onAttendanceBoard: () -> Unit,
    scaffoldPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val sections = iosProfileSections(destinations, hasUnreadLotteryResults, hasUnreadMail)
    val bottom = Spacing.lg + scaffoldPadding.bottomWithFloatingBar()
    val listState = rememberLazyListState()
    val leftScrollState = rememberScrollState()

    val account: @Composable () -> Unit = {
        IosAccountCard(
            state = state,
            onOpenSpace = destinations.space,
            onCredit = destinations.credit,
            onStardust = destinations.stardust,
            onAssets = destinations.assets,
            onSwitchAccount = destinations.accounts,
        )
    }
    val attendance: @Composable () -> Unit = {
        IosAttendanceCard(
            state = state,
            onAttendance = onAttendance,
            onAttendanceBoard = onAttendanceBoard,
        )
    }

    BoxWithConstraints(modifier.padding(scaffoldPadding.withoutBottom()).fillMaxSize()) {
        if (maxWidth >= PROFILE_TWO_COLUMN_MIN_WIDTH) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .widthIn(max = PROFILE_TWO_COLUMN_MAX_WIDTH)
                    .padding(horizontal = LayerPageGutter),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Column(
                    modifier = Modifier
                        .weight(PROFILE_ACCOUNT_COLUMN_WEIGHT)
                        .verticalScroll(leftScrollState)
                        .padding(top = Spacing.xs, bottom = bottom),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    account()
                    attendance()
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f - PROFILE_ACCOUNT_COLUMN_WEIGHT).fillMaxHeight(),
                    contentPadding = PaddingValues(top = Spacing.xs, bottom = bottom),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    sections.forEach { section ->
                        item(key = section.title.key) { IosProfileSectionGroup(section) }
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().readableWidth(),
                contentPadding = PaddingValues(start = LayerPageGutter, end = LayerPageGutter, top = Spacing.xs, bottom = bottom),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                item(key = "account") { account() }
                item(key = "attendance") {
                    Spacer(Modifier.height(Spacing.xs))
                    attendance()
                }
                sections.forEach { section ->
                    item(key = section.title.key) { IosProfileSectionGroup(section) }
                }
            }
        }
    }
}

@Composable
internal fun IosAccountCard(
    state: ProfileUiState,
    onOpenSpace: () -> Unit,
    onCredit: () -> Unit,
    onStardust: () -> Unit,
    onAssets: () -> Unit,
    onSwitchAccount: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val layers = LocalPlazaLayers.current
    LayerCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.largeIncreased,
        contentPadding = PaddingValues(0.dp),
        verticalArrangement = Arrangement.Top,
        elevated = false,
    ) {
        IosIdentityHeader(
            state = state,
            onOpenSpace = onOpenSpace,
            onSwitchAccount = onSwitchAccount,
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = Spacing.lg),
            thickness = 1.dp,
            color = layers.divider,
        )
        val metrics =
            listOf(
                IosProfileMetric(
                    label = stringResource(Res.string.profile_chicken),
                    value = state.chickenCount?.toString() ?: UNKNOWN,
                    onClick = onCredit,
                ),
                IosProfileMetric(
                    label = stringResource(Res.string.profile_stars),
                    value = state.starCount?.toString() ?: UNKNOWN,
                    onClick = onStardust,
                ),
                IosProfileMetric(
                    label = stringResource(Res.string.profile_level),
                    value = state.level ?: stringResource(Res.string.profile_level_unknown),
                    onClick = onAssets,
                ),
            )
        IosAssetMetricsStrip(metrics)
        state.levelProgress?.let { progress ->
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = Spacing.lg),
                thickness = 1.dp,
                color = layers.divider,
            )
            Box(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
                LevelProgressLine(progress)
            }
        }
    }
}

@Composable
private fun IosIdentityHeader(
    state: ProfileUiState,
    onOpenSpace: () -> Unit,
    onSwitchAccount: (() -> Unit)?,
) {
    val spaceLabel = stringResource(Res.string.profile_space)
    val subtitle = memberSubtitle(state)
    val switchLabel = stringResource(Res.string.accounts_switch)
    val switchTextStyle = MaterialTheme.typography.labelMedium
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()

    // Keep the switch button outside the clickable identity row so tapping or focusing switch in
    // VoiceOver never triggers personal space navigation.
    val identityContent: @Composable (Modifier) -> Unit = { rowModifier ->
        Row(
            modifier = rowModifier
                .heightIn(min = Sizes.minTouchTarget)
                .clip(MaterialTheme.shapes.large)
                .clickable(onClickLabel = spaceLabel, onClick = onOpenSpace)
                .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            UserAvatar(url = state.avatarUrl, name = state.displayName, size = 48.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = state.displayName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onSwitchAccount == null) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = 10.dp),
    ) {
        if (onSwitchAccount == null) {
            identityContent(Modifier.fillMaxWidth())
        } else {
            val inlineSwitch =
                remember(switchLabel, maxWidth, density.density, density.fontScale, switchTextStyle) {
                    with(density) {
                        val labelWidth =
                            measurer.measure(
                                text = switchLabel,
                                style = switchTextStyle,
                                maxLines = 1,
                                density = density,
                            ).size.width
                        val switchWidth = labelWidth.toDp() + 56.dp
                        val remainingForText = maxWidth - 48.dp - Spacing.md - Spacing.sm - switchWidth - Spacing.sm
                        remainingForText >= 132.dp
                    }
                }
            if (inlineSwitch) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    identityContent(Modifier.weight(1f))
                    AccountSwitchButton(onClick = onSwitchAccount)
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    identityContent(Modifier.fillMaxWidth())
                    Box(Modifier.padding(start = Spacing.xs)) {
                        AccountSwitchButton(onClick = onSwitchAccount)
                    }
                }
            }
        }
    }
}

@Composable
private fun IosAssetMetricsStrip(metrics: List<IosProfileMetric>) {
    val layers = LocalPlazaLayers.current
    val labelStyle = MaterialTheme.typography.labelSmall
    val baseBody = MaterialTheme.typography.bodyLarge
    val valueStyle =
        baseBody.copy(
            fontSize = baseBody.fontSize * 1.125f,
            lineHeight = baseBody.fontSize * 1.45f,
            fontWeight = FontWeight.Bold,
            fontFeatureSettings = TABULAR_FIGURES,
        )
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val metricTexts = metrics.map { it.label to it.value }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fitsColumns =
            remember(metricTexts, maxWidth, density.density, density.fontScale, labelStyle, valueStyle) {
                if (maxWidth <= 0.dp) return@remember false
                with(density) {
                    val columnInnerPx = ((maxWidth - 2.dp) / metrics.size - 20.dp).toPx()
                    if (columnInnerPx <= 0f) return@remember false
                    metricTexts.all { (label, value) ->
                        val labelWidth =
                            measurer.measure(
                                text = label,
                                style = labelStyle,
                                maxLines = 1,
                                density = density,
                            ).size.width
                        val valueWidth =
                            measurer.measure(
                                text = value,
                                style = valueStyle,
                                maxLines = 1,
                                density = density,
                            ).size.width
                        labelWidth <= columnInnerPx && valueWidth <= columnInnerPx
                    }
                }
            }
        if (fitsColumns) {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                metrics.forEachIndexed { index, metric ->
                    if (index > 0) {
                        VerticalDivider(
                            modifier = Modifier.fillMaxHeight().padding(vertical = 10.dp),
                            thickness = 1.dp,
                            color = layers.divider,
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .heightIn(min = 56.dp)
                            .clickable(role = Role.Button, onClick = metric.onClick)
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text(
                            text = metric.label,
                            style = labelStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = metric.value,
                            style = valueStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                metrics.forEachIndexed { index, metric ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = Spacing.lg),
                            thickness = 1.dp,
                            color = layers.divider,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Sizes.minTouchTarget)
                            .clickable(role = Role.Button, onClick = metric.onClick)
                            .padding(horizontal = Spacing.lg, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Text(
                            text = metric.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = metric.value,
                            style = valueStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun IosAttendanceCard(
    state: ProfileUiState,
    onAttendance: () -> Unit,
    onAttendanceBoard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GroupCard(modifier = modifier, elevated = false) {
        if (state.hasSignedInToday) {
            val receiptText =
                state.attendanceGain?.let { stringResource(Res.string.assets_signed_in, it) }
                    ?: state.attendanceMessage
                    ?: stringResource(Res.string.profile_attendance_done)
            val boardLabel =
                if (state.attendanceGain != null || state.attendanceMessage != null) {
                    stringResource(Res.string.profile_attendance_board)
                } else {
                    null
                }
            GroupedRow(
                title = receiptText,
                leading = {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                value = boardLabel,
                first = true,
                last = true,
                onClick = onAttendanceBoard,
            )
        } else {
            val busy = state.isSigningIn || state.isAttendanceUnknown
            Column(
                modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                MediumButton(
                    onClick = onAttendance,
                    enabled = !busy,
                    busy = busy,
                    icon = PlazaIcons.EventAvailable,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text =
                        when {
                            state.isSigningIn -> stringResource(Res.string.assets_signing_in)
                            state.isAttendanceUnknown -> stringResource(Res.string.profile_attendance_checking)
                            else -> stringResource(Res.string.profile_attendance_title)
                        },
                    )
                }
                if (!busy) {
                    Text(
                        text = stringResource(Res.string.profile_attendance_modes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            GroupedRow(
                title = stringResource(Res.string.profile_attendance_board),
                first = false,
                last = true,
                onClick = onAttendanceBoard,
            )
        }
    }
}

@Composable
private fun IosProfileSectionGroup(section: ProfileSection) {
    Column {
        SectionLabel(stringResource(section.title))
        Spacer(Modifier.height(Spacing.xs))
        ProfileGroupedRows(section.tiles)
    }
}

@Composable
private fun ProfileGroupedRows(tiles: List<ProfileTile>) {
    GroupCard(elevated = false) {
        tiles.forEachIndexed { index, tile ->
            GroupedRow(
                title = stringResource(tile.label),
                icon = tile.icon,
                first = index == 0,
                last = index == tiles.lastIndex,
                onClick = tile.onClick,
                trailing =
                if (tile.hasUnreadResults) {
                    {
                        val description = stringResource(tile.unreadDescription)
                        Badge(Modifier.semantics { contentDescription = description })
                    }
                } else {
                    null
                },
            )
        }
    }
}

private fun iosProfileSections(
    destinations: ProfileDestinations,
    hasUnreadLotteryResults: Boolean,
    hasUnreadMail: Boolean,
): List<ProfileSection> =
    listOf(
        ProfileSection(
            title = Res.string.profile_section_content,
            tiles =
            listOf(
                ProfileTile(Res.string.profile_tile_collections, PlazaIcons.Bookmark, destinations.collections),
                ProfileTile(Res.string.profile_tile_history, PlazaIcons.History, destinations.history),
                ProfileTile(Res.string.profile_tile_topics, PlazaIcons.Article, destinations.topics),
                ProfileTile(Res.string.profile_tile_comments, PlazaIcons.ChatBubble, destinations.comments),
                ProfileTile(Res.string.profile_tile_following, Icons.Default.Person, destinations.following),
                ProfileTile(Res.string.profile_tile_followers, PlazaIcons.Group, destinations.followers),
            ),
        ),
        ProfileSection(
            title = Res.string.profile_section_messages_events,
            tiles =
            listOf(
                ProfileTile(
                    Res.string.mail_title,
                    Icons.Default.Email,
                    destinations.mail,
                    hasUnreadMail,
                    Res.string.mail_unread,
                ),
                ProfileTile(
                    Res.string.lottery_title,
                    PlazaIcons.Alarm,
                    destinations.lotteryAssistant,
                    hasUnreadLotteryResults,
                ),
            ),
        ),
        ProfileSection(
            title = Res.string.profile_section_community,
            tiles =
            listOf(
                ProfileTile(Res.string.profile_tile_award, PlazaIcons.MenuBook, destinations.award),
                ProfileTile(Res.string.profile_tile_lucky, PlazaIcons.Casino, destinations.lucky),
                ProfileTile(Res.string.profile_tile_ruling, PlazaIcons.Gavel, destinations.ruling),
                ProfileTile(Res.string.profile_tile_providers, Icons.Default.ShoppingCart, destinations.providers),
                ProfileTile(Res.string.profile_tile_friends, PlazaIcons.Link, destinations.friends),
                ProfileTile(Res.string.profile_tile_block, PlazaIcons.Block, destinations.blockList),
                ProfileTile(Res.string.profile_tile_invite, PlazaIcons.ConfirmationNumber, destinations.invite),
                ProfileTile(Res.string.profile_tile_about_community, PlazaIcons.Forum, destinations.aboutCommunity),
            ),
        ),
        ProfileSection(
            title = Res.string.profile_section_settings_support,
            tiles =
            listOf(
                ProfileTile(Res.string.profile_tile_account, PlazaIcons.Badge, destinations.accountSettings),
                ProfileTile(
                    Res.string.profile_tile_notifications,
                    Icons.Default.Notifications,
                    destinations.notificationSettings,
                ),
                ProfileTile(Res.string.profile_tile_theme, PlazaIcons.Palette, destinations.themeSettings),
                ProfileTile(Res.string.profile_tile_about, Icons.Default.Info, destinations.about),
            ),
        ),
    )

/**
 * Signed-out 我的 screen for iOS: streamlined account placeholder and sign-in action, conditional
 * account-manager action, and grouped guest-accessible tools with unread indicators.
 */
@Composable
internal fun IosSignedOutProfile(
    onSignIn: () -> Unit,
    destinations: ProfileDestinations,
    hasUnreadLotteryResults: Boolean,
    hasUnreadMail: Boolean,
    scaffoldPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val bottom = Spacing.lg + scaffoldPadding.bottomWithFloatingBar()
    val signInCard: @Composable () -> Unit = {
        LayerCard(
            shape = MaterialTheme.shapes.largeIncreased,
            contentPadding = PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            elevated = false,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                IconBadge(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    size = 48.dp,
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(24.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(Res.string.profile_signed_out_title, siteName),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(Res.string.profile_signed_out_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            MediumButton(
                onClick = onSignIn,
                icon = PlazaIcons.Login,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.profile_sign_in, siteName))
            }
            destinations.accounts?.let { open ->
                TextButton(
                    onClick = open,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(stringResource(Res.string.accounts_title))
                }
            }
            Text(
                text = stringResource(Res.string.profile_sign_in_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    val guestTools =
        listOf(
            ProfileTile(
                Res.string.mail_title,
                Icons.Default.Email,
                destinations.mail,
                hasUnreadMail,
                Res.string.mail_unread,
            ),
            ProfileTile(
                Res.string.lottery_title,
                PlazaIcons.Alarm,
                destinations.lotteryAssistant,
                hasUnreadLotteryResults,
            ),
            ProfileTile(Res.string.profile_tile_award, PlazaIcons.MenuBook, destinations.award),
            ProfileTile(Res.string.profile_tile_lucky, PlazaIcons.Casino, destinations.lucky),
            ProfileTile(Res.string.profile_tile_ruling, PlazaIcons.Gavel, destinations.ruling),
            ProfileTile(Res.string.profile_tile_providers, Icons.Default.ShoppingCart, destinations.providers),
            ProfileTile(Res.string.profile_tools, PlazaIcons.DashboardCustomize, destinations.tools),
        )

    val guestToolsSection: @Composable () -> Unit = {
        Column {
            SectionLabel(stringResource(Res.string.profile_guest_section))
            Spacer(Modifier.height(Spacing.xs))
            ProfileGroupedRows(guestTools)
        }
    }

    val leftScroll = rememberScrollState()
    val listState = rememberLazyListState()
    BoxWithConstraints(modifier.padding(scaffoldPadding.withoutBottom()).fillMaxSize()) {
        if (maxWidth >= PROFILE_TWO_COLUMN_MIN_WIDTH) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .widthIn(max = PROFILE_TWO_COLUMN_MAX_WIDTH)
                    .padding(horizontal = LayerPageGutter),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Column(
                    modifier = Modifier
                        .weight(PROFILE_ACCOUNT_COLUMN_WEIGHT)
                        .verticalScroll(leftScroll)
                        .padding(top = Spacing.xs, bottom = bottom),
                ) {
                    signInCard()
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f - PROFILE_ACCOUNT_COLUMN_WEIGHT).fillMaxHeight(),
                    contentPadding = PaddingValues(top = Spacing.xs, bottom = bottom),
                ) {
                    item(key = "guest-tools") { guestToolsSection() }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().readableWidth(),
                contentPadding = PaddingValues(start = LayerPageGutter, end = LayerPageGutter, top = Spacing.xs, bottom = bottom),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                item(key = "sign-in") { signInCard() }
                item(key = "guest-tools") { guestToolsSection() }
            }
        }
    }
}
