package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
    val menu: List<GlassMenuAction> = emptyList(),
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

/** UIKit owns the navigation bar's glass, safe button grouping and native menu presentation. */
@Composable
expect fun AdaptiveGlassTopBar(
    title: String,
    titleAction: GlassAction?,
    back: GlassAction?,
    actions: List<GlassAction>,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit,
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
