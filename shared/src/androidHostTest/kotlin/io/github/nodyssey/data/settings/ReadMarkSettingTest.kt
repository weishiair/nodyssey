package io.github.nodyssey.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 已读标记's two answers, and the one that is not a value at all.
 *
 * The colour is nullable on purpose — null means 跟随主题 — which makes "forget the colour" a write
 * like any other. Storing it as, say, 0 rather than removing it would paint every marked card
 * transparent black, so the round trip through null is worth a test of its own.
 */
class ReadMarkSettingTest {
    @Test
    fun `a store written before 已读标记 existed fills the card`() =
        runTest {
            assertEquals(ReadMarkStyle.BLOCK, repository().settings.first().readMarkStyle)
        }

    @Test
    fun `the style a reader picked survives a restart`() =
        runTest {
            val repository = repository()
            repository.setReadMarkStyle(ReadMarkStyle.UNREAD_BLOCK)
            assertEquals(ReadMarkStyle.UNREAD_BLOCK, repository.settings.first().readMarkStyle)
        }

    @Test
    fun `a style this build does not know falls back to filling the card`() =
        runTest {
            val repository = repository()
            // A constant this build retired, or a value written by a newer one.
            dataStore.edit { it[KEY_READ_MARK_STYLE] = "DOT" }
            assertEquals(ReadMarkStyle.BLOCK, repository.settings.first().readMarkStyle)
        }

    @Test
    fun `an unpicked colour follows the theme`() =
        runTest {
            assertNull(repository().settings.first().readMarkColor)
        }

    @Test
    fun `a picked colour is kept exactly as it was picked`() =
        runTest {
            val repository = repository()
            repository.setReadMarkColor(MARK)
            assertEquals(MARK, repository.settings.first().readMarkColor)
        }

    @Test
    fun `going back to 跟随主题 drops the stored colour`() =
        runTest {
            val repository = repository()
            // Seeded through the store rather than by two setter calls in a row: what is under test
            // is that the setter *removes* the key, and a second write would add nothing but the
            // question of whether the two happened in order.
            dataStore.edit { it[KEY_READ_MARK_COLOR] = MARK }
            repository.setReadMarkColor(null)
            assertNull(repository.settings.first().readMarkColor)
        }

    private lateinit var dataStore: DataStore<Preferences>

    private fun CoroutineScope.repository(): SettingsRepository {
        val directory =
            Files.createTempDirectory("nodyssey-settings").toFile().apply { deleteOnExit() }
        dataStore =
            PreferenceDataStoreFactory.create(scope = this) {
                File(directory, "settings.preferences_pb")
            }
        return SettingsRepository(dataStore)
    }

    private companion object {
        val KEY_READ_MARK_STYLE = stringPreferencesKey("read_mark_style")
        val KEY_READ_MARK_COLOR = intPreferencesKey("read_mark_color")

        const val MARK = 0xFF2E9E6B.toInt()
    }
}
