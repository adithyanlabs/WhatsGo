package com.whatsgo.app.core.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "statuses",
    indices = [Index("senderJid"), Index("expiresAt"), Index("fromMe")]
)
data class StatusEntity(
    @PrimaryKey val id: String,
    val senderJid: String,
    val senderName: String,
    val timestamp: Long,
    val fromMe: Boolean,
    val mediaType: String,
    val text: String,
    val backgroundColor: Long,
    val url: String,
    val directPath: String,
    val mediaKey: ByteArray,
    val fileEncSHA256: ByteArray,
    val fileSHA256: ByteArray,
    val fileLength: Long,
    val mimetype: String,
    val thumbnail: ByteArray,
    val localPath: String?,
    val viewed: Boolean,
    val expiresAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StatusEntity) return false
        return id == other.id
    }
    override fun hashCode(): Int = id.hashCode()
}
