package com.whatsgo.app.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Query("SELECT name FROM contacts WHERE jid = :jid")
    suspend fun getName(jid: String): String?

    @Query("SELECT * FROM contacts WHERE jid = :jid LIMIT 1")
    suspend fun getByJid(jid: String): ContactEntity?

    @Query("SELECT * FROM contacts")
    fun observeAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE jid = :jid LIMIT 1")
    fun observeByJid(jid: String): Flow<ContactEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: ContactEntity)

    @Query("""
        INSERT INTO contacts (jid, name, avatarPath, avatarUpdatedAt)
        VALUES (:jid, :name, NULL, 0)
        ON CONFLICT(jid) DO UPDATE SET name = excluded.name
    """)
    suspend fun upsertName(jid: String, name: String)

    @Query("UPDATE contacts SET avatarPath = :path, avatarUpdatedAt = :ts WHERE jid = :jid")
    suspend fun updateAvatarPath(jid: String, path: String?, ts: Long = System.currentTimeMillis())

    @Query("UPDATE contacts SET avatarPath = NULL, avatarUpdatedAt = 0 WHERE jid = :jid")
    suspend fun clearAvatar(jid: String)
}