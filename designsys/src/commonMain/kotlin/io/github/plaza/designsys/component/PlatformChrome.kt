package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** Native chrome follows the OS appearance and never uses the Android glass renderer. */
expect val usesNativeChrome: Boolean

/** App-owned values only: a presentation must get its own focus, insets and lifecycle owners. */
val LocalPlatformPresentationValues = staticCompositionLocalOf<@Composable () -> Array<ProvidedValue<*>>> { { emptyArray() } }

/** Returns true when the platform drew the button, including its native menu when supplied. */
@Composable
expect fun platformActionButton(
    action: PlatformBarAction,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
    prominent: Boolean = false,
): Boolean

@Composable
expect fun platformActionToolbar(actions: List<PlatformBarAction>, modifier: Modifier = Modifier): Boolean
