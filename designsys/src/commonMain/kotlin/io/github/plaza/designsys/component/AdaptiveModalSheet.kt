package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * A modal bottom sheet that uses the platform's own presentation where one exists.
 *
 * On iOS with native glass enabled this presents a real `UISheetPresentationController`
 * (medium + large detents, grabber, swipe-to-dismiss), hosting the same [content]
 * the fallback would draw. Everywhere else it is exactly [fallback], unchanged.
 *
 * The native sheet is a separate composition. It receives the theme and this module's
 * app-environment locals; a caller that reads its own locals inside [content] must provide them
 * there again.
 */
@Composable
expect fun AdaptiveModalSheet(
    onDismiss: () -> Unit,
    fallback: @Composable () -> Unit,
    containerColor: Color? = null,
    /**
     * Replaces the native medium and large detents with one fitted to the measured [content],
     * scrolling when that is taller than the screen allows. For short forms such as page jump.
     */
    fitContent: Boolean = false,
    content: @Composable () -> Unit,
)
