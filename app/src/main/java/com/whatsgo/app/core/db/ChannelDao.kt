package com.whatsgo.app.core.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(channel: ChannelEntity)

    @Query("DELETE FROM channels WHERE jid NOT IN (:keep)")
    suspend fun deleteNotIn(keep: List<String>)
    @Query("SELECT * FROM channels WHERE jid = :jid LIMIT 1")
    fun observeByJid(jid: String): Flow<ChannelEntity?>

    @Query("SELECT * FROM channels WHERE jid = :jid LIMIT 1")
    suspend fun getByJid(jid: String): ChannelEntity?
}