package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable

/**
 * Tactile confirmation for two moments iOS users expect a physical click at: a switch flip and a
 * pull-to-refresh that commits to a reload. Android and desktop get a no-op — their switches and
 * refresh never had haptics, and adding any would change behaviour there, so the platform hook
 * exists for iOS only.
 */
interface PlatformHaptics {
    /** Selection tick for a user-driven toggle — fire from the row's onClick, not on state change. */
    fun toggle()

    /** Impact for the release that commits a pull-to-refresh — once per release, not per frame. */
    fun refreshTriggered()
}

@Composable
expect fun rememberPlatformHaptics(): PlatformHaptics
