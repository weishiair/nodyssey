package io.github.nodyssey.ui.login

import androidx.compose.runtime.compositionLocalOf

/** The Android shell applies its proxy before either authentication WebView starts loading. */
enum class WebViewNetworkState { Ready, Applying, Unavailable }

val LocalWebViewNetworkState = compositionLocalOf { WebViewNetworkState.Ready }
