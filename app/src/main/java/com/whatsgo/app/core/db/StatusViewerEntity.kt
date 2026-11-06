package com.whatsgo.app.core.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "status_viewers",
    indices = [Index("statusId")]
)
data class StatusViewerEntity(
    @PrimaryKey val key: String,
    val statusId: String,
    val viewerJid: String,
    val viewedAt: Long
)
