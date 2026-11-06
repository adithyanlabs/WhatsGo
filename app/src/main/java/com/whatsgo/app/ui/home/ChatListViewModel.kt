package com.whatsgo.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.whatsgo.app.core.SessionHolder
import com.whatsgo.app.core.db.AppDatabase
import com.whatsgo.app.core.db.ContactEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatListViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val session = SessionHolder.get(app)

    private val _chats = MutableStateFlow<List<ChatItem>>(emptyList())
    val chats: StateFlow<List<ChatItem>> = _chats.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                db.messageDao().observeChatSummaries(),
                db.contactDao().observeAll()
            ) { summaries, contacts ->
                val byJid = contacts.associateBy { it.jid }
                val messageChats = summaries.map { s -> toChatItem(s, byJid[s.chatJid]) }
                val messageJids = summaries.mapTo(HashSet()) { it.chatJid }

                val orphanGroups = contacts
                    .asSequence()
                    .filter { it.jid.endsWith("@g.us") && it.jid !in messageJids }
                    .sortedBy { it.name.lowercase() }
                    .map { contact ->
                        ChatItem(
                            id = contact.jid,
                            name = contact.name.ifBlank { contact.jid.substringBefore("@") },
                            lastMessage = "Tap to view",
                            time = "",
                            unread = 0,
                            isOnline = false,
                            isGroup = true,
                            avatarPath = contact.avatarPath
                        )
                    }
                    .toList()

                messageChats + orphanGroups
            }
                .distinctUntilChanged()
                .collect { list -> _chats.value = list }
        }
    }

    private suspend fun toChatItem(
        s: com.whatsgo.app.core.db.ChatSummary,
        contact: ContactEntity?
    ): ChatItem {
        val isGroup = runCatching { session.isGroupJID(s.chatJid) }.getOrDefault(false)
        val name = contact?.name?.takeIf { it.isNotBlank() } ?: resolveName(s.chatJid)
        return ChatItem(
            id = s.chatJid,
            name = name,
            lastMessage = if (s.fromMe) "You: ${s.lastMessage}" else s.lastMessage,
            time = formatTime(s.timestamp),
            unread = s.unread,
            isOnline = false,
            isGroup = isGroup,
            avatarPath = contact?.avatarPath
        )
    }

    private suspend fun resolveName(jid: String): String {
        db.contactDao().getName(jid)?.takeIf { it.isNotBlank() }?.let { return it }
        val isGroup = runCatching { session.isGroupJID(jid) }.getOrDefault(false)
        val fetched = withContext(Dispatchers.IO) {
            runCatching {
                if (isGroup) session.groupName(jid) else session.contactName(jid)
            }.getOrNull()
        }
        val name = fetched?.takeIf { it.isNotBlank() } ?: jid.substringBefore("@")
        db.contactDao().upsertName(jid, name)
        session.requestAvatarRefresh(jid)
        return name
    }

    private fun formatTime(epochSeconds: Long): String {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        return fmt.format(Date(epochSeconds * 1000))
    }

    fun markRead(chatJid: String) {
        viewModelScope.launch { db.messageDao().markChatRead(chatJid) }
    }
}