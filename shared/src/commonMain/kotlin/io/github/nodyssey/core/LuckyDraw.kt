package io.github.nodyssey.core

import io.github.plaza.core.net.WebUrl
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The parameters of a T-floor draw, as `/lucky` asks for them.
 *
 * NodeSeek's "幸运抽奖" is not a game — it is a notary: you declare in advance which thread, when it
 * closes, how many prizes, which floor counting starts from, and whether one user can win twice. The
 * link that comes out is what gets pasted into the thread, and anyone can open it afterwards to check
 * the result was not chosen after the fact. Nothing is spent.
 */
@Serializable
data class LuckyDrawParams(
    val postId: Long,
    val drawAtMillis: Long,
    val prizeCount: Int,
    val startFloor: Int,
    val dedupeFloors: Boolean,
) {
    /** Every parameter affects the result; two draws in one thread must remain independent. */
    val key: String get() = "$postId-$drawAtMillis-$prizeCount-$startFloor-$dedupeFloors"
}

object LuckyDraw {

    const val MAX_PRIZE_COUNT = 999

    /**
     * Builds the public draw link.
     *
     * The query keys follow the site's own form (`post` / `time` / `count` / `start` / `duplicate`). They are
     * read off the page rather than from a documented API, so this stays one small function with a
     * test: if the site renames a key, one assertion fails instead of a screen going quietly wrong.
     */
    fun link(params: LuckyDrawParams, site: Site = ActiveSite.current): String {
        val query =
            listOf(
                "post" to params.postId.toString(),
                "time" to params.drawAtMillis.toString(),
                "count" to params.prizeCount.coerceAtLeast(1).toString(),
                "start" to params.startFloor.coerceAtLeast(0).toString(),
                "duplicate" to (!params.dedupeFloors).toString(),
                "mode" to "view",
            ).joinToString("&") { (key, value) -> "$key=${value.urlEncode()}" }
        return "${site.origin}${NodeSeekSite.LUCKY_PATH}?$query"
    }

    /** Only the selected site's actual result links, never a lookalike host or another post. */
    fun parse(url: String, site: Site = ActiveSite.current): LuckyDrawParams? {
        val absolute = if (url.startsWith("/") && !url.startsWith("//")) site.origin + url else url
        val parsed = WebUrl.parse(absolute) ?: return null
        if (parsed.scheme != "https" || parsed.userInfo != null || parsed.port !in setOf(-1, 443) ||
            parsed.host?.lowercase() !in site.hosts || parsed.path != NodeSeekSite.LUCKY_PATH ||
            parsed.queryParameter("mode") != "view"
        ) {
            return null
        }
        val post = parsed.queryParameter("post")?.toLongOrNull()?.takeIf { it > 0 } ?: return null
        val time = parsed.queryParameter("time")?.toLongOrNull()
            ?.takeIf { it >= 1_595_431_050_000L && it <= 8_640_000_000_000_000L } ?: return null
        val count = parsed.queryParameter("count")?.toIntOrNull()?.takeIf { it in 1..MAX_PRIZE_COUNT } ?: return null
        val start = parsed.queryParameter("start")?.toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val duplicate = parsed.queryParameter("duplicate")?.takeIf { it == "true" || it == "false" } ?: return null
        return LuckyDrawParams(post, time, count, start, duplicate == "false")
    }

    fun find(nodes: List<RichNode>, postId: Long, site: Site = ActiveSite.current): List<LuckyDrawParams> {
        val found = mutableListOf<LuckyDrawParams>()
        fun inlines(items: List<InlineNode>) {
            items.filterIsInstance<InlineNode.Link>().forEach { link ->
                parse(link.url, site)?.takeIf { it.postId == postId }?.let(found::add)
            }
        }
        fun visit(items: List<RichNode>) {
            items.forEach { node ->
                when (node) {
                    is RichNode.Paragraph -> inlines(node.inlines)
                    is RichNode.Heading -> inlines(node.inlines)
                    is RichNode.Quote -> visit(node.children)
                    is RichNode.Fold -> visit(node.children)
                    is RichNode.Tabs -> node.tabs.forEach { visit(it.children) }
                    is RichNode.ListBlock -> node.items.forEach(::visit)
                    is RichNode.Table -> node.content.forEach { row -> row.forEach(::inlines) }
                    else -> Unit
                }
            }
        }
        visit(nodes)
        return found.distinctBy { it.key }
    }

    /** The same instant, formatted the way the form displays it. */
    fun formatDrawTime(millis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String =
        millis.at(zone).let { "${it.year}/${it.month.number}/${it.day} ${it.hour.pad()}:${it.minute.pad()}" }

    private fun Long.at(zone: TimeZone) = Instant.fromEpochMilliseconds(this).toLocalDateTime(zone)

    private fun Int.pad(): String = toString().padStart(2, '0')
}
