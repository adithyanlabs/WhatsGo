package com.whatsgo.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.whatsgo.app.core.PhoneFormatter
import com.whatsgo.app.core.SessionHolder
import com.whatsgo.app.core.db.AppDatabase
import com.whatsgo.app.core.db.ContactEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.stateIn
class ChatListViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val session = SessionHolder.get(app)
    private val summariesFlow = db.messageDao().observeChatSummaries()
        .shareIn(viewModelScope, SharingStarted.Eagerly, 1)
    private val contactsFlow = db.contactDao().observeAll()
        .shareIn(viewModelScope, SharingStarted.Eagerly, 1)
    private val _chats = MutableStateFlow<List<ChatItem>>(emptyList())
    val chats: StateFlow<List<ChatItem>> = _chats.asStateFlow()
    val channels: StateFlow<List<ChatItem>> = combine(
        db.channelDao().observeAll(),
        summariesFlow,
        contactsFlow
    ) { chans, sums, contacts ->
        val sumBy = sums.associateBy { it.chatJid }
        val avatarBy = contacts.associate { it.jid to it.avatarPath }
        chans.map { ch ->
            val s = sumBy[ch.jid]
            ChatItem(
                id = ch.jid,
                name = ch.name,
                lastMessage = s?.lastMessage ?: ch.description.ifBlank { "Tap to view" },
                time = s?.let { formatTime(it.timestamp) } ?: "",
                unread = s?.unread ?: 0,
                isChannel = true,
                avatarPath = avatarBy[ch.jid]
            ) to (s?.timestamp ?: 0L)
        }.sortedByDescending { it.second }.map { it.first }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val liveTypingState = MutableStateFlow<Map<String, String>>(emptyMap())
    private val livePresenceState = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    init {
        viewModelScope.launch {
            session.events.collect { event ->
                when (event) {
                    is com.whatsgo.app.core.LoginEvent.ChatPresenceUpdated -> {
                        val key = presenceKey(event.chatJid)
                        if (event.state == "composing") {
                            val label = if (event.media == "audio") "recording audio..." else "typing..."
                            liveTypingState.value = liveTypingState.value + (key to label)
                        } else if (event.state == "paused") {
                            liveTypingState.value = liveTypingState.value - key
                        }
                    }
                    is com.whatsgo.app.core.LoginEvent.PresenceUpdated -> {
                        val key = presenceKey(event.senderJid)
                        livePresenceState.value = livePresenceState.value + (key to event.online)
                    }
                    else -> Unit
                }
            }
        }

        viewModelScope.launch {
            combine(
                summariesFlow,
                contactsFlow,
                liveTypingState,
                livePresenceState
            ) { allSummaries, contacts, typingMap, presenceMap ->
                val summaries = allSummaries.filter { !it.chatJid.endsWith("@newsletter") }
                val byJid = contacts.associateBy { it.jid }
                val messageChats = summaries.map { s ->
                    val item = toChatItem(s, byJid[s.chatJid])
                    val key = presenceKey(s.chatJid)
                    val typingText = typingMap[key]
                    item.copy(
                        lastMessage = typingText ?: item.lastMessage,
                        isOnline = presenceMap[key] == true
                    )
                }
                val messageJids = summaries.mapTo(HashSet()) { it.chatJid }

                val orphanGroups = contacts
                    .asSequence()
                    .filter { it.jid.endsWith("@g.us") && it.jid !in messageJids }
                    .sortedBy { it.name.lowercase() }
                    .map { contact ->
                        val key = presenceKey(contact.jid)
                        val typingText = typingMap[key]
                        ChatItem(
                            id = contact.jid,
                            name = contact.name.ifBlank { contact.jid.substringBefore("@") },
                            lastMessage = typingText ?: "Tap to view",
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
                .flowOn(Dispatchers.Default)
                .collect { list -> _chats.value = list }
        }
    }

    private suspend fun toChatItem(
        s: com.whatsgo.app.core.db.ChatSummary,
        contact: ContactEntity?
    ): ChatItem {
        val isGroup = s.chatJid.endsWith("@g.us")
        val saved = contact?.name?.takeIf { it.isNotBlank() }
        val name = when {
            saved != null && PhoneFormatter.isPhoneLike(saved) ->
                withContext(Dispatchers.Default) { PhoneFormatter.format(getApplication(), s.chatJid) }
            saved != null -> saved
            else -> resolveName(s.chatJid)
        }
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
        val isGroup = jid.endsWith("@g.us")
        val fetched = withContext(Dispatchers.IO) {
            runCatching {
                if (isGroup) session.groupName(jid) else session.contactName(jid)
            }.getOrNull()
        }?.takeIf { it.isNotBlank() }

        if (fetched != null && !PhoneFormatter.isPhoneLike(fetched)) {
            db.contactDao().insertNameIfAbsent(jid, fetched)
            return fetched
        }
        return withContext(Dispatchers.Default) { PhoneFormatter.format(getApplication(), jid) }
    }

    private fun presenceKey(jid: String): String =
        jid.substringBefore(":").substringBefore("@")

    private fun formatTime(epochSeconds: Long): String {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        return fmt.format(Date(epochSeconds * 1000))
    }

    fun markRead(chatJid: String) {
        viewModelScope.launch { db.messageDao().markChatRead(chatJid) }
    }
}