package io.github.nodyssey.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.github.nodyssey.core.Site
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteSettingsIsolationTest {
    @Test
    fun `board arrangements and alert baselines remain independent across repeated switches`() = runTest {
        val store = MemoryPreferences()
        val ns = SettingsRepository(store, site = Site.NODESEEK)
        ns.setHomeBoardArrangement(listOf("daily", "trade"), setOf("trade"))
        ns.setHomeBoardHidden("tech", true)
        ns.setKeywordAlertBaseline(700_000)
        val df = SettingsRepository(store, site = Site.DEEPFLOOD)
        assertTrue(df.settings.first().homeBoardOrder.isEmpty())
        assertEquals(null, df.keywordAlertBaseline())
        df.setHomeBoardArrangement(listOf("ai", "sandbox"), setOf("sandbox"))
        df.setHomeBoardHidden("ai", true)
        df.setKeywordAlertBaseline(40_000)
        val nsAgain = SettingsRepository(store, site = Site.NODESEEK)
        assertEquals(listOf("daily", "trade"), nsAgain.settings.first().homeBoardOrder)
        assertEquals(setOf("trade"), nsAgain.settings.first().disabledHomeBoards)
        assertEquals(700_000L, nsAgain.keywordAlertBaseline())
        val dfAgain = SettingsRepository(store, site = Site.DEEPFLOOD)
        assertEquals(listOf("ai", "sandbox"), dfAgain.settings.first().homeBoardOrder)
        assertEquals(40_000L, dfAgain.keywordAlertBaseline())
        // Global appearance and networking choices still belong to the app, not to a forum.
        ns.setFontScale(1.2f)
        assertEquals(1.2f, dfAgain.settings.first().fontScale)
    }

    private class MemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        private val mutex = Mutex()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
            transform(data.value).also { data.value = it }
        }
    }
}
