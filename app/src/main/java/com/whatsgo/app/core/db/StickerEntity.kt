package com.whatsgo.app.core.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "stickers", indices = [Index("isFavorite"), Index("lastUsed"), Index("receivedAt")])
data class StickerEntity(
    @PrimaryKey val id: String,
    val url: String,
    val directPath: String,
    val mediaKey: ByteArray,
    val fileEncSha256: ByteArray,
    val fileSha256: ByteArray,
    val fileLength: Long,
    val mimetype: String,
    val animated: Boolean,
    val width: Int,
    val height: Int,
    val localPath: String? = null,
    val isFavorite: Boolean = false,
    val lastUsed: Long = 0L,
    val receivedAt: Long = 0L
)
