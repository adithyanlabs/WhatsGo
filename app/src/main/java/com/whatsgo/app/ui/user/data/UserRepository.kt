package com.whatsgo.app.ui.user.data

import com.whatsgo.app.core.ChatSession
import com.whatsgo.app.core.db.AppDatabase
import com.whatsgo.app.core.db.ProfileEntity
import com.whatsgo.app.ui.user.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

class UserRepository(
    private val db: AppDatabase,
    private val session: ChatSession
) {

    val profile: Flow<UserProfile> = db.profileDao().observe().map { row ->
        UserProfile(
            name = row?.name?.takeIf { it.isNotBlank() } ?: "",
            about = row?.about?.takeIf { it.isNotBlank() } ?: "Hey there! I'm using WhatsGo",
            phone = row?.phone?.takeIf { it.isNotBlank() } ?: "",
            avatarUri = row?.avatarUri,
            username = row?.username ?: ""
        )
    }

    private suspend fun current(): ProfileEntity =
        db.profileDao().observe().first() ?: ProfileEntity()

    suspend fun updateName(name: String) {
        db.profileDao().upsert(current().copy(name = name))
    }

    suspend fun updateAbout(about: String) {
        db.profileDao().upsert(current().copy(about = about))
    }

    suspend fun updateAvatar(uri: String) {
        db.profileDao().upsert(current().copy(avatarUri = uri))
    }

    suspend fun updateUsername(username: String) {
        db.profileDao().upsert(current().copy(username = username))
    }
}