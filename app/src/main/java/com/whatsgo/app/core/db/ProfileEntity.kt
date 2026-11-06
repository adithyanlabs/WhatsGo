package com.whatsgo.app.core.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 0,
    val name: String = "",
    val about: String = "",
    val phone: String = "",
    val avatarUri: String? = null,
    val username: String = ""
)