package io.github.nodyssey.ui.mail

import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import io.github.nodyssey.data.mail.MailHtmlBody
import io.github.nodyssey.data.mail.MailRemoteImage
import io.github.nodyssey.data.mail.isSafeMailLink
import java.awt.Color
import java.net.URI
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.util.Base64
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent
import javax.swing.text.Element
import javax.swing.text.StyleConstants
import javax.swing.text.View
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.ImageView

@Composable
internal actual fun MailHtmlReader(
    body: MailHtmlBody,
    images: Map<String, MailRemoteImage>,
    onOpenWebsite: (String) -> Unit,
    modifier: Modifier,
) {
    val document = body.rememberDocument(images, includeStyles = false) ?: return
    val openWebsite by rememberUpdatedState(onOpenWebsite)
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    key(document) {
        var width by remember { mutableIntStateOf(0) }
        var height by remember { mutableIntStateOf(1) }
        SwingPanel(
            background = surface,
            modifier = modifier.height(height.dp).onSizeChanged { width = it.width },
            factory = {
                JEditorPane().apply {
                    isEditable = false
                    border = null
                    editorKit = MailEditorKit()
                    putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
                    text = document
                    addHyperlinkListener { event ->
                        val url = event.description.orEmpty()
                        if (event.eventType == HyperlinkEvent.EventType.ACTIVATED && isSafeMailLink(url)) openWebsite(url)
                    }
                }
            },
            update = { pane ->
                pane.background = Color(surface.toArgb(), true)
                pane.foreground = Color(onSurface.toArgb(), true)
                if (width > 0) {
                    pane.ui.getRootView(pane).setSize(width.toFloat(), Int.MAX_VALUE.toFloat())
                    height = pane.preferredSize.height.coerceAtLeast(1)
                }
            },
        )
    }
}

private class MailEditorKit : HTMLEditorKit() {
    private val factory = object : HTMLFactory() {
        override fun create(element: Element): View = if (element.attributes.getAttribute(StyleConstants.NameAttribute) == HTML.Tag.IMG) {
            object : ImageView(element) {
                init {
                    loadsSynchronously = true
                }

                override fun getImageURL(): URL? {
                    val src = element.attributes.getAttribute(HTML.Attribute.SRC) as? String ?: return null
                    return decodeMailImage(src)
                }
            }
        } else {
            super.create(element)
        }
    }

    override fun getViewFactory(): HTMLFactory = factory
}

private fun decodeMailImage(src: String): URL? {
    val header = src.substringBefore(',').lowercase()
    if (!header.startsWith("data:image/") || !header.endsWith(";base64")) return null
    val data = runCatching { Base64.getDecoder().decode(src.substringAfter(',')) }.getOrNull() ?: return null
    return URL.of(
        URI.create("data:mail-image"),
        object : URLStreamHandler() {
            override fun openConnection(url: URL): URLConnection = object : URLConnection(url) {
                override fun connect() = Unit

                override fun getInputStream() = data.inputStream()

                override fun getContentType() = header.removePrefix("data:").removeSuffix(";base64")
            }
        },
    )
}
