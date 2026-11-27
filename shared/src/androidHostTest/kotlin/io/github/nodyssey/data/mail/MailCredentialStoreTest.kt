package io.github.nodyssey.data.mail

import io.github.nodyssey.data.security.SecretCipher
import io.github.nodyssey.data.testPreferenceStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MailCredentialStoreTest {
    @Test
    fun `draft edits replace the same draft and a different credential cannot read it`() = runTest {
        val store = testPreferenceStore(backgroundScope, "mail-drafts")
        val cipher = object : SecretCipher {
            override fun encrypt(plaintext: String) = "encrypted:$plaintext"

            override fun decrypt(stored: String) = stored.removePrefix("encrypted:")
        }
        val credentials = DataStoreMailCredentialStore(store, cipher)
        credentials.replace("sk_live_first")
        val earlier = SavedMailDraft("first", MailDraft(to = "one@example.org", body = "Original"), savedAt = 1)
        val another = SavedMailDraft("second", MailDraft(subject = "Another draft"), savedAt = 2)
        val edited = earlier.copy(draft = earlier.draft.copy(body = "Edited"), savedAt = 3)
        credentials.saveDraft(earlier)
        credentials.saveDraft(another)
        credentials.saveDraft(edited)

        val reopened = DataStoreMailCredentialStore(store, cipher)
        reopened.replace("sk_live_first")
        assertEquals(listOf(edited, another), reopened.readDrafts())
        reopened.deleteDraft(another.id)
        assertEquals(listOf(edited), reopened.readDrafts())

        reopened.replace("sk_live_other")
        assertEquals(emptyList(), reopened.readDrafts())
        reopened.expire()
        reopened.replace("sk_live_first")
        assertEquals(listOf(edited), reopened.readDrafts())
        reopened.clear()
        reopened.replace("sk_live_first")
        assertEquals(emptyList(), reopened.readDrafts())
    }

    @Test
    fun `replacing a credential atomically clears identity scoped mailbox state`() = runTest {
        val store = testPreferenceStore(backgroundScope, "mail-credential-replace")
        val credentials = DataStoreMailCredentialStore(
            store,
            object : SecretCipher {
                override fun encrypt(plaintext: String) = "encrypted:$plaintext"

                override fun decrypt(stored: String) = stored.removePrefix("encrypted:")
            },
        )
        credentials.save("sk_live_previous")
        credentials.saveDefaultMailboxId(9)
        credentials.saveNotificationCursor(9, 21)
        credentials.saveNotificationCursor(null, 25)

        credentials.replace("sk_live_replacement")

        assertEquals("sk_live_replacement", credentials.read())
        assertEquals(null, credentials.readDefaultMailboxId())
        assertEquals(null, credentials.readNotificationCursor(9))
        assertEquals(null, credentials.readNotificationCursor(null))
    }

    @Test
    fun `default mailbox is persisted separately from its credential`() = runTest {
        val store = testPreferenceStore(backgroundScope, "mail-default-mailbox")
        val credentials = DataStoreMailCredentialStore(
            store,
            object : SecretCipher {
                override fun encrypt(plaintext: String) = "encrypted:$plaintext"

                override fun decrypt(stored: String) = stored.removePrefix("encrypted:")
            },
        )
        credentials.saveDefaultMailboxId(9)
        assertEquals(9L, credentials.readDefaultMailboxId())
        credentials.saveNotificationCursor(9, 21)
        assertEquals(21L, credentials.readNotificationCursor(9))
        credentials.save("sk_live_example")
        credentials.clear()

        assertEquals(9L, credentials.readDefaultMailboxId())
        credentials.clearDefaultMailboxId()
        assertEquals(null, credentials.readDefaultMailboxId())
        credentials.clearNotificationCursors()
        assertEquals(null, credentials.readNotificationCursor(9))
        assertTrue(store.data.first().asMap().isEmpty())
    }

    @Test
    fun `keystore refusal never falls back to storing a plaintext API key`() = runTest {
        val store = testPreferenceStore(backgroundScope, "mail-refused-key")
        val cipher = object : SecretCipher {
            override fun encrypt(plaintext: String): String? = null

            override fun decrypt(stored: String): String = stored
        }
        val credentials = DataStoreMailCredentialStore(store, cipher)
        assertEquals(MailError.STORAGE, assertFailsWith<MailException> { credentials.save("sk_live_example") }.error)
        assertTrue(store.data.first().asMap().isEmpty())
    }
}
