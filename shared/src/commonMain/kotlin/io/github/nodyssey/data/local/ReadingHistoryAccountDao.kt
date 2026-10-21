package io.github.nodyssey.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/** Inactive accounts' history, separate from the active tables used by the feed's read badges. */
@Suppress("ROOM_EMBEDDED_PRIMARY_KEY_IS_DROPPED")
@Entity(tableName = "account_read_marks", primaryKeys = ["ownerKey", "postId"])
data class AccountReadMarkEntity(val ownerKey: String, @Embedded val mark: ReadMarkEntity)

@Suppress("ROOM_EMBEDDED_PRIMARY_KEY_IS_DROPPED")
@Entity(tableName = "account_reading_positions", primaryKeys = ["ownerKey", "postId"])
data class AccountReadingPositionEntity(val ownerKey: String, @Embedded val position: ReadingPositionEntity)

/** UID keys survive cookie rotation; unresolved sessions stay isolated until the site names a UID. */
@Entity(tableName = "reading_history_account")
data class ReadingHistoryAccountEntity(
    @PrimaryKey val id: Int = 0,
    val ownerKey: String,
    val authenticated: Boolean,
    val fingerprint: Int,
)

@Dao
interface ReadingHistoryAccountDao {
    @Query("SELECT * FROM reading_history_account WHERE id = 0")
    suspend fun active(): ReadingHistoryAccountEntity?

    @Upsert
    suspend fun setActive(account: ReadingHistoryAccountEntity)

    @Query("SELECT * FROM account_read_marks WHERE ownerKey = :owner")
    suspend fun marks(owner: String): List<AccountReadMarkEntity>

    @Query("SELECT * FROM account_reading_positions WHERE ownerKey = :owner")
    suspend fun positions(owner: String): List<AccountReadingPositionEntity>

    @Upsert
    suspend fun saveMarks(marks: List<AccountReadMarkEntity>)

    @Upsert
    suspend fun savePositions(positions: List<AccountReadingPositionEntity>)

    @Query("DELETE FROM account_read_marks WHERE ownerKey = :owner")
    suspend fun deleteMarks(owner: String)

    @Query("DELETE FROM account_reading_positions WHERE ownerKey = :owner")
    suspend fun deletePositions(owner: String)
}
