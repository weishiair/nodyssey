package io.github.nodyssey.data.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which of the two Chinese bundles a device's own language tag asks for.
 *
 * The consequence of getting this wrong is not a fallback but the wrong language: [isTraditionalChineseTag]
 * is what a Traditional reader on `zh-Hant-HK` depends on to reach `values-zh-rTW` at all, and what
 * keeps every Simplified reader out of it. The function's own note has the resolution rule behind that.
 */
class AppLanguageTest {

    /**
     * All regions and scripts that resolve to Traditional Chinese, including tags carrying `@`
     * locale extensions (such as `zh-Hant-MO@calendar=gregorian`).
     */
    @Test
    fun `traditional regions and locale extensions are recognised`() {
        assertTrue(isTraditionalChineseTag("zh-TW"))
        assertTrue(isTraditionalChineseTag("zh-HK"))
        assertTrue(isTraditionalChineseTag("zh-MO"))
        assertTrue(isTraditionalChineseTag("zh-Hant-TW"))
        assertTrue(isTraditionalChineseTag("zh-Hant-HK"))
        assertTrue(isTraditionalChineseTag("zh-Hant-MO@calendar=gregorian"))
        assertTrue(isTraditionalChineseTag("zh-MO@calendar=gregorian"))
        assertTrue(isTraditionalChineseTag("zh-TW@calendar=roc"))
    }

    /** A bare script with no region — what a device set to 繁體中文 with no country reports. */
    @Test
    fun `the script alone is enough`() {
        assertTrue(isTraditionalChineseTag("zh-Hant"))
    }

    @Test
    fun `simplified tags are not traditional`() {
        assertFalse(isTraditionalChineseTag("zh"))
        assertFalse(isTraditionalChineseTag("zh-CN"))
        assertFalse(isTraditionalChineseTag("zh-Hans-CN"))
        assertFalse(isTraditionalChineseTag("zh-SG"))
        assertFalse(isTraditionalChineseTag("zh-Hans-TW@calendar=gregorian"))
    }

    /**
     * A stated script wins over a region that disagrees with it.
     *
     * `zh-Hans-HK` is a real setting — a Simplified reader living in Hong Kong — and reading it off
     * the region alone would hand them the wrong bundle.
     */
    @Test
    fun `an explicit simplified script beats a traditional region`() {
        assertFalse(isTraditionalChineseTag("zh-Hans-HK"))
        assertFalse(isTraditionalChineseTag("zh-Hans-HK@calendar=gregorian"))
    }

    /** Underscores are what a JVM `Locale.toString()` hands over, and they have to parse the same. */
    @Test
    fun `underscores separate subtags too`() {
        assertTrue(isTraditionalChineseTag("zh_TW"))
        assertFalse(isTraditionalChineseTag("zh_CN"))
    }

    /** Case is not part of the answer: BCP 47 is case-insensitive and platforms disagree about it. */
    @Test
    fun `subtag case does not matter`() {
        assertTrue(isTraditionalChineseTag("ZH-hant-tw"))
        assertFalse(isTraditionalChineseTag("ZH-hans-cn"))
    }

    /** Everything that is not Chinese, including the tags that merely start with the letters. */
    @Test
    fun `other languages are never traditional chinese`() {
        assertFalse(isTraditionalChineseTag("en-US"))
        assertFalse(isTraditionalChineseTag("ja-JP"))
        assertFalse(isTraditionalChineseTag(""))
        assertFalse(isTraditionalChineseTag("zho-Hant"))
    }

    /** A store written by an older build has no entry at all, and a bad one must not throw. */
    @Test
    fun `an unknown stored name reads as follow the system`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.ofName(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.ofName(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.ofName("KLINGON"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.ofName("ENGLISH"))
    }

    /**
     * A leading tag that is not an exact `zh-TW` (including iOS Taiwan's `zh-Hant-TW`,
     * `zh-Hant-HK`, `zh-HK`, `zh-Hant`, `zh-Hant-MO@calendar=gregorian`, and `zh-TW@calendar=roc`)
     * is narrowed to `zh-TW` so `values-zh-rTW` always matches.
     */
    @Test
    fun `traditional leads other than exact zh-TW are narrowed to zh-TW`() {
        assertEquals(
            listOf("zh-TW"),
            narrowedForTraditionalChinese(listOf("zh-Hant-TW")),
        )
        assertEquals(
            listOf("zh-TW", "en-US"),
            narrowedForTraditionalChinese(listOf("zh-Hant-TW", "en-US")),
        )
        assertEquals(
            listOf("zh-TW", "en-HK"),
            narrowedForTraditionalChinese(listOf("zh-Hant-HK", "en-HK")),
        )
        assertEquals(listOf("zh-TW"), narrowedForTraditionalChinese(listOf("zh-HK")))
        assertEquals(listOf("zh-TW"), narrowedForTraditionalChinese(listOf("zh-MO")))
        assertEquals(listOf("zh-TW"), narrowedForTraditionalChinese(listOf("zh-Hant")))
        assertEquals(
            listOf("zh-TW"),
            narrowedForTraditionalChinese(listOf("zh-Hant-MO@calendar=gregorian")),
        )
        assertEquals(
            listOf("zh-TW"),
            narrowedForTraditionalChinese(listOf("zh-TW@calendar=roc")),
        )
    }

    /** A `zh-TW` further down the list is kept once — the narrowed head replaces the lead, not adds. */
    @Test
    fun `narrowing does not repeat a zh-TW already in the list`() {
        assertEquals(
            listOf("zh-TW", "en"),
            narrowedForTraditionalChinese(listOf("zh-Hant-HK", "zh-TW", "en")),
        )
        assertEquals(
            listOf("zh-TW", "en"),
            narrowedForTraditionalChinese(listOf("zh-Hant-TW", "zh_tw", "en")),
        )
    }

    /** Only the first entry decides, and exact `zh-TW` or non-Traditional leads are left alone. */
    @Test
    fun `lists that need no narrowing answer null`() {
        assertNull(narrowedForTraditionalChinese(listOf("zh-TW")))
        assertNull(narrowedForTraditionalChinese(listOf("zh_tw")))
        assertNull(narrowedForTraditionalChinese(listOf("ZH-TW")))
        assertNull(narrowedForTraditionalChinese(listOf("zh-Hans-HK")))
        assertNull(narrowedForTraditionalChinese(listOf("zh-Hans-TW@calendar=gregorian")))
        assertNull(narrowedForTraditionalChinese(listOf("zh-CN")))
        assertNull(narrowedForTraditionalChinese(listOf("en-US")))
        assertNull(narrowedForTraditionalChinese(emptyList()))
        assertNull(narrowedForTraditionalChinese(listOf("en-US", "zh-Hant-HK")))
    }
}
