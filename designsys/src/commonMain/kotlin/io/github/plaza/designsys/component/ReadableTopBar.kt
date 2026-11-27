package io.github.plaza.designsys.component

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import io.github.plaza.designsys.theme.LocalReadableWidthSpec
import io.github.plaza.designsys.theme.Sizes

/**
 * A full-width Material top bar whose contents sit over the column below it rather than in the
 * window's corners.
 *
 * On a tablet the page's content is held to [LocalReadableWidthSpec] (defaulting to
 * [Sizes.readableContentWidth]) and centred, and a bar left at full width put its back arrow and
 * its actions a hand's width outside that column — a gear in the far corner of an otherwise
 * centred page. The bar still spans the window, so its colour (and the tone it lifts to on scroll)
 * is unchanged; only the [WindowInsets] it lays its contents inside grow by the margin either
 * side. On a phone, narrower than the column, that margin is zero.
 *
 * Measured from the slot it is given rather than from the window, so a bar in one half of a split
 * lines up with its own half.
 *
 * @param maxContentWidth explicit cap, or [Dp.Unspecified] to follow [LocalReadableWidthSpec].
 * @param bar the bar itself, which must pass the insets on as its `windowInsets`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadableTopBar(
    maxContentWidth: Dp = Dp.Unspecified,
    bar: @Composable (windowInsets: WindowInsets) -> Unit,
) {
    val spec = LocalReadableWidthSpec.current
    val insets = TopAppBarDefaults.windowInsets
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val safeWidth = with(density) {
            val horizontalInsets = insets.getLeft(this, direction) + insets.getRight(this, direction)
            (maxWidth - horizontalInsets.toDp()).coerceAtLeast(0.dp)
        }
        val targetWidth =
            if (maxContentWidth.isSpecified) {
                safeWidth.coerceAtMost(maxContentWidth)
            } else {
                spec.resolveWidth(safeWidth, maxHeight)
            }
        val side = ((safeWidth - targetWidth) / 2).coerceAtLeast(0.dp)
        bar(insets.add(WindowInsets(left = side, right = side)))
    }
}
