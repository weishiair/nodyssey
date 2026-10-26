package io.github.nodyssey.ui.postdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.ThreadPreview
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.FreeChickenLegs
import io.github.nodyssey.data.composer.PostEditTarget
import io.github.nodyssey.model.PostContent
import io.github.nodyssey.model.PostReactions
import io.github.nodyssey.model.ReactionAction
import io.github.nodyssey.model.countOf
import io.github.nodyssey.model.hasSpent
import io.github.nodyssey.ui.common.BoardTag
import io.github.nodyssey.ui.common.BottomPullToRefreshBox
import io.github.nodyssey.ui.common.LocalUserNotes
import io.github.nodyssey.ui.common.NodeSeekIcons
import io.github.nodyssey.ui.common.PlazaSheet
import io.github.nodyssey.ui.common.RoleBadgeRow
import io.github.nodyssey.ui.common.SiteErrorSnackbar
import io.github.nodyssey.ui.common.SiteErrorState
import io.github.nodyssey.ui.common.UserNoteTag
import io.github.nodyssey.ui.common.describedAsLoading
import io.github.nodyssey.ui.common.postCardTitleStyle
import io.github.nodyssey.ui.common.rememberShareText
import io.github.nodyssey.ui.common.sharedThreadAuthor
import io.github.nodyssey.ui.common.sharedThreadAvatar
import io.github.nodyssey.ui.common.sharedThreadBoard
import io.github.nodyssey.ui.common.sharedThreadTitle
import io.github.nodyssey.ui.compare.LocalReportOrigin
import io.github.nodyssey.ui.compare.ReportOrigin
import io.github.nodyssey.ui.compare.ReportOriginFloor
import io.github.nodyssey.ui.composer.FloorReference
import io.github.nodyssey.ui.composer.ReplyComposerHost
import io.github.nodyssey.ui.composer.ReplyComposerViewModel
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.action_cancel
import io.github.nodyssey.ui.resources.action_copy_link
import io.github.nodyssey.ui.resources.action_more
import io.github.nodyssey.ui.resources.action_open_in_browser
import io.github.nodyssey.ui.resources.action_refresh
import io.github.nodyssey.ui.resources.action_share
import io.github.nodyssey.ui.resources.chicken_dialog_body
import io.github.nodyssey.ui.resources.chicken_dialog_body_free
import io.github.nodyssey.ui.resources.chicken_dialog_confirm
import io.github.nodyssey.ui.resources.chicken_dialog_title
import io.github.nodyssey.ui.resources.dislike_dialog_body
import io.github.nodyssey.ui.resources.dislike_dialog_confirm
import io.github.nodyssey.ui.resources.dislike_dialog_title
import io.github.nodyssey.ui.resources.post_all_replies
import io.github.nodyssey.ui.resources.post_author_age_days
import io.github.nodyssey.ui.resources.post_author_empty
import io.github.nodyssey.ui.resources.post_author_replies
import io.github.nodyssey.ui.resources.post_author_scan
import io.github.nodyssey.ui.resources.post_author_scan_retry
import io.github.nodyssey.ui.resources.post_badge_awarded
import io.github.nodyssey.ui.resources.post_badge_original_poster
import io.github.nodyssey.ui.resources.post_body_copied
import io.github.nodyssey.ui.resources.post_body_empty
import io.github.nodyssey.ui.resources.post_collect_action
import io.github.nodyssey.ui.resources.post_collected_action
import io.github.nodyssey.ui.resources.post_comment_blocked
import io.github.nodyssey.ui.resources.post_comment_blocked_show
import io.github.nodyssey.ui.resources.post_comments_empty
import io.github.nodyssey.ui.resources.post_copy_body
import io.github.nodyssey.ui.resources.post_edit_action
import io.github.nodyssey.ui.resources.post_edited
import io.github.nodyssey.ui.resources.post_edited_at
import io.github.nodyssey.ui.resources.post_floor_actions
import io.github.nodyssey.ui.resources.post_floor_number
import io.github.nodyssey.ui.resources.post_link_copied
import io.github.nodyssey.ui.resources.post_loading_earlier
import io.github.nodyssey.ui.resources.post_new_replies_divider
import io.github.nodyssey.ui.resources.post_only_author
import io.github.nodyssey.ui.resources.post_open_original
import io.github.nodyssey.ui.resources.post_quote_action
import io.github.nodyssey.ui.resources.post_quote_floor
import io.github.nodyssey.ui.resources.post_reaction_chicken
import io.github.nodyssey.ui.resources.post_reaction_cost
import io.github.nodyssey.ui.resources.post_reaction_dislike
import io.github.nodyssey.ui.resources.post_reaction_free
import io.github.nodyssey.ui.resources.post_reaction_free_today
import io.github.nodyssey.ui.resources.post_reaction_irreversible
import io.github.nodyssey.ui.resources.post_reaction_like
import io.github.nodyssey.ui.resources.post_reaction_spent
import io.github.nodyssey.ui.resources.post_reading_end
import io.github.nodyssey.ui.resources.post_reading_tools
import io.github.nodyssey.ui.resources.post_reply_action
import io.github.nodyssey.ui.resources.post_reply_to
import io.github.nodyssey.ui.resources.post_share_image
import io.github.nodyssey.ui.resources.post_share_image_failed
import io.github.nodyssey.ui.resources.post_share_image_generating
import io.github.nodyssey.ui.resources.post_sort_ascending
import io.github.nodyssey.ui.resources.post_track_full
import io.github.nodyssey.ui.resources.post_track_thread
import io.github.nodyssey.ui.resources.post_tracked
import io.github.nodyssey.ui.resources.post_untrack_thread
import io.github.nodyssey.ui.resources.user_note_action
import io.github.nodyssey.ui.richtext.PostRichContent
import io.github.nodyssey.ui.sticker.ImageActionsFrom
import io.github.nodyssey.ui.sticker.ImageActionsSheet
import io.github.nodyssey.ui.sticker.LocalImageActions
import io.github.nodyssey.ui.sticker.rememberImageActionsState
import io.github.plaza.core.TimeFormat
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode
import io.github.plaza.designsys.component.AppendSpinner
import io.github.plaza.designsys.component.BadgeChip
import io.github.plaza.designsys.component.BadgeTone
import io.github.plaza.designsys.component.GroupedListItem
import io.github.plaza.designsys.component.LoadingState
import io.github.plaza.designsys.component.MetaText
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.PlazaSpinner
import io.github.plaza.designsys.component.QuotePreview
import io.github.plaza.designsys.component.SkeletonBar
import io.github.plaza.designsys.component.TonalTag
import io.github.plaza.designsys.component.TonalTile
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.component.rememberClipboardCopy
import io.github.plaza.designsys.theme.LocalEinkMode
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Sizes
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import io.github.plaza.designsys.theme.asSignature
import io.github.plaza.designsys.theme.cardBorderStroke
import io.github.plaza.designsys.theme.readableWidth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
fun PostDetailRoute(
    viewModel: PostDetailViewModel,
    replyViewModel: ReplyComposerViewModel,
    /** Another page is on top of this one; see [ReplyComposerHost]'s `covered`. */
    replyCovered: Boolean,
    onBack: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    onSignIn: () -> Unit,
    onVerify: (String) -> Unit,
    onImageClick: (List<String>, String) -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    /** Body/comment links. Separate from [onOpenBrowser] so our own URLs can stay in the app. */
    onLinkClick: (String) -> Unit = onOpenBrowser,
    /** Opens the tapped author's space. */
    onAuthorClick: (Long) -> Unit = {},
    /** Opens the editor on a floor this account wrote. */
    onEdit: (PostEditTarget) -> Unit = {},
    /** Draws the votes embedded in the thread; see [PostDetailScreen]. */
    voteContent: @Composable (Long) -> Unit = {},
    stardustContent: (@Composable (RichNode.StardustReceive) -> Unit)? = null,
    /** 分享为图片's hand-off to the platform; null hides the row. See [PostDetailScreen]. */
    shareImage: (suspend (ImageBitmap, String?) -> Boolean)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.hasContent, state.totalPages, state.lastFloor, state.isLoading, state.isRefreshing) {
        if (!state.isLoading && !state.isRefreshing) viewModel.loadReplyCount()
    }
    val replyState by replyViewModel.uiState.collectAsStateWithLifecycle()
    val postUrl = viewModel.postUrl()
    // Every image in the thread as currently loaded, so the viewer can page between them without
    // going back to the data layer — the URLs were already parsed into the rendered content.
    val images =
        remember(state.body, state.comments, state.showBlockedContent) { state.imageUrls() }
    PostDetailScreen(
        state = state,
        postUrl = postUrl,
        onBack = onBack,
        onOpenBrowser = onOpenBrowser,
        onLinkClick = onLinkClick,
        onAuthorClick = onAuthorClick,
        onSignIn = onSignIn,
        onVerify = { onVerify(postUrl) },
        onImageClick = { url -> onImageClick(images.ifEmpty { listOf(url) }, url) },
        onRetry = viewModel::refresh,
        onPullRefresh = viewModel::pullRefresh,
        onRefreshPage = viewModel::refreshPage,
        onLoadMore = viewModel::loadNextPage,
        onLoadPrevious = viewModel::loadPreviousPage,
        onToggleOnlyAuthor = viewModel::toggleOnlyAuthor,
        onRefreshTail = viewModel::refreshTail,
        onLoadPage = viewModel::loadPage,
        onJumpToFloor = viewModel::jumpToFloor,
        onScrollHandled = viewModel::onScrollHandled,
        onReadingPositionChange = viewModel::recordReadingPosition,
        onVisibleAuthorsChange = viewModel::loadVisibleAuthors,
        onResumeReading = viewModel::resumeReading,
        showBackButton = showBackButton,
        // Replying needs an account; sending an anonymous reply into the void is the one outcome
        // the editor must not produce, so the sign-in page comes first.
        onReply = { target -> if (state.isSignedIn) replyViewModel.open(target) else onSignIn() },
        // 引用 needs the account for the same reason 回复 does, and it writes into the same editor.
        onQuote = { floor -> if (state.isSignedIn) replyViewModel.quote(floor) else onSignIn() },
        onEdit = onEdit,
        onReact = viewModel::react,
        onLoadFreeChickenLegs = viewModel::loadFreeChickenLegs,
        onReactionFailureShown = viewModel::onReactionFailureShown,
        onCollect = viewModel::toggleCollect,
        onCollectFailureShown = viewModel::onCollectFailureShown,
        onToggleTrack = viewModel::toggleTrack,
        onTrackMessageShown = viewModel::onTrackMessageShown,
        voteContent = voteContent,
        stardustContent = stardustContent,
        replyOpen = replyState.visible,
        shareImage = shareImage,
        modifier = modifier,
    )

    ReplyComposerHost(
        state = replyState,
        covered = replyCovered,
        onDismiss = replyViewModel::close,
        bodyState = replyViewModel.bodyState,
        onClearReplyTo = replyViewModel::clearReplyTo,
        onPreviewChange = replyViewModel::setPreviewing,
        onPickImages = replyViewModel::addImages,
        onRemoveAttachment = replyViewModel::removeAttachment,
        onRetryAttachment = replyViewModel::retryUpload,
        onRetryFailedUploads = replyViewModel::retryFailedUploads,
        // The floor number the site reports rides through to the jump: refresh() alone re-read the
        // window's first page, and on a multi-page thread the reply the reader just wrote stayed
        // out of sight. See [PostDetailViewModel.showPublishedReply].
        onPublish = { replyViewModel.publish(viewModel::showPublishedReply) },
        onClearError = replyViewModel::clearPublishError,
        onSignIn = onSignIn,
        onVerify = { onVerify(postUrl) },
        onToolbarChange = replyViewModel::setToolbar,
        onToolbarReset = replyViewModel::resetToolbar,
        onCreateVote = replyViewModel::createVote,
        onDismissVoteCreation = replyViewModel::dismissVoteCreation,
        payeeUid = replyViewModel::receiveCodePayeeUid,
        onInsertReceiveCode = replyViewModel::insertReceiveCode,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    state: PostDetailUiState,
    postUrl: String,
    onBack: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    /** [onRetry] as a pull gesture; separate so the view model can route it to its own indicator. */
    onPullRefresh: () -> Unit = onRetry,
    /** The same gesture at the other end of a finished thread — see [PostDetailViewModel.refreshTail]. */
    onRefreshTail: () -> Unit = {},
    /** Re-fetches the given page in place — the 刷新 menu item, aimed at the page on screen. */
    onRefreshPage: (Int) -> Unit = { onRetry() },
    onLoadPage: (Int) -> Unit = { onLoadMore() },
    /** Joins the previous page onto the list while keeping the current floor in place. */
    onLoadPrevious: () -> Unit = {},
    onToggleOnlyAuthor: () -> Unit = {},
    /** Scrolls to a floor, fetching its page first when that floor is not loaded. */
    onJumpToFloor: (String) -> Unit = {},
    onScrollHandled: () -> Unit = {},
    /** Reports where the reader is, so a later visit can offer to come back to it. */
    onReadingPositionChange: (Int, String?) -> Unit = { _, _ -> },
    /** Settled, visible replies only; cached profile requests never gate reading the thread. */
    onVisibleAuthorsChange: (Set<Long>) -> Unit = {},
    /** Returns to where a previous visit left off; only reachable when there was one. */
    onResumeReading: () -> Unit = {},
    showBackButton: Boolean = true,
    /** Opens the sign-in page. Separate from [onOpenBrowser] because "登录" is not "看看网页版". */
    onSignIn: () -> Unit = { onOpenBrowser(postUrl) },
    /** Clears a Cloudflare challenge on this thread's own URL. */
    onVerify: (String) -> Unit,
    /** `null` opens an empty reply; a floor addresses one (6d). The editor itself is hosted by the route. */
    onReply: (FloorReference?) -> Unit = {},
    /** Appends one more 引用 block to whatever the editor already holds. */
    onQuote: (FloorReference) -> Unit = {},
    /**
     * 编辑. Offered only where [PostContent.isMine] says the site itself would offer it, which is
     * also the only case where the endpoint would accept the write.
     */
    onEdit: (PostEditTarget) -> Unit = {},
    /** Spends one mark on a floor. Confirmation, where the site has one, happens before this. */
    onReact: (Long, ReactionAction) -> Unit = { _, _ -> },
    /** Asked for when a 投喂 confirmation opens, so it can say whether this one is free. */
    onLoadFreeChickenLegs: () -> Unit = {},
    onReactionFailureShown: () -> Unit = {},
    /** Collects the thread, or takes it out. Whole-thread, so only the opening post offers it. */
    onCollect: () -> Unit = {},
    onCollectFailureShown: () -> Unit = {},
    /** 追踪新回复 / 取消追踪, from the overflow menu. */
    onToggleTrack: () -> Unit = {},
    onTrackMessageShown: () -> Unit = {},
    /**
     * Draws the votes embedded in the body and the comments.
     *
     * Supplied by the navigation layer, which is the only place that can reach [AppContainer] to
     * build a per-vote ViewModel. Defaults to nothing, which is what a preview or a screen test wants.
     */
    voteContent: @Composable (Long) -> Unit = {},
    stardustContent: (@Composable (RichNode.StardustReceive) -> Unit)? = null,
    /** Hides the bottom toolbar while the editor covers it. */
    replyOpen: Boolean = false,
    /** Body/comment links. Separate from [onOpenBrowser] so our own URLs can stay in the app. */
    onLinkClick: (String) -> Unit = onOpenBrowser,
    /** Opens the tapped author's space. */
    onAuthorClick: (Long) -> Unit = {},
    /**
     * Hands a picture of a floor to the platform's share sheet, answering whether it did. Null — a
     * preview, a test, a platform with no sheet — hides 分享为图片 from the floor panel.
     */
    shareImage: (suspend (ImageBitmap, String?) -> Boolean)? = null,
) {
    val listState = rememberLazyListState()
    var isScrollbarDragging by remember { mutableStateOf(false) }
    var readingToolsExpanded by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var confirmTarget by remember { mutableStateOf<ReactionConfirm?>(null) }

    /** The floor whose 1c panel is open — from its ⋯, or a long press on its card. */
    var floorActions by remember { mutableStateOf<FloorActions?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    /** The floor 分享为图片 is drawing, from the panel's tap until the share sheet has it. */
    var shareImageOf by remember { mutableStateOf<PostContent?>(null) }
    var showPageSheet by remember { mutableStateOf(false) }
    var highlightedFloor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(highlightedFloor) {
        if (highlightedFloor != null) {
            delay(1800)
            highlightedFloor = null
        }
    }
    val density = LocalDensity.current

    // Measure the title's actual bottom so a multiline title only enters the bar after it leaves
    // the viewport. The constant is only the first frame's answer, before placement.
    var collapsedTitleThreshold by remember { mutableIntStateOf(with(density) { 72.dp.roundToPx() }) }
    val showCollapsedTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > collapsedTitleThreshold
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    // `canScrollBackward` is the predicate itself, already maintained by LazyListState as plain
    // state — no derivedStateOf and no per-frame read of the two scroll fields.
    val atListTop = !listState.canScrollBackward
    LaunchedEffect(shouldLoadMore, state.comments.size, isScrollbarDragging) {
        if (shouldLoadMore && !isScrollbarDragging) onLoadMore()
    }

    // The page the reader is looking at, not the furthest page fetched. Pages already in the list
    // are navigated by scrolling; only pages outside the loaded slice involve the network.
    val visiblePage by remember(state.commentPages, state.body != null, state.firstLoadedPage) {
        derivedStateOf {
            val commentIndex = listState.firstVisibleItemIndex - state.headerItemCount
            if (commentIndex < 0) {
                state.firstLoadedPage
            } else {
                state.commentPages.getOrNull(commentIndex)
                    ?: state.commentPages.lastOrNull()
                    ?: state.firstLoadedPage
            }
        }
    }

    // The topmost floor on screen, which is what makes a return exact rather than page-accurate. Null
    // above the first comment — the title and the opening post belong to no page's floors.
    val visibleFloor by remember(state.comments, state.body != null) {
        derivedStateOf {
            state.comments
                .getOrNull(listState.firstVisibleItemIndex - state.headerItemCount)
                ?.floor
        }
    }
    // Two gates, both against overwriting a real read's place with a visit that never happened. There
    // has to be a thread to be positioned in — an empty screen mid-fetch reports page 1 — and the
    // reader has to have got somewhere: every thread parks at its top on open, so opening one and
    // backing straight out would otherwise reset it to page 1.
    val positionWorthRecording = state.hasContent && (visiblePage > 1 || !atListTop)
    LaunchedEffect(visiblePage, visibleFloor, positionWorthRecording) {
        if (positionWorthRecording) onReadingPositionChange(visiblePage, visibleFloor)
    }

    // The state as it is *now*, for the waits below: `state` is this composition's value and a
    // coroutine holding it would never see the floors it is waiting for.
    val latestState by rememberUpdatedState(state)
    val latestVisibleAuthorsChange by rememberUpdatedState(onVisibleAuthorsChange)
    val latestReplyOpen by rememberUpdatedState(replyOpen)
    LaunchedEffect(listState) {
        snapshotFlow {
            if (listState.isScrollInProgress || latestReplyOpen) {
                emptySet()
            } else {
                val layout = listState.layoutInfo
                val keys = layout.visibleItemsInfo
                    .filter { it.offset < layout.viewportEndOffset && it.offset + it.size > layout.viewportStartOffset }
                    .map { it.key }.toSet()
                latestState.visibleReplyAuthorUids(keys)
            }
        }.distinctUntilChanged().collectLatest {
            delay(180)
            latestVisibleAuthorsChange(it)
        }
    }
    DisposableEffect(Unit) {
        onDispose { latestVisibleAuthorsChange(emptySet()) }
    }

    var prependAnchor by remember { mutableStateOf<PrependAnchor?>(null) }
    fun loadPrevious() {
        if (isScrollbarDragging) return
        if (!state.hasContent || state.firstLoadedPage <= 1) return
        if (state.isLoading || state.isAppending || state.isPrepending || state.isRefreshingTail) return
        if (prependAnchor != null) return
        // Stable lazy-item keys preserve a visible reply automatically. When a header is first,
        // LazyColumn would preserve that header instead, leaving the new replies below the reader.
        // Anchor the first visible reply with requestScrollToItem for that case only.
        if (listState.firstVisibleItemIndex < state.headerItemCount) {
            listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index in state.headerItemCount until state.headerItemCount + state.comments.size }
                ?.let { item -> prependAnchor = PrependAnchor(state.firstLoadedPage, item.key, item.offset) }
        }
        onLoadPrevious()
    }
    val shouldLoadPrevious by remember {
        derivedStateOf {
            listState.isScrollInProgress && listState.lastScrolledBackward &&
                listState.firstVisibleItemIndex <= THREAD_HEADER_ITEMS + 1
        }
    }
    LaunchedEffect(shouldLoadPrevious, state.firstLoadedPage, isScrollbarDragging) {
        if (shouldLoadPrevious && !isScrollbarDragging && state.pendingScroll == null && state.error == null) loadPrevious()
    }
    LaunchedEffect(state.firstLoadedPage, state.error) {
        val anchor = prependAnchor ?: return@LaunchedEffect
        if (state.firstLoadedPage < anchor.page) {
            val index = state.comments.indices.firstOrNull { state.comments[it].lazyKey(it) == anchor.key }
            if (index != null && state.pendingScroll == null) {
                listState.requestScrollToItem(state.headerItemCount + index, -anchor.offset)
            }
            prependAnchor = null
        } else if (state.error != null) {
            prependAnchor = null
        }
    }

    /** Set while 到最新 is waiting for the last page it just asked for. */
    var pendingBottom by remember { mutableStateOf(false) }

    /**
     * 到最新 — the newest floor, which is the foot of the last page rather than its head.
     *
     * Separate from 最后一页 because standing on that page and standing at the end of it are two
     * different places, and on a thread being replied to it is the second one people mean. The flag
     * outlives the fetch: the floors are not here yet when the last page has to be asked for, so the
     * scroll waits for the emission that carries them.
     */
    fun goToLatest() {
        val last = state.totalPages.coerceAtLeast(1)
        if (last in state.firstLoadedPage..state.lastLoadedPage) {
            scope.launch { listState.animateScrollToItem(state.lastItemIndex) }
        } else {
            pendingBottom = true
            onLoadPage(last)
        }
    }
    LaunchedEffect(pendingBottom, state.lastLoadedPage, state.comments.size) {
        if (!pendingBottom) return@LaunchedEffect
        if (state.lastLoadedPage < state.totalPages || state.comments.isEmpty()) return@LaunchedEffect
        listState.scrollToItem(state.lastItemIndex)
        pendingBottom = false
    }

    /**
     * 点标题看正文 — the way back to the opening post from a read that started past it.
     *
     * A cached opening post is retained for its metadata and actions, but only page 1 displays it.
     * A later-page window always offers the way back, even when that body was cached previously.
     * Loading a thread is not such a window, so the link waits until there is content to navigate.
     */
    val openOriginalPost: (() -> Unit)? =
        if ((state.firstLoadedPage > 1 || state.onlyAuthor) && state.hasContent) ({ onJumpToFloor("#0") }) else null

    // Keyed on the pages themselves rather than on how many comments there are: a jump swaps one page
    // of ten floors for another page of ten floors, so a count would not change and the effect would
    // never re-run against the content it was waiting for.
    LaunchedEffect(state.pendingScroll, state.commentPages, state.isLoading) {
        val target = state.pendingScroll ?: return@LaunchedEffect
        // A notification can name a new reply on an already cached page. The cached rows are
        // readable while refreshing, but must not consume the jump before the new snapshot lands.
        if (state.isLoading) return@LaunchedEffect
        // The fetch has finished but the new comments may not have flowed out of Room yet; wait for
        // the emission that carries them rather than scrolling to a stale end-of-list.
        if (target.page !in state.firstLoadedPage..state.lastLoadedPage) return@LaunchedEffect
        // Holding the page and holding its floors are not the same thing, and page 1 is where they
        // come apart: an empty thread on its very first frame already reports 1..1, so a notification
        // about a floor on page 1 used to be answered against a list with nothing in it — scrolled to
        // the top, marked handled, and gone by the time the floors arrived. Waiting for a comment
        // from that page or later covers the deleted-page case too, where its own never turn up.
        if (state.commentPages.none { it >= target.page }) return@LaunchedEffect
        // The floor when the site named one and it is on the page; otherwise the page's own start.
        // A floor can be missing from the page it was computed for — it was deleted, or the thread
        // was renumbered under it — and landing on the right page beats not moving at all.
        val index = target.floor?.let { state.indexAtOrAfterFloor(it) }
            ?: state.firstIndexOfPage(target.page)
            ?: (state.headerItemCount + state.comments.size - 1).coerceAtLeast(0)
        listState.scrollToItem(index)
        highlightedFloor = target.floor
        onScrollHandled()
    }
    /**
     * One mark on one floor, from wherever it was asked for — the floor's own row, or a tile of its
     * 1c panel. Both go through the same gates, so neither can spend what the other would have asked
     * about first.
     */
    val reactTo: (PostContent, ReactionAction) -> Unit = { content, action ->
        val commentId = content.commentId
        when {
            // Same rule as the editor: the account has to exist before the action, not after a
            // rejection that also spent the tap.
            !state.isSignedIn -> onSignIn()

            commentId == null -> Unit

            // 点赞 costs nothing and the site does not confirm it either.
            action == ReactionAction.Upvote -> onReact(commentId, action)

            else -> {
                confirmTarget = ReactionConfirm(content, commentId, action)
                if (action == ReactionAction.ChickenLeg) onLoadFreeChickenLegs()
            }
        }
    }

    val imageActions = rememberImageActionsState()
    ImageActionsSheet(imageActions, snackbarHostState)

    val currentFloor by remember(state.comments, state.firstLoadedPage, state.onlyAuthor) {
        derivedStateOf {
            val index = listState.layoutInfo.readingCommentIndex(
                state.headerItemCount,
                state.comments.size,
                atEnd = listState.canScrollBackward && !listState.canScrollForward,
            )
            NodeSeekSite.parseFloorNumber(index?.let { state.comments.getOrNull(it)?.floor })
                ?: if (state.firstLoadedPage > 1 || state.onlyAuthor) NodeSeekSite.parseFloorNumber(state.comments.firstOrNull()?.floor) ?: 0 else 0
        }
    }
    val readerLoading =
        state.isLoading ||
            state.isAppending ||
            state.isPrepending ||
            state.isRefreshing ||
            state.isRefreshingTail
    val lastFloor = state.readerLastFloor()
    val shareThread = rememberShareText()
    val shareThreadLabel = stringResource(Res.string.action_share)
    val readingItemKeys = remember(state.comments, state.isAppending, state.showReadingEnd) {
        buildList<Any> {
            add("body")
            add("comments-header")
            state.comments.forEachIndexed { index, comment -> add(comment.lazyKey(index)) }
            if (state.isAppending) {
                add("appending")
            } else if (state.showReadingEnd) {
                add("thread-end")
            }
        }
    }
    val readingLayoutReady by remember(readingItemKeys) {
        derivedStateOf { listState.layoutInfo.matchesReadingItems(readingItemKeys) }
    }
    val displayedFloor =
        rememberStableReadingFloor(
            currentFloor = currentFloor,
            loading = readerLoading,
            positionReady = state.hasContent && readingLayoutReady &&
                state.pendingScroll == null && prependAnchor == null && !pendingBottom,
        )
    fun jumpToFloor(floor: String) {
        readingToolsExpanded = false
        val index = if (floor == "#0" && state.firstLoadedPage == 1 && !state.onlyAuthor) 0 else state.indexOfFloor(floor)
        if (index != null) {
            scope.launch {
                listState.animateScrollToItem(index)
                highlightedFloor = floor
            }
        } else {
            onJumpToFloor(floor)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = threadPageColor(),
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                Modifier.padding(
                    bottom = if (state.hasContent && !replyOpen) {
                        if (readingToolsExpanded) 152.dp else 80.dp
                    } else {
                        0.dp
                    },
                ),
            )
        },
        topBar = {
            DetailTopBar(
                title = state.title,
                showTitle = showCollapsedTitle,
                onTitleClick = openOriginalPost,
                postUrl = postUrl,
                onBack = onBack,
                onOpenInBrowser = { onOpenBrowser(postUrl) },
                onRefresh = { onRefreshPage(visiblePage) },
                showBackButton = showBackButton,
                tracked = state.tracked,
                onToggleTrack = onToggleTrack,
                onReadingTools = { readingToolsExpanded = !readingToolsExpanded },
            )
        },
    ) { padding ->
        Box(
            Modifier.padding(padding).fillMaxSize().floatingToolbarVerticalNestedScroll(
                expanded = readingToolsExpanded,
                onExpand = { if (!isScrollbarDragging) readingToolsExpanded = true },
                onCollapse = { readingToolsExpanded = false },
            ),
        ) {
            // First in the Box, though it is never drawn: see [FloorShareCapture].
            val sharing = shareImageOf
            if (sharing != null && shareImage != null) {
                val generating = stringResource(Res.string.post_share_image_generating)
                val failed = stringResource(Res.string.post_share_image_failed)
                val shareLabel = stringResource(Res.string.post_share_image)
                LaunchedEffect(sharing) { snackbarHostState.showSnackbar(generating, duration = SnackbarDuration.Indefinite) }
                FloorShareCapture(
                    content = sharing,
                    threadTitle = state.title,
                    postUrl = postUrl,
                    onCaptured = { image ->
                        scope.launch {
                            shareImageOf = null
                            snackbarHostState.currentSnackbarData?.dismiss()
                            val shared = image != null && shareImage(image, shareLabel)
                            if (!shared) snackbarHostState.showSnackbar(failed)
                        }
                    },
                )
            }
            val error = state.error
            when {
                /*
                 * Only where nothing told us anything about this thread — a notification, a deep
                 * link, a cold start into a restored destination.
                 *
                 * A thread opened from a list does *not* come through here, even while it is still
                 * loading. It goes straight to [ThreadList], which draws what the list already knew
                 * and grey bars for the rest. Sending it here instead would put the loading state
                 * and the thread in two different subtrees, and swapping one for the other disposes
                 * the four shared elements mid-landing and flies them again.
                 */
                !state.hasContent && state.isLoading && state.preview == null ->
                    UnopenedThreadSkeleton()

                !state.hasContent && error != null ->
                    SiteErrorState(
                        error = error,
                        onRetry = onRetry,
                        // A locked thread is fixed by signing in, not by loading it again in a
                        // browser; a challenge is fixed on this thread's own URL. Both are named
                        // rather than folded into [onOpenBrowser] by a `when` here — picking the
                        // recovery per error is SiteErrorState's job, and the copy of that decision
                        // this used to hold is exactly the copy that goes stale.
                        onOpenBrowser = { onOpenBrowser(postUrl) },
                        onVerify = onVerify,
                        onSignIn = onSignIn,
                        // 等级不足 has no action that clears it, so the way out is the only button —
                        // and this screen, unlike a tab root, always has somewhere to go back to.
                        onBack = onBack.takeIf { showBackButton },
                    )

                else ->
                    PullToRefreshBox(
                        isRefreshing = state.isRefreshing || state.isPrepending,
                        onRefresh = { if (state.firstLoadedPage > 1) loadPrevious() else onPullRefresh() },
                    ) {
                        // The same gesture at the foot of a thread the site has no more pages of:
                        // where auto-append stops, dragging on re-reads the last page and brings in
                        // the floors posted since. Disabled while a next page exists, because there
                        // the drag already means "load it".
                        BottomPullToRefreshBox(
                            isRefreshing = state.isRefreshingTail,
                            onRefresh = onRefreshTail,
                            enabled = state.hasContent && !state.hasNextPage && !state.isAppending && !state.isPrepending,
                            indicatorBottomPadding = 80.dp,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            // Which thread a report on these floors came from, for 测评对比's basket;
                            // each floor adds its own number on top. See [LocalReportOrigin].
                            CompositionLocalProvider(
                                LocalReportOrigin provides ReportOrigin(state.postId, state.title),
                                // 1a: a long press on a picture in any floor opens the sheet above.
                                LocalImageActions provides imageActions.open,
                            ) {
                                ThreadList(
                                    state = state,
                                    listState = listState,
                                    onOpenOriginalPost = openOriginalPost,
                                    onLoadPrevious = ::loadPrevious,
                                    onToggleOnlyAuthor = onToggleOnlyAuthor,
                                    onRetryAuthor = onRetry,
                                    highlightedFloor = highlightedFloor,
                                    onOpenBrowser = onLinkClick,
                                    onImageClick = onImageClick,
                                    onJumpToFloor = ::jumpToFloor,
                                    onReact = reactTo,
                                    onOpenFloorActions = { floorActions = it },
                                    onReplyToFloor = onReply,
                                    onQuoteFloor = onQuote,
                                    onEditFloor = { target ->
                                        // Same gate as everything else that writes: an expired session is
                                        // discovered before the editor opens, not after a save is refused.
                                        if (state.isSignedIn) onEdit(target) else onSignIn()
                                    },
                                    onAuthorClick = onAuthorClick,
                                    // Same gate as the editor and the marks: the account has to exist before
                                    // the action, not after a rejection that also spent the tap.
                                    onCollect = { if (state.isSignedIn) onCollect() else onSignIn() },
                                    voteContent = voteContent,
                                    stardustContent = stardustContent,
                                    onTitleBottom = { collapsedTitleThreshold = it },
                                )
                            }
                        }
                    }
            }

            if (state.hasContent && state.comments.isNotEmpty()) {
                LoadedReadingScrollbar(
                    listState = listState,
                    itemKeys = readingItemKeys,
                    currentFloor = displayedFloor,
                    onDraggingChanged = {
                        isScrollbarDragging = it
                        if (it) readingToolsExpanded = false
                    },
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }

            if (state.hasContent && !replyOpen) {
                ThreadReadingControls(
                    currentFloor = displayedFloor,
                    lastFloor = lastFloor,
                    expanded = readingToolsExpanded,
                    onlyAuthor = state.onlyAuthor,
                    collected = state.collected,
                    collectPending = state.collectPending,
                    onJump = { showPageSheet = true },
                    onOriginal = { jumpToFloor("#0") },
                    onToggleOnlyAuthor = onToggleOnlyAuthor,
                    onCollect = { if (state.isSignedIn) onCollect() else onSignIn() },
                    onShare = { shareThread("${state.title}\n$postUrl", shareThreadLabel) },
                    onReply = { onReply(null) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            // A jump replaces the whole list, so the append spinner at its foot would be pointing at
            // content that is on its way out. This says "a different page is coming" where the reader
            // is already looking — and it is the only feedback between the tap and the new page.
            // A pull-to-refresh is the one load excused: its own indicator is already saying it.
            if (state.isLoading && !state.isRefreshing && state.hasContent) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).describedAsLoading(),
                )
            }
        }
    }

    confirmTarget?.let { target ->
        ReactionConfirmDialog(
            target = target,
            freeChickenLegs = state.freeChickenLegs,
            onDismiss = { confirmTarget = null },
            onConfirm = {
                confirmTarget = null
                onReact(target.commentId, target.action)
            },
        )
    }

    floorActions?.let { target ->
        // The floor as it is now, not as it was when the panel opened: a mark landing while the
        // panel is up has to turn its tile to 已表态 rather than leave it offering a second spend.
        val live = state.contentWithId(target.content.commentId) ?: target.content
        FloorActionSheet(
            content = live,
            actions = target,
            pending = state.pendingReactionFor(live),
            freeChickenLegs = state.freeChickenLegs,
            onDismiss = { floorActions = null },
            onReact = { action ->
                floorActions = null
                reactTo(live, action)
            },
            onShareImage = if (shareImage != null && shareImageOf == null) ({ shareImageOf = live }) else null,
        )
    }
    // The 投喂 tile says whether today's feed is free, which only the site knows. Asked for as the
    // panel opens — the same once-per-thread request the confirmation makes — and only for an
    // account that could spend one.
    val actionsOpen = floorActions != null
    LaunchedEffect(actionsOpen) {
        if (actionsOpen && state.isSignedIn) onLoadFreeChickenLegs()
    }

    // The site's own sentence is the whole value here — "鸡腿不足", "已经进行过加鸡腿操作" — so it is
    // shown verbatim, and our wording only stands in for the failures that never reached the site.
    //
    // A wall is the exception, and the reason this goes through [SiteErrorSnackbar]: Cloudflare and
    // a signed-out account send no sentence and are not cleared by pressing again, so they get the
    // control that does clear them. No 重试 — a mark is spent on one floor, and this far from the
    // tap there is nothing left to name.
    val failure = state.reactionFailure
    SiteErrorSnackbar(
        error = failure?.error,
        snackbarHostState = snackbarHostState,
        onShown = onReactionFailureShown,
        detail = failure?.detail,
        onVerify = onVerify,
        onSignIn = onSignIn,
    )

    // Same treatment for the star, kept separate so a refused collection and a refused mark cannot
    // clear each other's message.
    val collectFailure = state.collectFailure
    SiteErrorSnackbar(
        error = collectFailure?.error,
        snackbarHostState = snackbarHostState,
        onShown = onCollectFailureShown,
        detail = collectFailure?.detail,
        onVerify = onVerify,
        onSignIn = onSignIn,
        onRetry = onCollect,
    )

    // Shown, then consumed — the order `SiteErrorSnackbar` uses, so clearing the message cannot
    // cancel the effect that is showing it.
    val trackTracked = stringResource(Res.string.post_tracked)
    val trackFull = stringResource(Res.string.post_track_full)
    LaunchedEffect(state.trackMessage) {
        val message = state.trackMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(if (message == TrackMessage.TRACKED) trackTracked else trackFull)
        onTrackMessageShown()
    }

    if (showPageSheet) {
        ThreadFloorJumpSheet(
            currentFloor = currentFloor,
            maxFloor = maxOf(lastFloor ?: 0, state.totalPages * NodeSeekSite.COMMENTS_PER_PAGE),
            onDismiss = { showPageSheet = false },
            onGo = { floor ->
                showPageSheet = false
                jumpToFloor("#$floor")
            },
            onOriginal = {
                showPageSheet = false
                jumpToFloor("#0")
            },
            onLatest = {
                showPageSheet = false
                readingToolsExpanded = false
                goToLatest()
            },
            onResume = state.resumePosition?.takeUnless { state.onlyAuthor }?.let {
                {
                    showPageSheet = false
                    readingToolsExpanded = false
                    onResumeReading()
                }
            },
        )
    }
}

/** A quiet page surface keeps the individual reply bubbles legible. */
@Composable
private fun threadPageColor(): Color {
    val layers = LocalPlazaLayers.current
    return if (layers.page == layers.card && !LocalEinkMode.current) layers.inset else layers.page
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailTopBar(
    title: String,
    showTitle: Boolean,
    /** Jumps to the opening post, for the reads that start on a page without one. Null when there is nothing to jump to. */
    onTitleClick: (() -> Unit)?,
    postUrl: String,
    onBack: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onRefresh: () -> Unit,
    showBackButton: Boolean,
    /** Null hides 追踪新回复: this build has nowhere to keep the list. */
    tracked: Boolean? = null,
    onToggleTrack: () -> Unit = {},
    onReadingTools: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val shareText = rememberShareText()
    val copy = rememberClipboardCopy()
    val openOriginalPostLabel = stringResource(Res.string.post_open_original)
    val linkCopied = stringResource(Res.string.post_link_copied)
    val shareLabel = stringResource(Res.string.action_share)

    TopAppBar(
        title = {
            // Clickable only while the bar is actually showing the title: the collapsed bar is where
            // a reader deep in someone else's page 7 meets the title at all, and an invisible tap
            // target over an empty bar is worse than no affordance.
            val jump = onTitleClick?.takeIf { showTitle }
            Text(
                text = if (showTitle) title else "",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (jump != null) {
                    Modifier.clickable(onClick = jump).semantics {
                        contentDescription = openOriginalPostLabel
                    }
                } else {
                    Modifier
                },
            )
        },
        navigationIcon = {
            if (showBackButton) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.action_back),
                        modifier = Modifier.size(TopBarIconSize),
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onOpenInBrowser) {
                Icon(
                    PlazaIcons.OpenInNew,
                    contentDescription = stringResource(Res.string.action_open_in_browser),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(TopBarIconSize),
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(Res.string.action_more),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(TopBarIconSize),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.post_reading_tools)) },
                        onClick = {
                            menuOpen = false
                            onReadingTools()
                        },
                    )
                    // The menu twin of pull-to-refresh, for the reader who is thirty floors down and
                    // not about to scroll back up to pull.
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.action_refresh)) },
                        onClick = {
                            menuOpen = false
                            onRefresh()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.action_copy_link)) },
                        onClick = {
                            copy("post", postUrl, linkCopied)
                            menuOpen = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(shareLabel) },
                        onClick = {
                            menuOpen = false
                            shareText("$title\n$postUrl", shareLabel)
                        },
                    )
                    tracked?.let { on ->
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(if (on) Res.string.post_untrack_thread else Res.string.post_track_thread))
                            },
                            onClick = {
                                menuOpen = false
                                onToggleTrack()
                            },
                        )
                    }
                }
            }
        },
        // The controls and the reading column share one paper surface.
        colors =
        TopAppBarDefaults.topAppBarColors(
            containerColor = threadPageColor(),
            scrolledContainerColor = threadPageColor(),
        ),
        // A step under Material's 64dp, as 2b draws it: the bar holds three icons and, once the title
        // has scrolled away, one line of it. The buttons keep their 48dp targets.
        expandedHeight = TopBarHeight,
    )
}

private val TopBarHeight = 48.dp

private val TopBarIconSize = 20.dp

/**
 * The thread as one scroll.
 *
 * The site's pages join one continuous scroll in both directions. The footer carries page numbers,
 * keeping page boundaries out of the reply text and the opening post on its own first page.
 *
 * Title and tags form the thread header; the opening post and replies follow as full-width floors.
 * Keep the header and opening post in one lazy item so page and floor indices stay stable.
 */
@Composable
private fun ThreadList(
    state: PostDetailUiState,
    listState: LazyListState,
    /** See [PostDetailScreen]'s own — null when the opening post is already in the list. */
    onOpenOriginalPost: (() -> Unit)?,
    onLoadPrevious: () -> Unit,
    onToggleOnlyAuthor: () -> Unit,
    onRetryAuthor: () -> Unit,
    highlightedFloor: String?,
    onOpenBrowser: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onJumpToFloor: (String) -> Unit,
    onReact: (PostContent, ReactionAction) -> Unit,
    /** Opens a floor's 1c panel — the marks and actions its own row has no room for. */
    onOpenFloorActions: (FloorActions) -> Unit,
    onReplyToFloor: (FloorReference?) -> Unit,
    onQuoteFloor: (FloorReference) -> Unit,
    onEditFloor: (PostEditTarget) -> Unit,
    onAuthorClick: (Long) -> Unit,
    onCollect: () -> Unit,
    voteContent: @Composable (Long) -> Unit,
    stardustContent: (@Composable (RichNode.StardustReceive) -> Unit)?,
    /** Where the title ends inside the opening item, in px — see its use in [PostDetailScreen]. */
    onTitleBottom: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        contentPadding =
        PaddingValues(top = Spacing.sm, bottom = 160.dp),
        modifier = modifier
            .fillMaxHeight()
            .readableWidth(),
    ) {
        /*
         * Present whether or not the thread has arrived — which is the whole reason this is one item
         * and not an `item` guarded by `state.body != null`.
         *
         * The title, the avatar and the author's name inside it are shared elements, still settling
         * out of their flight from a feed row while the thread is on its way. A guard here would
         * dispose them the instant the body landed and compose fresh ones in a new subtree; the
         * shared-element machinery reads that as a *second* transition and flies them again, from
         * wherever the new node happened to be measured first. Same call site, changing arguments,
         * and nothing moves. See [ThreadOpeningPost].
         *
         * Keep this lazy item intact: reading-position and floor-jump helpers use one header item
         * for both the title and the opening floor.
         */
        item(key = "body") {
            val body = state.body
            // The opening post is always on page 1, wherever the reader currently is.
            val onEdit = body?.commentId
                ?.takeIf { body.isMine }
                ?.let { id ->
                    {
                        onEditFloor(
                            PostEditTarget(
                                postId = state.postId,
                                commentId = id,
                                page = 1,
                                isOpeningPost = true,
                            ),
                        )
                    }
                }
            ThreadOpeningPost(
                title = state.title,
                isAwarded = state.isAwarded,
                onOpenOriginalPost = onOpenOriginalPost,
                body = body,
                preview = state.preview,
                postId = state.postId,
                authorLevel = state.authorLevel,
                authorJoinedDays = state.authorJoinedDays,
                showBlockedContent = state.showBlockedContent,
                onOpenBrowser = onOpenBrowser,
                onImageClick = onImageClick,
                onJumpToFloor = onJumpToFloor,
                pendingReaction = body?.let { state.pendingReactionFor(it) },
                onReact = { action -> body?.let { onReact(it, action) } },
                onAuthorClick = onAuthorClick,
                // The opening post keeps the site's own set: no 回复 or 引用 of it, 编辑 on your own.
                onMore = body?.let { { onOpenFloorActions(FloorActions(it, onReply = null, onQuote = null, onEdit)) } },
                collected = state.collected,
                collectionCount = state.collectionCount,
                collectPending = state.collectPending,
                onCollect = onCollect,
                voteContent = voteContent,
                stardustContent = stardustContent,
                onTitleBottom = onTitleBottom,
                showOpeningPost = state.firstLoadedPage == 1 && !state.onlyAuthor,
            )
        }

        if (state.hasContent) {
            item(key = "comments-header") {
                CommentsHeader(state = state, onLoadPrevious = onLoadPrevious, onToggleOnlyAuthor = onToggleOnlyAuthor, onRetryAuthor = onRetryAuthor)
            }
        } else {
            // "共 0 条回复" would be a claim, and nobody has counted yet.
            item(key = "comments-skeleton") { CommentSkeletons() }
        }

        itemsIndexed(
            items = state.comments,
            key = { index, comment -> comment.lazyKey(index) },
        ) { index, comment ->
            // The site page this floor came from, which is the only page whose `__config__` carries
            // its Markdown. Read from the index rather than derived from the floor number: the list
            // is one scroll over several pages, and the two disagree as soon as a floor above has
            // been deleted.
            val onEdit = comment.commentId
                ?.takeIf { comment.isMine }
                ?.let { id ->
                    {
                        onEditFloor(
                            PostEditTarget(
                                postId = state.postId,
                                commentId = id,
                                page = state.commentSourcePages.getOrNull(index) ?: state.commentPages.getOrNull(index) ?: state.lastLoadedPage,
                                isOpeningPost = false,
                            ),
                        )
                    }
                }
            // 回复 gives way to 编辑 on this account's own floor, which is what the site does.
            val onReply = { onReplyToFloor(comment.toFloorReference()) }.takeIf { onEdit == null }
            val onQuote = { comment.toFloorReference()?.let(onQuoteFloor) ?: Unit }
            // Inside this floor's own item rather than an item of its own, so every index helper
            // below that mirrors the list's order stays one comment, one row.
            if (state.startsNewReplies(index)) NewRepliesDivider()
            val shape = RoundedCornerShape(topStart = 7.dp, topEnd = 19.dp, bottomStart = 19.dp, bottomEnd = 19.dp)
            Surface(
                modifier = Modifier.padding(start = 12.dp, end = 16.dp, bottom = 9.dp).fillMaxWidth(),
                shape = shape,
                color = if (comment.floor == highlightedFloor && highlightedFloor != null) MaterialTheme.colorScheme.primaryContainer else LocalPlazaLayers.current.card,
            ) {
                BlockAware(content = comment, revealed = state.showBlockedContent) {
                    CommentRow(
                        comment = comment,
                        authorInfo = comment.authorUid?.let(state.replyAuthors::get),
                        onOpenBrowser = onOpenBrowser,
                        onImageClick = onImageClick,
                        onJumpToFloor = onJumpToFloor,
                        pendingReaction = state.pendingReactionFor(comment),
                        onReact = { action -> onReact(comment, action) },
                        onReply = onReply,
                        onQuote = onQuote,
                        onEdit = onEdit,
                        showDivider = false,
                        onMore = { onOpenFloorActions(FloorActions(comment, onReply, onQuote, onEdit)) },
                        onAuthorClick = onAuthorClick,
                        voteContent = voteContent,
                        stardustContent = stardustContent,
                    )
                }
            }
        }

        if (state.isAppending) {
            item(key = "appending") { AppendSpinner() }
        } else if (state.showReadingEnd) {
            item(key = "thread-end") {
                Text(
                    stringResource(Res.string.post_reading_end),
                    Modifier.fillMaxWidth().padding(vertical = Spacing.lg),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Draws [floor], or the one-line stand-in the site's block list has earned it.
 *
 * A blocked floor is *kept*, not dropped: NodeSeek sends it and hides it, so the app collapses it the
 * same way. Dropping it would renumber nothing — floors carry their own numbers — but it would leave
 * a reply quoting #12 pointing at a floor that, as far as the reader can tell, never existed.
 *
 * [revealed] is 临时显示被屏蔽内容 and opens every floor at once; the row's own 显示 opens exactly one
 * and forgets it when the item scrolls out of the composition, which is the lighter of the two ways
 * to answer "what did they actually say".
 */
@Composable
private fun BlockAware(
    content: PostContent,
    revealed: Boolean,
    floor: @Composable () -> Unit,
) {
    var openedHere by rememberSaveable(content.commentId) { mutableStateOf(false) }
    if (!content.isBlocked || revealed || openedHere) {
        floor()
    } else {
        // A row like the floor it stands in for, but a slim one: it is a line, not a post.
        FloorRow(verticalPadding = 0.dp) {
            BlockedFloorRow(floor = content.floor, onShow = { openedHere = true })
        }
    }
}

@Composable
private fun BlockedFloorRow(
    floor: String?,
    onShow: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            PlazaIcons.VisibilityOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = floor?.let { "$it · " }.orEmpty() + stringResource(Res.string.post_comment_blocked),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onShow) {
            Text(stringResource(Res.string.post_comment_blocked_show))
        }
    }
}

/**
 * Full-width opening punctuation draws its ink in the right half of the em box, so a title like
 * 「【出】92折出SG落地机」 starts a good half character right of everything under it. That gap was
 * invisible while the opening post sat in its own container — the title and the container were on
 * different left edges anyway — and became the most obvious misalignment on the screen once they
 * shared one. Hanging the first line out by half an em puts the bracket's ink back on the margin.
 */
private val HANGING_PUNCTUATION = setOf('【', '「', '『', '《', '〈', '（', '〔', '“', '‘')

private fun TextStyle.hangLeadingPunctuation(text: String): TextStyle =
    if (text.firstOrNull() in HANGING_PUNCTUATION) {
        copy(textIndent = TextIndent(firstLine = fontSize * -0.5f))
    } else {
        this
    }

/** Board tags introduce the title, which keeps the feed's reading size through navigation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThreadHeader(
    title: String,
    postId: Long,
    body: PostContent?,
    isAwarded: Boolean,
    /**
     * What the list that opened this thread already said about it, drawn wherever [body] cannot yet.
     * See [io.github.nodyssey.PostDetailKey.preview].
     */
    preview: ThreadPreview?,
    modifier: Modifier = Modifier,
    /** Fetches page 1 and scrolls to the opening post; null when it is already on screen. */
    onOpenOriginalPost: (() -> Unit)? = null,
    onTitlePlaced: (LayoutCoordinates) -> Unit = {},
) {
    Column(
        modifier =
        modifier.then(if (onOpenOriginalPost != null) Modifier.clickable(onClick = onOpenOriginalPost) else Modifier),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        val category = body?.categoryTitle ?: preview?.categoryTitle
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                // The slug comes from the preview even once the body has arrived: the thread page's
                // own markup carries the board's name but not its slug, and the tag colours read the
                // slug first. Without it a tag would change colour under the reader the moment the
                // network answered — and it is the same tag, still in flight from the row.
                BoardTag(
                    title = category,
                    slug = preview?.categorySlug,
                    modifier = Modifier.sharedThreadBoard(postId),
                )
                // A labelled tag rather than the list's bare diamond: here there is room to name the thing.
                if (isAwarded) {
                    TonalTag(
                        text = stringResource(Res.string.post_badge_awarded),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        // The list's diamond, so the mark reads as the same one on both screens.
                        icon = NodeSeekIcons.Award,
                    )
                }
            }
            if (onOpenOriginalPost != null) {
                TextButton(onClick = onOpenOriginalPost, contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)) {
                    Text(stringResource(Res.string.post_open_original), style = MaterialTheme.typography.labelSmall)
                    Icon(PlazaIcons.OpenInNew, contentDescription = null, modifier = Modifier.padding(start = 3.dp).size(13.dp))
                }
            }
        }
        Text(
            text = title,
            style = postCardTitleStyle().hangLeadingPunctuation(title),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .sharedThreadTitle(postId)
                .onPlaced(onTitlePlaced),
        )
    }
}

/**
 * Who wrote a thread, at the size the opening post states it.
 *
 * Extracted because the loading state draws it too, from what the list already knew, and the two
 * have to agree to the pixel: the avatar and the name are mid-flight from a feed row when the
 * loading state is on screen, and they land again — without moving — when the thread replaces it.
 * Two hand-matched copies of this geometry would drift, and the drift would show as a twitch at the
 * exact moment the reader is watching.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThreadAuthorRow(
    postId: Long,
    avatarUrl: String?,
    authorName: String,
    modifier: Modifier = Modifier,
    /** Put on the avatar and the name only — the badges, note and time beside them are not a way in. */
    identity: Modifier = Modifier,
    accountBadge: @Composable () -> Unit = {},
    badges: @Composable () -> Unit = {},
) {
    Row(
        // Top, not centred: the avatar is shorter than the name and time beside it, and centring it
        // would tie where it lands to how tall that text measures — which changes the moment badges
        // arrive with the thread, under an avatar still settling out of its flight.
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = modifier,
    ) {
        UserAvatar(
            url = avatarUrl,
            name = authorName,
            size = ThreadAvatarSize,
            shape = CircleShape,
            modifier = Modifier
                .sharedThreadAvatar(postId)
                .then(identity)
                // The name beside it is the same way in, and the one a screen reader is told about.
                .clearAndSetSemantics {},
        )
        // Keep the name, account facts and role badges together, wrapping only when space runs out.
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = authorName,
                style = floorNameStyle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.sharedThreadAuthor(postId).then(identity),
            )
            accountBadge()
            badges()
        }
    }
}

/**
 * The thread header followed by the opening floor, with the feed preview kept while loading.
 *
 * A null [body] is the thread still loading, and this draws it rather than handing the screen over
 * to a separate skeleton, because the title, the avatar and the author's name here are shared
 * elements landing out of a flight from a feed row. Two composables would mean two sets of nodes,
 * and swapping one for the other the moment the body arrived would look to the shared-element
 * machinery like a fresh transition — it would fly them a second time, from wherever the replacement
 * was first measured. One call site whose arguments change is the whole fix: nothing is disposed, so
 * nothing moves.
 */
@Composable
private fun ThreadOpeningPost(
    title: String,
    isAwarded: Boolean,
    onOpenOriginalPost: (() -> Unit)?,
    body: PostContent?,
    preview: ThreadPreview?,
    postId: Long,
    authorLevel: Int?,
    authorJoinedDays: Int?,
    showBlockedContent: Boolean,
    onOpenBrowser: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onJumpToFloor: (String) -> Unit,
    pendingReaction: ReactionAction?,
    onReact: (ReactionAction) -> Unit,
    onAuthorClick: (Long) -> Unit,
    /** Opens the post's 1c panel; null until there is a post to act on. */
    onMore: (() -> Unit)?,
    /* Collection is whole-thread, so it belongs on the opening post and nowhere else. */
    collected: Boolean?,
    collectionCount: Int?,
    collectPending: Boolean,
    onCollect: () -> Unit,
    voteContent: @Composable (Long) -> Unit,
    stardustContent: (@Composable (RichNode.StardustReceive) -> Unit)?,
    /** Where the title ends inside this lazy item, in px, each time the two are laid out. */
    onTitleBottom: (Int) -> Unit = {},
    /** A retained body belongs to page 1; it must never be repeated above a later-page window. */
    showOpeningPost: Boolean = true,
) {
    // [BlockAware]'s job, done here rather than around this composable: a wrapper that swapped the
    // whole opening post out would take the shared elements with it, which is the thing this
    // arrangement exists to prevent. A blocked author is the one case where they *should* go — the
    // point of blocking is that the name and the face are not shown. The title stays: it is the
    // thread's, not the author's.
    var revealedHere by rememberSaveable(body?.commentId) { mutableStateOf(false) }
    val blocked = body != null && body.isBlocked && !showBlockedContent && !revealedHere
    val longPress = onMore.takeIf { showOpeningPost && !blocked }
    // Read in the title's placement callback, never in composition; the opening item is placed first.
    val opening = remember { OpeningCoordinates() }

    Column(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(LocalPlazaLayers.current.card)
            .fillMaxWidth()
            .onPlaced { opening.coordinates = it }
            .floorLongPressSemantics(longPress),
    ) {
        ThreadHeader(
            title = title,
            postId = postId,
            body = body,
            isAwarded = isAwarded,
            preview = preview,
            onOpenOriginalPost = onOpenOriginalPost,
            onTitlePlaced = { title ->
                opening.coordinates?.takeIf { it.isAttached }?.let { coordinates ->
                    val bottom = coordinates.localPositionOf(title, Offset(0f, title.size.height.toFloat())).y
                    onTitleBottom(bottom.roundToInt())
                }
            },
            modifier = Modifier
                .padding(horizontal = ThreadContentInset, vertical = Spacing.lg)
                .floorLongPress(longPress),
        )
        if (!showOpeningPost) return@Column
        Column(
            modifier = Modifier.padding(start = ThreadContentInset, end = ThreadContentInset, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) opening@{
            if (body != null && blocked) {
                BlockedFloorRow(floor = body.floor, onShow = { revealedHere = true })
                return@opening
            }

            Row(
                modifier = Modifier.floorLongPress(longPress),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                // The avatar and the name open the author's space; the rest of the row does not.
                ThreadAuthorRow(
                    postId = postId,
                    avatarUrl = body?.avatarUrl ?: preview?.avatarUrl,
                    authorName = body?.authorName ?: preview?.authorName.orEmpty(),
                    modifier = Modifier.weight(1f),
                    identity = Modifier.authorClickable(body?.authorUid, onAuthorClick),
                    accountBadge = { AuthorAccountBadge(authorLevel, authorJoinedDays) },
                    badges = {
                        if (body != null) {
                            FloorBadges(body, hasAccountLevel = authorLevel != null)
                        }
                        if (body != null) {
                            UserNoteTag(body.authorUid)
                        }
                    },
                )
                if (body != null) FloorMetadata(body) else MetaLinePlaceholder()
            }

            if (body == null) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    listOf(0.98f, 0.94f, 0.99f, 0.62f).forEach { SkeletonBar(it, 14.dp) }
                }
                return@opening
            }

            if (body.nodes.isEmpty()) {
                Text(
                    text = stringResource(Res.string.post_body_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ReportOriginFloor(body.floor) {
                    ImageActionsFrom(body.authorName, body.floor) {
                        PostRichContent(
                            nodes = body.nodes,
                            onLinkClick = onOpenBrowser,
                            onImageClick = onImageClick,
                            onQuoteRefClick = { onJumpToFloor(it.floor) },
                            textStyle = replyBodyStyle(),
                            voteContent = voteContent,
                            stardustContent = stardustContent,
                        )
                    }
                }
            }
            UserSignature(
                nodes = body.signatureNodes,
                bodyStyle = replyBodyStyle(),
                onOpenBrowser = onOpenBrowser,
                onImageClick = onImageClick,
                onJumpToFloor = onJumpToFloor,
            )
            OpeningPostActions(
                reactions = body.reactions,
                pending = pendingReaction,
                onReact = onReact,
                collected = collected,
                collectionCount = collectionCount,
                collectPending = collectPending,
                onCollect = onCollect,
                onMore = onMore,
                modifier = Modifier.floorLongPress(longPress),
            )
        }
    }
}

/** The opening item as last placed — see `onTitleBottom` on [ThreadOpeningPost]. */
private class OpeningCoordinates {
    var coordinates: LayoutCoordinates? = null
}

/** Names stay readable above the smaller badges and timestamps. */
@Composable
internal fun floorNameStyle(): TextStyle = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize, fontWeight = FontWeight.SemiBold)

/**
 * Main text uses the same reading size in the opening post and every reply.
 */
@Composable
internal fun replyBodyStyle(): TextStyle = MaterialTheme.typography.bodyMedium

private val ThreadContentInset = Spacing.lg

private val ThreadAvatarSize = 32.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommentsHeader(
    state: PostDetailUiState,
    onLoadPrevious: () -> Unit,
    onToggleOnlyAuthor: () -> Unit,
    onRetryAuthor: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        val count = state.replyCount?.let { " $it" }.orEmpty() + if (state.onlyAuthor && (state.isScanningAuthor || state.authorScanError != null)) "+" else ""
        Text(
            stringResource(if (state.onlyAuthor) Res.string.post_author_replies else Res.string.post_all_replies) + count,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onToggleOnlyAuthor,
                shape = CircleShape,
                colors = ButtonDefaults.textButtonColors(containerColor = if (state.onlyAuthor) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, contentColor = if (state.onlyAuthor) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(stringResource(Res.string.post_only_author), style = MaterialTheme.typography.labelSmall)
            }
            Text(stringResource(Res.string.post_sort_ascending), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
    if (state.isScanningAuthor || state.isPrepending) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlazaSpinner(size = 14.dp, strokeWidth = 1.5.dp)
            Text(
                if (state.isScanningAuthor) {
                    stringResource(Res.string.post_author_scan, state.authorScanChecked, state.authorScanTotal)
                } else {
                    stringResource(Res.string.post_loading_earlier)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else if (state.authorScanError != null || (state.firstLoadedPage > 1 && state.error != null)) {
        TextButton(onClick = if (state.onlyAuthor) onRetryAuthor else onLoadPrevious, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.post_author_scan_retry), style = MaterialTheme.typography.labelSmall)
        }
    }
    if (state.comments.isEmpty() && !state.isScanningAuthor && !state.isLoading) {
        Text(
            stringResource(if (state.onlyAuthor) Res.string.post_author_empty else Res.string.post_comments_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * One reply, with a separate identity header, full-width content, and trailing actions.
 *
 * Real replies on this forum are anywhere between two characters and several screens, so the avatar
 * stands beside the name and badges, with time and floor at the far end. The body starts below
 * the whole header so images, quotes and long text can use the full reading width.
 *
 * The foot carries 点赞, 引用 and 回复, with a ⋯ for the priced reactions and the remaining
 * actions, which [FloorActionSheet] lays out with their prices. A long press on
 * the header or the foot opens the same panel; on the text it selects, as it always has — see
 * [floorLongPress] for why the row as a whole does not listen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommentRow(
    comment: PostContent,
    authorInfo: ReplyAuthorInfo?,
    onOpenBrowser: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onJumpToFloor: (String) -> Unit,
    pendingReaction: ReactionAction?,
    onReact: (ReactionAction) -> Unit,
    /** Null on this account's own floor, where [onEdit] takes its place. */
    onReply: (() -> Unit)?,
    onQuote: () -> Unit,
    onEdit: (() -> Unit)?,
    showDivider: Boolean = true,
    onMore: () -> Unit,
    onAuthorClick: (Long) -> Unit,
    voteContent: @Composable (Long) -> Unit,
    stardustContent: (@Composable (RichNode.StardustReceive) -> Unit)?,
) {
    FloorRow(modifier = Modifier.floorLongPressSemantics(onMore), showDivider = showDivider) {
        Row(
            modifier = Modifier.fillMaxWidth().floorLongPress(onMore),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            UserAvatar(
                url = comment.avatarUrl,
                name = comment.authorName,
                size = ThreadAvatarSize,
                shape = CircleShape,
                modifier = Modifier
                    .authorClickable(comment.authorUid, onAuthorClick)
                    // The name beside it is the same way in, and the one a screen reader is told about.
                    .clearAndSetSemantics {},
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = comment.authorName,
                        style = floorNameStyle(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.authorClickable(comment.authorUid, onAuthorClick),
                    )
                    AuthorAccountBadge(authorInfo?.level, authorInfo?.joinedDays)
                    FloorBadges(comment, hasAccountLevel = authorInfo?.level != null)
                    UserNoteTag(comment.authorUid)
                }
                FloorTimeLine(comment)
            }
            comment.floor?.let { FloorLabel(it) }
        }
        ReportOriginFloor(comment.floor) {
            ImageActionsFrom(comment.authorName, comment.floor) {
                PostRichContent(
                    nodes = comment.nodes,
                    onLinkClick = onOpenBrowser,
                    onImageClick = onImageClick,
                    onQuoteRefClick = { ref -> onJumpToFloor(ref.floor) },
                    textStyle = replyBodyStyle(),
                    voteContent = voteContent,
                    stardustContent = stardustContent,
                )
            }
        }
        UserSignature(
            nodes = comment.signatureNodes,
            bodyStyle = replyBodyStyle(),
            onOpenBrowser = onOpenBrowser,
            onImageClick = onImageClick,
            onJumpToFloor = onJumpToFloor,
        )
        CommentFoot(
            reactions = comment.reactions,
            pending = pendingReaction,
            onReact = onReact,
            onReply = onReply,
            onQuote = onQuote,
            onEdit = onEdit,
            onMore = onMore,
            modifier = Modifier.floorLongPress(onMore),
        )
    }
}

/**
 * Shared floor content and gutters. Reply bubbles provide their own separation; standalone rows
 * and shared-image captures can retain the optional divider.
 */
@Composable
private fun FloorRow(
    modifier: Modifier = Modifier,
    verticalPadding: Dp = 14.dp,
    showDivider: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ThreadContentInset, vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            content = content,
        )
        if (showDivider) HorizontalDivider(color = LocalPlazaLayers.current.divider.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = ThreadContentInset))
    }
}

/** The time and floor belong together at the opposite edge from the author's identity. */
@Composable
private fun FloorMetadata(content: PostContent) {
    Column(
        modifier = Modifier.widthIn(max = 136.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        FloorTimeLine(content)
        content.floor?.let { FloorLabel(it) }
    }
}

/**
 * NodeSeek's public Markdown signature, visually separated from the floor's actual content.
 *
 * [bodyStyle] is the style of the floor this signature hangs off, which [asSignature] steps down
 * from — a signature is a footer to *that* text, so it has to stay smaller than it at any reading
 * size rather than sit at a size of its own.
 */
@Composable
private fun UserSignature(
    nodes: List<RichNode>,
    bodyStyle: TextStyle,
    onOpenBrowser: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onJumpToFloor: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (nodes.isEmpty()) return

    Column(modifier) {
        HorizontalDivider(
            color = LocalPlazaLayers.current.divider,
            modifier = Modifier.padding(bottom = Spacing.sm),
        )
        PostRichContent(
            nodes = nodes,
            onLinkClick = onOpenBrowser,
            onImageClick = onImageClick,
            onQuoteRefClick = { onJumpToFloor(it.floor) },
            textStyle = bodyStyle.asSignature().copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}

/** Tappable only when the uid was actually parsed; a dead ripple would promise a screen we cannot open. */
private fun Modifier.authorClickable(uid: Long?, onAuthorClick: (Long) -> Unit): Modifier =
    if (uid == null) this else clickable { onAuthorClick(uid) }

/**
 * A long press on a floor's header or foot opens its 1c panel.
 *
 * Put on those two and not on the card: the body and the signature are selectable text, and a
 * selection's long press does not consume the pointer, so a detector on the card timed out on the
 * same press and the panel rose over the selection the reader had just started — both fired, every
 * time. Nothing inside the header or the foot is selectable, so there the press means the panel.
 *
 * A `combinedClickable` on the whole floor would also intercept taps: a floor
 * whose tap does nothing would still ripple on every tap a reader makes while reading. A gesture
 * detector that listens only for the long press leaves the tap alone. Screen readers get the action
 * on the card as a whole, from [floorLongPressSemantics], beside the ⋯ that is always there.
 */
@Composable
private fun Modifier.floorLongPress(onLongPress: (() -> Unit)?): Modifier {
    if (onLongPress == null) return this
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(onLongPress)
    return pointerInput(Unit) {
        detectTapGestures(
            onLongPress = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                current()
            },
        )
    }
}

/** The [floorLongPress] panel as a screen reader's long-click action, on the whole card. */
@Composable
private fun Modifier.floorLongPressSemantics(onLongPress: (() -> Unit)?): Modifier {
    if (onLongPress == null) return this
    val current by rememberUpdatedState(onLongPress)
    val label = stringResource(Res.string.post_floor_actions)
    return semantics {
        onLongClick(label) {
            current()
            true
        }
    }
}

/** Thread actions share the replies' quiet, trailing pills. Unknown collection state stays absent. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpeningPostActions(
    reactions: PostReactions?,
    pending: ReactionAction?,
    onReact: (ReactionAction) -> Unit,
    collected: Boolean?,
    collectionCount: Int?,
    collectPending: Boolean,
    onCollect: () -> Unit,
    onMore: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(ReactionAction.Upvote, ReactionAction.ChickenLeg).forEach { action ->
            ReactionPill(action, reactions, pending, onReact)
        }
        if (collected != null) {
            FloorActionButton(
                icon = if (collected) PlazaIcons.Bookmark else PlazaIcons.BookmarkBorder,
                label = if (collected) Res.string.post_collected_action else Res.string.post_collect_action,
                count = collectionCount?.takeIf { it > 0 }?.toString().orEmpty(),
                selected = collected,
                pending = collectPending,
                onClick = onCollect.takeUnless { collectPending },
            )
        }
        if (onMore != null) FloorMoreButton(onMore)
    }
}

/** A spent reaction stays accented and inert; the existing confirmation gate still owns spending. */
@Composable
private fun ReactionPill(
    action: ReactionAction,
    reactions: PostReactions?,
    pending: ReactionAction?,
    onReact: (ReactionAction) -> Unit,
) {
    FloorActionButton(
        icon = action.icon(),
        label = action.labelRes(),
        count = reactions?.countOf(action)?.takeIf { it > 0 }?.toString().orEmpty(),
        spent = reactions?.hasSpent(action) == true,
        pending = pending == action,
        onClick = if (reactions != null && pending == null) ({ onReact(action) }) else null,
    )
}

/** Wrap complete buttons at large text sizes, keeping every action reachable. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommentFoot(
    reactions: PostReactions?,
    pending: ReactionAction?,
    onReact: (ReactionAction) -> Unit,
    onReply: (() -> Unit)?,
    onQuote: () -> Unit,
    onEdit: (() -> Unit)?,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReactionPill(ReactionAction.Upvote, reactions, pending, onReact)
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.End),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            FloorActionButton(PlazaIcons.FormatQuote, Res.string.post_quote_action, onClick = onQuote)
            when {
                onEdit != null -> FloorActionButton(Icons.Default.Edit, Res.string.post_edit_action, onClick = onEdit)
                onReply != null -> FloorActionButton(PlazaIcons.Reply, Res.string.post_reply_action, onClick = onReply)
            }
            FloorMoreButton(onMore)
        }
    }
}

/** The same explicit entry to all floor actions remains available beside the quick actions. */
@Composable
private fun FloorMoreButton(onClick: () -> Unit) {
    FloorActionButton(PlazaIcons.MoreHoriz, Res.string.post_floor_actions, onClick = onClick)
}

/** Material buttons keep their minimum touch targets while drawing a compact rounded pill. */
@Composable
private fun FloorActionButton(
    icon: ImageVector,
    label: StringResource,
    count: String = "",
    spent: Boolean = false,
    selected: Boolean = false,
    pending: Boolean = false,
    onClick: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    val accented = spent || selected
    val container = if (accented) scheme.primaryContainer else Color.Transparent
    val content = if (accented) scheme.onPrimaryContainer else scheme.onSurfaceVariant
    val description = stringResource(label)
    FilledTonalButton(
        onClick = onClick ?: {},
        enabled = onClick != null && !spent && !pending,
        shape = CircleShape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container,
            disabledContentColor = if (accented || pending) content else content.copy(alpha = 0.45f),
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        modifier = Modifier
            .height(36.dp)
            .widthIn(min = 44.dp)
            .semantics { contentDescription = description },
    ) {
        if (pending) {
            PlazaSpinner(modifier = Modifier.describedAsLoading(), strokeWidth = 2.dp, size = 20.dp)
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        if (count.isNotEmpty()) {
            Text(count, style = footLabelStyle(), modifier = Modifier.padding(start = Spacing.xs))
        } else if (label == Res.string.post_reply_action || label == Res.string.post_quote_action || label == Res.string.post_edit_action) {
            Text(description, style = footLabelStyle(), modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun footLabelStyle(): TextStyle =
    MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = TABULAR_FIGURES)

/** Each mark's glyph, wherever it is drawn — the row, the pill, the 1c tile. */
private fun ReactionAction.icon(): ImageVector =
    when (this) {
        ReactionAction.Upvote -> Icons.Default.ThumbUp
        ReactionAction.ChickenLeg -> NodeSeekIcons.ChickenLeg
        ReactionAction.Dislike -> PlazaIcons.ThumbDown
    }

private fun ReactionAction.labelRes(): StringResource =
    when (this) {
        ReactionAction.Upvote -> Res.string.post_reaction_like
        ReactionAction.ChickenLeg -> Res.string.post_reaction_chicken
        ReactionAction.Dislike -> Res.string.post_reaction_dislike
    }

/** The floor a confirmation is asking about, and what it would spend on it. */
private data class ReactionConfirm(
    val content: PostContent,
    val commentId: Long,
    val action: ReactionAction,
)

/**
 * The one gate in front of an irreversible spend.
 *
 * Only 加鸡腿 and 反对 get one, matching the site: 点赞 costs nothing and is sent on the tap. The body
 * has to name the price, because these are the only two places in the app where reading a thread can
 * cost the reader currency — and 反对 costs *two*, which is the kind of thing a reader discovers
 * afterwards if the dialog only says "确定吗".
 */
@Composable
private fun ReactionConfirmDialog(
    target: ReactionConfirm,
    freeChickenLegs: FreeChickenLegs?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val isChicken = target.action == ReactionAction.ChickenLeg
    // Only claim it is free when the site told us so; an unread quota says nothing either way, and
    // "免费" that turns out to have cost a chicken leg is the worse of the two mistakes.
    val free = isChicken && freeChickenLegs != null && freeChickenLegs.remaining > 0
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (isChicken) NodeSeekIcons.ChickenLeg else PlazaIcons.ThumbDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                stringResource(
                    if (isChicken) Res.string.chicken_dialog_title else Res.string.dislike_dialog_title,
                ),
            )
        },
        text = {
            Text(
                when {
                    free ->
                        stringResource(
                            Res.string.chicken_dialog_body_free,
                            target.content.authorName,
                            freeChickenLegs.remaining,
                        )

                    isChicken -> stringResource(Res.string.chicken_dialog_body, target.content.authorName)

                    else -> stringResource(Res.string.dislike_dialog_body)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(
                        if (isChicken) Res.string.chicken_dialog_confirm else Res.string.dislike_dialog_confirm,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}

/**
 * What a floor's 1c panel can do besides the three marks, as that floor's own row already wired it.
 *
 * Built where the row is, because that is where the answers are: whether this account wrote the
 * floor (编辑 instead of 回复), which site page carries its Markdown, and whether it is the opening
 * post — which the site offers neither 回复 nor 引用 of.
 */
private class FloorActions(
    val content: PostContent,
    val onReply: (() -> Unit)?,
    val onQuote: (() -> Unit)?,
    val onEdit: (() -> Unit)?,
)

/**
 * 楼层互动 (1c): the floor quoted at the top, its three marks as tiles that say what each costs, and
 * the floor's other actions as a list.
 *
 * The set is the site's and fixed — 点赞 / 投喂鸡腿 / 点踩, then 回复 (编辑 on your own floor) and 引用
 * — with 复制正文 the one thing the app adds, because a selection drag across a long floor is the
 * other way to get its text and it is a poor one. A tile only asks for the mark: [onReact] closes
 * the panel and goes through the same gates the row does, so 投喂 and 点踩 still stop at their
 * confirmations and a signed-out reader still goes to sign in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FloorActionSheet(
    content: PostContent,
    actions: FloorActions,
    pending: ReactionAction?,
    freeChickenLegs: FreeChickenLegs?,
    onDismiss: () -> Unit,
    onReact: (ReactionAction) -> Unit,
    /** 分享为图片; null where this platform has no share sheet, or while one is already being drawn. */
    onShareImage: (() -> Unit)? = null,
) {
    val copy = rememberClipboardCopy()
    val copied = stringResource(Res.string.post_body_copied)
    val notes = LocalUserNotes.current
    PlazaSheet(
        onDismiss = onDismiss,
        sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        Column(
            modifier = Modifier
                // A landscape phone, a split screen or a large text size is shorter than the panel,
                // and the sheet only clips what it cannot show — 复制正文 and the note under it
                // were simply out of reach there. The sheet hands the scroll its drag first.
                .verticalScroll(rememberScrollState())
                .padding(start = Spacing.md, end = Spacing.md, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // The panel's head: whose floor this is, and the first line of it, so the reader knows
            // what they are marking.
            QuotePreview(
                title = listOfNotNull(content.authorName, content.floor).joinToString(" · "),
                excerpt = content.nodes.excerpt(),
                leading = { UserAvatar(url = content.avatarUrl, name = content.authorName, size = FloorSheetAvatar) },
            )
            // One height for the three, since a label or a price may take a second line.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                listOf(ReactionAction.Upvote, ReactionAction.ChickenLeg, ReactionAction.Dislike).forEach { action ->
                    val reactions = content.reactions
                    ReactionTile(
                        action = action,
                        count = reactions?.countOf(action),
                        price = action.price(freeChickenLegs),
                        spent = reactions?.hasSpent(action) == true,
                        pending = pending == action,
                        // Same rule as the row: tallies never read are marks we cannot say are unspent.
                        onClick = if (reactions != null && pending == null) ({ onReact(action) }) else null,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
            val rows =
                buildList {
                    actions.onEdit?.let { add(Triple(Icons.Default.Edit, stringResource(Res.string.post_edit_action), it)) }
                        ?: actions.onReply?.let {
                            add(Triple(PlazaIcons.Reply, stringResource(Res.string.post_reply_to, content.authorName), it))
                        }
                    actions.onQuote?.let {
                        add(Triple(PlazaIcons.FormatQuote, stringResource(Res.string.post_quote_floor), it))
                    }
                    // The whole floor, code and lists and tables included — see [copyableText]. A floor
                    // with no text at all (a picture, a poll) has nothing to offer here: a row that
                    // copied an empty string, or only a picture's address, would still say it had
                    // copied the body.
                    if (content.nodes.hasCopyableText()) {
                        val bodyText = content.nodes.copyableText()
                        add(
                            Triple(PlazaIcons.ContentCopy, stringResource(Res.string.post_copy_body)) {
                                copy("post", bodyText, copied)
                            },
                        )
                    }
                    onShareImage?.let {
                        add(Triple(PlazaIcons.Image, stringResource(Res.string.post_share_image), it))
                    }
                    // 备注 is about other people; a note on yourself has nobody to remind.
                    val uid = content.authorUid
                    if (uid != null && !content.isMine) {
                        add(
                            Triple(PlazaIcons.Badge, stringResource(Res.string.user_note_action)) {
                                notes.edit(uid, content.authorName)
                            },
                        )
                    }
                }
            Column {
                rows.forEachIndexed { index, (icon, label, action) ->
                    GroupedListItem(
                        first = index == 0,
                        last = index == rows.lastIndex,
                        onClick = {
                            onDismiss()
                            action()
                        },
                        leadingContent = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        headlineContent = {
                            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium))
                        },
                        // Three short verbs under the tiles, a step under a settings page's 56dp rows.
                        // 3a draws them at 44; an interactive list row keeps Material's 48dp target.
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        modifier = Modifier.heightIn(min = Sizes.minTouchTarget),
                    )
                }
            }
            Text(
                text = stringResource(Res.string.post_reaction_irreversible),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * One mark as a tile: glyph, name and tally, and under them what it costs — 免费, 今日免费 N 次, 扣 2
 * 鸡腿 — or 已表态 once spent. The price is on the tile because the tile is where the choice is made;
 * the confirmation still says it again before anything is spent.
 */
@Composable
private fun ReactionTile(
    action: ReactionAction,
    count: Int?,
    price: String,
    spent: Boolean,
    pending: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val (container, ink) =
        when (action) {
            ReactionAction.Upvote -> scheme.primaryContainer to scheme.onPrimaryContainer
            ReactionAction.ChickenLeg -> scheme.tertiaryContainer to scheme.onTertiaryContainer
            ReactionAction.Dislike -> scheme.surfaceContainerHigh to scheme.onSurface
        }
    TonalTile(
        onClick = onClick ?: {},
        containerColor = container,
        contentColor = ink,
        enabled = onClick != null && !spent && !pending,
        border = LocalPlazaLayers.current.cardBorderStroke,
        contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier,
    ) {
        if (pending) {
            PlazaSpinner(modifier = Modifier.describedAsLoading(), strokeWidth = 2.dp, size = ReactionTileIcon)
        } else {
            Icon(action.icon(), contentDescription = null, modifier = Modifier.size(ReactionTileIcon))
        }
        // Two lines for the label and the price rather than one: a third of a phone's width holds
        // 点赞 and 扣 2 鸡腿, but not "Send a drumstick" or "Costs 2 drumsticks", and the price is the
        // thing this tile is here to say.
        //
        // The tally always has a line of its own under the label. It used to share the label's line
        // in a FlowRow and wrap under it when too wide, but the panel gives the three tiles one height
        // through IntrinsicSize.Min, and measured that way the FlowRow came out a line short whenever
        // it wrapped: at 360dp a four-digit 投喂 tally pushed 今日免费 1 次 out through the bottom of
        // its tile. A Column's intrinsic height is just its lines, so the tiles grow with it — and the
        // three are built alike whatever their tallies, where the FlowRow could put one tally beside
        // its label and the next one under it.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(action.labelRes()),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            count?.let {
                Text(
                    it.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
                    maxLines = 1,
                )
            }
        }
        Text(
            text = if (spent) stringResource(Res.string.post_reaction_spent) else price,
            style = MaterialTheme.typography.labelSmall,
            color = if (action == ReactionAction.Dislike) scheme.onSurfaceVariant else ink,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The floor's face at the head of the 楼层互动 panel, as 3a draws it. */
private val FloorSheetAvatar = 24.dp

/** A mark's glyph on its tile, as 3a draws it: a hair under Material's 24. */
private val ReactionTileIcon = 22.dp

/**
 * What a mark costs, as the tile states it.
 *
 * 投喂 is only called free when the site said today's allowance covers it; an unread quota says the
 * full price, which is what the confirmation says in the same case.
 */
@Composable
private fun ReactionAction.price(freeChickenLegs: FreeChickenLegs?): String =
    when {
        this == ReactionAction.Upvote -> stringResource(Res.string.post_reaction_free)

        this == ReactionAction.ChickenLeg && freeChickenLegs != null && freeChickenLegs.remaining > 0 ->
            stringResource(Res.string.post_reaction_free_today, freeChickenLegs.remaining)

        else -> stringResource(Res.string.post_reaction_cost, chickenLegCost)
    }

/**
 * The header's badge chips: everything the parser read, with 楼主 prepended when the page marked
 * the author as OP some other way (the opening post itself carries no `is-poster` span on page 1
 * of some templates). Capped at three plus a +N chip — see [RoleBadgeRow].
 */
@Composable
private fun FloorBadges(content: PostContent, hasAccountLevel: Boolean = false) {
    val opLabel = stringResource(Res.string.post_badge_original_poster)
    val labels =
        if (content.isOriginalPoster && content.badges.none { it == opLabel }) {
            listOf(opLabel) + content.badges
        } else {
            content.badges
        }
    val remaining = if (hasAccountLevel) labels.filterNot { ACCOUNT_LEVEL_BADGE.matches(it) } else labels
    if (remaining.isNotEmpty()) RoleBadgeRow(remaining)
}

/** Registration age and rank are account facts, with the same tone for new and established users. */
@Composable
private fun AuthorAccountBadge(level: Int?, days: Int?) {
    val parts = buildList {
        if (level != null) add("Lv $level")
        if (days != null) add(stringResource(Res.string.post_author_age_days, days))
    }
    if (parts.isNotEmpty()) BadgeChip(parts.joinToString(" | "), BadgeTone.Neutral)
}

private val ACCOUNT_LEVEL_BADGE = Regex("Lv\\.?\\s*\\d+", RegexOption.IGNORE_CASE)

/** Match stable keys rather than indices, which can still describe the previous prepended window. */
internal fun PostDetailUiState.visibleReplyAuthorUids(keys: Set<Any>): Set<Long> =
    comments.withIndex().mapNotNull { (index, comment) ->
        comment.authorUid?.takeIf {
            it > 0 && comment.lazyKey(index) in keys && (!comment.isBlocked || showBlockedContent)
        }
    }.toSet()

/**
 * The floor's time, and after it the dashed-underline 已编辑 marker (b1 §8).
 *
 * A [FlowRow], so at a large text size the marker moves under the time whole instead of both being
 * folded into columns a word wide.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FloorTimeLine(
    content: PostContent,
    modifier: Modifier = Modifier,
) {
    if (content.createdAtText == null && !content.isEdited) return
    FlowRow(
        modifier = modifier,
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.End),
    ) {
        // Drawn without its "ago", spoken with it: read aloud, a bare "5min" is not a time.
        content.createdAtText?.let {
            MetaText(TimeFormat.withoutAgo(it), Modifier.semantics { contentDescription = it })
        }
        if (content.isEdited) {
            if (content.createdAtText != null) MetaText("·")
            EditedMarker(content)
        }
    }
}

/**
 * 已编辑, and after it the moment — `编辑于 5min ago`, the same claim the site prints.
 *
 * The time is dropped rather than invented when the page did not carry one: some floors carry a bare
 * marker, and "编辑于" with nothing after it would read as a truncation.
 */
@Composable
private fun EditedMarker(content: PostContent) {
    val plain = stringResource(Res.string.post_edited)
    val label = content.editedAtText?.let { stringResource(Res.string.post_edited_at, TimeFormat.withoutAgo(it)) } ?: plain
    // The absolute stamp is the accessible name where the page gave one — a relative label read
    // aloud out of context ("5min ago", from a screen opened ten minutes back) dates itself.
    // Without one, the relative label is spoken whole — "ago" and all, which only the drawn one drops.
    val spoken =
        (content.editedAtTitle ?: content.editedAtText)?.let { stringResource(Res.string.post_edited_at, it) }
            ?: plain
    val underline = MaterialTheme.colorScheme.outlineVariant
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
        Modifier
            .semantics { contentDescription = spoken }
            .drawBehind {
                // TextDecoration has no dashed variant, so the spec's dashed underline is drawn.
                val y = size.height - 0.5.dp.toPx()
                drawLine(
                    color = underline,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
                )
            },
    )
}

/**
 * The context the reply editor opens with.
 *
 * `null` for a floor with no number — an answer that says "回复 #" answers nothing, and the editor
 * handles a missing target perfectly well by opening empty.
 */
private fun PostContent.toFloorReference(): FloorReference? {
    val number = floor?.trimStart('#')?.toIntOrNull() ?: return null
    return FloorReference(
        floor = number,
        author = authorName,
        excerpt = nodes.excerpt(),
        // The absolute timestamp, not the "3小时前" one: the quote outlives the moment it was written.
        postedAt = createdAtTitle,
    )
}

/**
 * The floor's readable text, in full.
 *
 * Deliberately not truncated here: the same string is the chip's label, the preview's quote block
 * *and* the blockquote that goes on the wire, and only the first of those wants to be short. The
 * chip ellipsizes at render time; publishing a quote cut to 40 characters with a `…` would put a
 * mangled quote into a real thread the moment the comment endpoint is wired up.
 */
private fun List<RichNode>.excerpt(): String = mapNotNull { node ->
    when (node) {
        is RichNode.Paragraph -> node.inlines.plainText()
        is RichNode.Heading -> node.inlines.plainText()
        else -> null
    }?.trim()?.takeIf(String::isNotBlank)
}.joinToString("\n")

private fun List<InlineNode>.plainText(): String = joinToString("") { inline ->
    when (inline) {
        is InlineNode.Text -> inline.text
        is InlineNode.Link -> inline.text
        else -> ""
    }
}

/** Tabular figures so `#9` and `#127` sit on the same right edge as the list scrolls. */
@Composable
internal fun FloorLabel(floor: String) {
    Text(
        text = stringResource(Res.string.post_floor_number, floor.removePrefix("#")),
        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private const val THREAD_HEADER_ITEMS = 2

private data class PrependAnchor(val page: Int, val key: Any, val offset: Int)

/** A floor without a comment id still needs a key that survives insertion of earlier pages. */
private fun PostContent.lazyKey(index: Int): Any = commentId ?: floor?.let { "floor:$it" } ?: "unlabelled:$index"

/**
 * The title/opening item and the comments header exist even on a page without an opening post.
 * Counting only a non-null body misplaces every floor jump on an uncached later page by one.
 */
private val PostDetailUiState.headerItemCount: Int
    get() = THREAD_HEADER_ITEMS

/** The floor with [commentId] among what is loaded, the opening post included. */
private fun PostDetailUiState.contentWithId(commentId: Long?): PostContent? =
    commentId?.let { id -> body?.takeIf { it.commentId == id } ?: comments.firstOrNull { it.commentId == id } }

/** The mark in flight on [content], if any — only one floor at a time can have one. */
private fun PostDetailUiState.pendingReactionFor(content: PostContent): ReactionAction? =
    pendingReaction?.takeIf { it.commentId == content.commentId }?.action

/** The last row of the list — the newest floor when the last page is loaded. */
private val PostDetailUiState.lastItemIndex: Int
    get() = (headerItemCount + comments.size - 1).coerceAtLeast(0)

private val PostDetailUiState.showReadingEnd: Boolean
    get() = comments.isNotEmpty() && !hasNextPage && !isLoading && !isScanningAuthor && authorScanError == null && error == null

/**
 * Whether 以下是新回复 belongs above the comment at [index]: the first loaded floor at or past
 * [PostDetailUiState.firstNewFloor]. "At or past" because the floor it names may have been deleted,
 * and the line still belongs above whatever came after it.
 */
private fun PostDetailUiState.startsNewReplies(index: Int): Boolean {
    val first = firstNewFloor ?: return false
    val number = NodeSeekSite.parseFloorNumber(comments[index].floor) ?: return false
    if (number < first) return false
    val previous = comments.getOrNull(index - 1)?.let { NodeSeekSite.parseFloorNumber(it.floor) }
    // The top of a window that starts mid-thread: only where the new floors begin, not wherever a
    // jump happened to land among them.
    if (previous == null) return index == 0 && NodeSeekSite.pageOfFloor(first) == NodeSeekSite.pageOfFloor(number)
    return previous < first
}

/**
 * A wavy rule with 以下是新回复 across it — where this visit's unseen floors begin.
 *
 * Material has no wavy divider; the wave is Expressive's [LinearWavyProgressIndicator], pinned full
 * with no track. [WavyProgressIndicatorDefaults.indicatorAmplitude] flattens the wave at 100%, so
 * the amplitude is held at full, and the speed is zero so a reading list has nothing crawling in it.
 * Swap this for a real divider component if Material ever ships one.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NewRepliesDivider() {
    val color = MaterialTheme.colorScheme.primary
    val wave: @Composable RowScope.() -> Unit = {
        LinearWavyProgressIndicator(
            progress = { 1f },
            modifier = Modifier.weight(1f),
            color = color,
            trackColor = Color.Transparent,
            stopSize = 0.dp,
            amplitude = { 1f },
            waveSpeed = 0.dp,
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ThreadContentInset, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        wave()
        Text(
            text = stringResource(Res.string.post_new_replies_divider),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
        wave()
    }
}

/** A deleted floor lands on the next surviving reply instead of the start of its server page. */
private fun PostDetailUiState.indexAtOrAfterFloor(floor: String): Int? {
    val number = NodeSeekSite.parseFloorNumber(floor) ?: return null
    if (number == 0) return 0.takeIf { firstLoadedPage == 1 && !onlyAuthor }
    val exact = indexOfFloor(floor)
    if (exact != null) return exact
    val next = comments.indexOfFirst { (NodeSeekSite.parseFloorNumber(it.floor) ?: -1) >= number }
    return if (next >= 0) headerItemCount + next else null
}

/** Mirrors [ThreadList]'s item order so a quote reference can scroll to the floor it points at. */
private fun PostDetailUiState.indexOfFloor(floor: String): Int? {
    val position = comments.indexOfFirst { it.floor == floor }
    if (position < 0) return null
    return headerItemCount + position
}

/**
 * The list index where [page] starts, or null when no loaded comment belongs to it yet. `>=` rather
 * than `==` so a page whose comments were all deleted resolves to the next page instead of nowhere.
 *
 * The slice's first page resolves to the first comment rather than to the top of the list: the title
 * and the opening post sit above every page, and a reader who asked for page 12 asked for its floors,
 * not for the post they have already read. Page 1 keeps the whole top, which is where it begins.
 */
private fun PostDetailUiState.firstIndexOfPage(page: Int): Int? {
    if (page <= firstLoadedPage) return if (firstLoadedPage > 1) headerItemCount else 0
    val position = commentPages.indexOfFirst { it >= page }
    if (position < 0) return null
    return headerItemCount + position
}

/**
 * A bar standing in for the posting time, in a box exactly as tall as the line it replaces.
 *
 * The height matters and the bar's own does not: the author's name is centred against this column,
 * so a placeholder even a dp off centres the name a dp high and drops it into place the moment the
 * thread arrives — one visible step, in an element the reader has just watched fly across the
 * screen.
 *
 * The height comes from the style the real line is drawn in, so it follows the reading-size
 * preference along with the text it stands in for. A single-line [MetaText] is exactly its style's
 * `lineHeight` tall.
 */
@Composable
private fun MetaLinePlaceholder() {
    val lineHeight = with(LocalDensity.current) {
        MaterialTheme.typography.labelSmall.lineHeight.toDp()
    }
    Box(Modifier.height(lineHeight), contentAlignment = Alignment.CenterStart) {
        SkeletonBar(0.22f, 10.dp)
    }
}

/**
 * The skeleton for a thread nothing told us anything about — a notification, a deep link, a cold
 * start straight into a restored destination.
 *
 * Grey all the way down, because grey is the only honest thing this screen can draw when it is
 * holding no facts. Nothing is in flight either, so unlike its sibling above this one owes the
 * final layout nothing.
 */
@Composable
private fun UnopenedThreadSkeleton() {
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = ThreadContentInset, vertical = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SkeletonBar(0.92f, 22.dp)
            SkeletonBar(0.55f, 22.dp)
            SkeletonBar(0.18f, 18.dp)
        }
        HorizontalDivider(color = LocalPlazaLayers.current.divider)
        FloorRow {
            FloorHeaderPlaceholder()
            listOf(0.98f, 0.94f, 0.99f, 0.62f).forEach { SkeletonBar(it, 14.dp) }
        }
        CommentSkeletons()
    }
}

/** Loading floors use the same header and full-width text edges as the content they replace. */
@Composable
private fun CommentSkeletons() {
    Column(Modifier.padding(top = Spacing.sm + 40.dp)) {
        repeat(3) {
            FloorRow {
                FloorHeaderPlaceholder()
                SkeletonBar(0.95f, 14.dp)
                SkeletonBar(0.65f, 14.dp)
            }
        }
    }
}

@Composable
private fun FloorHeaderPlaceholder() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(ThreadAvatarSize).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SkeletonBar(0.35f, 12.dp)
            SkeletonBar(0.22f, 10.dp)
        }
    }
}

// -------------------------------------------------------------------------------------------------

private fun previewContent(
    floor: String,
    author: String,
    text: String,
    op: Boolean = false,
    quote: Pair<String, String>? = null,
) = PostContent(
    commentId = floor.hashCode().toLong(),
    floor = floor,
    authorName = author,
    authorUid = 1,
    avatarUrl = null,
    isOriginalPoster = op,
    badges = emptyList(),
    createdAtText = "47分钟前",
    createdAtTitle = null,
    categoryTitle = "日常",
    nodes =
    listOf(
        RichNode.Paragraph(
            buildList {
                quote?.let { (name, target) ->
                    add(InlineNode.QuoteRef(name = name, floor = target, url = "/post-1$target"))
                }
                add(InlineNode.Text(text))
            },
        ),
    ),
)

internal val previewState =
    PostDetailUiState(
        title = "为啥nodequality复制格式非常慢，是电脑问题还是怎么回事",
        body =
        previewContent(
            floor = "#0",
            author = "花田错不错",
            text = "nodequality出来的结果，复制格式，后台等了半个小时都没复制好，因为一直没有显示文件下载。",
            op = true,
        ),
        comments =
        listOf(
            previewContent("#1", "jkjoy", "服务器原因吧"),
            previewContent("#2", "linda", "你自己的问题，自查"),
            previewContent("#3", "zhh123", "你试试前台等"),
            previewContent(
                "#4",
                "花田错不错",
                " 只等了几分钟，等不及就后台了",
                op = true,
                quote = "zhh123" to "#3",
            ),
        ),
    )

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "Post detail")
@Composable
private fun PostDetailPreview() {
    PlazaTheme {
        PostDetailScreen(
            state = previewState,
            postUrl = "https://www.nodeseek.com/post-1-1",
            onBack = {},
            onOpenBrowser = {},
            onVerify = {},
            onImageClick = {},
            onRetry = {},
            onLoadMore = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "Post detail · dark")
@Composable
private fun PostDetailDarkPreview() {
    PlazaTheme(darkTheme = true) {
        PostDetailScreen(
            state = previewState,
            postUrl = "https://www.nodeseek.com/post-1-1",
            onBack = {},
            onOpenBrowser = {},
            onVerify = {},
            onImageClick = {},
            onRetry = {},
            onLoadMore = {},
        )
    }
}

/**
 * Every block image in the thread, in reading order and without duplicates.
 *
 * Reading order is what makes the viewer's "2 / 4" mean anything: it has to match the order the images
 * appear while scrolling, so a tap on the third screenshot opens page three. Inline stickers are left
 * out — nobody opens a full-screen viewer for an emoji.
 *
 * Collapsed floors contribute nothing, for the same reason: paging past a hidden floor's screenshot
 * would show the reader exactly what the block spared them.
 */
internal fun PostDetailUiState.imageUrls(): List<String> =
    (listOfNotNull(body) + comments)
        .filter { showBlockedContent || !it.isBlocked }
        .flatMap { content -> content.nodes.imageUrls() }
        .distinct()

private fun List<RichNode>.imageUrls(): List<String> =
    flatMap { node ->
        when (node) {
            is RichNode.BlockImage -> listOf(node.url)

            is RichNode.Quote -> node.children.imageUrls()

            is RichNode.ListBlock -> node.items.flatMap { it.imageUrls() }

            // Cell thumbnails open the same viewer, so they page with everything else. Left out,
            // a tapped screenshot was "not in the list" and the viewer fell back to page one —
            // every image in a layout table opened as the post's first badge.
            is RichNode.Table ->
                node.content.flatten().flatMap { cell ->
                    cell.filterIsInstance<InlineNode.Image>().map { it.url }
                }

            is RichNode.Tabs -> node.tabs.flatMap { it.children.imageUrls() }

            // A 折叠 is where a 测评 files its 三网测速 screenshots — post-910421 is three of them.
            // Left out, every image inside one was "not in the list" and opened as page one, the
            // same way a table cell's did before the branch above existed. Counted whether or not
            // the fold is open: what the reader tapped has to be findable, and a viewer that paged
            // past the images of a fold it was opened from would be counting a different post.
            is RichNode.Fold -> node.children.imageUrls()

            else -> emptyList()
        }
    }

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "Post detail · skeleton")
@Composable
private fun PostDetailSkeletonPreview() {
    PlazaTheme { UnopenedThreadSkeleton() }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360, heightDp = 400, name = "Post detail · loading spinner")
@Composable
private fun LoadingStatePreview() {
    PlazaTheme { LoadingState() }
}
