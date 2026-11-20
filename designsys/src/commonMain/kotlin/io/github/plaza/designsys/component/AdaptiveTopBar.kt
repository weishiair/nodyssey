package io.github.plaza.designsys.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.resources.Res
import io.github.plaza.designsys.resources.top_bar_back
import io.github.plaza.designsys.resources.top_bar_close
import io.github.plaza.designsys.theme.LocalOneHandMode
import io.github.plaza.designsys.theme.LocalPlazaLayers
import org.jetbrains.compose.resources.stringResource

/**
 * One button in an [AdaptiveTopBar].
 *
 * [glass] is what iOS shows natively: its label, SF Symbol, enabled state and menu. Android draws an
 * `IconButton` with [icon] from the same label, click and enabled state. An action whose Android form
 * is more than that — a dropdown with rows of its own — passes that form whole as [compose], which
 * replaces the generated button and leaves the Android screen exactly as it was.
 */
data class TopBarAction(
    val glass: GlassAction,
    val icon: ImageVector? = null,
    val compose: (@Composable () -> Unit)? = null,
)

/**
 * The top bar of an ordinary pushed page, defined once.
 *
 * Android and the desktop draw the [OneHandTopAppBar] these pages always had — an arrow-back
 * `IconButton`, the title and one `IconButton` per action — so a page moved onto this bar renders
 * pixel for pixel as before. On iOS with native controls it is the system navigation bar instead:
 * back chevron, SF Symbols and `UIMenu`. With 单手模式 on it stays the Compose bar, since the reach
 * the blank gives is that mode's whole point and a navigation bar has no blank.
 *
 * A bar whose title is more than text (an avatar, tabs, a search field) is not this component's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveTopBar(
    title: String,
    state: OneHandAppBarState,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    /** A modal page (a composer, a sign-in step) is left with a cross rather than an arrow. */
    closeInsteadOfBack: Boolean = false,
    actions: List<TopBarAction> = emptyList(),
) {
    val backLabel = stringResource(if (closeInsteadOfBack) Res.string.top_bar_close else Res.string.top_bar_back)
    val composeBar: @Composable () -> Unit = {
        OneHandTopAppBar(
            title = title,
            state = state,
            modifier = modifier,
            subtitle = subtitle,
            navigationIcon = onBack?.let { back ->
                {
                    IconButton(onClick = back) {
                        Icon(
                            if (closeInsteadOfBack) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = backLabel,
                        )
                    }
                }
            },
            actions = {
                actions.forEach { action ->
                    val custom = action.compose
                    if (custom != null) {
                        custom()
                    } else {
                        IconButton(onClick = action.glass.onClick, enabled = action.glass.enabled) {
                            Icon(checkNotNull(action.icon) { "${action.glass.label} needs an icon or a compose form" }, action.glass.label)
                        }
                    }
                }
            },
        )
    }
    if (LocalOneHandMode.current) {
        composeBar()
        return
    }
    AdaptiveGlassTopBar(
        title = title,
        titleAction = null,
        back = onBack?.let { GlassAction(backLabel, it, symbol = if (closeInsteadOfBack) "xmark" else "chevron.backward") },
        actions = actions.map { it.glass },
        subtitle = subtitle,
        modifier = modifier
            .fillMaxWidth()
            .background(LocalPlazaLayers.current.page)
            .windowInsetsPadding(TopAppBarDefaults.windowInsets)
            .height(NativeBarHeight),
        fallback = composeBar,
    )
}

/** The post page's native bar height, so every native bar in the app stands the same. */
private val NativeBarHeight = 48.dp
