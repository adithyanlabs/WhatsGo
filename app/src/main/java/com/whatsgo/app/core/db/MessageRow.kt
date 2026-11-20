package com.whatsgo.app.core.db

data class MessageRow(
    val messageId: String,
    val chatJid: String,
    val senderJid: String,
    val text: String,
    val timestamp: Long,
    val fromMe: Boolean,
    val isRead: Boolean,
    val status: String,
    val quotedId: String?,
    val quotedText: String?,
    val quotedSenderJid: String?,
    val mediaType: String?,
    val mediaCaption: String?,
    val mediaLocalPath: String?,
    val mediaThumbnail: ByteArray?,
    val senderName: String?,
    val origText: String?,
    val origFromMe: Boolean?,
    val origSenderJid: String?,
    val origMediaType: String?,
    val origSenderName: String?,
    val mediaSeconds: Int?,
    val mediaWaveform: ByteArray?
)
