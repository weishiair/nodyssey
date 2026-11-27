package io.github.plaza.designsys.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.resources.Res
import io.github.plaza.designsys.resources.top_bar_back
import io.github.plaza.designsys.resources.top_bar_close
import io.github.plaza.designsys.theme.LocalOneHandMode
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.nativeControls
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
                    TopBarBackButton(back, closeInsteadOfBack, backLabel)
                }
            },
            actions = { TopBarActions(actions) },
        )
    }
    if (LocalOneHandMode.current || !nativeControls) {
        composeBar()
        return
    }
    ReadableTopBar { barInsets ->
        val statusBarHeight = with(LocalDensity.current) { barInsets.getTop(this).toDp() }
        AdaptiveGlassTopBar(
            title = title,
            titleAction = null,
            back = onBack?.let { backGlassAction(it, closeInsteadOfBack, backLabel) },
            actions = actions.map { it.glass },
            subtitle = subtitle,
            modifier = modifier
                .fillMaxWidth()
                .background(LocalPlazaLayers.current.page)
                .height(statusBarHeight + NativeBarHeight),
            barInsets = barInsets,
            fallback = composeBar,
        )
    }
}

/**
 * The same contract for a pushed page whose bar never had a one-hand blank: what these pages drew
 * was a plain `TopAppBar`, often inside [ReadableTopBar] so its contents kept to the page's column.
 *
 * Android and the desktop draw that same bar — pass [containerColor] what the old `colors` used and
 * [maxContentWidth] what the [ReadableTopBar] wrapped it at (or leave it [Dp.Unspecified] to follow
 * [io.github.plaza.designsys.theme.LocalReadableWidthSpec], or `null` to span full width). On iOS
 * it is the system navigation bar, always: there is no blank for one-hand mode to protect, so this
 * overload is not gated on [LocalOneHandMode]. An empty [title] leaves Android's title slot empty,
 * and an action whose Android icon was drawn at a non-default size goes through
 * [TopBarAction.compose] like any other custom form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /** A modal page (a composer, a sign-in step) is left with a cross rather than an arrow. */
    closeInsteadOfBack: Boolean = false,
    actions: List<TopBarAction> = emptyList(),
    containerColor: Color? = null,
    maxContentWidth: Dp? = Dp.Unspecified,
) {
    val backLabel = stringResource(if (closeInsteadOfBack) Res.string.top_bar_close else Res.string.top_bar_back)
    val composeBar: @Composable () -> Unit = {
        val bar: @Composable (WindowInsets) -> Unit = { insets ->
            TopAppBar(
                title = { if (title.isNotEmpty()) Text(title) },
                modifier = modifier,
                navigationIcon = onBack?.let { back ->
                    {
                        TopBarBackButton(back, closeInsteadOfBack, backLabel)
                    }
                } ?: {},
                actions = { TopBarActions(actions) },
                windowInsets = insets,
                colors =
                containerColor?.let {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = it,
                        scrolledContainerColor = it,
                    )
                } ?: TopAppBarDefaults.topAppBarColors(),
            )
        }
        if (maxContentWidth != null) {
            ReadableTopBar(maxContentWidth) { insets -> bar(insets) }
        } else {
            bar(TopAppBarDefaults.windowInsets)
        }
    }
    if (!nativeControls) {
        composeBar()
        return
    }
    val nativeBar: @Composable (WindowInsets) -> Unit = { barInsets ->
        val statusBarHeight = with(LocalDensity.current) { barInsets.getTop(this).toDp() }
        AdaptiveGlassTopBar(
            title = title,
            titleAction = null,
            back = onBack?.let { backGlassAction(it, closeInsteadOfBack, backLabel) },
            actions = actions.map { it.glass },
            modifier = modifier
                .fillMaxWidth()
                .background(containerColor ?: LocalPlazaLayers.current.page)
                .height(statusBarHeight + NativeBarHeight),
            barInsets = barInsets,
            fallback = composeBar,
        )
    }
    if (maxContentWidth != null) {
        ReadableTopBar(maxContentWidth, bar = nativeBar)
    } else {
        nativeBar(TopAppBarDefaults.windowInsets)
    }
}

@Composable
private fun TopBarBackButton(onBack: () -> Unit, closeInsteadOfBack: Boolean, label: String) {
    IconButton(onClick = onBack) {
        PlazaIcon(
            if (closeInsteadOfBack) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = label,
        )
    }
}

@Composable
private fun TopBarActions(actions: List<TopBarAction>) {
    actions.forEach { action ->
        val custom = action.compose
        if (custom != null) {
            custom()
        } else {
            IconButton(onClick = action.glass.onClick, enabled = action.glass.enabled) {
                PlazaIcon(
                    checkNotNull(action.icon) { "${action.glass.label} needs an icon or a compose form" },
                    action.glass.label,
                )
            }
        }
    }
}

private fun backGlassAction(onBack: () -> Unit, closeInsteadOfBack: Boolean, label: String) =
    GlassAction(label, onBack, symbol = if (closeInsteadOfBack) "xmark" else "chevron.backward")

/** The post page's native bar height, so every native bar in the app stands the same. */
private val NativeBarHeight = 48.dp
