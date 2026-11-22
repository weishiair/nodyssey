package io.github.plaza.designsys.component

import androidx.compose.foundation.layout.WindowInsets
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
    /** A note above the menu's entries, for what the fallback menu says under or over its list. */
    val menuTitle: String? = null,
    /** Small native content for a dense header; the host still owns the touch-target size. */
    val compact: Boolean = false,
    /** A red reminder dot on the native button, for what the fallback draws as an update dot. */
    val badge: Boolean = false,
)

data class GlassMenuAction(
    val label: String,
    val onClick: () -> Unit,
    val symbol: String? = null,
    /** A second, smaller line under the label, for what the fallback menu shows beneath an entry. */
    val subtitle: String? = null,
    val enabled: Boolean = true,
    val selected: Boolean = false,
    /** Drawn in the system's destructive red, for what the fallback menu colours as an error. */
    val destructive: Boolean = false,
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
    /** A second line under the title, the system's own navigation subtitle. */
    subtitle: String? = null,
    /** Apply system blur when the caller lays scrolling content behind this bar. */
    blurBackground: Boolean = false,
    /**
     * Insets the bar's buttons and title inside [modifier]'s area, which the background then fills.
     * Pass the status bar so one blur covers it and the bar together rather than meeting at a seam.
     */
    barInsets: WindowInsets? = null,
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
