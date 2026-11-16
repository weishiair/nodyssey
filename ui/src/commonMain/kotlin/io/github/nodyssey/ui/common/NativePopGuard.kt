package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Allows the active screen to intercept platform-native interactive pop gestures (such as iOS
 * edge/content swipe-back) before UIKit begins popping its controller.
 */
internal interface NativePopGuard {
    fun register(
        token: Any,
        blocked: () -> Boolean,
        onBlockedAttempt: (() -> Unit)?,
    )

    fun unregister(token: Any)
}

internal class PagePopGuard : NativePopGuard {
    private class Entry(
        val token: Any,
        var blocked: () -> Boolean,
        var onBlockedAttempt: (() -> Unit)?,
    )

    private val entries = mutableListOf<Entry>()

    override fun register(
        token: Any,
        blocked: () -> Boolean,
        onBlockedAttempt: (() -> Unit)?,
    ) {
        val existing = entries.firstOrNull { it.token === token }
        if (existing != null) {
            existing.blocked = blocked
            existing.onBlockedAttempt = onBlockedAttempt
        } else {
            entries += Entry(token, blocked, onBlockedAttempt)
        }
    }

    override fun unregister(token: Any) {
        entries.removeAll { it.token === token }
    }

    /**
     * Checks registered guards from newest to oldest. Returns false and notifies the blocking guard
     * when a pop gesture should not proceed.
     */
    fun shouldAllowPop(): Boolean {
        val blocking = entries.asReversed().firstOrNull { it.blocked() } ?: return true
        blocking.onBlockedAttempt?.invoke()
        return false
    }
}

internal val LocalNativePopGuard = staticCompositionLocalOf<NativePopGuard?> { null }

/**
 * While [blocked] is true on the active screen, prevents iOS native swipe-back from popping it and
 * invokes [onBlockedAttempt] when a swipe-back gesture begins.
 */
@Composable
internal fun NativePopGuardEffect(
    blocked: Boolean,
    onBlockedAttempt: (() -> Unit)? = null,
) {
    val guard = LocalNativePopGuard.current ?: return
    val token = remember { Any() }
    val currentBlocked by rememberUpdatedState(blocked)
    val currentOnBlockedAttempt by rememberUpdatedState(onBlockedAttempt)
    DisposableEffect(guard) {
        guard.register(
            token = token,
            blocked = { currentBlocked },
            onBlockedAttempt = { currentOnBlockedAttempt?.invoke() },
        )
        onDispose { guard.unregister(token) }
    }
}
