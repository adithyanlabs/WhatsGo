package com.whatsgo.app.core.db

data class UnreadMessageRef(
    val messageId: String,
    val senderJid: String
)