package com.whatsgo.app.ui.user.model

data class UserProfile(
    val name: String = "",
    val about: String = "",
    val phone: String = "",
    val avatarUri: String? = null,
    val username: String = ""
)