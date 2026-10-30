package io.github.nodyssey.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 搜索结果头像大小, clamped the same way 表情大小 is.
 *
 * The clamp is the part worth a test: the size feeds a slider whose ends are 20 and 44, and a value
 * outside them — written by a build with a different range, or by a bad store — would leave every
 * result card at a size no control on the screen can walk back.
 */
class SearchAvatarSettingTest {
    @Test
    fun `a size beyond the slider is pulled back to its ends`() =
        runTest {
            val repository = repository()

            repository.setSearchAvatarSize(4)
            assertEquals(SettingsRepository.MIN_SEARCH_AVATAR_DP, repository.settings.first().searchAvatarSize)

            repository.setSearchAvatarSize(400)
            assertEquals(SettingsRepository.MAX_SEARCH_AVATAR_DP, repository.settings.first().searchAvatarSize)
        }

    /** A size some other build wrote is read back inside this build's range, not obeyed. */
    @Test
    fun `a stored size this build cannot offer is clamped on the way out`() =
        runTest {
            val repository = repository()
            dataStore.edit { it[KEY_SEARCH_AVATAR_DP] = 100 }

            assertEquals(SettingsRepository.MAX_SEARCH_AVATAR_DP, repository.settings.first().searchAvatarSize)
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
        val KEY_SEARCH_AVATAR_DP = intPreferencesKey("search_avatar_dp")
    }
}
