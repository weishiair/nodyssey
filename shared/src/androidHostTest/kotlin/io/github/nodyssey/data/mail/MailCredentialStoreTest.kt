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
