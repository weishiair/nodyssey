package io.github.nodyssey.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.nodyssey.data.NotificationCounts
import io.github.nodyssey.data.OfflineLibrary
import io.github.nodyssey.data.PostRepository
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.settings.SettingsRepository
import kotlinx.serialization.Serializable

@Serializable
internal data class AccountLocalSnapshot(
    val postDrafts: Map<String, String> = emptyMap(),
    val commentDrafts: Map<String, String> = emptyMap(),
    val repliesSeen: Int = 0,
    val mentionsSeen: Int = 0,
    val messagesSeen: Int = 0,
)

/** Archives the existing draft files without changing their format or losing their timestamps. */
class AccountLocalData(
    private val postDrafts: DataStore<Preferences>,
    private val commentDrafts: DataStore<Preferences>,
    private val posts: PostRepository,
    private val profiles: ProfileRepository,
    private val offline: OfflineLibrary,
    private val settings: SettingsRepository,
    private val clearMail: suspend () -> Unit = {},
) {
    internal suspend fun capture(): AccountLocalSnapshot {
        val seen = settings.notificationSeenCounts()
        return AccountLocalSnapshot(
            postDrafts = snapshot(postDrafts),
            commentDrafts = snapshot(commentDrafts),
            repliesSeen = seen.replies,
            mentionsSeen = seen.mentions,
            messagesSeen = seen.messages,
        )
    }

    internal suspend fun clear(evictDownloads: Boolean = true) {
        clearMail()
        // Version one deliberately evicts downloads, rather than exposing a private offline page
        // to the next account. The account picker asks before this happens.
        clearAccountContent(
            posts,
            profiles,
            offline,
            settings,
            clearDrafts = {
                postDrafts.edit { it.clear() }
                commentDrafts.edit { it.clear() }
            },
            clearMessages = settings.messageDrafts::invalidateHandles,
            evictDownloads = evictDownloads,
        )
    }

    internal suspend fun evictDownloads() = offline.clearAll()

    internal suspend fun restore(snapshot: AccountLocalSnapshot) {
        restore(postDrafts, snapshot.postDrafts)
        restore(commentDrafts, snapshot.commentDrafts)
        settings.setNotificationSeenCounts(NotificationCounts(snapshot.repliesSeen, snapshot.mentionsSeen, snapshot.messagesSeen))
    }

    internal suspend fun forgetMessages(fingerprint: Int, uid: Long?) {
        settings.messageDrafts.clearAccount(fingerprint, uid)
    }

    private suspend fun snapshot(store: DataStore<Preferences>): Map<String, String> {
        // An edit is a barrier for writes queued by a page that has just closed.
        val preferences = store.edit { }
        return preferences.asMap().entries.mapNotNull { (key, value) ->
            (value as? String)?.let { key.name to it }
        }.toMap()
    }

    private suspend fun restore(store: DataStore<Preferences>, snapshot: Map<String, String>) {
        store.edit { preferences ->
            preferences.clear()
            snapshot.forEach { (name, value) -> preferences[stringPreferencesKey(name)] = value }
        }
    }
}
