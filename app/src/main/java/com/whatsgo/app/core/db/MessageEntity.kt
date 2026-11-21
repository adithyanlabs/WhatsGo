package com.whatsgo.app.core.db

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["chatJid", "timestamp"]),
        Index(value = ["chatJid", "fromMe", "isRead"]),
        Index(value = ["quotedId"])
    ]
)

data class MessageEntity(
    @PrimaryKey val messageId: String,
    val chatJid: String,
    val senderJid: String,
    val text: String,
    val timestamp: Long,
    val fromMe: Boolean,
    val isRead: Boolean = false,
    val quotedId: String? = null,
    val quotedText: String? = null,
    val quotedSenderJid: String? = null,
    val mediaType: String? = null,
    val mediaCaption: String? = null,
    val mediaMimetype: String? = null,
    val mediaUrl: String? = null,
    val mediaDirectPath: String? = null,
    val mediaKey: ByteArray? = null,
    val mediaFileEncSha256: ByteArray? = null,
    val mediaFileSha256: ByteArray? = null,
    val mediaFileLength: Long? = null,
    val mediaThumbnail: ByteArray? = null,
    val mediaLocalPath: String? = null,
    val mediaFileName: String? = null,
    val status: String = "sent",
    val mediaSeconds: Int? = null,
    val mediaWaveform: ByteArray? = null,
    @ColumnInfo(defaultValue = "0") val mediaAnimated: Boolean = false
)
