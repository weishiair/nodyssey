package io.github.nodyssey.data.mail

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.nodyssey.data.security.SecretCipher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

interface MailCredentialStore {
    suspend fun read(): String

    suspend fun save(key: String)

    suspend fun clear()
}

class DataStoreMailCredentialStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
) : MailCredentialStore {
    override suspend fun read(): String = cipher.decrypt(dataStore.data.first()[KEY].orEmpty())

    override suspend fun save(key: String): Unit = storage {
        val encrypted = cipher.encrypt(key) ?: throw MailException(MailError.STORAGE)
        if (encrypted.isBlank() || cipher.decrypt(encrypted) != key) throw MailException(MailError.STORAGE)
        dataStore.edit { it[KEY] = encrypted }
        if (read() != key) throw MailException(MailError.STORAGE)
    }

    override suspend fun clear(): Unit = storage {
        dataStore.edit { it.remove(KEY) }
    }

    private suspend fun storage(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw MailException(MailError.STORAGE)
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("api-key")
    }
}
