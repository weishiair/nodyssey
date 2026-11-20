package io.github.nodyssey.ios

import io.github.nodyssey.data.settings.narrowedForTraditionalChinese
import platform.Foundation.NSArgumentDomain
import platform.Foundation.NSUserDefaults

/**
 * Narrows a Traditional Chinese device language to `zh-TW` for this process, before anything reads
 * it. Compose Resources resolves a bundle from `NSLocale.preferredLanguages` alone, and a `zh-Hant-HK`
 * or `zh-MO` reader would otherwise get the Simplified default; see [narrowedForTraditionalChinese].
 *
 * The list goes into the volatile argument domain — where `-AppleLanguages` launch arguments live —
 * rather than the app's persistent `AppleLanguages`, which `ApplyAppLanguage` owns: written there it
 * would outlive the process and freeze 跟随系统 at whatever the device said today. An explicit in-app
 * choice is already in the persistent domain as one of the app's own tags, so the merged list leads
 * with it and nothing here applies. Delete with `isTraditionalChineseTag` the day Compose Resources
 * can alias one bundle to several qualifiers.
 */
internal fun narrowLaunchLanguage() {
    val defaults = NSUserDefaults.standardUserDefaults
    val preferred = defaults.arrayForKey(APPLE_LANGUAGES)?.mapNotNull { it as? String } ?: return
    val narrowed = narrowedForTraditionalChinese(preferred) ?: return
    val arguments = defaults.volatileDomainForName(NSArgumentDomain).toMutableMap()
    arguments[APPLE_LANGUAGES] = narrowed
    defaults.setVolatileDomain(arguments, forName = NSArgumentDomain)
}

/** The key iOS reads the language list from; the name is the platform's. */
private const val APPLE_LANGUAGES = "AppleLanguages"
