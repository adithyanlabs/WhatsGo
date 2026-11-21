package com.whatsgo.app.core.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "message_receipts", primaryKeys = ["messageId", "jid"])
data class ReceiptEntity(
    val messageId: String,
    val jid: String,
    val deliveredAt: Long,
    val readAt: Long
)

@Dao
interface ReceiptDao {

    @Query(
        """
        INSERT INTO message_receipts (messageId, jid, deliveredAt, readAt)
        VALUES (:messageId, :jid, :delivered, :read)
        ON CONFLICT(messageId, jid) DO UPDATE SET
            deliveredAt = CASE WHEN deliveredAt = 0 AND :delivered > 0 THEN :delivered ELSE deliveredAt END,
            readAt = CASE WHEN readAt = 0 AND :read > 0 THEN :read ELSE readAt END
        """
    )
    suspend fun upsert(messageId: String, jid: String, delivered: Long, read: Long)

    @Query("SELECT * FROM message_receipts WHERE messageId = :messageId")
    fun observe(messageId: String): Flow<List<ReceiptEntity>>

    @Query("DELETE FROM message_receipts WHERE messageId IN (:ids)")
    suspend fun deleteFor(ids: List<String>)
}