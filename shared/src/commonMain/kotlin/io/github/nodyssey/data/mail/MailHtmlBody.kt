package io.github.nodyssey.data.mail

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.safety.Cleaner
import com.fleeksoft.ksoup.safety.Safelist
import io.github.nodyssey.core.html.Selectors
import io.github.plaza.core.net.WebUrl
import kotlin.io.encoding.Base64

class MailHtmlBody private constructor(
    private val markup: String,
    val remoteImages: List<String>,
) {
    fun document(images: Map<String, MailRemoteImage>, dark: Boolean, includeStyles: Boolean = true): String {
        val document = Ksoup.parse(markup)
        document.outputSettings().prettyPrint(false)
        document.select(Selectors.MAIL_HTML_PENDING_IMAGE).forEach { image ->
            val url = image.attr("data-mail-image")
            image.removeAttr("data-mail-image")
            images[url]?.let { image.attr("src", it.dataUrl) }
        }
        if (!includeStyles) {
            document.select(Selectors.MAIL_HTML_STYLE).remove()
            document.select(Selectors.MAIL_HTML_STYLED).removeAttr("style")
        }
        val head = document.head()
        head.prependElement("style").append(
            """
            :root { color-scheme: ${if (dark) "dark" else "light"}; }
            body { margin: 0; font: 16px/1.5 sans-serif; overflow-wrap: anywhere; }
            img { max-width: 100%; height: auto; }
            table { max-width: 100%; }
            """.trimIndent(),
        )
        head.prependElement("meta").attr("name", "viewport").attr("content", "width=device-width, initial-scale=1")
        head.prependElement("meta").attr("name", "referrer").attr("content", "no-referrer")
        head.prependElement("meta").attr("http-equiv", "Content-Security-Policy").attr("content", CONTENT_POLICY)
        return "<!doctype html>${document.outerHtml()}"
    }

    companion object {
        private const val CONTENT_POLICY = "default-src 'none'; img-src data:; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'"
        private const val MAX_HTML_LENGTH = 2_000_000

        private val cleaner = Cleaner(
            Safelist.relaxed()
                .addTags("style", "center", "font", "header", "footer", "main", "section", "article", "figure", "figcaption", "wbr")
                .addAttributes(":all", "class", "id", "style", "dir", "lang", "title")
                .addAttributes("style", "media")
                .addAttributes("font", "face", "size", "color")
                .addAttributes("table", "align", "bgcolor", "border", "cellpadding", "cellspacing", "height")
                .addAttributes("tr", "align", "bgcolor", "valign")
                .addAttributes("td", "bgcolor", "width", "height", "valign")
                .addAttributes("th", "bgcolor", "width", "height", "valign")
                .addAttributes("p", "align")
                .removeProtocols("img", "src", "http", "https")
                .removeProtocols("a", "href", "http", "https", "ftp", "mailto"),
        )

        internal fun parse(raw: String?): MailHtmlBody? {
            if (raw.isNullOrBlank() || raw.length > MAX_HTML_LENGTH) return null
            val source = Ksoup.parse(raw)
            source.select(Selectors.MAIL_HTML_DISCARDED).remove()
            val originalBody = source.body()
            source.head().select(Selectors.MAIL_HTML_STYLE).toList().asReversed().forEach { originalBody.prependChild(it) }
            val clean = cleaner.clean(source)
            clean.outputSettings().prettyPrint(false)
            val body = clean.body()
            for (name in listOf("class", "id", "style", "dir", "lang", "bgcolor", "text", "link")) {
                if (originalBody.hasAttr(name)) body.attr(name, originalBody.attr(name))
            }
            clean.select(Selectors.MAIL_LINK).forEach { link ->
                val url = link.attr("href").trim()
                if (isSafeMailLink(url)) link.attr("href", url) else link.removeAttr("href")
            }
            val remote = linkedSetOf<String>()
            clean.select(Selectors.MAIL_HTML_IMAGE).forEach { image ->
                val src = image.attr("src").trim()
                val url = if (src.startsWith("//")) "https:$src" else src
                image.removeAttr("src")
                when {
                    isMailImageUrl(url) && (url in remote || remote.size < MAX_REMOTE_IMAGES) -> {
                        remote += url
                        image.attr("data-mail-image", url)
                    }

                    isInlineMailImage(src) -> image.attr("src", src)
                }
            }
            if (body.text().isBlank() && clean.select(Selectors.MAIL_HTML_READABLE).isEmpty()) return null
            return MailHtmlBody(clean.outerHtml(), remote.toList())
        }
    }
}

class MailRemoteImage internal constructor(mediaType: String, bytes: ByteArray) {
    internal val dataUrl = "data:$mediaType;base64,${Base64.encode(bytes)}"
}

internal val mailImageTypes = setOf("image/png", "image/jpeg", "image/gif", "image/webp", "image/bmp", "image/avif")

internal fun isMailImageUrl(url: String): Boolean = WebUrl.parse(url)?.let {
    (it.scheme == "http" || it.scheme == "https") && it.host != null && it.userInfo == null
} == true

private fun isInlineMailImage(src: String): Boolean {
    val header = src.substringBefore(',').lowercase()
    return header.startsWith("data:") && header.endsWith(";base64") &&
        header.removePrefix("data:").removeSuffix(";base64") in mailImageTypes &&
        src.substringAfter(',', "").let { it.isNotEmpty() && it.all { char -> char in BASE64_CHARS } }
}

private const val BASE64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/="
