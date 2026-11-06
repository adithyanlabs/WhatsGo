package com.whatsgo.app.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.whatsgo.app.core.SessionHolder
import com.whatsgo.app.core.db.AppDatabase
import com.whatsgo.app.core.db.ContactEntity
import com.whatsgo.app.core.db.MessageEntity
import com.whatsgo.app.ui.chat.models.ChatMessage
import com.whatsgo.app.ui.chat.models.MediaAttachment
import com.whatsgo.app.ui.chat.models.MessageStatus
import com.whatsgo.app.ui.chat.models.ReplyPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val session = SessionHolder.get(app)

    private var currentChatJid: String = ""
    private var collectJob: Job? = null

    private val _isGroup = MutableStateFlow(false)
    val isGroup: StateFlow<Boolean> = _isGroup.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    fun open(chatJid: String) {
        if (currentChatJid == chatJid) return
        collectJob?.cancel()
        currentChatJid = chatJid
        _messages.value = emptyList()

        viewModelScope.launch {
            _isGroup.value = runCatching { session.isGroupJID(chatJid) }.getOrDefault(false)
        }

        viewModelScope.launch {
            db.messageDao().markChatRead(chatJid)

            val unread = db.messageDao().getUnreadIncoming(chatJid)
            val bySender = unread.groupBy { it.senderJid }
            withContext(Dispatchers.IO) {
                bySender.forEach { (senderJid, refs) ->
                    runCatching {
                        session.markChatReadOnServer(chatJid, refs.map { it.messageId }, senderJid)
                    }
                }
            }
        }

        collectJob = viewModelScope.launch {
            db.messageDao().messagesForChat(chatJid).collect { entities ->
                val sorted = entities.sortedByDescending { it.timestamp }
                val mapped = mutableListOf<ChatMessage>()
                for (entity in sorted) {
                    mapped.add(entity.toChatMessage())
                }
                _messages.value = mapped
            }
        }
    }

    fun send(text: String, replyTo: ChatMessage? = null) {
        if (text.isBlank() || currentChatJid.isEmpty()) return
        viewModelScope.launch {
            session.sendMessage(
                chatJid = currentChatJid,
                text = text,
                replyToId = replyTo?.messageId,
                replyToSenderJid = replyTo?.senderJid,
                replyToText = replyTo?.text
            )
        }
    }

    fun sendImage(localPath: String, caption: String = "", thumbnail: ByteArray? = null) {
        if (currentChatJid.isEmpty()) return
        viewModelScope.launch { session.sendImageMessage(currentChatJid, localPath, caption, thumbnail) }
    }

    fun sendVideo(localPath: String, caption: String = "", thumbnail: ByteArray? = null) {
        if (currentChatJid.isEmpty()) return
        viewModelScope.launch { session.sendVideoMessage(currentChatJid, localPath, caption, thumbnail) }
    }

    fun downloadMedia(message: ChatMessage) {
        val media = message.media ?: return
        if (media.isDownloaded) return
        viewModelScope.launch {
            val ext = if (media.type == com.whatsgo.app.ui.chat.models.MediaType.VIDEO) "mp4" else "jpg"
            val outFile = java.io.File(getApplication<Application>().cacheDir, "dl_${message.messageId}.$ext")
            withContext(Dispatchers.IO) {
                runCatching {
                    session.downloadMedia(
                        mediaType = if (media.type == com.whatsgo.app.ui.chat.models.MediaType.VIDEO) "video" else "image",
                        url = media.remoteUrl ?: "",
                        directPath = media.directPath ?: "",
                        mediaKey = media.mediaKey ?: ByteArray(0),
                        fileEncSha256 = media.fileEncSha256 ?: ByteArray(0),
                        fileSha256 = media.fileSha256 ?: ByteArray(0),
                        fileLength = media.fileLength ?: 0L,
                        messageId = message.messageId,
                        outPath = outFile.absolutePath
                    )
                }
            }
        }
    }

    private suspend fun MessageEntity.toChatMessage(): ChatMessage {
        val senderName = if (!fromMe) resolveSenderName(senderJid) else ""

        val replyPreview = quotedId?.let { qid ->
            val originalMsg = db.messageDao().getById(qid)
            val quotedName = when {
                originalMsg != null && originalMsg.fromMe -> "You"
                originalMsg != null -> resolveSenderName(originalMsg.senderJid)
                quotedSenderJid != null && isOwnJid(quotedSenderJid) -> "You"
                quotedSenderJid != null -> resolveSenderName(quotedSenderJid)
                else -> ""
            }
            ReplyPreview(
                senderName = quotedName.ifBlank { "Unknown" },
                text = originalMsg?.text ?: (quotedText ?: "")
            )
        }

        val media = mediaType?.let { type ->
            MediaAttachment(
                type = if (type == "video") com.whatsgo.app.ui.chat.models.MediaType.VIDEO else com.whatsgo.app.ui.chat.models.MediaType.IMAGE,
                urlOrPath = mediaLocalPath ?: "",
                isDownloaded = mediaLocalPath != null,
                thumbnail = mediaThumbnail,
                remoteUrl = mediaUrl,
                directPath = mediaDirectPath,
                mediaKey = mediaKey,
                fileEncSha256 = mediaFileEncSha256,
                fileSha256 = mediaFileSha256,
                fileLength = mediaFileLength
            )
        }

        val mappedStatus = when {
            !fromMe -> MessageStatus.DELIVERED
            this.status == "read" -> MessageStatus.READ
            this.status == "delivered" -> MessageStatus.DELIVERED
            this.status == "sending" -> MessageStatus.SENDING
            else -> MessageStatus.SENT
        }

        return ChatMessage(
            id = timestamp.toInt() xor messageId.hashCode(),
            messageId = messageId,
            senderJid = senderJid,
            senderName = senderName,
            text = if (media != null) (mediaCaption ?: "") else text,
            time = formatTime(timestamp),
            isMine = fromMe,
            status = mappedStatus,
            replyTo = replyPreview,
            media = media
        )
    }

    private fun isOwnJid(jid: String): Boolean {
        val ownUser = session.ownJID().substringBefore(":").substringBefore("@")
        val otherUser = jid.substringBefore(":").substringBefore("@")
        return ownUser.isNotBlank() && ownUser == otherUser
    }

    private suspend fun resolveSenderName(jid: String): String {
        db.contactDao().getName(jid)?.let { return it }
        val fetched = withContext(Dispatchers.IO) {
            runCatching { session.contactName(jid) }.getOrNull()
        }
        val name = fetched?.takeIf { it.isNotBlank() } ?: jid.substringBefore("@")
        db.contactDao().upsert(ContactEntity(jid, name))
        return name
    }

    private fun formatTime(epochSeconds: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochSeconds * 1000))
}