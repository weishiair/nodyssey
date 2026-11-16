package io.github.nodyssey.ui.composer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.nodyssey.data.composer.ImageAttachment
import io.github.nodyssey.data.composer.PickedImage
import io.github.nodyssey.ui.common.NativePopGuardEffect
import io.github.nodyssey.ui.common.describedAsLoading
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.action_close
import io.github.nodyssey.ui.resources.action_preview
import io.github.nodyssey.ui.resources.action_publish
import io.github.nodyssey.ui.resources.action_undo
import io.github.nodyssey.ui.resources.composer_image_default_name
import io.github.nodyssey.ui.resources.composer_publishing
import io.github.nodyssey.ui.resources.post_quote_prefix
import io.github.nodyssey.ui.resources.post_quote_reply
import io.github.nodyssey.ui.resources.post_reply_clear_draft
import io.github.nodyssey.ui.resources.post_reply_draft_cleared
import io.github.nodyssey.ui.resources.post_reply_draft_saved
import io.github.nodyssey.ui.resources.post_reply_editor_hint
import io.github.nodyssey.ui.resources.post_reply_editor_title
import io.github.nodyssey.ui.resources.post_reply_editor_title_to_floor
import io.github.nodyssey.ui.resources.post_reply_preview_title
import io.github.nodyssey.ui.resources.post_reply_preview_title_floor
import io.github.nodyssey.ui.resources.post_reply_publish_unavailable
import io.github.nodyssey.ui.resources.post_reply_quote_remove
import io.github.nodyssey.ui.stardust.StardustReceiveComposeDialog
import io.github.nodyssey.ui.vote.VoteComposeDialog
import io.github.plaza.core.TimeFormat
import io.github.plaza.designsys.component.AdaptiveGlassButton
import io.github.plaza.designsys.component.AdaptiveGlassTopBar
import io.github.plaza.designsys.component.EditorTextField
import io.github.plaza.designsys.component.GlassAction
import io.github.plaza.designsys.component.InlineBanner
import io.github.plaza.designsys.component.PlazaBackHandler
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.PlazaSheetDefaults
import io.github.plaza.designsys.component.PlazaSpinner
import io.github.plaza.designsys.component.QuotePreview
import io.github.plaza.designsys.component.ReadableTopBar
import io.github.plaza.designsys.component.TonalTag
import io.github.plaza.designsys.editor.ComposerEditorBar
import io.github.plaza.designsys.editor.EditorAction
import io.github.plaza.designsys.editor.MarkdownEditorState
import io.github.plaza.designsys.editor.ToolbarCustomizeSheet
import io.github.plaza.designsys.editor.rememberMarkdownEditorState
import io.github.plaza.designsys.theme.CommentBody
import io.github.plaza.designsys.theme.LocalPlazaDarkTheme
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.Sizes
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.backdropGlass
import io.github.plaza.designsys.theme.glassRimBorderStroke
import io.github.plaza.designsys.theme.glassSheetBrush
import io.github.plaza.designsys.theme.nativeGlass
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.stringResource

/**
 * The reply editor: a modal sheet (2c, 6d before it) that expands to a full-screen preview (C4).
 *
 * Hosted as a sibling of the thread rather than inside it, because both halves cover the screen —
 * the sheet in its own window, the preview over everything — and neither belongs in the thread's
 * own layout. The two are mutually exclusive: the preview replaces the sheet rather than stacking
 * on it, which is what the shared-axis transition in the board describes.
 */
@Composable
fun ReplyComposerHost(
    state: ReplyComposerUiState,
    /**
     * Another page has been pushed over the thread — 表情管理 or 图床 from the emoji panel, sign-in or
     * the challenge from the error strip. The reply stays open in [state]; only its sheet is
     * withheld until the thread is on top again, and then it slides back in.
     *
     * The sheet is a window of its own, so it does not leave with the page under it. Left drawn, a
     * back gesture on the page on top composes the thread for the predictive-back preview, the
     * sheet comes back with it and takes the gesture, the preview is dropped — and back never
     * leaves that page. Closing the sheet on the way out, as [rememberLeavingStickerNavigation]
     * does for the sheets inside this one, would throw away the reply the writer means to come
     * back to.
     */
    covered: Boolean,
    onDismiss: () -> Unit,
    bodyState: TextFieldState,
    onClearReplyTo: () -> Unit,
    onPreviewChange: (Boolean) -> Unit,
    onPickImages: (List<PickedImage>) -> Unit,
    onRemoveAttachment: (ImageAttachment) -> Unit,
    onRetryAttachment: (ImageAttachment) -> Unit,
    onRetryFailedUploads: () -> Unit,
    onPublish: () -> Unit,
    onClearError: () -> Unit,
    /**
     * Opens the sign-in page; a reply refused for being signed out is not a retry.
     *
     * Nullable rather than defaulted to `{}`, and the same for [onVerify]: a button wired to nothing
     * is the failure this whole seam exists to remove. A host that cannot reach either page passes
     * null and the strip shows no button at all.
     */
    onSignIn: (() -> Unit)?,
    /** Clears a Cloudflare challenge on the thread being replied to. */
    onVerify: ((String) -> Unit)?,
    onToolbarChange: (List<EditorAction>) -> Unit,
    onToolbarReset: () -> Unit,
    onCreateVote: (String, Boolean, Boolean, List<String>, () -> Unit) -> Unit,
    onDismissVoteCreation: () -> Unit,
    /** Null while the app does not know the account; the 收款码 dialog then explains itself. */
    payeeUid: () -> Long?,
    onInsertReceiveCode: (Int, Long, String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onDiscardDraft: () -> Unit = {},
    onUndoDiscard: () -> Unit = {},
) {
    // Above the early return on purpose: the host stays composed while the sheet is closed, so
    // this is the one place the recents survive both the emoji panel and the sheet being dismissed.
    val editorState = rememberMarkdownEditorState()
    // The panel is part of the sheet even though its state is not, so it goes down with it — the
    // recents are what outlive the dismissal, not a half-open drawer.
    LaunchedEffect(state.visible) { if (!state.visible) editorState.closePanels() }
    if (!state.visible || covered) return
    NativePopGuardEffect(blocked = state.isPublishing && !state.previewing)
    // Hosted here rather than inside the editor sheet: it is a sheet too, and a sheet opened from
    // inside another sheet's content stacks two dialog windows for no reason. As siblings the wrench
    // panel simply covers the editor, which is what it should look like anyway.
    var customizing by rememberSaveable { mutableStateOf(false) }
    // Siblings of the sheet for the same reason the wrench panel is one: a dialog opened from inside
    // a ModalBottomSheet's content stacks two windows, and the sheet's own scrim ends up over it.
    var composingVote by rememberSaveable { mutableStateOf(false) }
    var composingReceiveCode by rememberSaveable { mutableStateOf(false) }
    // A sheet as well, so a sibling for the wrench panel's reason.
    var pickingHosted by rememberSaveable { mutableStateOf(false) }
    val launchPicker =
        rememberImagePicker(
            maxItems = MAX_IMAGES_PER_PICK,
            fallbackName = stringResource(Res.string.composer_image_default_name),
            onPicked = onPickImages,
        )
    val motionScheme = MaterialTheme.motionScheme

    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = state.previewing,
            // Shared axis Y: the preview rises out of the sheet and sinks back into it, so the two
            // read as one surface changing state rather than two screens swapping.
            enter = fadeIn(motionScheme.defaultEffectsSpec()) +
                slideInVertically(motionScheme.defaultSpatialSpec()) { it / SLIDE_FRACTION },
            exit = fadeOut(motionScheme.fastEffectsSpec()) +
                slideOutVertically(motionScheme.fastSpatialSpec()) { it / SLIDE_FRACTION },
        ) {
            ReplyPreviewScreen(
                state = state,
                onBack = { onPreviewChange(false) },
                onPublish = onPublish,
                onClearError = onClearError,
                onSignIn = onSignIn,
                onVerify = onVerify,
            )
        }
    }

    if (!state.previewing) {
        ReplyEditorSheet(
            state = state,
            onDismiss = onDismiss,
            bodyState = bodyState,
            onClearReplyTo = onClearReplyTo,
            onPreview = { onPreviewChange(true) },
            onPickImages = launchPicker,
            onPickHostedImages = { pickingHosted = true },
            onRemoveAttachment = onRemoveAttachment,
            onRetryAttachment = onRetryAttachment,
            onRetryFailedUploads = onRetryFailedUploads,
            onPublish = onPublish,
            onClearError = onClearError,
            onSignIn = onSignIn,
            onVerify = onVerify,
            editorState = editorState,
            onCustomize = { customizing = true },
            onInsertVote = { composingVote = true },
            onInsertStardust = { composingReceiveCode = true },
            onDiscardDraft = onDiscardDraft,
            onUndoDiscard = onUndoDiscard,
        )
    }

    if (customizing) {
        ToolbarCustomizeSheet(
            layout = state.toolbar,
            onChange = onToolbarChange,
            onReset = onToolbarReset,
            onDismiss = { customizing = false },
        )
    }

    if (pickingHosted) {
        HostedImagePickerSheet(bodyState = bodyState, onDismiss = { pickingHosted = false })
    }

    if (composingVote) {
        VoteComposeDialog(
            state = state.voteCreation,
            onCreate = { title, multiple, isPublic, items ->
                onCreateVote(title, multiple, isPublic, items) { composingVote = false }
            },
            onDismiss = {
                composingVote = false
                onDismissVoteCreation()
            },
        )
    }

    if (composingReceiveCode) {
        StardustReceiveComposeDialog(
            needsSignIn = payeeUid() == null,
            onInsert = { amount, refId, description, onetime ->
                composingReceiveCode = false
                onInsertReceiveCode(amount, refId, description, onetime)
            },
            onDismiss = { composingReceiveCode = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReplyEditorSheet(
    state: ReplyComposerUiState,
    onDismiss: () -> Unit,
    bodyState: TextFieldState,
    onClearReplyTo: () -> Unit,
    onPreview: () -> Unit,
    onPickImages: () -> Unit,
    onPickHostedImages: () -> Unit,
    onRemoveAttachment: (ImageAttachment) -> Unit,
    onRetryAttachment: (ImageAttachment) -> Unit,
    onRetryFailedUploads: () -> Unit,
    onPublish: () -> Unit,
    onClearError: () -> Unit,
    onSignIn: (() -> Unit)?,
    onVerify: ((String) -> Unit)?,
    editorState: MarkdownEditorState,
    onCustomize: () -> Unit,
    onInsertVote: () -> Unit,
    onInsertStardust: () -> Unit,
    onDiscardDraft: () -> Unit,
    onUndoDiscard: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val stickerPictures = rememberStickerPictures()
    // The link key's long press opens a popup menu, and a popup takes the window's focus with it.
    // Asking for the field again on the way back is what keeps the keyboard and the caret where the
    // writer left them — the post editor does the same.
    val bodyFocus = remember { FocusRequester() }

    // On a tablet the editor is a card in the middle of the window, the way Mail's reply is, rather
    // than a phone's sheet rising from the bottom edge of one column under a scrim that greys out the
    // whole window. Height counts as well as width: a phone on its side is wide, but a centred card
    // over its keyboard would leave the editor a sliver.
    val windowSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val asCard =
        with(density) {
            windowSize.width.toDp() >= REPLY_CARD_MIN_WINDOW && windowSize.height.toDp() >= REPLY_CARD_MIN_WINDOW
        }
    val maxEditorHeight =
        with(density) {
            (windowSize.height.toDp() * 0.34f).coerceIn(120.dp, MAX_EDITOR_HEIGHT)
        }
    val dismiss = { if (!state.isPublishing) onDismiss() }
    val nativeGlass = LocalPlazaLayers.current.nativeGlass
    val editor: @Composable (Modifier) -> Unit = { editorModifier ->
        Column(modifier = editorModifier.fillMaxWidth()) {
            // The post editor's top bar in miniature: ✕ · where the reply goes · 预览 · 发布. 发布
            // moved up here from the end of the formatting strip, which is what let the strip go —
            // it was the reason that strip had to squeeze its keys to 42dp.
            Row(
                modifier = Modifier.fillMaxWidth().height(Sizes.minTouchTarget)
                    .padding(start = Spacing.xs, end = if (nativeGlass) Spacing.lg else Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                IconButton(onClick = onDismiss, enabled = !state.isPublishing) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.action_close),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    // The author is in the target chip right under this, so the header names the
                    // floor only — and falls back to plain 回复 the moment that chip is dismissed.
                    text = state.replyTo?.let {
                        stringResource(Res.string.post_reply_editor_title_to_floor, it.floor)
                    } ?: stringResource(Res.string.post_reply_editor_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (bodyState.text.isNotBlank() || state.replyTo != null || state.savedAtMillis != null || state.attachments.isNotEmpty()) {
                    IconButton(
                        onClick = onDiscardDraft,
                        enabled = !state.isPublishing,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(Res.string.post_reply_clear_draft),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                IconButton(
                    onClick = {
                        // The preview covers the sheet, so the IME has to be gone before it rises —
                        // otherwise it comes back to a keyboard over a screen that has no field.
                        keyboard?.hide()
                        onPreview()
                    },
                    enabled = !state.isPublishing,
                ) {
                    Icon(
                        imageVector = PlazaIcons.Visibility,
                        contentDescription = stringResource(Res.string.action_preview),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                PublishReplyButton(
                    isPublishing = state.isPublishing,
                    enabled = state.canPublish,
                    onClick = onPublish,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                // The dismissible 回复 target (6d), recessed into the sheet (2c): it is a reference to
                // something outside the reply, not part of it. Only 回复 gets one, because only 回复 is a
                // property of the comment as a whole; a 引用 is text in the body, visible and editable
                // there, and a chip would imply it could be dismissed the same way.
                state.replyTo?.let { replyTo ->
                    QuotePreview(
                        title = stringResource(Res.string.post_quote_reply, replyTo.author, "#${replyTo.floor}"),
                        excerpt = replyTo.excerpt,
                        leading = {
                            Icon(
                                PlazaIcons.FormatQuote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        inset = true,
                        onRemove = onClearReplyTo,
                        removeLabel = stringResource(Res.string.post_reply_quote_remove),
                        modifier = Modifier.padding(horizontal = Spacing.md),
                    )
                }
                // The default container fills the width it was given, which is what this field needs:
                // `readableWidth` centres what it wraps, so a decoration that measured to its content
                // would centre a half-typed reply.
                EditorTextField(
                    state = bodyState,
                    hint = stringResource(Res.string.post_reply_editor_hint),
                    textStyle = CommentBody.copy(color = MaterialTheme.colorScheme.onSurface),
                    hintStyle = CommentBody,
                    inlinePictures = stickerPictures,
                    modifier = Modifier
                        .readableWidth()
                        .heightIn(min = MIN_EDITOR_HEIGHT, max = maxEditorHeight)
                        .focusRequester(bodyFocus)
                        .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.md, bottom = Spacing.sm),
                )
                AttachmentTray(
                    attachments = state.attachments,
                    onRemove = onRemoveAttachment,
                    onRetry = onRetryAttachment,
                )
                // Under the text it describes rather than in the header (2c), where it used to crowd
                // the title out of the one row that also carries 预览 and 发布.
                state.savedAtMillis?.let {
                    Text(
                        text = stringResource(Res.string.post_reply_draft_saved, formatTime(it)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.sm),
                    )
                }
                if (state.canUndoClear) {
                    InlineBanner(
                        text = stringResource(Res.string.post_reply_draft_cleared),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                        action = {
                            TextButton(onClick = onUndoDiscard, contentPadding = PaddingValues(horizontal = Spacing.md)) {
                                Text(stringResource(Res.string.action_undo), color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        },
                    )
                }
                ComposerErrorBanner(
                    error = state.publishError,
                    detail = state.publishErrorDetail,
                    failedUploads = state.failedUploadCount,
                    uploadFailure = state.uploadFailure,
                    uploadErrorDetail = state.uploadErrorDetail,
                    onRetryPublish = onPublish,
                    onRetryUploads = onRetryFailedUploads,
                    onDismiss = onClearError,
                    onSignIn = onSignIn,
                    onVerify = onVerify,
                    fallbackErrorText = stringResource(Res.string.post_reply_publish_unavailable),
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                )
            }
            ComposerEditorBar(
                actions = state.toolbar.enabled,
                bodyState = bodyState,
                editorState = editorState,
                // The strip already has an 8dp gutter; align it with the reply's 16dp text inset.
                barModifier = if (nativeGlass) Modifier.padding(start = Spacing.sm, end = Spacing.sm, top = Spacing.sm) else Modifier,
                onPickImages = onPickImages,
                onPickHostedImages = onPickHostedImages,
                onFormatted = { bodyFocus.requestFocus() },
                onCustomize = onCustomize,
                appMenu = {
                    ComposerAppMenu(onInsertVote = onInsertVote, onInsertStardust = onInsertStardust)
                },
                emojiPanel = { panel ->
                    NodeSeekEmojiPanel(
                        onInsert = panel.onInsert,
                        onBackspace = panel.onBackspace,
                        recent = panel.recent,
                        onRecentChange = panel.onRecentChange,
                    )
                },
                inlinePictures = stickerPictures,
            )
        }
    }
    val layers = LocalPlazaLayers.current
    val darkTheme = LocalPlazaDarkTheme.current
    if (asCard) {
        Dialog(
            onDismissRequest = dismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = !state.isPublishing,
                dismissOnClickOutside = !state.isPublishing,
            ),
        ) {
            // The keyboard pushes the card up rather than covering its bottom, where 发布's strip is.
            Box(
                modifier = Modifier.imePadding().padding(Spacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = layers.card,
                    border = if (layers.backdropGlass) layers.glassRimBorderStroke(darkTheme) else null,
                    modifier = Modifier.widthIn(max = REPLY_CARD_MAX_WIDTH).fillMaxWidth(),
                ) {
                    editor(
                        Modifier
                            .then(
                                if (layers.backdropGlass) {
                                    Modifier.background(layers.glassSheetBrush(darkTheme))
                                } else {
                                    Modifier
                                },
                            )
                            .padding(top = Spacing.xs),
                    )
                }
            }
        }
    } else {
        val currentPublishing by rememberUpdatedState(state.isPublishing)
        ModalBottomSheet(
            // Guarded like the close button and the BackHandler: while a publish is in flight, a
            // swipe, back press, or scrim tap must not hide the sheet before publish settles.
            onDismissRequest = dismiss,
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
                confirmValueChange = { target -> !currentPublishing || target != SheetValue.Hidden },
            ),
            sheetGesturesEnabled = !state.isPublishing,
            properties = ModalBottomSheetProperties(
                shouldDismissOnBackPress = !state.isPublishing,
                shouldDismissOnClickOutside = !state.isPublishing,
            ),
            // A sheet of paper, like the post editor's page (2c): the editor is one card lifted over the
            // thread, and only its bar and the emoji panel under it recede.
            containerColor = layers.card,
            shape = PlazaSheetDefaults.shape,
            dragHandle = { PlazaSheetDefaults.DragHandle() },
        ) {
            // Material clips the whole sheet, including its handle, to one outer shape. Painting
            // another full-width background below the handle creates a second, square-edged panel.
            editor(Modifier.imePadding())
        }
    }
}

/** C4: the sheet's content, full screen, rendered exactly the way the thread will render it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReplyPreviewScreen(
    state: ReplyComposerUiState,
    onBack: () -> Unit,
    onPublish: () -> Unit,
    onClearError: () -> Unit,
    onSignIn: (() -> Unit)?,
    onVerify: ((String) -> Unit)?,
) {
    val density = LocalDensity.current
    val edgeWidthPx = with(density) { PREVIEW_SWIPE_EDGE_WIDTH.toPx() }
    val triggerDistancePx = with(density) { PREVIEW_SWIPE_TRIGGER.toPx() }
    val surfaceColor = MaterialTheme.colorScheme.surface
    val previewTitle = state.replyTo?.let {
        stringResource(Res.string.post_reply_preview_title_floor, it.floor)
    } ?: stringResource(Res.string.post_reply_preview_title)
    val publishText = stringResource(if (state.isPublishing) Res.string.composer_publishing else Res.string.action_publish)
    PlazaBackHandler(enabled = !state.isPublishing, onBack = onBack)
    NativePopGuardEffect(
        blocked = true,
        onBlockedAttempt = {
            if (!state.isPublishing) onBack()
        },
    )
    Surface(
        color = surfaceColor,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(state.isPublishing) {
                if (state.isPublishing) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (down.position.x > edgeWidthPx) return@awaitEachGesture
                    var totalDragX = 0f
                    val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                        if (over > 0f) {
                            totalDragX += over
                            change.consume()
                        }
                    } ?: return@awaitEachGesture
                    if (totalDragX >= triggerDistancePx) {
                        onBack()
                        return@awaitEachGesture
                    }
                    var triggered = false
                    horizontalDrag(drag.id) { change ->
                        totalDragX += change.positionChange().x
                        if (totalDragX >= triggerDistancePx && !triggered) {
                            triggered = true
                            change.consume()
                            onBack()
                        }
                    }
                }
            },
    ) {
        Column {
            ReadableTopBar { barInsets ->
                AdaptiveGlassTopBar(
                    title = previewTitle,
                    titleAction = null,
                    back = GlassAction(
                        label = stringResource(Res.string.action_back),
                        onClick = { if (!state.isPublishing) onBack() },
                        symbol = "chevron.backward",
                        enabled = !state.isPublishing,
                    ),
                    actions = listOf(
                        GlassAction(
                            label = publishText,
                            title = publishText,
                            onClick = onPublish,
                            enabled = state.canPublish,
                            prominent = true,
                            loading = state.isPublishing,
                        ),
                    ),
                    containerColor = surfaceColor,
                    modifier = Modifier.fillMaxWidth().background(surfaceColor).windowInsetsPadding(barInsets).height(PREVIEW_TOP_BAR_HEIGHT),
                ) {
                    TopAppBar(
                        windowInsets = barInsets,
                        title = {
                            Text(
                                text = previewTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontSize = 16.sp,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack, enabled = !state.isPublishing) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(Res.string.action_back),
                                )
                            }
                        },
                        actions = {
                            PublishReplyButton(
                                isPublishing = state.isPublishing,
                                enabled = state.canPublish,
                                onClick = onPublish,
                                modifier = Modifier.padding(end = Spacing.sm),
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = surfaceColor),
                    )
                }
            }
            ComposerErrorBanner(
                error = state.publishError,
                detail = state.publishErrorDetail,
                failedUploads = 0,
                uploadFailure = null,
                uploadErrorDetail = null,
                onRetryPublish = onPublish,
                onRetryUploads = {},
                onDismiss = onClearError,
                onSignIn = onSignIn,
                onVerify = onVerify,
                fallbackErrorText = stringResource(Res.string.post_reply_publish_unavailable),
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .readableWidth()
                    .padding(horizontal = Spacing.xl, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm + 2.dp),
            ) {
                // Only the 回复 reference: any 引用 is part of the body below, and the Markdown
                // preview already renders it as the blockquote it will become.
                state.replyTo?.let { replyTo ->
                    // How the 回复 reads once published: the addressed floor, ahead of the body.
                    TonalTag(
                        text = "@${replyTo.author} ${stringResource(Res.string.post_quote_prefix, replyTo.floor)}",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                MarkdownPreviewBody(markdown = state.body)
            }
        }
    }
}

@Composable
private fun PublishReplyButton(
    isPublishing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layers = LocalPlazaLayers.current
    val darkTheme = LocalPlazaDarkTheme.current
    val scheme = MaterialTheme.colorScheme
    // Material's extra-small size step with its square shape: the Lean round's 32dp button, the
    // header's one filled thing without being the tallest.
    val label = stringResource(if (isPublishing) Res.string.composer_publishing else Res.string.action_publish)
    AdaptiveGlassButton(
        action = GlassAction(
            label = label,
            title = label,
            symbol = "paperplane",
            onClick = onClick,
            enabled = enabled,
            loading = isPublishing,
            prominent = true,
            compact = true,
        ),
        modifier = modifier.heightIn(min = 44.dp),
    ) {
        Button(
            onClick = onClick,
            enabled = enabled && !isPublishing,
            shape = ButtonDefaults.squareShape,
            border = if (layers.backdropGlass) layers.glassRimBorderStroke(darkTheme) else null,
            colors =
            if (layers.backdropGlass) {
                ButtonDefaults.buttonColors(
                    containerColor = scheme.primary.copy(alpha = 0.88f),
                    contentColor = scheme.onPrimary,
                    disabledContainerColor = layers.raised.copy(alpha = if (darkTheme) 0.56f else 0.72f),
                    disabledContentColor = scheme.onSurfaceVariant,
                )
            } else {
                ButtonDefaults.buttonColors()
            },
            contentPadding = ButtonDefaults.ExtraSmallContentPadding,
            modifier = modifier.heightIn(min = ButtonDefaults.ExtraSmallContainerHeight),
        ) {
            if (isPublishing) {
                PlazaSpinner(
                    modifier = Modifier.describedAsLoading(),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                    size = 14.dp,
                )
            } else {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Text(
                text = stringResource(if (isPublishing) Res.string.composer_publishing else Res.string.action_publish),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
    }
}

private fun formatTime(timestamp: Long): String = TimeFormat.clock(timestamp)

private val MIN_EDITOR_HEIGHT = 80.dp
private val MAX_EDITOR_HEIGHT = 260.dp
private val PREVIEW_TOP_BAR_HEIGHT = 48.dp
private val PREVIEW_SWIPE_EDGE_WIDTH = 28.dp
private val PREVIEW_SWIPE_TRIGGER = 48.dp
private const val SLIDE_FRACTION = 6

/** From here on, in both directions, the reply editor is a centred card rather than a bottom sheet. */
private val REPLY_CARD_MIN_WINDOW = 600.dp

/** The card's width at most: the readable column, so a line in the editor reads like one in a post. */
private val REPLY_CARD_MAX_WIDTH = 640.dp
