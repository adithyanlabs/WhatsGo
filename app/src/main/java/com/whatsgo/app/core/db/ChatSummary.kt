package com.whatsgo.app.core.db

data class ChatSummary(
    val chatJid: String,
    val lastMessage: String,
    val timestamp: Long,
    val fromMe: Boolean,
    val unread: Int
)