package io.github.nodyssey.data.attendance

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.nodyssey.core.Site
import io.github.nodyssey.data.AttendanceMode
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.IOException

/** Settings and receipts share the existing DataStore, with separate keys for each site. */
class AutoAttendanceStore internal constructor(
    private val dataStore: DataStore<Preferences>,
    site: Site,
) {
    private val enabledKey = booleanPreferencesKey("auto_attendance_enabled${site.storageSuffix}")
    private val modeKey = stringPreferencesKey("auto_attendance_mode${site.storageSuffix}")
    private val recordsKey = stringPreferencesKey("auto_attendance_records${site.storageSuffix}")
    private val json = Json { ignoreUnknownKeys = true }

    val config = dataStore.data.catch { failure ->
        if (failure is IOException) emit(emptyPreferences()) else throw failure
    }.map { values ->
        AutoAttendanceConfig(
            enabled = values[enabledKey] ?: false,
            mode = values[modeKey]?.let { runCatching { AttendanceMode.valueOf(it) }.getOrNull() }
                ?: AttendanceMode.RANDOM,
        )
    }.distinctUntilChanged()

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[enabledKey] = enabled }
    }

    suspend fun setMode(mode: AttendanceMode) {
        dataStore.edit { it[modeKey] = mode.name }
    }

    internal suspend fun records(): List<AutoAttendanceRecord> = decode(dataStore.data.first()[recordsKey])

    internal suspend fun save(record: AutoAttendanceRecord) {
        dataStore.edit { values ->
            val records = decode(values[recordsKey]).filterNot { it.fingerprint == record.fingerprint || (record.uid != null && it.uid == record.uid) }
            // One receipt per known UID. Do not evict an enabled account's receipt because many
            // other saved accounts ran; anonymous pre-identification attempts remain bounded.
            values[recordsKey] = json.encodeToString(records.filter { it.uid != null } + records.filter { it.uid == null }.takeLast(15) + record)
        }
    }

    private fun decode(value: String?): List<AutoAttendanceRecord> =
        value?.let { runCatching { json.decodeFromString<List<AutoAttendanceRecord>>(it) }.getOrNull() }
            ?: emptyList()
}

@Serializable
data class AutoAttendanceConfig(
    val enabled: Boolean = false,
    val mode: AttendanceMode = AttendanceMode.RANDOM,
)

@Serializable
internal data class AutoAttendanceRecord(
    val fingerprint: Int,
    val date: String,
    val attempts: Int,
    val attemptedAtMillis: Long,
    val uid: Long? = null,
    val completed: Boolean = false,
    val gain: Int? = null,
    val failure: AutoAttendanceFailure? = null,
)

@Serializable
enum class AutoAttendanceFailure { NETWORK, VERIFICATION, SIGN_IN, OTHER }
