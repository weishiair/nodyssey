package io.github.nodyssey.data

import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import io.github.nodyssey.core.Site
import io.github.nodyssey.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MessageDraftStoreTest {
    private val directory = Files.createTempDirectory("message-drafts").toFile().apply { deleteOnExit() }

    @Test
    fun `removing a saved account blocks its late writes while the active account can keep drafting`() = runTest {
        val store = settings(backgroundScope).messageDrafts
        val removed = store.open(11, 42)
        val active = store.open(22, 42, accountUid = 200)
        store.save(removed, MessageDraft(text = "remove this account"))
        store.save(active, MessageDraft(text = "keep this account"))
        store.clearAccount(11, 100)
        store.save(removed, MessageDraft(text = "late cleanup must not resurrect this"))
        store.save(active, MessageDraft(text = "still editing the active account"))
        assertFalse(store.open(11, 42).initial.hasContent)
        assertEquals("still editing the active account", store.open(22, 42, accountUid = 200).initial.text)
    }

    @Test
    fun `text selection quotes and recoverable failures survive closing the store`() = runTest {
        val firstRun = CoroutineScope(coroutineContext + Job())
        val firstStore = settings(firstRun).messageDrafts
        val quote = MessageDraftBubble("q", false, "[链接](/post-1-1)\n第二行", true, 100L, null)
        val pending = MessageDraftBubble("p", true, "未发送", true, 200L, null, MessageFailure(MessageRecovery.VERIFY, verificationUrl = "https://www.nodeseek.com/api/notification/message/send"))
        val draft = MessageDraft("peer", "明天再说😀", 2, 5, listOf(quote), listOf(pending), 300L)
        firstStore.save(firstStore.open(11, 42), draft)
        firstRun.coroutineContext[Job]!!.cancelAndJoin()

        val restored = settings(backgroundScope).messageDrafts.open(11, 42).initial
        assertEquals(draft, restored)
    }

    @Test
    fun `clearing one conversation cannot remove another recipient session or site`() = runTest {
        val preferences = preferences(backgroundScope)
        val store = SettingsRepository(preferences, site = Site.NODESEEK).messageDrafts
        val otherSite = SettingsRepository(preferences, site = Site.DEEPFLOOD).messageDrafts
        store.save(store.open(11, 42), MessageDraft(text = "first peer"))
        store.save(store.open(11, 43), MessageDraft(text = "second peer"))
        store.save(store.open(12, 42), MessageDraft(text = "other account"))
        otherSite.save(otherSite.open(11, 42), MessageDraft(text = "other site"))

        store.save(store.open(11, 42), MessageDraft())

        assertEquals(setOf(43L), store.drafts(11).first().keys)
        assertEquals("other account", store.open(12, 42).initial.text)
        assertEquals("other site", otherSite.open(11, 42).initial.text)
        store.clear()
        assertEquals("other site", otherSite.open(11, 42).initial.text)
    }

    @Test
    fun `logout invalidates late saves from pages that were already open`() = runTest {
        val store = settings(backgroundScope).messageDrafts
        val oldPage = store.open(11, 42)
        store.save(oldPage, MessageDraft(text = "private text"))

        store.clear()
        store.save(oldPage, MessageDraft(text = "late cleanup must not resurrect it"))

        assertTrue(store.drafts(11).first().isEmpty())
        val newPage = store.open(11, 42)
        store.save(newPage, MessageDraft(text = "a new session can save"))
        assertEquals("a new session can save", store.open(11, 42).initial.text)
    }

    @Test
    fun `renewed cookies recover every conversation only for the same verified account`() = runTest {
        val store = settings(backgroundScope).messageDrafts
        val first = store.open(11, 42, accountUid = 100)
        val second = store.open(11, 43, accountUid = 100)
        store.save(first, MessageDraft(text = "first conversation", savedAtMillis = 100))
        store.save(second, MessageDraft(text = "second conversation", savedAtMillis = 200))

        assertFalse(store.open(22, 42, accountUid = 200).initial.hasContent)
        val resumed = store.open(33, 42, accountUid = 100)
        assertEquals("first conversation", resumed.initial.text)
        assertEquals(setOf(42L, 43L), store.drafts(33).first().keys)
        assertTrue(store.drafts(11).first().isEmpty())
        // The old entry may still be closing after the renewed session has removed its outbox.
        store.save(resumed, MessageDraft())
        store.save(first, MessageDraft(text = "must not resurrect"))
        assertFalse(store.open(44, 42, accountUid = 100).initial.hasContent)
        assertEquals("second conversation", store.open(44, 43, accountUid = 100).initial.text)
    }

    private fun settings(scope: CoroutineScope) = SettingsRepository(
        preferences(scope),
    )

    private fun preferences(scope: CoroutineScope) = PreferenceDataStoreFactory.create(
        storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { File(directory, "settings.preferences_pb").toOkioPath() },
        scope = scope,
    )
}
