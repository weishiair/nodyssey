package io.github.nodyssey.data.settings

import io.github.nodyssey.data.testPreferenceStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OneHandModeSettingsTest {
    @Test
    fun `iOS defaults to disabled without overriding the saved choice`() = runTest {
        val store = testPreferenceStore(backgroundScope)
        val settings = SettingsRepository(store, oneHandModeDefault = false)
        assertFalse(settings.settings.first().oneHandMode)

        settings.setOneHandMode(true)

        val reopened = SettingsRepository(store, oneHandModeDefault = false)
        assertTrue(reopened.settings.first().oneHandMode)
    }

    @Test
    fun `platforms using the existing default still start enabled`() = runTest {
        val settings = SettingsRepository(testPreferenceStore(backgroundScope))
        assertTrue(settings.settings.first().oneHandMode)
    }
}
