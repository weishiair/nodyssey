package io.github.nodyssey.ui.mail

import android.graphics.Color
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.nodyssey.data.mail.MailHtmlBody
import io.github.nodyssey.data.mail.MailRemoteImage
import io.github.nodyssey.data.mail.isSafeMailLink

@Composable
internal actual fun MailHtmlReader(
    body: MailHtmlBody,
    images: Map<String, MailRemoteImage>,
    onOpenWebsite: (String) -> Unit,
    modifier: Modifier,
) {
    val document = body.rememberDocument(images) ?: return
    val openWebsite by rememberUpdatedState(onOpenWebsite)
    key(document) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    setBackgroundColor(Color.TRANSPARENT)
                    settings.javaScriptEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.blockNetworkLoads = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val url = request.url.toString()
                            if (request.isForMainFrame && request.hasGesture() && isSafeMailLink(url)) openWebsite(url)
                            return true
                        }
                    }
                    loadDataWithBaseURL(null, document, "text/html", "UTF-8", null)
                }
            },
            onRelease = { view ->
                view.stopLoading()
                view.destroy()
            },
        )
    }
}
