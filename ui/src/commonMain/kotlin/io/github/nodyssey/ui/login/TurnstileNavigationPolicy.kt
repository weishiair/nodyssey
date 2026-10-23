package io.github.nodyssey.ui.login

/**
 * Keeps the supplied challenge document in place without blocking Cloudflare's child frames.
 *
 * WebKit asks about the initial `loadHTMLString` as well; Android does not ask about the app's
 * `loadDataWithBaseURL`, so its initial document allowance starts spent. A null target is a new
 * window, which a verification widget never needs.
 */
internal class TurnstileNavigationPolicy(allowInitialDocument: Boolean = true) {
    private var canLoadDocument = allowInitialDocument

    fun allowsNavigation(isMainFrame: Boolean?): Boolean =
        when (isMainFrame) {
            false -> true

            null -> false

            true -> {
                val allowed = canLoadDocument
                canLoadDocument = false
                allowed
            }
        }
}
