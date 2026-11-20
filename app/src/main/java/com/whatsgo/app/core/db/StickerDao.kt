package com.whatsgo.app.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StickerDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(sticker: StickerEntity): Long

    @Query("SELECT * FROM stickers WHERE isFavorite = 1 ORDER BY receivedAt DESC")
    fun observeFavorites(): Flow<List<StickerEntity>>

    @Query("SELECT * FROM stickers WHERE lastUsed > 0 ORDER BY lastUsed DESC LIMIT 30")
    fun observeRecent(): Flow<List<StickerEntity>>

    @Query("SELECT * FROM stickers WHERE isFavorite = 0 ORDER BY receivedAt DESC LIMIT 200")
    fun observeReceived(): Flow<List<StickerEntity>>

    @Query("UPDATE stickers SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: String, fav: Boolean)

    @Query("UPDATE stickers SET lastUsed = :ts WHERE id = :id")
    suspend fun touch(id: String, ts: Long)

    @Query("UPDATE stickers SET localPath = :path WHERE id = :id")
    suspend fun setLocalPath(id: String, path: String)
}
