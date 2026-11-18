package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.github.nodyssey.data.settings.AppLanguage
import platform.Foundation.NSUserDefaults

/**
 * Writes the chosen override into `AppleLanguages`, which is where iOS stores a per-app language.
 *
 * Unlike Android, Compose Resources on iOS offers no supported way to swap `LocalComposeEnvironment`
 * live inside a running process — see [ProvideAppLanguage]. Setting [ApplyAppLanguage] persists the
 * explicit language (`zh-CN`, `zh-TW`, `en`) for the next launch, and removes `AppleLanguages` when
 * [AppLanguage.SYSTEM] is selected so OS language changes continue to take effect dynamically.
 *
 * Traditional Chinese tags (`zh-Hant`, `zh-Hant-HK`, `zh-HK`, `zh-MO`) under [AppLanguage.SYSTEM]
 * are normalized in memory at launch by `PreferredLanguageGuard` (`IosPreferredLanguages` using
 * `normalizeIosPreferredLanguages`) rather than written into `AppleLanguages`, avoiding any
 * persistent freeze while mapping cleanly onto `values-zh-rTW`.
 */
@Composable
actual fun ApplyAppLanguage(language: AppLanguage?) {
    // Null means the store has not answered yet; see the note on the expect. Removing the key on
    // that first composition would read as 跟随系统 and throw away a choice already on disk.
    if (language == null) return
    LaunchedEffect(language) {
        val defaults = NSUserDefaults.standardUserDefaults
        val tag = language.tag
        if (tag == null) {
            defaults.removeObjectForKey(APPLE_LANGUAGES)
        } else {
            defaults.setObject(listOf(tag), APPLE_LANGUAGES)
        }
    }
}

/** The `NSUserDefaults` key iOS itself reads; the name is the platform's, not ours. */
private const val APPLE_LANGUAGES = "AppleLanguages"
