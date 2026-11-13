package io.github.nodyssey.ui.common

/** Metadata checks do not read contents; only [readText] may request paste permission. */
internal interface ClipboardUrlSource {
    val changeCount: Long
    val isOwnCopy: Boolean
    val hasText: Boolean

    suspend fun containsWebLink(): Boolean

    fun readText(): String?
}

/** Gates a permission-bearing read on a pattern match for the same, external clipboard item. */
internal class ClipboardUrlReader(private val source: ClipboardUrlSource) {
    private var examinedChangeCount: Long? = null

    suspend fun readNewUrlText(): String? {
        val changeCount = source.changeCount
        if (changeCount == examinedChangeCount) return null
        if (source.isOwnCopy || !source.hasText) {
            examinedChangeCount = changeCount
            return null
        }
        val hasWebLink = source.containsWebLink()
        // Detection suspends. A replacement must earn its own match, not inherit the old one.
        if (source.changeCount != changeCount) return null
        examinedChangeCount = changeCount
        if (!hasWebLink || source.isOwnCopy) return null
        return source.readText()
    }
}
