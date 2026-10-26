package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable

/** Null on platforms without this Android window control; values are the screen's supported choices. */
@Composable
internal expect fun rememberDisplayRefreshRates(): Set<Int>?

/** Zero leaves scheduling to the system. Implementations must restore the window on disposal. */
@Composable
internal expect fun ApplyDisplayRefreshRate(hertz: Int)
