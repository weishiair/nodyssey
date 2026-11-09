package io.github.nodyssey.data.mail

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.nodyssey.data.security.SecretCipher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

interface MailCredentialStore {
    suspend fun read(): String

    suspend fun save(key: String)

    suspend fun replace(key: String) {
        save(key)
        clearDefaultMailboxId()
        clearNotificationCursors()
    }

    suspend fun readDefaultMailboxId(): Long?

    suspend fun saveDefaultMailboxId(id: Long?)

    suspend fun clearDefaultMailboxId()

    suspend fun readNotificationCursor(mailboxId: Long): Long? = null

    suspend fun saveNotificationCursor(mailboxId: Long, messageId: Long) = Unit

    suspend fun clearNotificationCursors() = Unit

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

    override suspend fun replace(key: String): Unit = storage {
        val encrypted = cipher.encrypt(key) ?: throw MailException(MailError.STORAGE)
        if (encrypted.isBlank() || cipher.decrypt(encrypted) != key) throw MailException(MailError.STORAGE)
        dataStore.edit { preferences ->
            preferences[KEY] = encrypted
            preferences.remove(DEFAULT_MAILBOX_ID)
            preferences.asMap().keys
                .filter { it.name.startsWith(NOTIFICATION_CURSOR_PREFIX) }
                .forEach { preferences.remove(it) }
        }
        if (read() != key) throw MailException(MailError.STORAGE)
    }

    override suspend fun readDefaultMailboxId(): Long? = try {
        dataStore.data.first()[DEFAULT_MAILBOX_ID]
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        throw MailException(MailError.STORAGE)
    }

    override suspend fun saveDefaultMailboxId(id: Long?): Unit = storage {
        dataStore.edit {
            if (id == null) it.remove(DEFAULT_MAILBOX_ID) else it[DEFAULT_MAILBOX_ID] = id
        }
    }

    override suspend fun clearDefaultMailboxId(): Unit = storage {
        dataStore.edit { it.remove(DEFAULT_MAILBOX_ID) }
    }

    override suspend fun readNotificationCursor(mailboxId: Long): Long? = try {
        dataStore.data.first()[notificationCursorKey(mailboxId)]
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        throw MailException(MailError.STORAGE)
    }

    override suspend fun saveNotificationCursor(mailboxId: Long, messageId: Long): Unit = storage {
        require(mailboxId > 0 && messageId >= 0)
        dataStore.edit { it[notificationCursorKey(mailboxId)] = messageId }
    }

    override suspend fun clearNotificationCursors(): Unit = storage {
        dataStore.edit { preferences ->
            preferences.asMap().keys
                .filter { it.name.startsWith(NOTIFICATION_CURSOR_PREFIX) }
                .forEach { key -> preferences.remove(key) }
        }
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
        val DEFAULT_MAILBOX_ID = longPreferencesKey("default-mailbox-id")
        const val NOTIFICATION_CURSOR_PREFIX = "mail-notification-cursor-"

        fun notificationCursorKey(mailboxId: Long) = longPreferencesKey("$NOTIFICATION_CURSOR_PREFIX$mailboxId")
    }
}
