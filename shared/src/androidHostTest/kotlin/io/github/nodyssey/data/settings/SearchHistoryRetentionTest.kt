package io.github.nodyssey.data.settings

import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import io.github.nodyssey.model.SearchHistoryEntry
import io.github.nodyssey.model.SearchTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SearchHistoryRetentionTest {
    private fun settings(scope: CoroutineScope): SettingsRepository {
        val dir = Files.createTempDirectory("search-retention").toFile().apply { deleteOnExit() }
        return SettingsRepository(
            PreferenceDataStoreFactory.create(
                storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { File(dir, "settings.preferences_pb").toOkioPath() },
                scope = scope,
            ),
        )
    }

    @Test
    fun `posts and users each retain a full independent history`() = runTest {
        val settings = settings(backgroundScope)
        repeat(120) { settings.addSearchHistory(SearchHistoryEntry("post $it", SearchTarget.POSTS)) }
        repeat(110) { settings.addSearchHistory(SearchHistoryEntry("user $it", SearchTarget.USERS)) }
        val history = settings.settings.first().searchHistory
        assertEquals((109 downTo 10).map { "user $it" }, history.filter { it.target == SearchTarget.USERS }.map { it.query })
        assertEquals((119 downTo 20).map { "post $it" }, history.filter { it.target == SearchTarget.POSTS }.map { it.query })
    }

    @Test
    fun `lowering the cap trims both categories and repeated searches move to the front`() = runTest {
        val settings = settings(backgroundScope)
        repeat(70) {
            settings.addSearchHistory(SearchHistoryEntry("post $it", SearchTarget.POSTS))
            settings.addSearchHistory(SearchHistoryEntry("user $it", SearchTarget.USERS))
        }
        settings.setSearchHistoryLimit(50)
        settings.addSearchHistory(SearchHistoryEntry(" post 30 ", SearchTarget.POSTS))
        val history = settings.settings.first().searchHistory
        assertEquals(100, history.size)
        assertEquals("post 30", history.first().query)
        assertEquals(1, history.count { it.query == "post 30" })
        assertFalse(history.any { it.query == "post 19" || it.query == "user 19" })
        settings.clearSearchHistory(SearchTarget.POSTS)
        assertEquals(50, settings.settings.first().searchHistory.size)
    }

    @Test
    fun `increasing the cap survives repository recreation without losing older searches`() = runTest {
        val dir = Files.createTempDirectory("search-retention").toFile().apply { deleteOnExit() }
        val store = PreferenceDataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { File(dir, "settings.preferences_pb").toOkioPath() },
            scope = backgroundScope,
        )
        val settings = SettingsRepository(store)
        settings.setSearchHistoryLimit(200)
        repeat(150) { settings.addSearchHistory(SearchHistoryEntry("post $it", SearchTarget.POSTS)) }
        val reopened = SettingsRepository(store).settings.first()
        assertEquals(200, reopened.searchHistoryLimit)
        assertEquals(150, reopened.searchHistory.size)
        assertEquals("post 0", reopened.searchHistory.last().query)
    }
}
