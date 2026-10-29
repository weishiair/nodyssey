package io.github.nodyssey

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.ui.common.rememberShareImage
import io.github.nodyssey.ui.common.sharedThreadSurface
import io.github.nodyssey.ui.composer.PostComposerRoute
import io.github.nodyssey.ui.composer.PostComposerViewModel
import io.github.nodyssey.ui.composer.ReplyComposerViewModel
import io.github.nodyssey.ui.login.WebViewGoal
import io.github.nodyssey.ui.postdetail.PostDetailRoute
import io.github.nodyssey.ui.postdetail.PostDetailViewModel
import io.github.nodyssey.ui.stardust.StardustReceiveCard
import io.github.nodyssey.ui.stardust.StardustReceiveViewModel
import io.github.nodyssey.ui.viewer.ImageViewerScreen
import io.github.nodyssey.ui.viewer.ImageViewerViewModel
import io.github.nodyssey.ui.viewer.rememberImageGallerySaver
import io.github.nodyssey.ui.vote.VoteCard
import io.github.nodyssey.ui.vote.VoteViewModel

/**
 * The reading-and-writing core: a thread, the composer, and the image viewer.
 *
 * One of the region files `Navigation.kt`'s `destinationProvider` assembles; see [StackEntryScope]
 * for the capture rules they all share.
 */
internal fun EntryProviderScope<NavKey>.threadEntries(nav: StackEntryScope) = with(nav) {
    entry<ImageViewerKey>(
        /*
         * The picture opens in place. Its background switches immediately, while zooming and
         * paging stay inside the viewer. Predictive back uses the display's gesture transition.
         */
        metadata =
        stationaryPageEntrance() + NavDisplay.popTransitionSpec { SNAP_CONTENT_TRANSFORM },
    ) { key ->
        val saver = rememberImageGallerySaver(container.dispatchers)
        val viewModel: ImageViewerViewModel =
            viewModel(factory = ImageViewerViewModel.factory(saver))
        val saveOutcome by viewModel.saveOutcome.collectAsStateWithLifecycle()
        ImageViewerScreen(
            urls = key.urls,
            initialIndex = key.index,
            onClose = { backStack.removeLastOrNull() },
            // The browser, even for an image hosted on nodeseek.com: this one is the user
            // reaching for downloading and sharing, which is the browser's job and not
            // something the session's web view does better.
            onOpenBrowser = openExternalUrl,
            saveOutcome = saveOutcome,
            onSave = viewModel::save,
        )
    }

    entry<PostDetailKey>(
        /*
         * Keep both page backgrounds stationary when a row supplies the shared thread header.
         * Only the shared elements travel during entrance. Return uses the display's slide so
         * the outgoing page moves away while shared elements finish, rather than covering the list.
         * Threads without a preview retain the display's directional entrance too.
         */
        metadata = { key ->
            if (key.preview == null) emptyMap() else stationaryPageEntrance()
        },
    ) { key ->
        // Keyed so navigating to a different post builds a fresh ViewModel.
        val viewModel: PostDetailViewModel =
            viewModel(
                key = "post-${key.postId}",
                factory =
                PostDetailViewModel.factory(
                    container,
                    key.postId,
                    initialFloor = key.floor,
                    initialPage = key.page,
                    preview = key.preview,
                ),
            )
        // Its own ViewModel, keyed the same way: an unsent reply belongs to one thread
        // and has to outlive the sheet that shows it.
        val replyViewModel: ReplyComposerViewModel =
            viewModel(
                key = "reply-${key.postId}",
                factory = ReplyComposerViewModel.factory(container, key.postId),
            )
        PostDetailRoute(
            modifier = Modifier.sharedThreadSurface(key.preview?.origin),
            viewModel = viewModel,
            replyViewModel = replyViewModel,
            // By identity: the stack hands the entry this very key, and an equal key further down
            // is a different page.
            replyCovered = backStack.lastOrNull() !== key,
            showBackButton = !(isListDetailExpanded() && backStack.showsListPane()),
            onBack = { backStack.removeLastOrNull() },
            onOpenBrowser = openWebUrl,
            // A link to this same thread stays on this screen; see [PostDetailViewModel.openLinkInPlace].
            onLinkClick = { url -> if (!viewModel.openLinkInPlace(url)) openContentUrl(url) },
            onAuthorClick = openSpace,
            onSignIn = { backStack.add(SignInKey) },
            onVerify = { backStack.add(WebKey(it, siteTitle, WebViewGoal.CHALLENGE)) },
            onImageClick = { urls, url -> backStack.add(imageViewerKeyFor(urls, url)) },
            onEdit = { target -> backStack.add(PostComposerKey(target)) },
            // Supplied here because this is the only layer that can reach the container.
            // Keyed by vote id and not merely by post: a thread may embed more than one, and
            // without the key they would share a single ViewModel and each other's state.
            voteContent = { voteId ->
                VoteCard(
                    viewModel =
                    viewModel(
                        key = "vote-$voteId",
                        factory = VoteViewModel.factory(container, voteId),
                    ),
                    onSignIn = { backStack.add(SignInKey) },
                    onUserClick = openSpace,
                )
            },
            // Keyed by payee *and* Ref ID, for the same reason a vote is keyed by its id: one
            // post may carry several codes, and a shared ViewModel would show one code's
            // tally under another's amount.
            stardustContent = { node ->
                StardustReceiveCard(
                    node = node,
                    viewModel =
                    viewModel(
                        key = "stardust-${node.memberId}-${node.refId}",
                        factory = StardustReceiveViewModel.factory(container, node),
                    ),
                    onSignIn = { backStack.add(SignInKey) },
                )
            },
            shareImage = rememberShareImage(container.dispatchers),
        )
    }

    entry<PostComposerKey> { key ->
        val viewModel: PostComposerViewModel =
            viewModel(
                // Keyed by what is being written, or two edits opened in one session would
                // share the first one's ViewModel — and therefore the first one's text.
                key = "composer-${key.edit?.commentId ?: "new"}",
                factory = PostComposerViewModel.factory(container, key.edit),
            )
        if (key.edit == null) {
            // Keyed on the pending share so a second one arriving while this editor is open lands
            // in it too; `take` is what keeps a recomposition from adding the same one twice.
            val share = pendingComposerShare.pending
            LaunchedEffect(share) { pendingComposerShare.take()?.let(viewModel::receiveShare) }
        }
        // Whatever this editor is about: the thread when editing, the new-post page otherwise.
        val webUrl =
            key.edit
                ?.let { NodeSeekSite.BASE_URL + NodeSeekSite.postPath(it.postId, it.page) }
                ?: (NodeSeekSite.BASE_URL + NodeSeekSite.NEW_DISCUSSION_PATH)
        PostComposerRoute(
            viewModel = viewModel,
            onClose = { backStack.removeLastOrNull() },
            onSignIn = {
                backStack.add(SignInKey)
            },
            onVerify = { url ->
                backStack.add(WebKey(url, siteTitle, WebViewGoal.CHALLENGE))
            },
            onOpenBrowser = {
                backStack.add(WebKey(webUrl, siteTitle, WebViewGoal.MANAGE))
            },
            onPublished = { postId ->
                backStack.removeLastOrNull()
                // An edit returns to the thread it came from, which is already underneath —
                // pushing it again would stack a second copy of the screen being updated.
                if (key.edit == null) postId?.let { backStack.add(PostDetailKey(it)) }
            },
        )
    }
}

/**
 * Replace the scene background on entrance without suppressing child shared-element animations.
 * Ordinary and predictive back retain the display's directional slide unless a caller overrides it.
 */
internal fun stationaryPageEntrance() = NavDisplay.transitionSpec { SNAP_CONTENT_TRANSFORM }

private fun imageViewerKeyFor(urls: List<String>, url: String): ImageViewerKey =
    ImageViewerKey(urls = urls, index = urls.indexOf(url).coerceAtLeast(0))
