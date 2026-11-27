package io.github.nodyssey.data.settings

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.nodyssey.data.testPreferenceStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PadLayoutModeSettingsTest {
    @Test
    fun `fresh installs default to split and unknown stored values fall back to split`() = runTest {
        val store = testPreferenceStore(backgroundScope)
        val settings = SettingsRepository(store)
        assertEquals(PadLayoutMode.SPLIT, settings.settings.first().padLayoutMode)

        settings.setPadLayoutMode(PadLayoutMode.SINGLE)
        assertEquals(PadLayoutMode.SINGLE, SettingsRepository(store).settings.first().padLayoutMode)

        store.edit { it[stringPreferencesKey("pad_layout_mode")] = "UNKNOWN" }
        assertEquals(PadLayoutMode.SPLIT, SettingsRepository(store).settings.first().padLayoutMode)
    }
}
