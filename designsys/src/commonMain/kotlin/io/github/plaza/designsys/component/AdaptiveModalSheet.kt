package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * A modal bottom sheet that uses the platform's own presentation where one exists.
 *
 * On iOS with native glass enabled this presents a real `UISheetPresentationController`
 * (medium + large detents, grabber, swipe-to-dismiss), hosting the same [content]
 * the fallback would draw. Everywhere else it is exactly [fallback], unchanged.
 */
@Composable
expect fun AdaptiveModalSheet(
    onDismiss: () -> Unit,
    fallback: @Composable () -> Unit,
    containerColor: Color? = null,
    content: @Composable () -> Unit,
)
