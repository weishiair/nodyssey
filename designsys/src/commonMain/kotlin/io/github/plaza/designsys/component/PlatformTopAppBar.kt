package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/** An operation the platform can render as a real bar button or menu item. */
data class PlatformBarAction(
    val label: String,
    val systemImage: String? = null,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val selected: Boolean = false,
    val badge: Boolean = false,
    val accessibilityLabel: String? = null,
    val menu: List<PlatformBarAction> = emptyList(),
    val onClick: () -> Unit = {},
)

data class PlatformTopAppBar(
    val title: String,
    val subtitle: String?,
    val navigation: PlatformBarAction?,
    val actions: List<PlatformBarAction>,
    val search: PlatformBarSearch? = null,
    val titleAction: (() -> Unit)? = null,
    /** A local mode (selection, search or preview) must close before UIKit pops the page. */
    val allowsNavigationPop: Boolean = true,
)

data class PlatformBarSearch(
    val query: String,
    val placeholder: String,
    val onQueryChange: (String) -> Unit,
    val onSubmit: () -> Unit = {},
    val autoFocus: Boolean = true,
)

/** One host per screen; an outgoing composition must not clear its replacement's bar. */
class PlatformTopAppBarHost(private val onChange: (PlatformTopAppBar?) -> Unit) {
    private val bars = linkedMapOf<Any, PlatformTopAppBar>()
    val current: PlatformTopAppBar? get() = bars.values.lastOrNull()

    fun update(owner: Any, bar: PlatformTopAppBar) {
        val previous = current
        bars[owner] = bar
        if (previous !== current) onChange(current)
    }

    fun release(owner: Any) {
        val previous = current
        bars.remove(owner)
        if (previous !== current) onChange(current)
    }
}

val LocalPlatformTopAppBarHost = staticCompositionLocalOf<PlatformTopAppBarHost?> { null }

@Composable
fun publishPlatformTopAppBar(bar: PlatformTopAppBar): Boolean {
    val host = LocalPlatformTopAppBarHost.current ?: return false
    val owner = remember(host) { Any() }
    DisposableEffect(host, owner) {
        onDispose { host.release(owner) }
    }
    SideEffect { host.update(owner, bar) }
    return true
}
