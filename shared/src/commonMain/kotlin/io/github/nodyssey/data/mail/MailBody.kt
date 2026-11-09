package io.github.nodyssey.data.mail

import com.fleeksoft.ksoup.Ksoup
import io.github.nodyssey.core.html.Selectors

/**
 * Email is untrusted content. Convert it to selectable text; never run it in the authenticated
 * WebView or fetch remote images. HTML-only mail keeps paragraphs, table cells and safe link targets.
 */
object MailBody {
    fun text(mail: MailMessage): String {
        mail.bodyText?.takeIf(String::isNotBlank)?.let { return it.trim() }
        val html = mail.bodyHtml?.takeIf(String::isNotBlank) ?: return ""
        val document = Ksoup.parse(html)
        document.select(Selectors.MAIL_ACTIVE_CONTENT).remove()
        document.select(Selectors.MAIL_LINK).forEach { link ->
            val href = link.attr("href").trim()
            if (isSafeMailLink(href) && href != link.text()) link.appendText(" ($href)")
        }
        document.select(Selectors.MAIL_BLOCK)
            .forEach { it.appendText("\n") }
        document.select(Selectors.MAIL_CELL).forEach { it.appendText("\t") }
        return document.body().wholeText().replace(Regex("\n[\t ]*\n(?:[\t ]*\n)+"), "\n\n").trim()
    }
}

fun isSafeMailLink(url: String): Boolean =
    (
        url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("mailto:", ignoreCase = true)
        ) && url.none { it.isWhitespace() || it.code < 32 }
