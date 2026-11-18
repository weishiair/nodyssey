@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.nodyssey.ui.postdetail

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import platform.UIKit.UIAction
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIBarButtonItemStyle
import platform.UIKit.UIBarButtonSystemItem
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UIViewController
import platform.UIKit.accessibilityLabel
import platform.UIKit.navigationController
import platform.UIKit.setToolbarItems

/** UINavigationController places its toolbar on the same system baseline as UITabBar. */
@Composable
internal fun NativeReadingToolbar(state: NativeReadingToolbarState) {
    val controller = LocalUIViewController.current
    val host = remember(controller) { NativeReadingToolbarHost(controller) }
    val accent = MaterialTheme.colorScheme.primary
    SideEffect {
        host.update(
            state,
            UIColor(red = accent.red.toDouble(), green = accent.green.toDouble(), blue = accent.blue.toDouble(), alpha = accent.alpha.toDouble()),
        )
    }
    DisposableEffect(host) {
        onDispose { host.dispose() }
    }
}

// UIAction owns only callbacks, so it cannot retain the controller through its toolbar items.
private class ReadingToolbarCallbacks {
    var onJump: () -> Unit = {}
    var onReply: () -> Unit = {}
}

private class NativeReadingToolbarHost(private val controller: UIViewController) {
    private val callbacks = ReadingToolbarCallbacks()
    private val position = callbacks.let { actions -> UIBarButtonItem(primaryAction = UIAction.actionWithHandler { actions.onJump() }) }
    private val reply = callbacks.let { actions -> UIBarButtonItem(primaryAction = UIAction.actionWithHandler { actions.onReply() }) }
    private val items = listOf(
        UIBarButtonItem(barButtonSystemItem = UIBarButtonSystemItem.UIBarButtonSystemItemFlexibleSpace, target = null, action = null),
        position,
        UIBarButtonItem(barButtonSystemItem = UIBarButtonSystemItem.UIBarButtonSystemItemFlexibleSpace, target = null, action = null),
        reply,
    )
    private var visible = false

    fun update(state: NativeReadingToolbarState, accent: UIColor) {
        callbacks.onJump = state.position.onClick
        callbacks.onReply = state.reply.onClick
        position.title = state.position.title
        position.accessibilityLabel = state.position.label
        reply.image = state.reply.symbol?.let { UIImage.systemImageNamed(it) }
        reply.accessibilityLabel = state.reply.label
        reply.style = UIBarButtonItemStyle.UIBarButtonItemStyleDone
        position.tintColor = accent
        reply.tintColor = accent
        if (state.visible != visible) {
            visible = state.visible
            controller.setToolbarItems(if (visible) items else emptyList<UIBarButtonItem>(), animated = false)
        }
        controller.navigationController?.let { navigation ->
            if (navigation.topViewController == controller && navigation.toolbarHidden == visible) {
                navigation.setToolbarHidden(!visible, animated = false)
            }
        }
    }

    fun dispose() {
        callbacks.onJump = {}
        callbacks.onReply = {}
        controller.setToolbarItems(emptyList<UIBarButtonItem>(), animated = false)
        controller.navigationController?.let { navigation ->
            if (navigation.topViewController == controller) navigation.setToolbarHidden(true, animated = false)
        }
    }
}
