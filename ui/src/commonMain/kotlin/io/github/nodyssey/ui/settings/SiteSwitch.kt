package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable
import io.github.nodyssey.core.Site

/**
 * Hands back the action that moves the app to another [Site].
 *
 * **Why this is a platform call and not a repository write.** [io.github.nodyssey.core.ActiveSite] is
 * read by the whole graph — the database file's name, every URL, the trusted-host set — and most of
 * those readers cached their answer when they were built. Flipping it under a running graph would
 * leave repositories issuing requests to the new site and writing the rows into the old site's
 * database, for as long as their in-flight coroutines lasted. That window is short, silent, and
 * produces one site's threads under the other's ids, which is the worst way for this to go wrong.
 *
 * Android disposes the navigation and retires its graph before installing the new site. Shared
 * stores remain open and site-specific stores keep their existing filenames. Other platforms may
 * defer the choice until the next launch — see the actuals.
 */
@Composable
expect fun rememberSiteSwitch(): (Site) -> Unit

/**
 * Whether [rememberSiteSwitch] takes effect immediately, or only at the next launch.
 *
 * The historical name is retained for the platform implementations; Android now rebuilds in place.
 */
expect val siteSwitchRestartsApp: Boolean
