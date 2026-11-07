package com.whatsgo.app.ui.user

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.whatsgo.app.core.LoginEvent
import com.whatsgo.app.core.SessionHolder
import com.whatsgo.app.core.db.AppDatabase
import com.whatsgo.app.ui.user.data.UserRepository
import com.whatsgo.app.ui.user.model.UserProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProfileViewModel(app: Application) : AndroidViewModel(app) {
    private val session = SessionHolder.get(app)
    private val repo = UserRepository(AppDatabase.get(app), session)

    val profile: StateFlow<UserProfile> = repo.profile
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserProfile()
        )

    init {
        viewModelScope.launch {
            val currentName = withContext(Dispatchers.IO) {
                runCatching { session.ownName() }.getOrNull()
            }
            if (!currentName.isNullOrBlank()) repo.updateName(currentName)
            fetchAndStoreAvatar()
        }

        viewModelScope.launch {
            session.events.collect { evt ->
                when (evt) {
                    is LoginEvent.OwnNameUpdated ->
                        if (evt.name.isNotBlank()) repo.updateName(evt.name)
                    is LoginEvent.OwnAboutUpdated ->
                        if (evt.about.isNotBlank()) repo.updateAbout(evt.about)
                    is LoginEvent.OwnAvatarChanged ->
                        if (!evt.removed) fetchAndStoreAvatar()
                    is LoginEvent.Connected ->
                        fetchAndStoreAvatar()
                    else -> Unit
                }
            }
        }
    }

    private fun fetchAndStoreAvatar() {
        viewModelScope.launch {
            val url = withContext(Dispatchers.IO) {
                runCatching { session.fetchOwnAvatarUrl() }.getOrNull()
            }
            if (!url.isNullOrBlank()) repo.updateAvatar(url)
        }
    }

    fun updateName(name: String) { viewModelScope.launch { repo.updateName(name) } }
    fun updateAbout(about: String) { viewModelScope.launch { repo.updateAbout(about) } }
    fun updateAvatar(uri: String) { viewModelScope.launch { repo.updateAvatar(uri) } }
    fun updateUsername(username: String) { viewModelScope.launch { repo.updateUsername(username) } }
}