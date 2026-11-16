package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** The native control's content and current action; the fallback keeps its existing Compose API. */
data class GlassAction(
    val label: String,
    val onClick: () -> Unit,
    val symbol: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val enabled: Boolean = true,
    val prominent: Boolean = false,
    val loading: Boolean = false,
    val selected: Boolean = false,
    val menu: List<GlassMenuAction> = emptyList(),
    /** Small native content for a dense header; the host still owns the touch-target size. */
    val compact: Boolean = false,
)

data class GlassMenuAction(
    val label: String,
    val onClick: () -> Unit,
    val symbol: String? = null,
    val enabled: Boolean = true,
    val selected: Boolean = false,
)

/** Native Liquid Glass on supported iOS, the original component everywhere else. */
@Composable
expect fun AdaptiveGlassButton(
    action: GlassAction,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit,
)

/** One glass group, with native buttons inside it rather than glass stacked on glass. */
@Composable
expect fun AdaptiveGlassToolbar(
    actions: List<GlassAction>,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    fallback: @Composable () -> Unit,
)

/** UIKit owns the navigation bar's appearance, safe button grouping and native menu presentation. */
@Composable
expect fun AdaptiveGlassTopBar(
    title: String,
    titleAction: GlassAction?,
    back: GlassAction?,
    actions: List<GlassAction>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    containerColor: Color = Color.Unspecified,
    fallback: @Composable () -> Unit,
)

/** Native UIKit menu trigger on supported platforms; Compose dropdown fallback everywhere else. */
@Composable
expect fun AdaptiveNativeMenu(
    label: String,
    actions: List<GlassMenuAction>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cooperativeInScroll: Boolean = false,
    fallback: @Composable (expanded: Boolean, onDismiss: () -> Unit) -> Unit,
    trigger: @Composable (onClick: () -> Unit) -> Unit,
)

/** Material's SnackbarHost retains the queue and lifetime; UIKit draws the supported iOS notice. */
@Composable
expect fun AdaptiveGlassNotice(
    message: String,
    action: GlassAction?,
    dismissAction: GlassAction?,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit,
)
