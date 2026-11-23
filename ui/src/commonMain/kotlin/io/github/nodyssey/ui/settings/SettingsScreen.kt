package io.github.nodyssey.ui.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.data.AttendanceMode
import io.github.nodyssey.data.attendance.AutoAttendanceConfig
import io.github.nodyssey.data.attendance.AutoAttendanceState
import io.github.nodyssey.data.dns.DohServer
import io.github.nodyssey.data.proxy.ProxyType
import io.github.nodyssey.data.settings.AccountBadgeStyle
import io.github.nodyssey.data.settings.AppLanguage
import io.github.nodyssey.data.settings.PostReplyStyle
import io.github.nodyssey.data.settings.ReadMarkStyle
import io.github.nodyssey.data.settings.ReportFormat
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.data.settings.ThemeMode
import io.github.nodyssey.data.settings.UserSettings
import io.github.nodyssey.ui.account.shortNameRes
import io.github.nodyssey.ui.common.clipboardPostPromptSupported
import io.github.nodyssey.ui.common.rememberDisplayRefreshRates
import io.github.nodyssey.ui.common.rememberFileSizeLabel
import io.github.nodyssey.ui.postdetail.accountBadgeStyleInForce
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.doh_paused_for_proxy
import io.github.nodyssey.ui.resources.imagehost_connected
import io.github.nodyssey.ui.resources.imagehost_not_connected
import io.github.nodyssey.ui.resources.imagehost_title
import io.github.nodyssey.ui.resources.keywords_block_title
import io.github.nodyssey.ui.resources.keywords_summary_count
import io.github.nodyssey.ui.resources.keywords_summary_none
import io.github.nodyssey.ui.resources.notify_master_title
import io.github.nodyssey.ui.resources.notify_settings_title
import io.github.nodyssey.ui.resources.profile_tile_notifications
import io.github.nodyssey.ui.resources.proxy_type_http
import io.github.nodyssey.ui.resources.proxy_type_socks
import io.github.nodyssey.ui.resources.settings_about
import io.github.nodyssey.ui.resources.settings_about_app
import io.github.nodyssey.ui.resources.settings_about_app_update
import io.github.nodyssey.ui.resources.settings_account_badge_style
import io.github.nodyssey.ui.resources.settings_account_badge_style_hint
import io.github.nodyssey.ui.resources.settings_account_badge_style_natural
import io.github.nodyssey.ui.resources.settings_account_badge_style_vivid
import io.github.nodyssey.ui.resources.settings_app_links
import io.github.nodyssey.ui.resources.settings_app_links_summary_off
import io.github.nodyssey.ui.resources.settings_app_links_summary_on
import io.github.nodyssey.ui.resources.settings_appearance
import io.github.nodyssey.ui.resources.settings_body_size
import io.github.nodyssey.ui.resources.settings_clear_cache
import io.github.nodyssey.ui.resources.settings_clear_cache_size
import io.github.nodyssey.ui.resources.settings_clipboard_post
import io.github.nodyssey.ui.resources.settings_clipboard_post_hint
import io.github.nodyssey.ui.resources.settings_doh_entry
import io.github.nodyssey.ui.resources.settings_doh_summary_chain
import io.github.nodyssey.ui.resources.settings_eink
import io.github.nodyssey.ui.resources.settings_eink_hint
import io.github.nodyssey.ui.resources.settings_entry_off
import io.github.nodyssey.ui.resources.settings_home_page_bar
import io.github.nodyssey.ui.resources.settings_home_page_bar_hint
import io.github.nodyssey.ui.resources.settings_language
import io.github.nodyssey.ui.resources.settings_language_en
import io.github.nodyssey.ui.resources.settings_language_restart_hint
import io.github.nodyssey.ui.resources.settings_language_system
import io.github.nodyssey.ui.resources.settings_language_zh_hans
import io.github.nodyssey.ui.resources.settings_language_zh_hant
import io.github.nodyssey.ui.resources.settings_licenses
import io.github.nodyssey.ui.resources.settings_network
import io.github.nodyssey.ui.resources.settings_network_check_entry
import io.github.nodyssey.ui.resources.settings_notify_summary_every
import io.github.nodyssey.ui.resources.settings_notify_summary_quiet
import io.github.nodyssey.ui.resources.settings_notify_summary_unavailable
import io.github.nodyssey.ui.resources.settings_one_hand
import io.github.nodyssey.ui.resources.settings_one_hand_hint
import io.github.nodyssey.ui.resources.settings_post_reply_style
import io.github.nodyssey.ui.resources.settings_post_reply_style_cards
import io.github.nodyssey.ui.resources.settings_post_reply_style_flat
import io.github.nodyssey.ui.resources.settings_post_reply_style_hint
import io.github.nodyssey.ui.resources.settings_post_reply_style_original
import io.github.nodyssey.ui.resources.settings_predictive_back
import io.github.nodyssey.ui.resources.settings_predictive_back_hint
import io.github.nodyssey.ui.resources.settings_proxy_entry
import io.github.nodyssey.ui.resources.settings_read_mark
import io.github.nodyssey.ui.resources.settings_read_mark_block
import io.github.nodyssey.ui.resources.settings_read_mark_dim
import io.github.nodyssey.ui.resources.settings_read_mark_hint
import io.github.nodyssey.ui.resources.settings_read_mark_off
import io.github.nodyssey.ui.resources.settings_read_mark_unread_block
import io.github.nodyssey.ui.resources.settings_refresh_rate
import io.github.nodyssey.ui.resources.settings_refresh_rate_hint
import io.github.nodyssey.ui.resources.settings_refresh_rate_value
import io.github.nodyssey.ui.resources.settings_report_format
import io.github.nodyssey.ui.resources.settings_report_format_adapted
import io.github.nodyssey.ui.resources.settings_report_format_source
import io.github.nodyssey.ui.resources.settings_search_avatar
import io.github.nodyssey.ui.resources.settings_search_avatar_hint
import io.github.nodyssey.ui.resources.settings_search_avatar_size
import io.github.nodyssey.ui.resources.settings_search_history_limit
import io.github.nodyssey.ui.resources.settings_search_history_limit_count
import io.github.nodyssey.ui.resources.settings_search_history_limit_hint
import io.github.nodyssey.ui.resources.settings_section_compose
import io.github.nodyssey.ui.resources.settings_section_interaction
import io.github.nodyssey.ui.resources.settings_section_lists
import io.github.nodyssey.ui.resources.settings_section_storage
import io.github.nodyssey.ui.resources.settings_section_thread
import io.github.nodyssey.ui.resources.settings_show_author_account_info
import io.github.nodyssey.ui.resources.settings_show_author_account_info_hint
import io.github.nodyssey.ui.resources.settings_show_home_pinned_posts
import io.github.nodyssey.ui.resources.settings_show_home_pinned_posts_hint
import io.github.nodyssey.ui.resources.settings_sticker_size
import io.github.nodyssey.ui.resources.settings_sticker_uniform
import io.github.nodyssey.ui.resources.settings_sticker_uniform_hint
import io.github.nodyssey.ui.resources.settings_text_preview
import io.github.nodyssey.ui.resources.settings_theme_dark
import io.github.nodyssey.ui.resources.settings_theme_light
import io.github.nodyssey.ui.resources.settings_theme_mode
import io.github.nodyssey.ui.resources.settings_theme_system
import io.github.nodyssey.ui.resources.settings_title
import io.github.nodyssey.ui.resources.settings_update_dev_channel
import io.github.nodyssey.ui.resources.settings_update_dev_channel_hint
import io.github.nodyssey.ui.resources.settings_update_on_launch
import io.github.nodyssey.ui.resources.settings_version
import io.github.nodyssey.ui.resources.settings_wifi_images
import io.github.nodyssey.ui.resources.settings_wifi_images_hint
import io.github.nodyssey.ui.resources.sticker_manage_tab_mine
import io.github.nodyssey.ui.resources.sticker_manage_tab_subs
import io.github.nodyssey.ui.resources.sticker_manage_title
import io.github.nodyssey.ui.sticker.LocalStickerLibrary
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.settings.SettingsChoicePresentation
import io.github.plaza.designsys.settings.SettingsItem
import io.github.plaza.designsys.settings.SettingsPage
import io.github.plaza.designsys.settings.SettingsSample
import io.github.plaza.designsys.settings.SettingsSection
import io.github.plaza.designsys.theme.LocalOneHandModeAvailable
import io.github.plaza.designsys.theme.PlazaTheme
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenProxy: () -> Unit,
    onOpenDoh: () -> Unit,
    onOpenNetworkCheck: () -> Unit,
    onOpenImageHost: () -> Unit,
    onOpenStickers: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenBlockKeywords: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val attendanceConfig by viewModel.attendanceConfig.collectAsStateWithLifecycle()
    val attendanceState by viewModel.attendanceState.collectAsStateWithLifecycle()
    // Read here rather than in the ViewModel: it is a fact about the system's settings, not about
    // this app's, and it changes while the user is away on a screen we do not own.
    val appLinkHandlingEnabled = rememberAppLinkHandlingEnabled()
    val openAppLinkSettings = rememberAppLinkSettingsLauncher()
    SettingsScreen(
        state = state,
        appLinkHandlingEnabled = appLinkHandlingEnabled,
        onOpenAppLinkSettings = openAppLinkSettings,
        onBack = onBack,
        onOpenTheme = onOpenTheme,
        attendanceConfig = attendanceConfig,
        attendanceState = attendanceState,
        onAutoAttendanceEnabledChange = viewModel::setAutoAttendanceEnabled,
        onAutoAttendanceModeChange = viewModel::setAutoAttendanceMode,
        onThemeModeChange = viewModel::setThemeMode,
        onReadMarkStyleChange = viewModel::setReadMarkStyle,
        onReadMarkColorChange = viewModel::setReadMarkColor,
        onAppLanguageChange = viewModel::setAppLanguage,
        onOneHandModeChange = viewModel::setOneHandMode,
        onPredictiveBackChange = viewModel::setPredictiveBack,
        onPostReplyStyleChange = viewModel::setPostReplyStyle,
        onShowAuthorAccountInfoChange = viewModel::setShowAuthorAccountInfo,
        onAccountBadgeStyleChange = viewModel::setAccountBadgeStyle,
        onSearchHistoryLimitChange = viewModel::setSearchHistoryLimit,
        onEinkModeChange = viewModel::setEinkMode,
        supportedRefreshRates = rememberDisplayRefreshRates(),
        onDisplayRefreshRateChange = viewModel::setDisplayRefreshRate,
        onFontScaleChange = viewModel::setFontScale,
        onStickerUniformSizeChange = viewModel::setStickerUniformSize,
        onStickerSizeChange = viewModel::setStickerSize,
        onSearchAvatarChange = viewModel::setSearchAvatarEnabled,
        onSearchAvatarSizeChange = viewModel::setSearchAvatarSize,
        onImagesOnWifiOnlyChange = viewModel::setImagesOnWifiOnly,
        onReportFormatChange = viewModel::setReportFormat,
        onHomePageBarChange = viewModel::setHomePageBar,
        onShowHomePinnedPostsChange = viewModel::setShowHomePinnedPosts,
        onClipboardPostPromptChange = viewModel::setClipboardPostPrompt.takeIf { clipboardPostPromptSupported },
        onUpdateCheckOnLaunchChange = viewModel::setUpdateCheckOnLaunch,
        onUpdateDevChannelChange = viewModel::setUpdateDevChannel,
        onClearCache = viewModel::clearCache,
        onOpenNotifications = onOpenNotifications,
        onOpenProxy = onOpenProxy,
        onOpenDoh = onOpenDoh,
        onOpenNetworkCheck = onOpenNetworkCheck,
        onOpenImageHost = onOpenImageHost,
        onOpenStickers = onOpenStickers,
        onOpenAbout = onOpenAbout,
        onOpenLicenses = onOpenLicenses,
        onOpenBlockKeywords = onOpenBlockKeywords,
        modifier = modifier,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    /** Null where the system has no such switch — see [appLinkHandlingEnabled]. */
    appLinkHandlingEnabled: Boolean?,
    onOpenAppLinkSettings: () -> Unit,
    onBack: () -> Unit,
    onOpenTheme: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onReadMarkStyleChange: (ReadMarkStyle) -> Unit,
    onReadMarkColorChange: (Int?) -> Unit,
    onOneHandModeChange: (Boolean) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onStickerUniformSizeChange: (Boolean) -> Unit,
    onStickerSizeChange: (Int) -> Unit,
    onImagesOnWifiOnlyChange: (Boolean) -> Unit,
    onReportFormatChange: (ReportFormat) -> Unit,
    onHomePageBarChange: (Boolean) -> Unit,
    onUpdateCheckOnLaunchChange: (Boolean) -> Unit,
    onUpdateDevChannelChange: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier,
    onShowHomePinnedPostsChange: (Boolean) -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenProxy: () -> Unit = {},
    onOpenDoh: () -> Unit = {},
    onOpenNetworkCheck: () -> Unit = {},
    onOpenImageHost: () -> Unit = {},
    onOpenStickers: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onOpenLicenses: () -> Unit = {},
    onOpenBlockKeywords: () -> Unit = {},
    onAppLanguageChange: (AppLanguage) -> Unit = {},
    onEinkModeChange: (Boolean) -> Unit = {},
    supportedRefreshRates: Set<Int>? = null,
    onDisplayRefreshRateChange: (Int) -> Unit = {},
    onPredictiveBackChange: (Boolean) -> Unit = {},
    onPostReplyStyleChange: (PostReplyStyle) -> Unit = {},
    onShowAuthorAccountInfoChange: (Boolean) -> Unit = {},
    onAccountBadgeStyleChange: (AccountBadgeStyle) -> Unit = {},
    onSearchHistoryLimitChange: (Int) -> Unit = {},
    /** Null hides 识别剪贴板中的帖子: a platform where reading the clipboard costs a prompt each time. */
    onClipboardPostPromptChange: ((Boolean) -> Unit)? = null,
    attendanceConfig: AutoAttendanceConfig = AutoAttendanceConfig(),
    attendanceState: AutoAttendanceState = AutoAttendanceState(),
    onAutoAttendanceEnabledChange: (Boolean) -> Unit = {},
    onAutoAttendanceModeChange: (AttendanceMode) -> Unit = {},
    onSearchAvatarChange: (Boolean) -> Unit = {},
    onSearchAvatarSizeChange: (Int) -> Unit = {},
    backgroundPollingSupported: Boolean = platformHasBackgroundNotificationPolling,
    updateChecksSupported: Boolean = platformHasUpdateChecks,
) {
    val settings = state.settings
    var bodyFontSize by remember(settings.fontScale) {
        mutableFloatStateOf(fontScaleToBodySize(settings.fontScale))
    }
    // Keep the drag local and store the released size once. A write per frame would update every open post body.
    var stickerSize by remember(settings.stickerSize) {
        mutableFloatStateOf(settings.stickerSize.toFloat())
    }
    var searchAvatarSize by remember(settings.searchAvatarSize) {
        mutableFloatStateOf(settings.searchAvatarSize.toFloat())
    }
    var pickingReadMarkColor by remember { mutableStateOf(false) }

    val appearance = buildList<SettingsItem> {
        // 明暗 stays one tap from 设置 because it is reached far more often than the other theme controls.
        // 墨水屏模式 forces light and disables both this control and 主题.
        add(themeModeChoice(settings.themeMode, enabled = !settings.einkMode, onSelect = onThemeModeChange))
        // The link summarizes the colour and style on the separate 主题 page. Keep it visible when e-ink
        // takes over so the stored settings are still easy to find when e-ink is turned off.
        add(themeSettingsLink(settings, onClick = onOpenTheme))

        // Keep the override below the controls it disables so readers see their stored settings first.
        add(
            SettingsItem.Toggle(
                key = "eink",
                title = stringResource(Res.string.settings_eink),
                description = stringResource(Res.string.settings_eink_hint),
                icon = PlazaIcons.Contrast,
                checked = settings.einkMode,
                onCheckedChange = onEinkModeChange,
            ),
        )
        supportedRefreshRates?.let { supported ->
            add(displayRefreshRateChoice(settings.displayRefreshRate, supported, !settings.einkMode, onDisplayRefreshRateChange))
        }
        // Language belongs with the rest of the app's appearance and closes the group.
        add(appLanguageChoice(settings.appLanguage, onAppLanguageChange))
    }
    // What a list of threads shows and how a read one is told apart.
    val lists = buildList<SettingsItem> {
        // The four read-mark styles fit in a menu. A colour row is useful only for styles that paint a block.
        add(
            settingsChoice(
                key = "read-mark-style",
                title = stringResource(Res.string.settings_read_mark),
                choices = ReadMarkStyle.entries.map { it to readMarkStyleLabel(it) },
                selected = settings.readMarkStyle,
                onSelect = onReadMarkStyleChange,
                presentation = SettingsChoicePresentation.Menu,
                description = stringResource(Res.string.settings_read_mark_hint),
                icon = PlazaIcons.DoneAll,
            ),
        )
        if (settings.readMarkStyle == ReadMarkStyle.BLOCK || settings.readMarkStyle == ReadMarkStyle.UNREAD_BLOCK) {
            add(
                readMarkColorItem(
                    color = settings.readMarkColor,
                    onPick = onReadMarkColorChange,
                    onPickCustom = { pickingReadMarkColor = true },
                ),
            )
        }
        add(
            SettingsItem.Toggle(
                key = "home-page-bar",
                title = stringResource(Res.string.settings_home_page_bar),
                description = stringResource(Res.string.settings_home_page_bar_hint),
                icon = PlazaIcons.LastPage,
                checked = settings.homePageBar,
                onCheckedChange = onHomePageBarChange,
            ),
        )
        add(
            SettingsItem.Toggle(
                key = "home-pinned-posts",
                title = stringResource(Res.string.settings_show_home_pinned_posts),
                description = stringResource(Res.string.settings_show_home_pinned_posts_hint),
                icon = PlazaIcons.PushPin,
                checked = settings.showHomePinnedPosts,
                onCheckedChange = onShowHomePinnedPostsChange,
            ),
        )
        add(
            SettingsItem.Link(
                key = "block-keywords",
                title = stringResource(Res.string.keywords_block_title),
                value = if (state.blockKeywordCount == 0) {
                    stringResource(Res.string.keywords_summary_none)
                } else {
                    stringResource(Res.string.keywords_summary_count, state.blockKeywordCount)
                },
                onClick = onOpenBlockKeywords,
                icon = PlazaIcons.VisibilityOff,
            ),
        )
        // A face helps a reader scan a result list and uses space the title could take.
        add(
            SettingsItem.Toggle(
                key = "search-avatar",
                title = stringResource(Res.string.settings_search_avatar),
                description = stringResource(Res.string.settings_search_avatar_hint),
                icon = PlazaIcons.PersonSearch,
                checked = settings.showSearchAvatar,
                onCheckedChange = onSearchAvatarChange,
            ),
        )
        if (settings.showSearchAvatar) {
            add(
                SettingsItem.Slider(
                    key = "search-avatar-size",
                    title = stringResource(Res.string.settings_search_avatar_size),
                    value = searchAvatarSize,
                    valueLabel = searchAvatarSize.roundToInt().toString(),
                    range = SEARCH_AVATAR_SIZE_RANGE,
                    // The narrow 20..44dp range uses 1dp stops so the preview can be adjusted precisely.
                    steps = SEARCH_AVATAR_SIZE_STEPS,
                    onValueChange = { searchAvatarSize = it },
                    onValueChangeFinished = { onSearchAvatarSizeChange(searchAvatarSize.roundToInt()) },
                    testTag = SEARCH_AVATAR_SLIDER_TAG,
                ).withPreview { SearchAvatarPreview(searchAvatarSize.roundToInt()) },
            )
        }
    }
    // How a thread itself reads.
    val thread = buildList<SettingsItem> {
        add(
            SettingsItem.Slider(
                key = "font-size",
                title = stringResource(Res.string.settings_body_size),
                value = bodyFontSize,
                valueLabel = bodyFontSize.roundToInt().toString(),
                range = BODY_FONT_SIZE_RANGE,
                // The 14..24sp slider has nine stops between its ends.
                steps = BODY_FONT_SIZE_STEPS,
                onValueChange = { bodyFontSize = it },
                onValueChangeFinished = { onFontScaleChange(bodySizeToFontScale(bodyFontSize)) },
                sample = SettingsSample(stringResource(Res.string.settings_text_preview), bodyFontSize),
                testTag = BODY_FONT_SIZE_SLIDER_TAG,
            ),
        )
        add(postReplyStyleChoice(settings.postReplyStyle, onPostReplyStyleChange))
        add(
            SettingsItem.Toggle(
                key = "author-account-info",
                title = stringResource(Res.string.settings_show_author_account_info),
                description = stringResource(Res.string.settings_show_author_account_info_hint),
                icon = PlazaIcons.EventAvailable,
                checked = settings.showAuthorAccountInfo,
                onCheckedChange = onShowAuthorAccountInfoChange,
            ),
        )
        // Only the badge's colour; it has nothing to colour while the badge is hidden.
        if (settings.showAuthorAccountInfo) {
            add(
                accountBadgeStyleChoice(
                    selected = settings.accountBadgeStyleInForce(),
                    onSelect = onAccountBadgeStyleChange,
                ),
            )
        }
        add(reportFormatChoice(settings.reportFormat, onReportFormatChange))
        add(
            SettingsItem.Toggle(
                key = "sticker-uniform",
                title = stringResource(Res.string.settings_sticker_uniform),
                description = stringResource(Res.string.settings_sticker_uniform_hint),
                icon = PlazaIcons.Mood,
                checked = settings.stickerUniformSize,
                onCheckedChange = onStickerUniformSizeChange,
            ),
        )
        if (settings.stickerUniformSize) {
            add(
                SettingsItem.Slider(
                    key = "sticker-size",
                    title = stringResource(Res.string.settings_sticker_size),
                    value = stickerSize,
                    valueLabel = stickerSize.roundToInt().toString(),
                    range = STICKER_SIZE_RANGE,
                    // 20..90sp in 5sp stops gives fourteen intervals and thirteen interior stops.
                    steps = STICKER_SIZE_STEPS,
                    onValueChange = { stickerSize = it },
                    onValueChangeFinished = { onStickerSizeChange(stickerSize.roundToInt()) },
                    testTag = STICKER_SIZE_SLIDER_TAG,
                ).withPreview { StickerSizePreview(stickerSize.roundToInt()) },
            )
        }
    }
    val interaction = buildList<SettingsItem> {
        // One switch controls the position of the title on every screen that uses the app bar.
        if (LocalOneHandModeAvailable.current) {
            add(
                SettingsItem.Toggle(
                    key = "one-hand",
                    title = stringResource(Res.string.settings_one_hand),
                    description = stringResource(Res.string.settings_one_hand_hint),
                    icon = PlazaIcons.PanTool,
                    checked = settings.oneHandMode,
                    onCheckedChange = onOneHandModeChange,
                ),
            )
        }
        // Only where Compose runs the back stack; UIKit owns the iOS gesture and its animation.
        if (platformHasPredictiveBack) {
            add(
                SettingsItem.Toggle(
                    key = "predictive-back",
                    title = stringResource(Res.string.settings_predictive_back),
                    description = stringResource(Res.string.settings_predictive_back_hint),
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    checked = settings.predictiveBack,
                    onCheckedChange = onPredictiveBackChange,
                ),
            )
        }
        appLinkHandlingEnabled?.let { enabled ->
            add(
                SettingsItem.Link(
                    key = "app-links",
                    title = stringResource(Res.string.settings_app_links),
                    description = stringResource(
                        if (enabled) Res.string.settings_app_links_summary_on else Res.string.settings_app_links_summary_off,
                    ),
                    onClick = onOpenAppLinkSettings,
                    icon = PlazaIcons.Link,
                ),
            )
        }
        onClipboardPostPromptChange?.let { onChange ->
            add(
                SettingsItem.Toggle(
                    key = "clipboard-post",
                    title = stringResource(Res.string.settings_clipboard_post),
                    description = stringResource(Res.string.settings_clipboard_post_hint),
                    icon = PlazaIcons.ContentPaste,
                    checked = settings.clipboardPostPrompt,
                    onCheckedChange = onChange,
                ),
            )
        }
        add(searchHistoryLimitChoice(settings.searchHistoryLimit, onSearchHistoryLimitChange))
    }
    val compose = buildList<SettingsItem> {
        add(
            SettingsItem.Link(
                key = "image-host",
                title = stringResource(Res.string.imagehost_title),
                // Report the host and whether it can insert pictures into a post.
                value = stringResource(state.imageHostProvider.shortNameRes()) + SUBTITLE_SEPARATOR +
                    stringResource(if (state.imageHostConnected) Res.string.imagehost_connected else Res.string.imagehost_not_connected),
                onClick = onOpenImageHost,
                icon = PlazaIcons.CloudUpload,
            ),
        )
        stickersLink(onOpenStickers)?.let { add(it) }
    }
    val storage = buildList<SettingsItem> {
        add(
            SettingsItem.Toggle(
                key = "wifi-images",
                title = stringResource(Res.string.settings_wifi_images),
                description = stringResource(Res.string.settings_wifi_images_hint),
                icon = PlazaIcons.Wifi,
                checked = settings.imagesOnWifiOnly,
                onCheckedChange = onImagesOnWifiOnlyChange,
            ),
        )
        add(
            SettingsItem.Action(
                key = "clear-cache",
                title = stringResource(Res.string.settings_clear_cache),
                // Keep the size absent until the scan finishes so a pending scan doesn't read as zero.
                description = state.cacheSizeBytes?.let { bytes ->
                    stringResource(Res.string.settings_clear_cache_size, rememberFileSizeLabel(bytes))
                },
                icon = Icons.Default.Delete,
                onClick = onClearCache,
                loading = state.isClearingCache,
            ),
        )
    }
    val notifications = SettingsSection(
        key = "notifications",
        header = stringResource(Res.string.notify_settings_title),
        items = listOf(
            SettingsItem.Link(
                key = "notification-settings",
                title = stringResource(if (backgroundPollingSupported) Res.string.notify_master_title else Res.string.profile_tile_notifications),
                value = notificationSummary(settings, backgroundPollingSupported),
                onClick = onOpenNotifications,
                icon = Icons.Default.Notifications,
            ),
        ),
    )
    val network = SettingsSection(
        key = "network",
        header = stringResource(Res.string.settings_network),
        items = buildList {
            add(
                SettingsItem.Link(
                    key = "proxy",
                    title = stringResource(Res.string.settings_proxy_entry),
                    value = proxySummary(state.proxy),
                    onClick = onOpenProxy,
                    icon = PlazaIcons.VpnLock,
                ),
            )
            // Only platforms with a DoH implementation supply a chain. Default app links are handled the same way.
            state.dohChain?.let { chain ->
                add(
                    SettingsItem.Link(
                        key = "doh",
                        title = stringResource(Res.string.settings_doh_entry),
                        value = if (state.dohPausedForProxy) stringResource(Res.string.doh_paused_for_proxy) else dohSummary(chain),
                        onClick = onOpenDoh,
                        icon = PlazaIcons.Dns,
                    ),
                )
            }
            // Diagnostics follow the settings that change network behavior and require platform support.
            if (state.hasNetworkCheck) {
                add(
                    SettingsItem.Link(
                        key = "network-check",
                        title = stringResource(Res.string.settings_network_check_entry),
                        onClick = onOpenNetworkCheck,
                        icon = PlazaIcons.NetworkCheck,
                    ),
                )
            }
        },
    )
    val about = SettingsSection(
        key = "about",
        header = stringResource(Res.string.settings_about),
        items = buildList {
            add(
                SettingsItem.Link(
                    key = "about-app",
                    title = stringResource(Res.string.settings_about_app),
                    description = state.updateVersionName
                        ?.let { stringResource(Res.string.settings_about_app_update, it) }
                        ?: stringResource(Res.string.settings_version, state.versionName),
                    onClick = onOpenAbout,
                    icon = Icons.Default.Info,
                    badge = state.updateVersionName != null,
                ),
            )
            if (updateChecksSupported) {
                add(
                    SettingsItem.Toggle(
                        key = "update-on-launch",
                        title = stringResource(Res.string.settings_update_on_launch),
                        icon = PlazaIcons.Update,
                        checked = settings.updateCheckOnLaunch,
                        onCheckedChange = onUpdateCheckOnLaunchChange,
                    ),
                )
                add(
                    SettingsItem.Toggle(
                        key = "update-dev-channel",
                        title = stringResource(Res.string.settings_update_dev_channel),
                        description = stringResource(Res.string.settings_update_dev_channel_hint),
                        icon = PlazaIcons.Science,
                        checked = settings.updateDevChannel,
                        onCheckedChange = onUpdateDevChannelChange,
                    ),
                )
            }
            add(
                SettingsItem.Link(
                    key = "licenses",
                    title = stringResource(Res.string.settings_licenses),
                    onClick = onOpenLicenses,
                    icon = PlazaIcons.Gavel,
                ),
            )
        },
    )
    val page = SettingsPage(
        title = stringResource(Res.string.settings_title),
        sections = listOf(
            SettingsSection(key = "appearance", header = stringResource(Res.string.settings_appearance), items = appearance),
            SettingsSection(key = "lists", header = stringResource(Res.string.settings_section_lists), items = lists),
            SettingsSection(key = "thread", header = stringResource(Res.string.settings_section_thread), items = thread),
            SettingsSection(key = "interaction", header = stringResource(Res.string.settings_section_interaction), items = interaction),
            SettingsSection(key = "compose", header = stringResource(Res.string.settings_section_compose), items = compose),
            SettingsSection(key = "storage", header = stringResource(Res.string.settings_section_storage), items = storage),
            autoAttendanceSection(attendanceConfig, attendanceState, onAutoAttendanceEnabledChange, onAutoAttendanceModeChange),
            notifications,
            network,
            about,
        ),
        showRowIconsOnIos = true,
    )
    SettingsPageScreen(page = page, onBack = onBack, modifier = modifier)
    if (pickingReadMarkColor) {
        ReadMarkColorPicker(
            color = settings.readMarkColor,
            onDismiss = { pickingReadMarkColor = false },
            onPick = {
                pickingReadMarkColor = false
                onReadMarkColorChange(it)
            },
        )
    }
}

@Composable
private fun displayRefreshRateChoice(
    selected: Int,
    supported: Set<Int>,
    enabled: Boolean,
    onChange: (Int) -> Unit,
): SettingsItem {
    val choices = SettingsRepository.DISPLAY_REFRESH_RATE_CHOICES.map { it to refreshRateLabel(it) }
    return settingsChoice(
        key = "refresh-rate",
        title = stringResource(Res.string.settings_refresh_rate),
        choices = choices,
        selected = selected,
        onSelect = onChange,
        presentation = SettingsChoicePresentation.Menu,
        description = stringResource(Res.string.settings_refresh_rate_hint),
        icon = PlazaIcons.Contrast,
        enabled = enabled,
        isChoiceEnabled = { it == 0 || it in supported },
    ).withChevronMenu(value = refreshRateLabel(selected))
}

@Composable
private fun refreshRateLabel(hertz: Int): String =
    if (hertz == 0) stringResource(Res.string.settings_theme_system) else stringResource(Res.string.settings_refresh_rate_value, hertz)

@Composable
private fun searchHistoryLimitChoice(limit: Int, onChange: (Int) -> Unit): SettingsItem =
    settingsChoice(
        key = "search-history-limit",
        title = stringResource(Res.string.settings_search_history_limit),
        choices = SettingsRepository.SEARCH_HISTORY_LIMIT_CHOICES.map { it to stringResource(Res.string.settings_search_history_limit_count, it) },
        selected = limit,
        onSelect = onChange,
        presentation = SettingsChoicePresentation.Menu,
        description = stringResource(Res.string.settings_search_history_limit_hint),
        icon = PlazaIcons.History,
    ).withChevronMenu(value = stringResource(Res.string.settings_search_history_limit_count, limit))

/**
 * A menu keeps all four locale names available in the language each one selects.
 */
@Composable
private fun appLanguageChoice(selected: AppLanguage, onSelect: (AppLanguage) -> Unit): SettingsItem {
    val choices = appLanguageChoices()
    val current = choices.first { it.first == selected }.second
    // The system menu already shows the selected answer. Use the subtitle for the restart note
    // where applying the choice takes another launch.
    val restartHint = if (appLanguageAppliesOnRestart) stringResource(Res.string.settings_language_restart_hint) else null
    return settingsChoice(
        key = "app-language",
        title = stringResource(Res.string.settings_language),
        choices = choices,
        selected = selected,
        onSelect = onSelect,
        presentation = SettingsChoicePresentation.Menu,
        description = restartHint,
        icon = PlazaIcons.Translate,
    ).withChevronMenu(subtitle = if (restartHint == null) current else current + SUBTITLE_SEPARATOR + restartHint)
}

private const val SUBTITLE_SEPARATOR = " · "

/**
 * 新消息通知's second line: how often the check runs and whether 免打扰 holds it back — the two
 * answers on that screen that change what a reader should expect to see, and when.
 */
@Composable
private fun notificationSummary(settings: UserSettings, backgroundPollingSupported: Boolean): String {
    if (!backgroundPollingSupported) return stringResource(Res.string.settings_notify_summary_unavailable)
    if (!settings.notificationsEnabled) return stringResource(Res.string.settings_entry_off)
    val every =
        stringResource(
            Res.string.settings_notify_summary_every,
            stringResource(pollMinutesLabel(settings.notificationPollMinutes)),
        )
    return if (settings.notificationQuietHours) {
        every + SUBTITLE_SEPARATOR + stringResource(Res.string.settings_notify_summary_quiet)
    } else {
        every
    }
}

/** 代理's second line: 「SOCKS5 · 127.0.0.1:7890」, or 未开启 when requests go direct. */
@Composable
private fun proxySummary(proxy: ProxyEndpoint?): String {
    proxy ?: return stringResource(Res.string.settings_entry_off)
    val type = stringResource(if (proxy.type == ProxyType.HTTP) Res.string.proxy_type_http else Res.string.proxy_type_socks)
    // An IPv6 literal is bracketed, as in a URL, or its last group would read as the port.
    val host = if (':' in proxy.host) "[${proxy.host}]" else proxy.host
    return type + SUBTITLE_SEPARATOR + host + ":" + proxy.port
}

/**
 * 加密 DNS's second line: the server tried first, and how many there are in all when the rest stand
 * behind it as fallbacks.
 */
@Composable
private fun dohSummary(chain: List<DohServer>): String {
    val first = chain.firstOrNull() ?: return stringResource(Res.string.settings_entry_off)
    val name = dohServerLabel(first)
    return if (chain.size == 1) name else stringResource(Res.string.settings_doh_summary_chain, name, chain.size)
}

/**
 * The four entries of 语言, in the order the menu lists them.
 *
 * The three that name a bundle read the same in every language, because each is written in the one
 * it selects — that is what lets a reader who has landed in a language they cannot read find their
 * way back out. Only the first is translated, and it is the one that has to be, since it describes
 * a behaviour rather than naming a language.
 */
@Composable
private fun appLanguageChoices(): List<Pair<AppLanguage, String>> =
    listOf(
        AppLanguage.SYSTEM to stringResource(Res.string.settings_language_system),
        AppLanguage.SIMPLIFIED_CHINESE to stringResource(Res.string.settings_language_zh_hans),
        AppLanguage.TRADITIONAL_CHINESE to stringResource(Res.string.settings_language_zh_hant),
        AppLanguage.ENGLISH to stringResource(Res.string.settings_language_en),
    )

/**
 * 已读标记's four answers, in the order [ReadMarkStyle] declares them.
 *
 * A `when` rather than a lookup keyed on the enum, so that adding a style without wording fails to
 * compile instead of drawing an empty menu entry.
 */
@Composable
private fun readMarkStyleLabel(style: ReadMarkStyle): String =
    stringResource(
        when (style) {
            ReadMarkStyle.BLOCK -> Res.string.settings_read_mark_block
            ReadMarkStyle.UNREAD_BLOCK -> Res.string.settings_read_mark_unread_block
            ReadMarkStyle.DIM -> Res.string.settings_read_mark_dim
            ReadMarkStyle.OFF -> Res.string.settings_read_mark_off
        },
    )

/** 明暗 remains directly on the settings home screen because it is used frequently. */
@Composable
private fun themeModeChoice(selected: ThemeMode, enabled: Boolean, onSelect: (ThemeMode) -> Unit): SettingsItem.Choice =
    settingsChoice(
        key = "theme-mode",
        title = stringResource(Res.string.settings_theme_mode),
        choices = listOf(
            ThemeMode.SYSTEM to stringResource(Res.string.settings_theme_system),
            ThemeMode.LIGHT to stringResource(Res.string.settings_theme_light),
            ThemeMode.DARK to stringResource(Res.string.settings_theme_dark),
        ),
        selected = selected,
        onSelect = onSelect,
        presentation = SettingsChoicePresentation.Segmented,
        enabled = enabled,
    )

@Composable
private fun reportFormatChoice(selected: ReportFormat, onSelect: (ReportFormat) -> Unit): SettingsItem.Choice =
    settingsChoice(
        key = "report-format",
        title = stringResource(Res.string.settings_report_format),
        choices = listOf(
            ReportFormat.SOURCE to stringResource(Res.string.settings_report_format_source),
            ReportFormat.ADAPTED to stringResource(Res.string.settings_report_format_adapted),
        ),
        selected = selected,
        onSelect = onSelect,
        presentation = SettingsChoicePresentation.Segmented,
    )

@Composable
private fun accountBadgeStyleChoice(selected: AccountBadgeStyle, onSelect: (AccountBadgeStyle) -> Unit): SettingsItem.Choice =
    settingsChoice(
        key = "account-badge-style",
        title = stringResource(Res.string.settings_account_badge_style),
        choices = listOf(
            AccountBadgeStyle.VIVID to stringResource(Res.string.settings_account_badge_style_vivid),
            AccountBadgeStyle.NATURAL to stringResource(Res.string.settings_account_badge_style_natural),
        ),
        selected = selected,
        onSelect = onSelect,
        presentation = SettingsChoicePresentation.Segmented,
        description = stringResource(Res.string.settings_account_badge_style_hint),
    )

/** The app offers 通栏, 轻卡片 and 原版 in that order. */
@Composable
private fun postReplyStyleChoice(selected: PostReplyStyle, onSelect: (PostReplyStyle) -> Unit): SettingsItem.Choice =
    settingsChoice(
        key = "post-reply-style",
        title = stringResource(Res.string.settings_post_reply_style),
        choices = listOf(
            PostReplyStyle.FLAT to stringResource(Res.string.settings_post_reply_style_flat),
            PostReplyStyle.CARDS to stringResource(Res.string.settings_post_reply_style_cards),
            PostReplyStyle.ORIGINAL to stringResource(Res.string.settings_post_reply_style_original),
        ),
        selected = selected,
        onSelect = onSelect,
        presentation = SettingsChoicePresentation.Segmented,
        description = stringResource(Res.string.settings_post_reply_style_hint),
    )

private val STICKER_SIZE_RANGE =
    SettingsRepository.MIN_STICKER_SIZE_SP.toFloat()..SettingsRepository.MAX_STICKER_SIZE_SP.toFloat()
private const val STICKER_SIZE_STEPS = 13
internal const val STICKER_SIZE_SLIDER_TAG = "sticker-size-slider"

private val SEARCH_AVATAR_SIZE_RANGE =
    SettingsRepository.MIN_SEARCH_AVATAR_DP.toFloat()..SettingsRepository.MAX_SEARCH_AVATAR_DP.toFloat()
private const val SEARCH_AVATAR_SIZE_STEPS = 23
internal const val SEARCH_AVATAR_SLIDER_TAG = "search-avatar-slider"

private val BODY_FONT_SIZE_RANGE = 14f..24f
private const val BODY_FONT_SIZE_STEPS = 9
private const val BASE_BODY_FONT_SIZE = 16f
internal const val BODY_FONT_SIZE_SLIDER_TAG = "body-font-size-slider"

private fun fontScaleToBodySize(fontScale: Float): Float =
    (fontScale * BASE_BODY_FONT_SIZE)
        .roundToInt()
        .toFloat()
        .coerceIn(BODY_FONT_SIZE_RANGE)

private fun bodySizeToFontScale(bodySize: Float): Float =
    (bodySize.roundToInt() / BASE_BODY_FONT_SIZE)
        .coerceIn(SettingsRepository.MIN_FONT_SCALE, SettingsRepository.MAX_FONT_SCALE)

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun SettingsPreview() {
    PlazaTheme {
        SettingsScreen(
            state = SettingsUiState(versionName = "1.1.1"),
            onBack = {},
            onOpenTheme = {},
            onThemeModeChange = {},
            onReadMarkStyleChange = {},
            onReadMarkColorChange = {},
            onOneHandModeChange = {},
            onFontScaleChange = {},
            onStickerUniformSizeChange = {},
            onStickerSizeChange = {},
            onImagesOnWifiOnlyChange = {},
            onReportFormatChange = {},
            onHomePageBarChange = {},
            onUpdateCheckOnLaunchChange = {},
            onUpdateDevChannelChange = {},
            onClearCache = {},
            appLinkHandlingEnabled = false,
            onOpenAppLinkSettings = {},
        )
    }
}

// The tablet width — what `readableWidth` is supposed to do to a form this tall is only visible here.
@Preview(showBackground = true, widthDp = 840, heightDp = 800, name = "设置 · 840dp")
@Composable
private fun SettingsWidePreview() {
    PlazaTheme {
        SettingsScreen(
            state = SettingsUiState(versionName = "1.1.1"),
            onBack = {},
            onOpenTheme = {},
            onThemeModeChange = {},
            onReadMarkStyleChange = {},
            onReadMarkColorChange = {},
            onOneHandModeChange = {},
            onFontScaleChange = {},
            onStickerUniformSizeChange = {},
            onStickerSizeChange = {},
            onImagesOnWifiOnlyChange = {},
            onReportFormatChange = {},
            onHomePageBarChange = {},
            onUpdateCheckOnLaunchChange = {},
            onUpdateDevChannelChange = {},
            onClearCache = {},
            appLinkHandlingEnabled = false,
            onOpenAppLinkSettings = {},
        )
    }
}

/**
 * 表情管理, summarised the way its neighbours are — by what is in it — counted straight off the
 * sticker library rather than through this screen's ViewModel, which has nothing else to do with it.
 */
@Composable
private fun stickersLink(onClick: () -> Unit): SettingsItem.Link? {
    val library = LocalStickerLibrary.current ?: return null
    val mine by remember(library) { library.mine }.collectAsStateWithLifecycle(emptyList())
    val subscriptions by remember(library) { library.subscriptions }.collectAsStateWithLifecycle(emptyList())
    return SettingsItem.Link(
        key = "stickers",
        title = stringResource(Res.string.sticker_manage_title),
        value = stringResource(Res.string.sticker_manage_tab_mine, mine.size) + SUBTITLE_SEPARATOR +
            stringResource(Res.string.sticker_manage_tab_subs, subscriptions.sumOf { it.folders.size }),
        onClick = onClick,
        icon = PlazaIcons.Mood,
    )
}
