@file:OptIn(ExperimentalForeignApi::class)

package io.github.nodyssey.ui.mail

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import io.github.nodyssey.data.mail.MailHtmlBody
import io.github.nodyssey.data.mail.MailRemoteImage
import io.github.nodyssey.data.mail.isSafeMailLink
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSNumber
import platform.UIKit.UIColor
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKNavigationTypeLinkActivated
import platform.WebKit.WKNavigationTypeOther
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.NSObject
import kotlin.math.ceil

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
        var height by remember { mutableFloatStateOf(1f) }
        val delegate = remember { MailNavigationDelegate({ openWebsite(it) }) { height = it } }
        val webView = remember {
            val configuration = WKWebViewConfiguration().apply {
                websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore()
                defaultWebpagePreferences.allowsContentJavaScript = false
                preferences.javaScriptCanOpenWindowsAutomatically = false
            }
            WKWebView(frame = CGRectZero.readValue(), configuration = configuration).apply {
                navigationDelegate = delegate
                opaque = false
                backgroundColor = UIColor.clearColor
                scrollView.scrollEnabled = false
                loadHTMLString(document, baseURL = null)
            }
        }
        UIKitView(
            factory = { webView },
            modifier = modifier.height(height.dp).onSizeChanged { delegate.resize(webView, it.width) },
            onRelease = { view ->
                view.stopLoading()
                view.navigationDelegate = null
            },
        )
    }
}

private class MailNavigationDelegate(
    private val openWebsite: (String) -> Unit,
    private val setHeight: (Float) -> Unit,
) : NSObject(),
    WKNavigationDelegateProtocol {
    private var width = 0
    private var ready = false

    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
    ) {
        val action = decidePolicyForNavigationAction
        val url = action.request.URL?.absoluteString.orEmpty()
        if (!ready && url == "about:blank" && action.navigationType == WKNavigationTypeOther) {
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
            return
        }
        decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
        if (action.navigationType == WKNavigationTypeLinkActivated && isSafeMailLink(url)) openWebsite(url)
    }

    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        ready = true
        measure(webView)
    }

    fun resize(webView: WKWebView, newWidth: Int) {
        if (width == newWidth) return
        width = newWidth
        if (ready) measure(webView)
    }

    private fun measure(webView: WKWebView) {
        webView.evaluateJavaScript("Math.max(document.body.scrollHeight, document.body.getBoundingClientRect().height)") { result, _ ->
            val height = (result as? NSNumber)?.doubleValue ?: webView.scrollView.contentSize.useContents { height }
            if (height > 0) setHeight(ceil(height).toFloat())
        }
    }
}
