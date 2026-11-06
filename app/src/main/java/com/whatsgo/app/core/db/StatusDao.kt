package com.whatsgo.app.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StatusDao {

    @Query("SELECT * FROM statuses ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<StatusEntity>>

    @Query("SELECT * FROM statuses WHERE expiresAt > :now ORDER BY timestamp DESC")
    fun observeActive(now: Long = System.currentTimeMillis()): Flow<List<StatusEntity>>

    @Query("SELECT * FROM statuses WHERE senderJid = :senderJid AND expiresAt > :now ORDER BY timestamp ASC")
    suspend fun getBySender(senderJid: String, now: Long = System.currentTimeMillis()): List<StatusEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(status: StatusEntity)

    @Query("UPDATE statuses SET viewed = 1 WHERE id = :id")
    suspend fun markViewed(id: String)

    @Query("UPDATE statuses SET localPath = :path WHERE id = :id")
    suspend fun setLocalPath(id: String, path: String)

    @Query("DELETE FROM statuses WHERE expiresAt <= :now")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis())

    @Query("DELETE FROM statuses WHERE id = :id")
    suspend fun deleteById(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertViewer(viewer: StatusViewerEntity)

    @Query("SELECT * FROM status_viewers WHERE statusId = :statusId ORDER BY viewedAt DESC")
    fun observeViewers(statusId: String): Flow<List<StatusViewerEntity>>

    @Query("SELECT COUNT(*) FROM status_viewers WHERE statusId = :statusId")
    suspend fun getViewerCount(statusId: String): Int
}
