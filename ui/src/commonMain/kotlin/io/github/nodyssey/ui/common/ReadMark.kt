package io.github.nodyssey.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import io.github.nodyssey.data.settings.ReadMarkStyle
import io.github.plaza.designsys.theme.LocalEinkMode
import io.github.plaza.designsys.theme.LocalPlazaDarkTheme
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.PlazaSeedHct
import io.github.plaza.designsys.theme.toPlazaSeedHct
import kotlin.math.min

/**
 * 已读标记, as the lists that draw it need it: which halves of the list are tinted, and with what.
 *
 * [color] is null while the reader has not chosen one, and the scheme's own `primary` is used
 * instead — so a fresh install has a mark that belongs to the theme rather than a colour of its own.
 *
 * Read through [LocalReadMarkPaint], which `NodysseyRoot` fills from the settings SSOT: three list
 * surfaces draw a card (首页与版块, 搜索结果, 推荐阅读) and none of them should be deciding this for
 * itself, or the answer would drift between screens the way the read dim drifted from its spec.
 */
data class ReadMarkPaint(
    val style: ReadMarkStyle = ReadMarkStyle.BLOCK,
    val color: Color? = null,
)

/** Ambient 已读标记. The default is what a screen standing on its own — a preview, a render — gets. */
val LocalReadMarkPaint = staticCompositionLocalOf { ReadMarkPaint() }

/**
 * What a marked card is filled with, in HCT rather than as a tint laid over the card.
 *
 * The reader picks a hue; the tone is ours. That is what keeps the mark a block *difference* in both
 * themes instead of a brightness change: alpha over a white card darkens it, and the same alpha over
 * a near-black one lightens it — so 已读 would recede by day and advance by night. Pinned to a tone
 * on the page's own side, the card stays as light or as dark as its neighbours and differs only in
 * colour.
 *
 * Chroma is capped too. A reader who picks a neon hue is choosing a colour, not a lamp, and past
 * about 16 the fill stops reading as paper and starts reading as a highlighter.
 */
private const val MARK_TINT_CHROMA = 16f

private const val MARK_TINT_TONE_LIGHT = 88f

private const val MARK_TINT_TONE_DARK = 26f

/** Whether [isRead] rows are the half this style paints. */
private fun ReadMarkPaint.tints(isRead: Boolean): Boolean =
    when (style) {
        ReadMarkStyle.BLOCK -> isRead
        ReadMarkStyle.UNREAD_BLOCK -> !isRead
        ReadMarkStyle.DIM, ReadMarkStyle.OFF -> false
    }

/**
 * The fill for a card, given its read state: the theme's card colour, or the mark's.
 *
 * Pass the answer to `LayerCard(containerColor = …)`. 墨水屏模式 gets the plain card back — a colour
 * difference is what that mode exists to remove, and [readMarkDimsTitle] takes over as the mark there.
 */
@Composable
fun readMarkCardColor(isRead: Boolean): Color {
    val paint = LocalReadMarkPaint.current
    val card = LocalPlazaLayers.current.card
    if (!paint.tints(isRead) || LocalEinkMode.current) return card
    val source = (paint.color ?: MaterialTheme.colorScheme.primary).toPlazaSeedHct()
    val tone = if (LocalPlazaDarkTheme.current) MARK_TINT_TONE_DARK else MARK_TINT_TONE_LIGHT
    return PlazaSeedHct(hue = source.hue, chroma = min(source.chroma, MARK_TINT_CHROMA), tone = tone).toColor()
}

/**
 * Whether the title should recede instead of, or as well as, the block being painted.
 *
 * Never alongside a tint — the block is the signal, and quieting the title on top of it would make
 * the marked half the harder one to read. [ReadMarkStyle.DIM] is the case this exists for: no
 * colour, so the title carries the state on its own, which is what the redesign asked for and what
 * the feed shipped without (the weight below was fixed at SemiBold for both halves).
 */
@Composable
fun readMarkDimsTitle(isRead: Boolean): Boolean =
    when (LocalReadMarkPaint.current.style) {
        ReadMarkStyle.DIM -> isRead

        // The unread half is the one painted, so the read half keeps this as its mark.
        ReadMarkStyle.UNREAD_BLOCK -> isRead

        // Never drawn beside the tint — this is 墨水屏模式's substitute for it.
        ReadMarkStyle.BLOCK -> isRead && LocalEinkMode.current

        ReadMarkStyle.OFF -> false
    }

/**
 * A title's ink: the state is drawn, so the title keeps its contrast; in [ReadMarkStyle.DIM] the
 * read half drops to the scheme's secondary ink, which is the whole of what that style does.
 */
@Composable
fun readMarkTitleColor(isRead: Boolean): Color =
    if (readMarkDimsTitle(isRead)) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }

/**
 * A title's weight. 未读粗、已读淡 — the half of the original spec that never reached the feed, where
 * `SemiBold` was written once and applied to both states.
 *
 * Only while the title is the one carrying the state; a painted card keeps its bold title.
 */
@Composable
fun readMarkTitleWeight(isRead: Boolean): FontWeight =
    if (readMarkDimsTitle(isRead)) FontWeight.Medium else FontWeight.SemiBold
