@file:OptIn(kotlin.experimental.ExperimentalObjCName::class)

package io.github.nodyssey.ios

import io.github.nodyssey.data.settings.normalizeIosPreferredLanguages
import kotlin.native.ObjCName

/**
 * Exposes [normalizeIosPreferredLanguages] to `App.swift` so launch-time normalization of
 * `NSLocale.preferredLanguages` reuses the shared Traditional Chinese tag rules without writing back
 * to persistent `AppleLanguages`.
 */
object IosPreferredLanguages {
    @ObjCName("normalize")
    fun normalize(
        @ObjCName("languages") preferredLanguages: List<String>,
    ): List<String> = normalizeIosPreferredLanguages(preferredLanguages)
}
