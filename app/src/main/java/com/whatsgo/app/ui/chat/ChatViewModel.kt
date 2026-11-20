package com.whatsgo.app.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.whatsgo.app.core.LoginEvent
import com.whatsgo.app.core.PhoneFormatter
import com.whatsgo.app.core.SessionHolder
import com.whatsgo.app.core.VoiceRecorder
import com.whatsgo.app.core.db.AppDatabase
import com.whatsgo.app.core.db.MessageRow
import com.whatsgo.app.core.db.StickerEntity
import com.whatsgo.app.ui.chat.models.ChatMessage
import com.whatsgo.app.ui.chat.models.MediaAttachment
import com.whatsgo.app.ui.chat.models.MediaType
import com.whatsgo.app.ui.chat.models.MessageStatus
import com.whatsgo.app.ui.chat.models.ReplyPreview
import com.whatsgo.app.ui.settings.data.SettingsRepository
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import com.whatsgo.app.core.GroupInfoData
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

private const val TYPING_HEARTBEAT_MS = 3000L
private const val TYPING_IDLE_TIMEOUT_MS = 4000L
private const val PAGE_SIZE = 40

private class Page(
    val limit: Int,
    val count: Int,
    val hasUnread: Boolean,
    val items: List<ChatMessage>
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val stickerDao = db.stickerDao()
    private val session = SessionHolder.get(app)
    private val settingsRepo = SettingsRepository(app)
    private val recorder = VoiceRecorder(app)

    private var currentChatJid: String = ""
    private var collectJob: Job? = null

    private val _isGroup = MutableStateFlow(false)
    val isGroup: StateFlow<Boolean> = _isGroup.asStateFlow()
    private val _groupInfo = MutableStateFlow<GroupInfoData?>(null)
    val groupInfo: StateFlow<GroupInfoData?> = _groupInfo.asStateFlow()
    val channelLoading: StateFlow<Boolean> = session.channelLoading

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _presenceSubtitle = MutableStateFlow<String?>(null)
    val presenceSubtitle: StateFlow<String?> = _presenceSubtitle.asStateFlow()
    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()
    private val _recordSeconds = MutableStateFlow(0)
    val recordSeconds: StateFlow<Int> = _recordSeconds.asStateFlow()
    private var recordTimerJob: Job? = null

    private var typingResetJob: Job? = null
    private var outgoingTypingJob: Job? = null
    private var presenceCollectJob: Job? = null
    private var readJob: Job? = null

    @Volatile private var lastTypingInputAt = 0L
    private var cachedPresenceStatus: String? = null

    private val limitFlow = MutableStateFlow(PAGE_SIZE)
    @Volatile private var hasMore = false

    private val nameCache = ConcurrentHashMap<String, String>()
    private val stickerRequested: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val favoriteStickers: StateFlow<List<StickerEntity>> = stickerDao.observeFavorites().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentStickers: StateFlow<List<StickerEntity>> = stickerDao.observeRecent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val receivedStickers: StateFlow<List<StickerEntity>> = stickerDao.observeReceived().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val resolving: MutableSet<String> = ConcurrentHashMap.newKeySet()
    @Volatile private var ownUser = ""

    private val timeFmt = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("HH:mm", Locale.getDefault())
    }

    fun open(chatJid: String) {
        val sameChat = (currentChatJid == chatJid)
        currentChatJid = chatJid
        if (!sameChat) {
            if (chatJid.endsWith("@newsletter")) {
                viewModelScope.launch(Dispatchers.IO) { session.openChannel(chatJid) }
            } else {
                viewModelScope.launch(Dispatchers.IO) { session.watchPresence(chatJid) }
            }
            collectJob?.cancel()
            presenceCollectJob?.cancel()
            typingResetJob?.cancel()
            cachedPresenceStatus = null
            _presenceSubtitle.value = null
            session.cachedPresence(chatJid)?.let {
                val s = statusFrom(it.online, it.lastSeen)
                cachedPresenceStatus = s
                _presenceSubtitle.value = s
            }
            _messages.value = emptyList()
            val group = chatJid.endsWith("@g.us")
            _isGroup.value = group
            _groupInfo.value = if (group) session.cachedGroupInfo(chatJid) else null
            if (group) {
                viewModelScope.launch {
                    session.fetchGroupInfo(chatJid)?.let { if (currentChatJid == chatJid) _groupInfo.value = it }
                }
            }
            hasMore = false
            limitFlow.value = PAGE_SIZE
            startPresenceCollect()
            startMessageCollect(chatJid)
        }
        markIncomingAsRead(chatJid)
    }

    fun loadMore() {
        if (!hasMore) return
        hasMore = false
        limitFlow.value = limitFlow.value + PAGE_SIZE
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private fun startMessageCollect(chatJid: String) {
        collectJob = viewModelScope.launch {
            val cache = HashMap<String, ChatMessage>()
            limitFlow
                .flatMapLatest { limit ->
                    db.messageDao().observeRecent(chatJid, limit)
                        .debounce(120)
                        .map { limit to it }
                }
                .map { (limit, rows) ->
                    Page(
                        limit = limit,
                        count = rows.size,
                        hasUnread = rows.any { !it.fromMe && !it.isRead },
                        items = mapRows(rows, cache)
                    )
                }
                .flowOn(Dispatchers.Default)
                .collect { page ->
                    hasMore = page.count >= page.limit
                    _messages.value = page.items
                    page.items.filter { it.media?.type == MediaType.STICKER && !it.media.isDownloaded }
                        .forEach { if (stickerRequested.add(it.messageId)) downloadMedia(it) }
                    if (page.hasUnread && currentChatJid == chatJid) markIncomingAsRead(chatJid)
                }
        }
    }

    private fun startPresenceCollect() {
        presenceCollectJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            session.events.collect { event ->
                when (event) {
                    is LoginEvent.ChatPresenceUpdated -> {
                        if (isMatchingChat(event.chatJid, currentChatJid)) {
                            if (event.state == "composing") {
                                typingResetJob?.cancel()
                                _presenceSubtitle.value =
                                    if (event.media == "audio") "recording audio..." else "typing..."
                                typingResetJob = launch {
                                    delay(3000)
                                    _presenceSubtitle.value = cachedPresenceStatus
                                }
                            } else if (event.state == "paused") {
                                typingResetJob?.cancel()
                                _presenceSubtitle.value = cachedPresenceStatus
                            }
                        }
                    }
                    is LoginEvent.PresenceUpdated -> {
                        if (isMatchingChat(event.senderJid, currentChatJid)) {
                            typingResetJob?.cancel()
                            val newStatus = statusFrom(event.online, event.lastSeen)
                                ?: cachedPresenceStatus?.takeIf { it != "online" }
                            cachedPresenceStatus = newStatus
                            _presenceSubtitle.value = newStatus
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun mapRows(rows: List<MessageRow>, cache: HashMap<String, ChatMessage>): List<ChatMessage> {
        val out = ArrayList<ChatMessage>(rows.size)
        val ids = HashSet<String>(rows.size * 2)
        for (r in rows) {
            val fresh = r.toChatMessage()
            val old = cache[r.messageId]
            val msg = if (old != null && old == fresh) old else fresh
            cache[r.messageId] = msg
            ids.add(r.messageId)
            out.add(msg)
        }
        cache.keys.retainAll(ids)
        return out
    }

    private fun MessageRow.toChatMessage(): ChatMessage {
        val name = if (fromMe) "" else displayName(senderJid, senderName)

        val reply = quotedId?.let {
            val quoter = when {
                origSenderJid != null && origFromMe == true -> "You"
                origSenderJid != null -> displayName(origSenderJid, origSenderName)
                quotedSenderJid != null && isOwnJid(quotedSenderJid) -> "You"
                quotedSenderJid != null -> displayName(quotedSenderJid, null)
                else -> "Unknown"
            }
            ReplyPreview(
                senderName = quoter.ifBlank { "Unknown" },
                text = origText ?: (quotedText ?: ""),
                hasMedia = origMediaType != null
            )
        }

        val media = mediaType?.let { type ->
            MediaAttachment(
                type = when (type) {
                    "video" -> MediaType.VIDEO
                    "voice", "audio" -> MediaType.VOICE
                    "sticker" -> MediaType.STICKER
                    else -> MediaType.IMAGE
                },
                urlOrPath = mediaLocalPath ?: "",
                isDownloaded = mediaLocalPath != null,
                thumbnail = mediaThumbnail,
                durationText = mediaSeconds?.let { "%d:%02d".format(it / 60, it % 60) },
                waveform = mediaWaveform,
                animated = mediaAnimated ?: false
            )
        }

        val mapped = when {
            !fromMe -> MessageStatus.DELIVERED
            status == "read" -> MessageStatus.READ
            status == "delivered" -> MessageStatus.DELIVERED
            status == "sending" -> MessageStatus.SENDING
            else -> MessageStatus.SENT
        }

        return ChatMessage(
            id = timestamp.toInt() xor messageId.hashCode(),
            messageId = messageId,
            senderJid = senderJid,
            senderName = name,
            text = if (media != null) (mediaCaption ?: "") else text,
            time = timeFmt.get()!!.format(Date(timestamp * 1000)),
            isMine = fromMe,
            status = mapped,
            replyTo = reply,
            media = media
        )
    }

    private fun displayName(jid: String, dbName: String?): String {
        if (jid.endsWith("@newsletter")) return ""
        dbName?.takeIf { it.isNotBlank() }?.let { return it }
        nameCache[jid]?.let { return it }
        requestName(jid)
        return PhoneFormatter.format(getApplication(), jid)
    }

    private fun requestName(jid: String) {
        if (!resolving.add(jid)) return
        viewModelScope.launch(Dispatchers.IO) {
            val fetched = runCatching { session.contactName(jid) }.getOrNull()
                ?.takeIf { it.isNotBlank() && !PhoneFormatter.isPhoneLike(it) }
            if (fetched != null) {
                nameCache[jid] = fetched
                // insertNameIfAbsent: no REPLACE, so avatarPath is not wiped.
                // Write triggers re-emit, JOIN then gives the name.
                runCatching { db.contactDao().insertNameIfAbsent(jid, fetched) }
            }
        }
    }

    private fun isOwnJid(jid: String): Boolean {
        if (ownUser.isBlank()) {
            ownUser = runCatching { session.ownJID().substringBefore(":").substringBefore("@") }
                .getOrDefault("")
        }
        val other = jid.substringBefore(":").substringBefore("@")
        return ownUser.isNotBlank() && ownUser == other
    }

    private suspend fun isMatchingChat(jid1: String, jid2: String): Boolean {
        if (jid1.isBlank() || jid2.isBlank()) return false
        val clean1 = jid1.substringBefore(":").substringBefore("@")
        val clean2 = jid2.substringBefore(":").substringBefore("@")
        if (clean1 == clean2) return true
        // name fallback only for unnormalized LID, off main thread
        if (!jid1.endsWith("@lid") && !jid2.endsWith("@lid")) return false
        return withContext(Dispatchers.IO) {
            val name1 = runCatching { session.contactName(jid1) }.getOrNull()?.trim()
            val name2 = runCatching { session.contactName(jid2) }.getOrNull()?.trim()
            !name1.isNullOrEmpty() && !name2.isNullOrEmpty() && name1.equals(name2, ignoreCase = true)
        }
    }

    private fun statusFrom(online: Boolean, lastSeen: Long): String? = when {
        online -> "online"
        lastSeen > 0L -> "last seen ${formatLastSeen(lastSeen)}"
        else -> null
    }

    private fun formatLastSeen(epochSeconds: Long): String {
        val date = Date(epochSeconds * 1000)
        val calMsg = Calendar.getInstance().apply { time = date }
        val calNow = Calendar.getInstance()
        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date)

        val isToday = calMsg.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
                calMsg.get(Calendar.DAY_OF_YEAR) == calNow.get(Calendar.DAY_OF_YEAR)

        val calYesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val isYesterday = calMsg.get(Calendar.YEAR) == calYesterday.get(Calendar.YEAR) &&
                calMsg.get(Calendar.DAY_OF_YEAR) == calYesterday.get(Calendar.DAY_OF_YEAR)

        return when {
            isToday -> "today at $timeStr"
            isYesterday -> "yesterday at $timeStr"
            else -> "${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)} at $timeStr"
        }
    }

    fun onUserTyping(text: String) {
        val chatJid = currentChatJid
        if (chatJid.isEmpty()) return

        if (text.isBlank()) {
            stopOutgoingTyping(chatJid)
            return
        }

        lastTypingInputAt = System.currentTimeMillis()
        if (outgoingTypingJob?.isActive == true) return

        outgoingTypingJob = viewModelScope.launch(Dispatchers.IO) {
            if (!settingsRepo.getBoolean("send_typing_presence", true)) return@launch
            session.sendChatPresence(chatJid, "composing")
            while (isActive) {
                delay(1000)
                val idleMs = System.currentTimeMillis() - lastTypingInputAt
                if (idleMs >= TYPING_IDLE_TIMEOUT_MS) {
                    session.sendChatPresence(chatJid, "paused")
                    break
                }
                if (idleMs >= TYPING_HEARTBEAT_MS) {
                    session.sendChatPresence(chatJid, "composing")
                }
            }
        }
    }

    private fun stopOutgoingTyping(chatJid: String) {
        val wasTyping = outgoingTypingJob?.isActive == true
        outgoingTypingJob?.cancel()
        outgoingTypingJob = null
        lastTypingInputAt = 0L
        if (!wasTyping) return
        viewModelScope.launch(Dispatchers.IO) {
            if (settingsRepo.getBoolean("send_typing_presence", true)) {
                session.sendChatPresence(chatJid, "paused")
            }
        }
    }

    fun close() {
        finishRecording(send = false)
        VoicePlayer.stop()
        session.closeChannel()
        session.watchPresence(null)
        if (currentChatJid.isNotEmpty()) {
            stopOutgoingTyping(currentChatJid)
        }
        currentChatJid = ""
        collectJob?.cancel()
        collectJob = null
        presenceCollectJob?.cancel()
        presenceCollectJob = null
        typingResetJob?.cancel()
        typingResetJob = null
        readJob?.cancel()
        readJob = null
        _presenceSubtitle.value = null
    }

    private fun markIncomingAsRead(chatJid: String) {
        if (readJob?.isActive == true) return
        readJob = viewModelScope.launch {
            if (chatJid.endsWith("@newsletter")) {
                db.messageDao().markChatRead(chatJid)
                return@launch
            }
            val readReceiptsEnabled = settingsRepo.getBoolean("read_receipts", true)
            val unread = if (readReceiptsEnabled) {
                db.messageDao().getUnreadIncoming(chatJid)
            } else emptyList()

            db.messageDao().markChatRead(chatJid)

            if (unread.isNotEmpty()) {
                val bySender = unread.groupBy { it.senderJid }
                withContext(Dispatchers.IO) {
                    bySender.forEach { (senderJid, refs) ->
                        runCatching {
                            session.markChatReadOnServer(chatJid, refs.map { it.messageId }, senderJid)
                        }
                    }
                }
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

    fun sendSticker(sticker: StickerEntity) {
        val jid = currentChatJid
        if (jid.isNotEmpty()) viewModelScope.launch(Dispatchers.IO) { session.sendSticker(jid, sticker) }
    }

    fun toggleFavoriteSticker(sticker: StickerEntity) {
        viewModelScope.launch(Dispatchers.IO) { stickerDao.setFavorite(sticker.id, !sticker.isFavorite) }
    }

    fun downloadSticker(sticker: StickerEntity) = session.downloadSticker(sticker)

    fun startRecording(): Boolean {
        if (_recording.value || currentChatJid.isEmpty()) return false
        if (!recorder.start(viewModelScope)) return false
        VoicePlayer.stop()
        _recording.value = true
        _recordSeconds.value = 0
        recordTimerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _recordSeconds.value += 1
            }
        }
        val chatJid = currentChatJid
        viewModelScope.launch(Dispatchers.IO) {
            session.sendChatPresence(chatJid, "composing", "audio")
        }
        return true
    }

    fun finishRecording(send: Boolean) {
        if (!_recording.value) return
        recordTimerJob?.cancel()
        recordTimerJob = null
        _recording.value = false
        val chatJid = currentChatJid
        val result = recorder.stop()
        viewModelScope.launch(Dispatchers.IO) {
            session.sendChatPresence(chatJid, "paused", "audio")
        }
        if (result == null) return
        if (send && result.seconds >= 1 && chatJid.isNotEmpty()) {
            session.sendAudioMessage(chatJid, result.path, result.seconds, result.waveform)
        } else {
            File(result.path).delete()
        }
    }

    fun toggleVoice(message: ChatMessage) {
        val media = message.media ?: return
        if (media.isDownloaded) VoicePlayer.toggle(message.messageId, media.urlOrPath)
        else downloadMedia(message)
    }

    fun downloadMedia(message: ChatMessage) {
        val media = message.media ?: return
        if (media.isDownloaded) return
        viewModelScope.launch(Dispatchers.IO) {
            val e = db.messageDao().getById(message.messageId) ?: return@launch
            val mediaType = e.mediaType ?: "image"
            val ext = when (mediaType) {
                "video" -> "mp4"
                "voice", "audio" -> "ogg"
                "sticker" -> "webp"
                else -> "jpg"
            }
            val baseDir = if (mediaType == "sticker") File(getApplication<Application>().filesDir, "stickers").apply { mkdirs() }
            else getApplication<Application>().cacheDir
            val outFile = File(baseDir, "dl_${e.messageId}.$ext")
            runCatching {
                session.downloadMedia(
                    mediaType = mediaType,
                    url = e.mediaUrl ?: "",
                    directPath = e.mediaDirectPath ?: "",
                    mediaKey = e.mediaKey ?: ByteArray(0),
                    fileEncSha256 = e.mediaFileEncSha256 ?: ByteArray(0),
                    fileSha256 = e.mediaFileSha256 ?: ByteArray(0),
                    fileLength = e.mediaFileLength ?: 0L,
                    messageId = e.messageId,
                    outPath = outFile.absolutePath
                )
            }
        }
    }
}