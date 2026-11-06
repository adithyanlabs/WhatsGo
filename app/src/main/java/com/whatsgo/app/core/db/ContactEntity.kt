package com.whatsgo.app.core.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val jid: String,
    val name: String,
    val avatarPath: String? = null,
    val avatarUpdatedAt: Long = 0L
)