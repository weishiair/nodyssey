package io.github.nodyssey.data.mail

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.nodyssey.data.security.SecretCipher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8

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

    suspend fun readNotificationCursor(mailboxId: Long?): Long? = null

    suspend fun saveNotificationCursor(mailboxId: Long?, messageId: Long) = Unit

    suspend fun clearNotificationCursors() = Unit

    suspend fun readDrafts(): List<SavedMailDraft> = emptyList()

    suspend fun saveDraft(draft: SavedMailDraft): Unit = throw MailException(MailError.STORAGE)

    suspend fun deleteDraft(id: String) = Unit

    suspend fun clear()

    suspend fun expire() = clear()
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

    override suspend fun readNotificationCursor(mailboxId: Long?): Long? = try {
        dataStore.data.first()[notificationCursorKey(mailboxId)]
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        throw MailException(MailError.STORAGE)
    }

    override suspend fun saveNotificationCursor(mailboxId: Long?, messageId: Long): Unit = storage {
        require((mailboxId == null || mailboxId > 0) && messageId >= 0)
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
        dataStore.edit { preferences ->
            preferences.remove(KEY)
            preferences.asMap().keys.filter { it.name.startsWith(DRAFT_PREFIX) }.forEach { preferences.remove(it) }
        }
    }

    override suspend fun expire(): Unit = storage {
        dataStore.edit { it.remove(KEY) }
    }

    override suspend fun readDrafts(): List<SavedMailDraft> = storage {
        val preferences = dataStore.data.first()
        val prefix = draftPrefix(preferences)
        preferences.asMap().entries
            .filter { it.key.name.startsWith(prefix) }
            .map { entry -> Json.decodeFromString<SavedMailDraft>(cipher.decrypt(entry.value as String)) }
            .sortedByDescending(SavedMailDraft::savedAt)
    }

    override suspend fun saveDraft(draft: SavedMailDraft): Unit = storage {
        val encoded = Json.encodeToString(draft)
        val encrypted = cipher.encrypt(encoded) ?: throw MailException(MailError.STORAGE)
        if (cipher.decrypt(encrypted) != encoded) throw MailException(MailError.STORAGE)
        dataStore.edit { it[stringPreferencesKey(draftPrefix(it) + draft.id)] = encrypted }
    }

    override suspend fun deleteDraft(id: String): Unit = storage {
        dataStore.edit { it.remove(stringPreferencesKey(draftPrefix(it) + id)) }
    }

    private fun draftPrefix(preferences: Preferences): String {
        val credential = cipher.decrypt(preferences[KEY].orEmpty())
        if (credential.isBlank()) throw MailException(MailError.STORAGE)
        return "$DRAFT_PREFIX${credential.encodeUtf8().sha256().hex()}-"
    }

    private suspend fun <T> storage(block: suspend () -> T): T =
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw MailException(MailError.STORAGE)
        }
    private companion object {
        val KEY = stringPreferencesKey("api-key")
        val DEFAULT_MAILBOX_ID = longPreferencesKey("default-mailbox-id")
        const val NOTIFICATION_CURSOR_PREFIX = "mail-notification-cursor-"
        const val DRAFT_PREFIX = "mail-draft-"

        fun notificationCursorKey(mailboxId: Long?) = longPreferencesKey("$NOTIFICATION_CURSOR_PREFIX${mailboxId ?: "all"}")
    }
}
