package io.github.nodyssey.ui.common

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionDefaults
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import io.github.nodyssey.predictiveBackAnimationSpec

/**
 * The scope a thread's own things travel in on their way from a list row to the thread it opens, or
 * null wherever they must not travel.
 *
 * Null in two situations, and both matter:
 *
 * - Outside `NavDisplay` entirely — a `@Preview`, a screen test. Every modifier below reads this
 *   before it reaches for `LocalNavAnimatedContentScope`, which throws rather than defaulting when
 *   nothing has provided it, so a screen rendered on its own draws plain content and no scope is
 *   touched.
 * - On a window wide enough for two panes. There the list is not replaced by the thread, it sits
 *   beside it, so a row and the header for the same post are on screen *at the same time* — two
 *   live claims on one shared-element key, which is a state the shared-transition machinery has no
 *   answer for. Nothing travels in that layout because nothing goes anywhere: the row stays exactly
 *   where it was.
 */
val LocalThreadTransition = compositionLocalOf<SharedTransitionScope?> { null }

private val LocalThreadSharedContentConfig =
    compositionLocalOf<SharedTransitionScope.SharedContentConfig> { SharedTransitionDefaults.SharedContentConfig }

internal val LocalThreadMatches = compositionLocalOf<ThreadTransitionMatches?> { null }
internal val LocalThreadSharedReturnEnabled = compositionLocalOf { true }
internal val LocalThreadBackGestureInProgress = compositionLocalOf { false }

/** NavDisplay has no shared-key lookup; defer this lookup until its slide offset is measured. */
internal class ThreadTransitionMatches {
    private val titles = mutableStateListOf<SharedTransitionScope.SharedContentState>()

    fun add(state: SharedTransitionScope.SharedContentState) {
        titles.add(state)
    }

    fun remove(state: SharedTransitionScope.SharedContentState) {
        titles.remove(state)
    }

    fun hasMatch(postId: Long, origin: String): Boolean =
        titles.any { it.key == threadPartKey(ThreadPart.TITLE, postId, origin) && it.isMatchFound }
}

/**
 * Share the header in both directions when its originating row is actually composed. A child page
 * has no matching row, so returning from an author or image viewer cannot replay the entrance.
 * Turning previews off still interrupts a quick back without retaining invisible shared content.
 */
@Composable
internal fun ThreadSharedTransition(content: @Composable () -> Unit) {
    if (LocalThreadTransition.current == null) {
        content()
        return
    }
    val visibility = LocalNavAnimatedContentScope.current.transition
    val sharedReturn = LocalThreadSharedReturnEnabled.current
    val config = remember(visibility, sharedReturn) {
        object : SharedTransitionScope.SharedContentConfig {
            override val SharedTransitionScope.SharedContentState.isEnabled: Boolean
                get() = sharedReturn || visibility.targetState == EnterExitState.Visible

            override val shouldKeepEnabledForOngoingAnimation: Boolean = false
        }
    }
    CompositionLocalProvider(LocalThreadSharedContentConfig provides config, content = content)
}

/**
 * Which list a row is travelling from, as part of the key that pairs it with the thread header.
 *
 * The key's other job is to keep two *rows* from pairing. 首页 and a user's space can both show the
 * same post, and a transition between them composes both lists at once — one key with two live
 * claims is a state the shared-element machinery has no answer for. Each list names its own origin
 * and the header matches only the one handed over in [io.github.nodyssey.ThreadPreview.origin]; an
 * empty origin (a deep link, a notification, a saved thread from before the field existed) matches
 * no row and leaves the thread on its ordinary slide.
 */
const val THREAD_ORIGIN_FEED = "feed"
const val THREAD_ORIGIN_MY_TOPICS = "mytopics"

/** A user's space is its uid, not merely `space`: two space pages can sit on one stack. */
fun spaceOrigin(uid: Long): String = "space-$uid"

/** The initial query identifies the search entry even if its submitted query later changes. */
fun searchOrigin(initialQuery: String?): String =
    "search-${initialQuery?.let { "query:$it" } ?: "default"}"

/**
 * The four things a feed row and a thread state about the same post.
 *
 * A row is not a summary of the thread so much as the same four facts laid out in one line, and
 * opening the thread re-lays them out rather than replacing them: the title grows and takes the
 * width, the board tag drops under it, the avatar grows and takes the author's name down with it.
 * Animating that is only saying out loud what the two layouts already have in common — which is
 * why the *time* is not in this list. The row shows when the thread was last active and the thread
 * shows when it was posted; those are two different facts that happen to look alike.
 *
 * Every one of them is keyed by post id, which is what pairs the two ends. A row whose thread is
 * opened from somewhere else, or a thread opened from a notification, simply never finds its
 * partner — that costs nothing and animates as it always did.
 */
private enum class ThreadPart {
    TITLE,
    AVATAR,
    AUTHOR,
    BOARD,
}

private fun threadPartKey(part: ThreadPart, postId: Long, origin: String): String = "$origin-${part.name}-$postId"

private const val THREAD_SURFACE_Z_INDEX = 1f
private const val THREAD_CONTENT_Z_INDEX = 2f

/**
 * Keep an arriving thread's opaque surface under its travelling header in the same overlay.
 *
 * Shared elements bypass the scene's drawing layer. Leaving their destination surface in that
 * layer lets an outgoing scene in the shared overlay cover it: the header flies over the old list
 * and the thread background only appears when the overlay is released. The official overlay
 * modifier moves the existing surface with its header, without another copy or an opacity change.
 * Only the destination is elevated. A matched return fades the ordinary surface below the header;
 * an unmatched return keeps it opaque and slides the whole page.
 */
@Composable
fun Modifier.sharedThreadSurface(postId: Long, origin: String?): Modifier {
    if (origin.isNullOrEmpty()) return this
    val sharedScope = LocalThreadTransition.current ?: return this
    val animatedScope = LocalNavAnimatedContentScope.current
    val matches = LocalThreadMatches.current
    val effectsSpec = threadEffectsSpec(fast = true)
    val exitAlpha by animatedScope.transition.animateFloat(
        transitionSpec = { effectsSpec },
        label = "threadReturnSurface",
    ) { state ->
        if (state == EnterExitState.PostExit && matches?.hasMatch(postId, origin) == true) 0f else 1f
    }
    return with(sharedScope) {
        this@sharedThreadSurface.renderInSharedTransitionScopeOverlay(
            zIndexInOverlay = THREAD_SURFACE_Z_INDEX,
            renderInOverlay = {
                isTransitionActive &&
                    animatedScope.transition.targetState == EnterExitState.Visible
            },
        ).graphicsLayer { alpha = exitAlpha }
    }
}

/**
 * The title, which the row states in 15sp over two lines at most and the thread in 20sp over as many
 * as it takes.
 *
 * [SharedTransitionScope.sharedBounds] and not `sharedElement`, because the two are the same *title*
 * and not the same piece of text — only the box they occupy can be interpolated honestly.
 * `scaleToBounds` scales the outgoing text into that travelling box instead of re-wrapping it every
 * frame, which is what stops a title that wraps differently at the two ends from reflowing in
 * flight.
 */
@Composable
fun Modifier.sharedThreadTitle(
    postId: Long,
    origin: String?,
): Modifier = if (origin == null) this else sharedThreadBounds(ThreadPart.TITLE, postId, origin)

/**
 * The author's name: 12sp in the row's meta line, `titleSmall` beside the larger avatar in the
 * thread. Bounds again, for the title's reason — same name, different type.
 */
@Composable
fun Modifier.sharedThreadAuthor(
    postId: Long,
    origin: String?,
): Modifier = if (origin == null) this else sharedThreadBounds(ThreadPart.AUTHOR, postId, origin)

/**
 * The avatar: 34dp in the row, 40dp in the thread, and the *same picture* at both ends — so this one
 * is a true [SharedTransitionScope.sharedElement], which scales its content rather than crossfading
 * two versions of it. [io.github.plaza.designsys.component.AvatarShape] states its corner as a
 * percentage, so the rounding scales along with it and stays right at every frame in between.
 *
 * Not applied to a pinned row, which draws a pin where the avatar goes. Flying a pin into a face
 * would be a claim that they are the same thing.
 */
@Composable
fun Modifier.sharedThreadAvatar(
    postId: Long,
    origin: String?,
): Modifier = if (origin == null) this else sharedThreadElement(ThreadPart.AVATAR, postId, origin)

/**
 * The board tag, which is the one part drawn identically at both ends — same component, same text,
 * same tonal colours. Nothing to crossfade, so it travels as a [SharedTransitionScope.sharedElement]
 * and simply moves.
 */
@Composable
fun Modifier.sharedThreadBoard(
    postId: Long,
    origin: String?,
): Modifier = if (origin == null) this else sharedThreadElement(ThreadPart.BOARD, postId, origin)

/**
 * The app's own spatial motion, so everything that travels does so on the same curve as the rest of
 * the app — and, more to the point, as each other. Four things leaving one row for four places have
 * to arrive together or they read as four animations rather than one layout rearranging.
 */
@Composable
private fun threadBoundsTransform(): BoundsTransform {
    val spec = if (LocalThreadBackGestureInProgress.current) {
        predictiveBackAnimationSpec<Rect>()
    } else {
        MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
    }
    return BoundsTransform { _, _ -> spec }
}

@Composable
private fun threadEffectsSpec(fast: Boolean): FiniteAnimationSpec<Float> {
    val visibility = LocalNavAnimatedContentScope.current.transition
    val gesture = LocalThreadBackGestureInProgress.current
    val themeSpec = if (fast) MaterialTheme.motionScheme.fastEffectsSpec<Float>() else MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    // Keep the gesture timeline through completion, rather than switching curves on finger-up.
    return remember(visibility.currentState, visibility.targetState, themeSpec) {
        if (gesture) predictiveBackAnimationSpec() else themeSpec
    }
}

@Composable
private fun SharedTransitionScope.rememberThreadPart(part: ThreadPart, postId: Long, origin: String): SharedTransitionScope.SharedContentState {
    val state = rememberSharedContentState(threadPartKey(part, postId, origin), LocalThreadSharedContentConfig.current)
    val matches = LocalThreadMatches.current
    if (part == ThreadPart.TITLE && matches != null) {
        DisposableEffect(matches, state) {
            matches.add(state)
            onDispose { matches.remove(state) }
        }
    }
    return state
}

@Composable
private fun Modifier.sharedThreadBounds(
    part: ThreadPart,
    postId: Long,
    origin: String,
): Modifier {
    val transition = LocalThreadTransition.current ?: return this
    // Deliberately read only after the null check above: it throws outside a NavDisplay entry.
    val animatedScope = LocalNavAnimatedContentScope.current
    val boundsTransform = threadBoundsTransform()
    val effectsSpec = threadEffectsSpec(fast = false)
    return with(transition) {
        this@sharedThreadBounds.sharedBounds(
            sharedContentState = rememberThreadPart(part, postId, origin),
            animatedVisibilityScope = animatedScope,
            enter = fadeIn(effectsSpec),
            exit = fadeOut(effectsSpec),
            boundsTransform = boundsTransform,
            zIndexInOverlay = THREAD_CONTENT_Z_INDEX,
            resizeMode =
            SharedTransitionScope.ResizeMode.scaleToBounds(
                contentScale = ContentScale.FillWidth,
                alignment = Alignment.TopStart,
            ),
        )
    }
}

@Composable
private fun Modifier.sharedThreadElement(
    part: ThreadPart,
    postId: Long,
    origin: String,
): Modifier {
    val transition = LocalThreadTransition.current ?: return this
    val animatedScope = LocalNavAnimatedContentScope.current
    val boundsTransform = threadBoundsTransform()
    return with(transition) {
        this@sharedThreadElement.sharedElement(
            sharedContentState = rememberThreadPart(part, postId, origin),
            animatedVisibilityScope = animatedScope,
            boundsTransform = boundsTransform,
            zIndexInOverlay = THREAD_CONTENT_Z_INDEX,
        )
    }
}
