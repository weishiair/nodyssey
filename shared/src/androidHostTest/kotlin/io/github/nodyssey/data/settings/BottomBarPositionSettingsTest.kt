package io.github.nodyssey.data.settings

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.nodyssey.data.testPreferenceStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BottomBarPositionSettingsTest {
    @Test
    fun `existing installs default to medium and a saved position survives the glass switch`() = runTest {
        val store = testPreferenceStore(backgroundScope)
        val settings = SettingsRepository(store)
        assertEquals(BottomBarPosition.MEDIUM, settings.settings.first().bottomBarPosition)

        for (position in BottomBarPosition.entries) {
            settings.setBottomBarPosition(position)
            settings.setFrostedGlass(true)
            assertEquals(position, SettingsRepository(store).settings.first().bottomBarPosition)
            settings.setFrostedGlass(false)
            assertEquals(position, SettingsRepository(store).settings.first().bottomBarPosition)
        }
    }

    @Test
    fun `reading an unknown position falls back to medium`() = runTest {
        val store = testPreferenceStore(backgroundScope)
        store.edit { it[stringPreferencesKey("bottom_bar_position")] = "UNKNOWN" }

        assertEquals(BottomBarPosition.MEDIUM, SettingsRepository(store).settings.first().bottomBarPosition)
    }
}
