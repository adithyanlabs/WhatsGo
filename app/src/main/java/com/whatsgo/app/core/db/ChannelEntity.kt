package com.whatsgo.app.core.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val jid: String,
    val name: String,
    val description: String = "",
    val subscribers: Long = 0L,
    val verified: Boolean = false,
    val muted: Boolean = false
)