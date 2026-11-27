package io.github.nodyssey.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import com.github.panpf.zoomimage.CoilZoomState
import com.github.panpf.zoomimage.compose.coil.CoilComposeSubsamplingImageGenerator
import com.github.panpf.zoomimage.rememberCoilZoomState
import com.github.panpf.zoomimage.subsampling.SubsamplingImageGenerateResult
import com.github.panpf.zoomimage.zoom.Edge
import com.github.panpf.zoomimage.zoom.ReadMode
import io.github.nodyssey.ui.common.rememberShareText
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_close
import io.github.nodyssey.ui.resources.action_load_anyway
import io.github.nodyssey.ui.resources.action_open_in_browser
import io.github.nodyssey.ui.resources.action_retry
import io.github.nodyssey.ui.resources.action_share
import io.github.nodyssey.ui.resources.viewer_deferred_wifi_only
import io.github.nodyssey.ui.resources.viewer_gesture_hint
import io.github.nodyssey.ui.resources.viewer_load_failed
import io.github.nodyssey.ui.resources.viewer_page
import io.github.nodyssey.ui.resources.viewer_save
import io.github.nodyssey.ui.resources.viewer_save_failed
import io.github.nodyssey.ui.resources.viewer_save_no_permission
import io.github.nodyssey.ui.resources.viewer_save_unsupported
import io.github.nodyssey.ui.resources.viewer_saved
import io.github.nodyssey.ui.resources.viewer_zoom_hint
import io.github.plaza.designsys.component.PlazaIcon
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.imageLoadFailureText
import io.github.plaza.designsys.image.ImageLoadFailure
import io.github.plaza.designsys.image.ImagesDeferredException
import io.github.plaza.designsys.image.allowMeteredImage
import io.github.plaza.designsys.image.diagnoseImageFailure
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/**
 * Full-screen image viewer with pinch and double-tap zoom. Tall screenshots open at reading width,
 * with the top of the image in view. Swiping vertically dismisses an image when it fits on screen.
 */
@Composable
fun ImageViewerScreen(
    urls: List<String>,
    initialIndex: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenBrowser: (String) -> Unit = {},
    saveOutcome: SaveOutcome? = null,
    onSave: (String) -> Unit = {},
) {
    if (urls.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val shareText = rememberShareText()
    val pagerState = rememberPagerState(initialPage = initialIndex.coerceIn(0, urls.lastIndex)) { urls.size }
    var canDismissByDrag by remember(pagerState.currentPage) { mutableStateOf(true) }
    var canPage by remember(pagerState.currentPage) { mutableStateOf(true) }

    val notice =
        when (saveOutcome) {
            null -> null
            SaveOutcome.SAVED -> stringResource(Res.string.viewer_saved)
            SaveOutcome.UNSUPPORTED_OS -> stringResource(Res.string.viewer_save_unsupported)
            SaveOutcome.PERMISSION_DENIED -> stringResource(Res.string.viewer_save_no_permission)
            SaveOutcome.FAILED -> stringResource(Res.string.viewer_save_failed)
        }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VIEWER_BACKGROUND)
            .systemBarsPadding(),
    ) {
        ViewerTopBar(
            page = pagerState.currentPage + 1,
            total = urls.size,
            onClose = onClose,
            onSave = { onSave(urls[pagerState.currentPage]) },
            onShare = { shareText(urls[pagerState.currentPage], null) },
            onOpenBrowser = { onOpenBrowser(urls[pagerState.currentPage]) },
        )

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = canPage,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) { page ->
            key(urls[page]) {
                ZoomableImage(
                    url = urls[page],
                    isCurrentPage = page == pagerState.currentPage,
                    zoomState = rememberViewerZoomState(),
                    onDismiss = onClose,
                    onGesturesChanged = { dismissByDrag, pageBySwipe ->
                        canDismissByDrag = dismissByDrag
                        canPage = pageBySwipe
                    },
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (urls.size > 1) PageDots(count = urls.size, current = pagerState.currentPage)
            val hint = if (canDismissByDrag) Res.string.viewer_gesture_hint else Res.string.viewer_zoom_hint
            Text(
                text = notice ?: stringResource(hint),
                style = MaterialTheme.typography.labelMedium,
                color = VIEWER_SECONDARY_CONTENT,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ViewerTopBar(
    page: Int,
    total: Int,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onOpenBrowser: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            PlazaIcon(
                Icons.Default.Close,
                contentDescription = stringResource(Res.string.action_close),
                tint = VIEWER_CONTENT,
            )
        }
        Text(
            text = stringResource(Res.string.viewer_page, page, total),
            style =
            MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
            color = VIEWER_CONTENT,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSave) {
            PlazaIcon(PlazaIcons.Download, contentDescription = stringResource(Res.string.viewer_save), tint = VIEWER_CONTENT)
        }
        IconButton(onClick = onShare) {
            PlazaIcon(Icons.Default.Share, contentDescription = stringResource(Res.string.action_share), tint = VIEWER_CONTENT)
        }
        IconButton(onClick = onOpenBrowser) {
            PlazaIcon(
                PlazaIcons.OpenInNew,
                contentDescription = stringResource(Res.string.action_open_in_browser),
                tint = VIEWER_CONTENT,
            )
        }
    }
}

@Composable
private fun PageDots(
    count: Int,
    current: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        repeat(count) { index ->
            val active = index == current
            Box(
                Modifier
                    .width(if (active) 20.dp else 8.dp)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(if (active) VIEWER_CONTENT else VIEWER_INACTIVE_DOT),
            )
        }
    }
}

@Composable
internal fun rememberViewerZoomState(): CoilZoomState = rememberCoilZoomState(
    subsamplingImageGenerators = persistentListOf(SampledImagesOnly),
)

private data object SampledImagesOnly : CoilComposeSubsamplingImageGenerator {
    override suspend fun generateImage(
        context: PlatformContext,
        imageLoader: ImageLoader,
        result: SuccessResult,
        painter: Painter,
    ): SubsamplingImageGenerateResult? =
        if (result.isSampled) null else SubsamplingImageGenerateResult.Error("Preview uses original pixels")
}

@Composable
internal fun ZoomableImage(
    url: String,
    isCurrentPage: Boolean,
    zoomState: CoilZoomState,
    onDismiss: () -> Unit,
    onGesturesChanged: (canDismissByDrag: Boolean, canPage: Boolean) -> Unit,
) {
    var dragY by remember { mutableFloatStateOf(0f) }
    var retryToken by remember { mutableIntStateOf(0) }
    var allowMetered by remember { mutableStateOf(false) }
    val context = LocalPlatformContext.current
    val request = remember(context, url, allowMetered) {
        ImageRequest
            .Builder(context)
            .data(url)
            // Keep the overview within Coil's bitmap cap. ZoomImage reads details from the source.
            .size(Size.ORIGINAL)
            .allowMeteredImage(allowMetered)
            .build()
    }
    zoomState.zoomable.setReadMode(VerticalReadMode)
    val zoom = zoomState.zoomable
    val canDismissByDrag = zoom.scrollEdge.vertical == Edge.BOTH && zoom.transform.scale.scaleX <= zoom.minScale
    val canPage = zoom.scrollEdge.horizontal == Edge.BOTH
    LaunchedEffect(isCurrentPage, canDismissByDrag, canPage) {
        if (isCurrentPage) onGesturesChanged(canDismissByDrag, canPage)
    }

    var loadError by remember(request, retryToken) { mutableStateOf<Throwable?>(null) }
    val dismissDragThresholdPx = with(LocalDensity.current) { DISMISS_DRAG_THRESHOLD.toPx() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(canDismissByDrag, dismissDragThresholdPx, onDismiss) {
                if (!canDismissByDrag) return@pointerInput
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (abs(dragY) > dismissDragThresholdPx) onDismiss()
                        dragY = 0f
                    },
                    onDragCancel = { dragY = 0f },
                ) { _, delta -> dragY += delta }
            },
        contentAlignment = Alignment.Center,
    ) {
        key(retryToken, allowMetered) {
            CoilZoomAsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.sm)
                    .graphicsLayer { translationY = dragY },
                zoomState = zoomState,
                onState = { state ->
                    loadError = (state as? AsyncImagePainter.State.Error)?.result?.throwable
                },
            )
        }
        loadError?.let { error ->
            val deferred = error is ImagesDeferredException
            ImageFailure(
                url = url,
                deferred = deferred,
                failure = if (deferred) null else diagnoseImageFailure(error),
                onRetry = { if (deferred) allowMetered = true else retryToken++ },
            )
        }
    }
}

@Composable
private fun ImageFailure(
    url: String,
    deferred: Boolean,
    failure: ImageLoadFailure?,
    onRetry: () -> Unit,
) {
    val reason = imageLoadFailureText(failure)
    Surface(
        color = VIEWER_ACTION_CONTAINER,
        contentColor = VIEWER_CONTENT,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.padding(horizontal = Spacing.lg),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            PlazaIcon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = VIEWER_WARNING,
                modifier = Modifier.size(20.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(
                        if (deferred) Res.string.viewer_deferred_wifi_only else Res.string.viewer_load_failed,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
                // Why it failed, above which file failed: 重试 is on this card and the reason is what
                // says whether it is worth pressing — a Cloudflare challenge will refuse it again, and
                // 用浏览器打开 in the bar above is the way through.
                if (reason != null) {
                    Text(
                        reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = VIEWER_SECONDARY_CONTENT,
                    )
                }
                Text(
                    url.toFileName(),
                    style = MaterialTheme.typography.labelSmall,
                    color = VIEWER_SECONDARY_CONTENT,
                )
            }
            Surface(
                onClick = onRetry,
                color = VIEWER_RETRY_CONTAINER,
                contentColor = VIEWER_CONTENT,
                shape = CircleShape,
            ) {
                Text(
                    stringResource(
                        if (deferred) Res.string.action_load_anyway else Res.string.action_retry,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/*
 * Fixed colours rather than theme roles: this screen is the same near-black in light and dark mode,
 * because it is a lightbox — the image is the content and everything else steps out of its way.
 */
private val VIEWER_BACKGROUND = Color(0xFF0E0E11)
private val VIEWER_CONTENT = Color(0xFFE9E9EE)
private val VIEWER_SECONDARY_CONTENT = Color(0xFFC9C9D2)
private val VIEWER_INACTIVE_DOT = Color(0xFF5A5A64)
private val VIEWER_ACTION_CONTAINER = Color(0x1AFFFFFF)
private val VIEWER_RETRY_CONTAINER = Color(0x24FFFFFF)
private val VIEWER_WARNING = Color(0xFFF2B8B5)

private val VerticalReadMode = ReadMode(sizeType = ReadMode.SIZE_TYPE_VERTICAL)
private val DISMISS_DRAG_THRESHOLD = 120.dp

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "8h 图片查看器")
@Composable
private fun ImageViewerPreview() {
    ImageViewerScreen(
        urls = listOf("https://www.nodeseek.com/static/nft-list-ruleset.png"),
        initialIndex = 0,
        onClose = {},
    )
}
