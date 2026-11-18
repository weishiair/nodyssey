package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable
import io.github.nodyssey.data.settings.AppLanguage

/**
 * Nothing to provide inside composition on iOS.
 *
 * Compose Resources keeps `LocalComposeEnvironment` internal and resolves strings from
 * `NSLocale.preferredLanguages` at startup, with no configuration handle to invalidate mid-process.
 * Changing language takes effect on the next launch via [ApplyAppLanguage], while Traditional
 * Chinese system tags (`zh-Hant`, `zh-Hant-HK`, `zh-HK`, `zh-MO`) are mapped to `zh-TW` in memory
 * at process start (`PreferredLanguageGuard` / `normalizeIosPreferredLanguages`) without freezing
 * `AppleLanguages`.
 */
@Composable
actual fun ProvideAppLanguage(
    language: AppLanguage?,
    content: @Composable () -> Unit,
) = content()

actual val appLanguageAppliesOnRestart: Boolean = true
