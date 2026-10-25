package io.github.nodyssey.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import io.github.nodyssey.core.ActiveSite
import io.github.nodyssey.core.Site

/** The Android shell replaces the UI and graph; it owns their lifetimes. */
val LocalSiteSwitch = staticCompositionLocalOf<((Site) -> Unit)?> { null }

@Composable
actual fun rememberSiteSwitch(): (Site) -> Unit {
    val switch = LocalSiteSwitch.current
    val context = LocalContext.current
    return remember(context, switch) {
        { site ->
            // The shell owns disposal and persistence; previews without it only save for next launch.
            if (site != ActiveSite.current) {
                if (switch != null) switch(site) else AndroidActiveSite.store(context, site)
            }
        }
    }
}

/** The Android shell applies the choice immediately without restarting the process or Activity. */
actual val siteSwitchRestartsApp: Boolean = true

/**
 * 站点, as this platform stores and applies it — the same shape, and for the same reason, as
 * [AndroidAppLanguage].
 *
 * **Why `SharedPreferences` and not the settings DataStore.** [install] has to run in
 * `attachBaseContext`, before anything asynchronous can have finished and before the dependency
 * graph exists — and it has to run *that* early because the answer decides which files the graph
 * then opens: the Room database's name and the composer stores' names are built from
 * [Site.storageSuffix]. A suspending `Flow` cannot answer a question asked that early. Unlike 语言,
 * there is no DataStore copy behind this one: nothing reads the site asynchronously, so a second
 * store would be a second source of truth bought for nothing.
 */
object AndroidActiveSite {
    private const val PREFERENCES = "active_site"
    private const val KEY_SITE = "site"

    /**
     * Reads the stored choice and installs it. Called from `attachBaseContext`, once per process.
     *
     * Takes the base context rather than the application: at that point there is no application to
     * take, which is the whole reason this reads a `SharedPreferences` file directly.
     */
    fun install(context: Context) {
        ActiveSite.install(Site.ofStoredValue(preferences(context).getString(KEY_SITE, null)))
    }

    /** Commit before reporting success so the next launch cannot silently select the old site. */
    // The KTX edit extension returns Unit, so it cannot report a failed disk commit to the shell.
    @SuppressLint("UseKtx")
    fun store(context: Context, site: Site): Boolean =
        preferences(context).edit().putString(KEY_SITE, site.storedValue).commit()
    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
