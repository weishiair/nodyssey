package io.github.nodyssey.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.nodyssey.core.Site
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class MessageRecovery {
    RETRY,
    SIGN_IN,
    VERIFY,
    WAIT,
    NONE,
}

/** Recovery survives a restart with the failed message, including a challenge's actual URL. */
@Serializable
data class MessageFailure(
    val recovery: MessageRecovery = MessageRecovery.RETRY,
    val detail: String? = null,
    val verificationUrl: String? = null,
    val retryAtMillis: Long = 0L,
    val uncertain: Boolean = false,
)

@Serializable
data class MessageDraftBubble(
    val id: String,
    val isMine: Boolean,
    val content: String,
    val isMarkdown: Boolean,
    val sentAtMillis: Long?,
    val sentAtText: String?,
    val failure: MessageFailure? = null,
    /** No acknowledgement was received before this page/process went away. Never auto-resend it. */
    val wasSending: Boolean = false,
)

@Serializable
data class MessageDraft(
    val userName: String = "",
    val text: String = "",
    val selectionStart: Int = 0,
    val selectionEnd: Int = 0,
    val quotes: List<MessageDraftBubble> = emptyList(),
    val pending: List<MessageDraftBubble> = emptyList(),
    val savedAtMillis: Long = 0L,
    /** Only supplied after a profile belonging to these cookies identifies the signed-in account. */
    val accountUid: Long? = null,
) {
    val hasContent: Boolean get() = text.isNotEmpty() || quotes.isNotEmpty() || pending.isNotEmpty()
}

/** A logout invalidates open handles too, so a late ViewModel cleanup cannot restore private text. */
class MessageDraftHandle internal constructor(
    val fingerprint: Int,
    val uid: Long,
    internal val revision: Long,
    val initial: MessageDraft,
    val accountUid: Long? = null,
    internal val sessionRevision: Long = 0,
)

/** Unsent conversation content only; delivered history continues to come from the site. */
class MessageDraftStore internal constructor(
    private val dataStore: DataStore<Preferences>,
    site: Site,
) {
    private val prefix = "message_drafts${site.storageSuffix}:"
    private val revisionKey = longPreferencesKey("${prefix}revision")
    private val json = Json { ignoreUnknownKeys = true }

    fun drafts(fingerprint: Int): Flow<Map<Long, MessageDraft>> =
        dataStore.data.map { decode(it[key(fingerprint)]) }.distinctUntilChanged()

    suspend fun open(fingerprint: Int, uid: Long, accountUid: Long? = null): MessageDraftHandle {
        if (accountUid != null && accountUid > 0) return openIdentified(fingerprint, uid, accountUid)
        val preferences = dataStore.data.first()
        val initial = decode(preferences[key(fingerprint)])[uid] ?: MessageDraft()
        initial.accountUid?.let { return openIdentified(fingerprint, uid, it) }
        return MessageDraftHandle(
            fingerprint,
            uid,
            preferences[revisionKey] ?: 0L,
            initial,
            sessionRevision = preferences[sessionRevisionKey(fingerprint)] ?: 0L,
        )
    }

    suspend fun identify(fingerprint: Int, accountUid: Long) {
        if (accountUid > 0) bindAccount(fingerprint, accountUid)
    }

    /** Renewed cookies may resume the same person's words; a different verified UID never does. */
    private suspend fun openIdentified(fingerprint: Int, uid: Long, accountUid: Long): MessageDraftHandle {
        val preferences = bindAccount(fingerprint, accountUid)
        val initial = decode(preferences[key(fingerprint)])[uid] ?: MessageDraft(accountUid = accountUid)
        return MessageDraftHandle(fingerprint, uid, preferences[revisionKey] ?: 0L, initial, accountUid, preferences[sessionRevisionKey(fingerprint)] ?: 0L)
    }

    private suspend fun bindAccount(fingerprint: Int, accountUid: Long): Preferences =
        dataStore.edit { preferences ->
            val destination = key(fingerprint)
            val current = decode(preferences[destination])
                .filterValues { it.accountUid == null || it.accountUid == accountUid }
                .mapValues { it.value.copy(accountUid = accountUid) }.toMutableMap()
            preferences.asMap().toList().forEach { (storedKey, value) ->
                if (storedKey == destination || !storedKey.name.startsWith(prefix) || storedKey.name.removePrefix(prefix).toIntOrNull() == null) return@forEach
                val other = decode(value as? String).toMutableMap()
                other.toMap().forEach { (recipient, draft) ->
                    if (draft.accountUid == accountUid) {
                        val existing = current[recipient]
                        if (existing == null || draft.savedAtMillis > existing.savedAtMillis) current[recipient] = draft
                        other.remove(recipient)
                    }
                }
                val source = stringPreferencesKey(storedKey.name)
                if (other.isEmpty()) preferences.remove(source) else preferences[source] = json.encodeToString(other)
            }
            if (current.isEmpty()) preferences.remove(destination) else preferences[destination] = json.encodeToString(current)
            preferences[accountFingerprintKey(accountUid)] = fingerprint
        }

    suspend fun save(handle: MessageDraftHandle, draft: MessageDraft) {
        dataStore.edit { preferences ->
            if ((preferences[revisionKey] ?: 0L) != handle.revision) return@edit
            if ((preferences[sessionRevisionKey(handle.fingerprint)] ?: 0L) != handle.sessionRevision) return@edit
            // An older page can finish cleanup after renewed cookies have already resumed this
            // account. It must not resurrect an outbox the resumed page has since sent or removed.
            val key = key(handle.fingerprint)
            val drafts = decode(preferences[key]).toMutableMap()
            val accountUid = handle.accountUid ?: drafts[handle.uid]?.accountUid
            if (accountUid != null && preferences[accountFingerprintKey(accountUid)] != handle.fingerprint) return@edit
            if (draft.hasContent) drafts[handle.uid] = draft.copy(accountUid = accountUid) else drafts.remove(handle.uid)
            if (drafts.isEmpty()) preferences.remove(key) else preferences[key] = json.encodeToString(drafts)
        }
    }

    suspend fun clear() {
        dataStore.edit { preferences ->
            val next = (preferences[revisionKey] ?: 0L) + 1L
            preferences.asMap().keys.filter { it.name.startsWith(prefix) }.forEach { preferences.remove(it) }
            preferences[revisionKey] = next
        }
    }

    suspend fun invalidateHandles() {
        dataStore.edit { it[revisionKey] = (it[revisionKey] ?: 0L) + 1L }
    }

    /** Forget one account without deleting the other saved accounts' private drafts. */
    suspend fun clearAccount(fingerprint: Int, accountUid: Long?) {
        dataStore.edit { preferences ->
            val affected = mutableSetOf(fingerprint)
            preferences.asMap().toList().forEach { (storedKey, value) ->
                if (!storedKey.name.startsWith(prefix) || storedKey.name.removePrefix(prefix).toIntOrNull() == null) return@forEach
                val previous = decode(value as? String)
                val drafts = previous.filterValues { draft ->
                    storedKey != key(fingerprint) && (accountUid == null || draft.accountUid != accountUid)
                }
                if (drafts.size != previous.size) affected += storedKey.name.removePrefix(prefix).toInt()
                val destination = stringPreferencesKey(storedKey.name)
                if (drafts.isEmpty()) preferences.remove(destination) else preferences[destination] = json.encodeToString(drafts)
            }
            accountUid?.let {
                preferences.remove(accountFingerprintKey(it))
                Unit
            }
            affected.forEach { session ->
                val key = sessionRevisionKey(session)
                preferences[key] = (preferences[key] ?: 0L) + 1L
            }
        }
    }

    private fun key(fingerprint: Int) = stringPreferencesKey("$prefix$fingerprint")

    private fun accountFingerprintKey(uid: Long) = intPreferencesKey("${prefix}account_$uid")
    private fun sessionRevisionKey(fingerprint: Int) = longPreferencesKey("${prefix}session_revision_$fingerprint")

    private fun decode(encoded: String?): Map<Long, MessageDraft> =
        encoded?.let { runCatching { json.decodeFromString<Map<Long, MessageDraft>>(it) }.getOrNull() }.orEmpty()
}
