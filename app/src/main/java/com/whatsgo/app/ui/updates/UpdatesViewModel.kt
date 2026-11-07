package com.whatsgo.app.ui.updates

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.whatsgo.app.core.SessionHolder
import com.whatsgo.app.core.db.StatusEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class UpdatesViewModel(app: Application) : AndroidViewModel(app) {

    private val session = SessionHolder.get(app)

    val allStatuses: StateFlow<List<StatusEntity>> =
        session.observeStatuses()
            .map { list ->
                val now = System.currentTimeMillis()
                list.filter { it.expiresAt > now }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    fun postTextStatus(text: String, backgroundColor: Long) {
        session.sendTextStatus(text, backgroundColor)
    }

    fun postImageStatus(path: String, caption: String) {
        session.sendImageStatus(path, caption)
    }

    fun postVideoStatus(path: String, caption: String) {
        session.sendVideoStatus(path, caption)
    }

    fun markViewed(senderJid: String, statusIds: List<String>) {
        session.markStatusViewed(senderJid, statusIds)
    }

    fun downloadStatus(status: StatusEntity) {
        session.downloadStatusMedia(status)
    }
    fun deleteStatus(status: StatusEntity) {
        session.deleteStatus(status)
    }
}
