package io.github.plaza.core.richtext

/** Adds links to literal text without interpreting any Markdown or changing the displayed text. */
fun linkifyPlainText(text: String): List<InlineNode> = buildList {
    var start = 0
    for (match in WEB_URL.findAll(text)) {
        val url = webUrlFromMatch(text, match) ?: continue
        if (start < match.range.first) add(InlineNode.Text(text.substring(start, match.range.first)))
        add(InlineNode.Link(text = url, url = url))
        start = match.range.first + url.length
    }
    if (start < text.length) add(InlineNode.Text(text.substring(start)))
}

internal fun webUrlAt(text: String, start: Int): String? {
    if (text[start] != 'h' && text[start] != 'H') return null
    return WEB_URL.matchAt(text, start)?.let { webUrlFromMatch(text, it) }
}

private fun webUrlFromMatch(text: String, match: MatchResult): String? {
    // A URL can follow CJK prose without a space, but not start inside an ASCII word or scheme.
    val preceding = text.getOrNull(match.range.first - 1)
    if (preceding != null && (preceding in 'a'..'z' || preceding in 'A'..'Z' || preceding in '0'..'9' || preceding == '_')) return null

    val candidate = match.value
    val start = candidate.indexOf("://") + 3
    var end = candidate.length
    var parens = candidate.count { it == '(' } - candidate.count { it == ')' }
    var brackets = candidate.count { it == '[' } - candidate.count { it == ']' }
    // Keep balanced brackets in the URL, including IPv6 hosts and paths such as /wiki/Foo_(bar).
    while (end > start) {
        when (candidate[end - 1]) {
            '.', ',', ':', ';', '!', '?', '\'' -> Unit
            ')' -> if (parens < 0) parens++ else break
            ']' -> if (brackets < 0) brackets++ else break
            else -> break
        }
        end--
    }
    return candidate.substring(0, end).takeIf { end > start }
}

private val WEB_URL = Regex("""https?://[^\s\p{Z}<>"`，。！？、；：…（）【】《》“”‘’]+""", RegexOption.IGNORE_CASE)
