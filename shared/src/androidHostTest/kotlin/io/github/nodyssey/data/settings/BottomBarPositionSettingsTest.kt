package io.github.nodyssey.data.settings

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.nodyssey.data.testPreferenceStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomBarPositionSettingsTest {
    @Test
    fun `fresh installs default to glass chrome with a floating medium bar`() = runTest {
        val store = testPreferenceStore(backgroundScope)
        val initial = SettingsRepository(store).settings.first()
        assertTrue(initial.frostedGlass)
        assertTrue(initial.effectiveFrostedGlass)
        assertEquals(BottomBarStyle.FLOATING, initial.bottomBarStyle)
        assertEquals(BottomBarPosition.MEDIUM, initial.bottomBarPosition)
    }

    @Test
    fun `standard bottom bar disables effective glass without erasing the stored preference`() = runTest {
        val store = testPreferenceStore(backgroundScope)
        val settings = SettingsRepository(store)

        settings.setBottomBarStyle(BottomBarStyle.STANDARD)
        val standard = SettingsRepository(store).settings.first()
        assertTrue(standard.frostedGlass)
        assertFalse(standard.effectiveFrostedGlass)

        settings.setBottomBarStyle(BottomBarStyle.FLOATING)
        val floating = SettingsRepository(store).settings.first()
        assertTrue(floating.effectiveFrostedGlass)
    }

    @Test
    fun `a saved bottom bar style survives the glass switch and unknown values fall back to floating`() = runTest {
        val store = testPreferenceStore(backgroundScope)
        val settings = SettingsRepository(store)

        for (style in BottomBarStyle.entries) {
            settings.setBottomBarStyle(style)
            settings.setFrostedGlass(false)
            assertEquals(style, SettingsRepository(store).settings.first().bottomBarStyle)
            settings.setFrostedGlass(true)
            assertEquals(style, SettingsRepository(store).settings.first().bottomBarStyle)
        }

        store.edit { it[stringPreferencesKey("bottom_bar_style")] = "UNKNOWN" }
        assertEquals(BottomBarStyle.FLOATING, SettingsRepository(store).settings.first().bottomBarStyle)
    }

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
