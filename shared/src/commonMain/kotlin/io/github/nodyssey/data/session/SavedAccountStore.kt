package io.github.nodyssey.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.nodyssey.data.attendance.AutoAttendanceConfig
import io.github.nodyssey.data.security.SecretCipher
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A platform snapshot includes cookie attributes, not just a Cookie request header. */
interface AccountCookieStore {
    suspend fun capture(): String

    /** Replaces only this site's cookies, including the sign-in browser's copy. */
    suspend fun replace(snapshot: String)
}

/** Only malformed snapshot data, never a failure to access the platform cookie store. */
class InvalidAccountCookieSnapshot(cause: Throwable? = null) : IllegalStateException("Saved account cookies are invalid", cause)

@Serializable
data class SavedAccount(
    val uid: Long,
    val name: String,
    val avatarUrl: String,
    val needsSignIn: Boolean = false,
    val attendance: AutoAttendanceConfig = AutoAttendanceConfig(),
)

@Serializable
internal data class StoredAccount(
    val account: SavedAccount,
    val cookies: String,
    val fingerprint: Int,
    val local: AccountLocalSnapshot = AccountLocalSnapshot(),
)

internal data class AttendanceAccount(val uid: Long, val cookies: String, val storedCookies: String, val fingerprint: Int)

@Serializable
internal data class PendingAccount(
    val previousUid: Long?,
    val cookies: String,
    val local: AccountLocalSnapshot,
)

@Serializable
internal data class AccountVault(
    val accounts: List<StoredAccount> = emptyList(),
    val activeUid: Long? = null,
    val pending: PendingAccount? = null,
    val recovery: PendingAccount? = null,
    /** Unreadable snapshots and their drafts remain protected, but are never replayed. */
    val quarantined: List<PendingAccount> = emptyList(),
    /** Durable namespace for navigation's saved state, including process-death restoration. */
    val navigationKey: String = "legacy",
)

/** The entire payload, including drafts and account metadata, uses the platform's secret storage. */
class SavedAccountStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
    siteKey: String,
) {
    private val key = stringPreferencesKey("accounts_v1_$siteKey")
    private val json = Json { ignoreUnknownKeys = true }

    internal suspend fun read(): AccountVault {
        val stored = dataStore.data.first()[key] ?: return AccountVault()
        val plaintext = cipher.decrypt(stored)
        check(plaintext.isNotEmpty()) { "Saved accounts are unavailable on this device" }
        return json.decodeFromString(plaintext)
    }

    internal suspend fun write(vault: AccountVault) {
        val stored = checkNotNull(cipher.encrypt(json.encodeToString(vault))) { "Cannot protect saved accounts" }
        dataStore.edit { it[key] = stored }
    }
}
