package com.whatsgo.app.core

import android.content.Context
import java.io.File
import android.util.Log
import androidx.room.withTransaction
import com.whatsgo.app.core.db.AppDatabase
import com.whatsgo.app.core.db.ContactEntity
import com.whatsgo.app.core.db.MessageEntity
import com.whatsgo.app.core.db.StatusEntity
import com.whatsgo.app.core.db.StatusViewerEntity
import com.whatsgo.app.core.db.StickerEntity
import com.whatsgo.app.ui.chat.models.ReplyPreview
import engine.Client
import engine.Engine
import engine.LoginCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.whatsgo.app.core.HistorySyncService
import com.whatsgo.app.ui.settings.data.SettingsRepository
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

import kotlinx.coroutines.Job
import kotlin.time.Duration.Companion.milliseconds

sealed class LoginEvent {
    data object Idle : LoginEvent()
    data class QrReady(val payload: String) : LoginEvent()
    data class PairingReady(val code: String) : LoginEvent()
    data object Connected : LoginEvent()
    data class LoggedOut(val reason: String) : LoginEvent()
    data class Error(val message: String) : LoginEvent()
    data class OwnNameUpdated(val name: String) : LoginEvent()
    data class OwnAboutUpdated(val about: String) : LoginEvent()
    data class OwnAvatarChanged(val removed: Boolean) : LoginEvent()
    data class ChatPresenceUpdated(val chatJid: String, val senderJid: String, val state: String, val media: String) : LoginEvent()
    data class PresenceUpdated(val senderJid: String, val online: Boolean, val lastSeen: Long) : LoginEvent()
}

class RevokeTarget(val messageId: String, val senderJid: String, val fromMe: Boolean)
private const val STATUS_TTL_MS = 24L * 60 * 60 * 1000

class ChatSession(dbPath: String, context: Context) {

    private val appContext = context.applicationContext
    private val _events = kotlinx.coroutines.flow.MutableSharedFlow<LoginEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val events: kotlinx.coroutines.flow.SharedFlow<LoginEvent> = _events.asSharedFlow()

    private val db = AppDatabase.get(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private class Incoming(
        val entity: MessageEntity,
        val chatJid: String,
        val senderJid: String,
        val fromMe: Boolean,
        val pushName: String
    )

    private data class IncomingStatus(
        val id: String,
        val senderJid: String,
        val senderName: String,
        val timestamp: Long,
        val fromMe: Boolean,
        val mediaType: String,
        val text: String,
        val backgroundColor: Long,
        val url: String,
        val directPath: String,
        val mediaKey: ByteArray,
        val fileEncSHA256: ByteArray,
        val fileSHA256: ByteArray,
        val fileLength: Long,
        val mimetype: String,
        val thumbnail: ByteArray
    )


    private sealed class Op {
        class Msg(val m: Incoming) : Op()
        class ContactName(val jid: String, val name: String) : Op()
        class Status(val id: String, val status: String) : Op()
        class StatusMsg(val s: IncomingStatus) : Op()
        class StatusViewed(val statusId: String, val viewerJid: String) : Op()
        class HistoryChat(val chatJid: String, val name: String, val unread: Int) : Op()
        class ChatRead(val chatJid: String, val ts: Long) : Op()
        data object OfflineDone : Op()

        class Revoke(val id: String) : Op()
        class Pin(val id: String, val pinned: Boolean, val at: Long, val exp: Long) : Op()
        class Forwarded(val id: String) : Op()
        class Receipt(val id: String, val jid: String, val kind: String, val ts: Long) : Op()
    }

    private val http = okhttp3.OkHttpClient()

    private val ops = Channel<Op>(Channel.UNLIMITED)
    private val avatarRefreshQueue = Channel<String>(Channel.UNLIMITED)
    private val avatarQueued: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private val stickerDownloading: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val subscribed: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    @Volatile private var watchedJid: String? = null
    private val presenceMutex = kotlinx.coroutines.sync.Mutex()

    private val presenceOnline = AtomicBoolean(false)
    private val connected = AtomicBoolean(false)
    private var presenceJob: kotlinx.coroutines.Job? = null
    private val appForeground = AtomicBoolean(false)
    private val prefs = appContext.getSharedPreferences("whatsgo_sync", Context.MODE_PRIVATE)
    private val settingsRepo = SettingsRepository(appContext)

    private val _uploadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val uploadProgress: StateFlow<Map<String, Int>> = _uploadProgress.asStateFlow()
    private val cancelledUploads: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val fetchStarted = AtomicBoolean(false)

    private val _historyProgress = MutableStateFlow<Int?>(null)
    val historyProgress: StateFlow<Int?> = _historyProgress

    data class PresenceInfo(val online: Boolean, val lastSeen: Long)
    private val presenceCache = ConcurrentHashMap<String, PresenceInfo>()

    private fun presenceKey(jid: String) = jid.substringBefore(":").substringBefore("@")

    private val callback = object : LoginCallback {
        override fun onQrCode(payload: String) { _events.tryEmit(LoginEvent.QrReady(payload)) }
        override fun onPairingCode(code: String) { _events.tryEmit(LoginEvent.PairingReady(code)) }
        override fun onConnected() {
            connected.set(true)
            presenceOnline.set(false);
            subscribed.clear()
            HistorySyncService.markConnected()
            _events.tryEmit(LoginEvent.Connected)
            refreshPresence()
            if (fetchStarted.compareAndSet(false, true)) runPostConnectTasks()
        }
        override fun onOwnName(name: String) { _events.tryEmit(LoginEvent.OwnNameUpdated(name)) }
        override fun onOwnAbout(about: String) { _events.tryEmit(LoginEvent.OwnAboutUpdated(about)) }
        override fun onOwnAvatarChanged(pictureID: String, removed: Boolean) { _events.tryEmit(LoginEvent.OwnAvatarChanged(removed)) }
        override fun onLoggedOut(reason: String) {
            connected.set(false)
            fetchStarted.set(false)
            presenceOnline.set(false)
            subscribed.clear()
            _events.tryEmit(LoginEvent.LoggedOut(reason))
        }
        override fun onError(msg: String) { _events.tryEmit(LoginEvent.Error(msg)) }
        override fun onMessageStatus(messageID: String, status: String) { ops.trySend(Op.Status(messageID, status)) }

        override fun onUploadProgress(localID: String, percent: Long) {
            if (localID in cancelledUploads) return
            _uploadProgress.update { if (it.containsKey(localID)) it + (localID to percent.toInt()) else it }
        }

        override fun onOfflineSyncPreview(total: Long, messages: Long, notifications: Long, receipts: Long) {
            HistorySyncService.startOffline(appContext, messages.toInt())
        }

        override fun onOfflineSyncCompleted(count: Long) {
            ops.trySend(Op.OfflineDone)
        }

        override fun onChannelEvent(kind: String, jid: String) {
            scope.launch { syncChannels() }
        }

        override fun onContactName(jid: String, name: String) {
            ops.trySend(Op.ContactName(jid, name))
        }

        override fun onChatRead(chatJid: String, timestamp: Long) {
            ops.trySend(Op.ChatRead(chatJid, timestamp))
        }

        override fun onChatPresence(chatJid: String, senderJid: String, state: String, media: String) {
            _events.tryEmit(LoginEvent.ChatPresenceUpdated(chatJid, senderJid, state, media))
        }

        override fun onPresence(senderJid: String, online: Boolean, lastSeen: Long) {
            val key = presenceKey(senderJid)
            val prevSeen = presenceCache[key]?.lastSeen ?: prefs.getLong("last_seen_$key", 0L)
            val seen = if (lastSeen > 0L) lastSeen else prevSeen
            if (lastSeen > 0L) prefs.edit().putLong("last_seen_$key", lastSeen).apply()
            presenceCache[key] = PresenceInfo(online, seen)
            _events.tryEmit(LoginEvent.PresenceUpdated(senderJid, online, lastSeen))
        }

        override fun onMessage(
            chatJid: String, senderJid: String, messageId: String, text: String,
            timestamp: Long, fromMe: Boolean, quotedId: String, quotedText: String,
            senderPushName: String, quotedSenderJid: String
        ) {
            ops.trySend(
                Op.Msg(
                    Incoming(
                        MessageEntity(
                            messageId = messageId, chatJid = chatJid, senderJid = senderJid,
                            text = text, timestamp = timestamp, fromMe = fromMe, isRead = fromMe,
                            quotedId = quotedId.ifBlank { null },
                            quotedText = quotedText.ifBlank { null },
                            quotedSenderJid = quotedSenderJid.ifBlank { null }
                        ),
                        chatJid, senderJid, fromMe, senderPushName
                    )
                )
            )
        }

        override fun onHistoryContact(jid: String, name: String) {
            scope.launch { db.contactDao().insertNameIfAbsent(jid, name) }
        }

        override fun onHistoryChat(chatJid: String, name: String, unreadCount: Long) {
            HistorySyncService.report(_historyProgress.value, chatDone = true)
            ops.trySend(Op.HistoryChat(chatJid, name, unreadCount.toInt()))
        }

        override fun onHistorySync(syncType: String, progress: Long) {
            val p = if (progress in 0..100) progress.toInt() else null
            _historyProgress.value = p
            HistorySyncService.report(p)
        }

        override fun onMediaMessage(
            chatJid: String, senderJid: String, messageId: String,
            mediaType: String, caption: String, timestamp: Long, fromMe: Boolean,
            url: String, directPath: String,
            mediaKey: ByteArray?, fileEncSHA256: ByteArray?, fileSHA256: ByteArray?,
            fileLength: Long, mimetype: String, thumbnail: ByteArray?,
            quotedId: String, quotedText: String, quotedSenderJid: String,
            senderPushName: String
        ) {
            ops.trySend(Op.Msg(
                Incoming(
                    MessageEntity(
                        messageId = messageId, chatJid = chatJid, senderJid = senderJid,
                        text = caption, timestamp = timestamp, fromMe = fromMe, isRead = fromMe,
                        quotedId = quotedId.ifBlank { null },
                        quotedText = quotedText.ifBlank { null },
                        quotedSenderJid = quotedSenderJid.ifBlank { null },
                        mediaType = mediaType,
                        mediaCaption = caption.ifBlank { null },
                        mediaMimetype = mimetype,
                        mediaUrl = url,
                        mediaDirectPath = directPath,
                        mediaKey = mediaKey ?: ByteArray(0),
                        mediaFileEncSha256 = fileEncSHA256 ?: ByteArray(0),
                        mediaFileSha256 = fileSHA256 ?: ByteArray(0),
                        mediaFileLength = fileLength,
                        mediaThumbnail = thumbnail?.takeIf { it.isNotEmpty() },
                        mediaLocalPath = null
                    ),
                    chatJid, senderJid, fromMe, senderPushName
                )
            )
            )
        }

        override fun onDocumentMessage(
            chatJid: String, senderJid: String, messageId: String,
            caption: String, fileName: String, timestamp: Long, fromMe: Boolean,
            url: String, directPath: String,
            mediaKey: ByteArray?, fileEncSHA256: ByteArray?, fileSHA256: ByteArray?,
            fileLength: Long, mimetype: String,
            quotedId: String, quotedText: String, quotedSenderJid: String,
            senderPushName: String
        ) {
            ops.trySend(Op.Msg(
                Incoming(
                    MessageEntity(
                        messageId = messageId, chatJid = chatJid, senderJid = senderJid,
                        text = caption, timestamp = timestamp, fromMe = fromMe, isRead = fromMe,
                        quotedId = quotedId.ifBlank { null },
                        quotedText = quotedText.ifBlank { null },
                        quotedSenderJid = quotedSenderJid.ifBlank { null },
                        mediaType = "document",
                        mediaCaption = caption.ifBlank { null },
                        mediaMimetype = mimetype,
                        mediaUrl = url,
                        mediaDirectPath = directPath,
                        mediaKey = mediaKey ?: ByteArray(0),
                        mediaFileEncSha256 = fileEncSHA256 ?: ByteArray(0),
                        mediaFileSha256 = fileSHA256 ?: ByteArray(0),
                        mediaFileLength = fileLength,
                        mediaFileName = fileName.ifBlank { "Document" },
                        mediaLocalPath = null
                    ),
                    chatJid, senderJid, fromMe, senderPushName
                )
            ))
        }

        override fun onAudioMessage(
            chatJid: String, senderJid: String, messageId: String,
            timestamp: Long, fromMe: Boolean, url: String, directPath: String,
            mediaKey: ByteArray?, fileEncSHA256: ByteArray?, fileSHA256: ByteArray?,
            fileLength: Long, mimetype: String, seconds: Long, ptt: Boolean, waveform: ByteArray?,
            quotedId: String, quotedText: String, quotedSenderJid: String, senderPushName: String
        ) {
            ops.trySend(Op.Msg(
                Incoming(
                    MessageEntity(
                        messageId = messageId, chatJid = chatJid, senderJid = senderJid,
                        text = "", timestamp = timestamp, fromMe = fromMe, isRead = fromMe,
                        quotedId = quotedId.ifBlank { null },
                        quotedText = quotedText.ifBlank { null },
                        quotedSenderJid = quotedSenderJid.ifBlank { null },
                        mediaType = if (ptt) "voice" else "audio",
                        mediaMimetype = mimetype,
                        mediaUrl = url,
                        mediaDirectPath = directPath,
                        mediaKey = mediaKey ?: ByteArray(0),
                        mediaFileEncSha256 = fileEncSHA256 ?: ByteArray(0),
                        mediaFileSha256 = fileSHA256 ?: ByteArray(0),
                        mediaFileLength = fileLength,
                        mediaSeconds = seconds.toInt(),
                        mediaWaveform = waveform?.takeIf { it.isNotEmpty() }
                    ),
                    chatJid, senderJid, fromMe, senderPushName
                )
            ))
        }

        override fun onStickerMessage(
            chatJid: String, senderJid: String, messageId: String, timestamp: Long, fromMe: Boolean,
            url: String, directPath: String, mediaKey: ByteArray?, fileEncSHA256: ByteArray?, fileSHA256: ByteArray?,
            fileLength: Long, mimetype: String, animated: Boolean, width: Long, height: Long,
            quotedId: String, quotedText: String, quotedSenderJid: String, senderPushName: String
        ) {
            ops.trySend(Op.Msg(Incoming(MessageEntity(
                messageId = messageId, chatJid = chatJid, senderJid = senderJid, text = "", timestamp = timestamp,
                fromMe = fromMe, isRead = fromMe, quotedId = quotedId.ifBlank { null }, quotedText = quotedText.ifBlank { null },
                quotedSenderJid = quotedSenderJid.ifBlank { null }, mediaType = "sticker", mediaMimetype = mimetype,
                mediaUrl = url, mediaDirectPath = directPath, mediaKey = mediaKey ?: ByteArray(0),
                mediaFileEncSha256 = fileEncSHA256 ?: ByteArray(0), mediaFileSha256 = fileSHA256 ?: ByteArray(0),
                mediaFileLength = fileLength, mediaAnimated = animated
            ), chatJid, senderJid, fromMe, senderPushName)))
            if (!fromMe) saveSticker(url, directPath, mediaKey, fileEncSHA256, fileSHA256, fileLength, mimetype, animated, width, height, false)
        }

        override fun onFavoriteSticker(
            url: String, directPath: String, mediaKey: ByteArray?, fileEncSHA256: ByteArray?,
            fileLength: Long, mimetype: String, animated: Boolean, width: Long, height: Long
        ) = saveSticker(url, directPath, mediaKey, fileEncSHA256, null, fileLength, mimetype, animated, width, height, true)

        override fun onStatusMessage(
            statusID: String, senderJID: String, senderName: String, timestamp: Long,
            fromMe: Boolean, mediaType: String, text: String, backgroundColor: Long,
            url: String, directPath: String, mediaKey: ByteArray?, fileEncSHA256: ByteArray?,
            fileSHA256: ByteArray?, fileLength: Long, mimetype: String, thumbnail: ByteArray?
        ) {
            ops.trySend(
                Op.StatusMsg(
                    IncomingStatus(
                        id = statusID,
                        senderJid = senderJID,
                        senderName = senderName,
                        timestamp = timestamp,
                        fromMe = fromMe,
                        mediaType = mediaType,
                        text = text,
                        backgroundColor = backgroundColor,
                        url = url,
                        directPath = directPath,
                        mediaKey = mediaKey ?: ByteArray(0),
                        fileEncSHA256 = fileEncSHA256 ?: ByteArray(0),
                        fileSHA256 = fileSHA256 ?: ByteArray(0),
                        fileLength = fileLength,
                        mimetype = mimetype,
                        thumbnail = thumbnail ?: ByteArray(0)
                    )
                )
            )
        }

        override fun onStatusViewed(statusID: String, viewerJID: String) {
            ops.trySend(Op.StatusViewed(statusID, viewerJID))
        }
        override fun onMessageRevoked(chatJID: String, messageID: String, byJID: String) { ops.trySend(Op.Revoke(messageID)) }
        override fun onMessagePinned(chatJID: String, messageID: String, pinned: Boolean, pinnedAt: Long, expiresAt: Long) {
            ops.trySend(Op.Pin(messageID, pinned, pinnedAt, expiresAt))
        }
        override fun onMessageForwarded(messageID: String) { ops.trySend(Op.Forwarded(messageID)) }
        override fun onReceiptInfo(messageID: String, jid: String, kind: String, timestamp: Long) {
            ops.trySend(Op.Receipt(messageID, jid, kind, timestamp))
        }
    }

    private val client: Client = Engine.newClient(dbPath, callback)

    init {

        scope.launch { runCatching { db.messageDao().failStaleSending() } }

        scope.launch {
            val batch = ArrayList<Incoming>(500)
            val history = ArrayList<Op.HistoryChat>()
            val statuses = ArrayList<Op.Status>()
            val contacts = ArrayList<Op.ContactName>()
            for (first in ops) {
                try {
                    if (first is Op.Msg || first is Op.Status || first is Op.ContactName) delay(250)
                    var cur: Op? = first
                    while (cur != null) {
                        when (cur) {
                            is Op.HistoryChat -> history.add(cur)
                            is Op.ContactName -> contacts.add(cur)
                            is Op.Status -> statuses.add(cur)
                            is Op.Msg -> {
                                batch.add(cur.m)
                                if (batch.size >= 500) {
                                    flush(batch); flushStatuses(statuses); flushContacts(contacts)
                                }
                            }
                            is Op.ChatRead -> {
                                flush(batch); flushStatuses(statuses); flushContacts(contacts)
                                applyHistory(history)
                                db.messageDao().markChatReadUpTo(cur.chatJid, cur.ts)
                            }
                            is Op.StatusMsg -> {
                                flush(batch)
                                handleStatusMessage(cur.s)
                            }
                            is Op.StatusViewed -> {
                                flush(batch)
                                db.statusDao().insertViewer(
                                    StatusViewerEntity(
                                        key = "${cur.statusId}|${cur.viewerJid}",
                                        statusId = cur.statusId,
                                        viewerJid = cur.viewerJid,
                                        viewedAt = System.currentTimeMillis()
                                    )
                                )
                            }
                            Op.OfflineDone -> {
                                flush(batch); flushStatuses(statuses); flushContacts(contacts)
                                applyHistory(history)
                                HistorySyncService.finishOffline()
                                scope.launch { syncGroupNames() }
                            }
                            is Op.Revoke -> {
                                flush(batch)
                                db.messageDao().markDeleted(cur.id, System.currentTimeMillis() / 1000)
                                db.receiptDao().deleteFor(listOf(cur.id))
                            }
                            is Op.Pin -> {
                                flush(batch)
                                db.messageDao().setPinned(cur.id, cur.pinned, if (cur.pinned) cur.at * 1000 else 0L, cur.exp * 1000)
                            }
                            is Op.Forwarded -> { flush(batch); db.messageDao().setForwarded(cur.id) }
                            is Op.Receipt -> db.receiptDao().upsert(cur.id, cur.jid, cur.ts, if (cur.kind == "read") cur.ts else 0L)
                        }
                        cur = ops.tryReceive().getOrNull()
                    }
                    flush(batch); flushStatuses(statuses); flushContacts(contacts)
                    applyHistory(history)
                } catch (t: Throwable) {
                    Log.e("ChatSession", "ops failed", t)
                    batch.clear(); history.clear(); statuses.clear(); contacts.clear()
                    HistorySyncService.finishOffline()
                }
            }
        }

        scope.launch {
            for (jid in avatarRefreshQueue) {
                avatarQueued.remove(jid)
                if (jid.isBlank() ||
                    jid.endsWith("@broadcast") ||
                    jid.endsWith("@newsletter")
                ) continue

                val existing = runCatching { db.contactDao().getByJid(jid) }.getOrNull()
                val existingPath = existing?.avatarPath
                if (existingPath != null && File(existingPath).exists()) continue

                val lastTry = existing?.avatarUpdatedAt ?: 0L
                if (existingPath == null && lastTry > 0 &&
                    System.currentTimeMillis() - lastTry < 7L * 24 * 60 * 60 * 1000) continue

                runCatching {
                    val url = client.getProfilePictureURL(jid)
                    if (url.isNullOrBlank()) {
                        db.contactDao().updateAvatarPath(jid, null)
                        return@runCatching
                    }

                    val bytes = http
                        .newCall(okhttp3.Request.Builder().url(url).build())
                        .execute()
                        .use { resp ->
                            if (resp.code != 200) throw RuntimeException("avatar ${resp.code}")
                            resp.body?.bytes() ?: throw RuntimeException("empty")
                        }

                    val dir = File(appContext.filesDir, "avatars").apply { mkdirs() }
                    val safe = jid.replace("@", "_at_").replace(".", "_dot_")
                    val file = File(dir, "$safe.jpg")
                    file.writeBytes(bytes)

                    db.contactDao().updateAvatarPath(jid, file.absolutePath)
                }
            }
        }

        scope.launch {
            while (isActive) {
                runCatching { db.statusDao().deleteExpired() }
                delay(60L * 60 * 1000)
            }
        }
    }

    private suspend fun handleStatusMessage(s: IncomingStatus) {
        db.statusDao().upsert(
            StatusEntity(
                id = s.id,
                senderJid = s.senderJid,
                senderName = s.senderName.ifBlank {
                    if (s.fromMe) client.getOwnName().ifBlank { "You" }
                    else runCatching { client.getContactName(s.senderJid) }.getOrDefault("")
                },
                timestamp = s.timestamp,
                fromMe = s.fromMe,
                mediaType = s.mediaType,
                text = s.text,
                backgroundColor = s.backgroundColor,
                url = s.url,
                directPath = s.directPath,
                mediaKey = s.mediaKey,
                fileEncSHA256 = s.fileEncSHA256,
                fileSHA256 = s.fileSHA256,
                fileLength = s.fileLength,
                mimetype = s.mimetype,
                thumbnail = s.thumbnail,
                localPath = null,
                viewed = s.fromMe,
                expiresAt = s.timestamp * 1000L + STATUS_TTL_MS
            )
        )
        if (!s.fromMe && s.senderName.isNotBlank()) {
            runCatching { db.contactDao().insertNameIfAbsent(s.senderJid, s.senderName) }
        }
    }

    private suspend fun applyHistory(list: ArrayList<Op.HistoryChat>) {
        if (list.isEmpty()) return
        db.withTransaction {
            for (h in list) {
                if (h.name.isNotBlank()) db.contactDao().insertNameIfAbsent(h.chatJid, h.name)
                db.messageDao().applyHistoryUnread(h.chatJid, h.unread)
                db.messageDao().markAllSentRead(h.chatJid)
            }
        }
        list.clear()
    }

    private suspend fun flushStatuses(list: ArrayList<Op.Status>) {
        if (list.isEmpty()) return
        db.withTransaction {
            for (s in list) {
                if (s.status == "read_self") db.messageDao().markReadUpTo(s.id)
                else db.messageDao().setStatus(s.id, s.status)
            }
        }
        list.clear()
    }

    private suspend fun flushContacts(list: ArrayList<Op.ContactName>) {
        if (list.isEmpty()) return
        db.withTransaction {
            list.forEach { db.contactDao().upsertName(it.jid, it.name) }
        }
        list.clear()
    }

    private suspend fun flush(batch: ArrayList<Incoming>) {
        if (batch.isEmpty()) return
        db.withTransaction {
            val existing = db.messageDao()
                .localState(batch.map { it.entity.messageId })
                .associateBy { it.messageId }
            batch.forEach {
                val e = it.entity
                val old = existing[e.messageId]
                when {
                    old == null -> db.messageDao().insert(e)
                    old.isDeleted -> Unit
                    e.mediaType == null -> Unit
                    else -> db.messageDao().insert(
                        e.copy(
                            isRead = old.isRead || e.isRead,
                            mediaLocalPath = old.mediaLocalPath ?: e.mediaLocalPath,
                            status = old.status,
                            isForwarded = e.isForwarded || old.isForwarded,
                            isPinned = old.isPinned, pinnedAt = old.pinnedAt, pinExpiresAt = old.pinExpiresAt
                        )
                    )
                }
            }
        }
        saveNames(batch)
        HistorySyncService.reportOffline(batch.size)
        batch.clear()
    }

    private suspend fun saveNames(batch: List<Incoming>) {
        val pushNames = HashMap<String, String>()
        for (m in batch) {
            if (m.fromMe || m.pushName.isBlank()) continue
            val isGroup = m.chatJid.endsWith("@g.us")   // no JNI
            if (isGroup) pushNames[m.senderJid] = m.pushName
            else pushNames[m.chatJid] = m.pushName
        }
        if (pushNames.isEmpty()) return
        db.withTransaction {
            pushNames.forEach { (jid, name) -> db.contactDao().insertNameIfAbsent(jid, name) }
        }
    }

    private suspend fun syncGroupNames() {
        runCatching {
            val json = withContext(Dispatchers.IO) { client.getJoinedGroupsJSON() }
            val arr = org.json.JSONArray(json)
            db.withTransaction {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val jid = o.optString("jid"); val name = o.optString("name")
                    if (jid.isNotBlank() && name.isNotBlank()) db.contactDao().upsertName(jid, name)
                }
            }
        }
    }

    private suspend fun syncSavedContacts() {
        runCatching {
            val json = withContext(Dispatchers.IO) { client.getSavedContactsJSON() }
            val arr = org.json.JSONArray(json)
            db.withTransaction {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val jid = o.optString("jid"); val name = o.optString("name")
                    if (jid.isNotBlank() && name.isNotBlank()) db.contactDao().upsertName(jid, name)
                }
            }
        }.onFailure { Log.e("ChatSession", "syncSavedContacts failed: ${it.message}") }
    }

    fun messagesForChat(chatJid: String) = db.messageDao().messagesForChat(chatJid)
    fun allChatJids() = db.messageDao().allChatJids()

    fun beginQrLogin() {
        try { client.startQrLogin() } catch (e: Exception) { _events.tryEmit(LoginEvent.Error(e.message ?: "qr_login_failed")) }
    }
    fun beginPairingLogin(phoneDigits: String) {
        try { client.startPairingLogin(phoneDigits) } catch (e: Exception) { _events.tryEmit(LoginEvent.Error(e.message ?: "pairing_failed")) }
    }
    fun isLoggedIn(): Boolean = client.isLoggedIn()
    fun reconnectIfNeeded() {
        try { client.reconnect() } catch (e: Exception) { _events.tryEmit(LoginEvent.Error(e.message ?: "reconnect_failed")) }
    }

    fun logout(onComplete: (Boolean) -> Unit) {
        scope.launch {
            val serverOk = runCatching { client.logout() }
                .onFailure { Log.e("ChatSession", "server logout failed", it) }
                .isSuccess

            runCatching { db.clearAllTables() }
                .onFailure { Log.e("ChatSession", "clearAllTables failed", it) }

            runCatching {
                File(appContext.filesDir, "avatars").deleteRecursively()
                File(appContext.filesDir, "status_media").deleteRecursively()
                File(appContext.cacheDir, "status").deleteRecursively()
            }.onFailure { Log.e("ChatSession", "delete media failed", it) }

            runCatching {
                File(appContext.filesDir, "whatsgo.db").delete()
                File(appContext.filesDir, "whatsgo.db-shm").delete()
                File(appContext.filesDir, "whatsgo.db-wal").delete()
            }.onFailure { Log.e("ChatSession", "delete session db failed", it) }

            prefs.edit().clear().apply()
            fetchStarted.set(false)

            _events.tryEmit(LoginEvent.LoggedOut("user_requested"))
            onComplete(serverOk)
        }
    }

    fun disconnect() = client.disconnect()

    fun ownPhone(): String = client.getOwnPhone()
    fun ownName(): String = client.getOwnName()
    fun fetchOwnAvatarUrl(): String = client.fetchOwnProfilePictureURL()

    fun contactName(jid: String): String = client.getContactName(jid)
    fun isGroupJID(jid: String): Boolean = client.isGroupJID(jid)
    fun groupName(jid: String): String = client.getGroupName(jid)
    fun ownJID(): String = client.getOwnJID()

    fun requestAvatarRefresh(jid: String) = enqueueAvatar(jid)

    fun markChatReadOnServer(chatJid: String, messageIds: List<String>, senderJid: String): Boolean {
        return try {
            client.markChatRead(chatJid, messageIds.joinToString(","), senderJid)
            true
        } catch (e: Exception) {
            Log.w("ChatSession", "markChatRead failed for $chatJid: ${e.message}")
            false
        }
    }

    fun setAppForeground(foreground: Boolean) {
        appForeground.set(foreground)
        refreshPresence()
    }

    fun refreshPresence() {
        val immediate = !appForeground.get() || connected.get()
        presenceJob?.cancel()
        presenceJob = scope.launch {
            if (!immediate) delay(1200)       // only cold start needs coalescing
            repeat(5) { attempt ->
                if (applyPresence().isSuccess) return@launch
                delay((attempt + 1) * 1000L)
            }
        }
    }

    fun cachedPresence(jid: String): PresenceInfo? {
        val key = presenceKey(jid)
        presenceCache[key]?.let { return it }
        val saved = prefs.getLong("last_seen_$key", 0L)
        return if (saved > 0L) PresenceInfo(false, saved) else null
    }

    private suspend fun applyPresence(): Result<Unit> = runCatching {
        if (!connected.get()) error("not connected yet")
        presenceMutex.withLock {
            val wantOnline = appForeground.get() &&
                    settingsRepo.getBoolean("send_online_presence", true)
            withContext(Dispatchers.IO) {
                if (wantOnline && !presenceOnline.get()) {
                    client.sendPresenceAvailable()
                    presenceOnline.set(true)
                } else if (!wantOnline && presenceOnline.get()) {
                    client.sendPresenceUnavailable()
                    presenceOnline.set(false)
                    subscribed.clear()          // resubscribe once when back online
                }
                if (presenceOnline.get()) subscribeWatchedLocked()
            }
        }
    }.onFailure {
        if (it.message?.contains("not connected", ignoreCase = true) == true) {
            connected.set(false)
        }
        Log.w("ChatSession", "applyPresence failed: ${it.message}")
    }

    private fun subscribeWatchedLocked() {
        val jid = watchedJid ?: return
        if (!subscribed.add(jid)) return
        try {
            client.subscribePresence(jid)
        } catch (e: Exception) {
            subscribed.remove(jid)
            throw e
        }
    }

    fun watchPresence(jid: String?) {
        watchedJid = jid?.takeUnless { isGroupJID(it) || it.endsWith("@broadcast") || it.endsWith("@newsletter") }
        if (watchedJid != null) refreshPresence()
    }

    fun sendChatPresence(chatJid: String, state: String, media: String = "") =
        runCatching {
            // never drop "paused", or the other side shows typing until its own timeout
            if (state == "composing" && !presenceOnline.get()) return@runCatching
            client.sendChatPresence(chatJid, state, media)
        }.onFailure {
            Log.w("ChatSession", "sendChatPresence failed for $chatJid: ${it.message}")
        }

    /** Fire and forget on session scope. Survives ViewModel cancel. */
    fun sendPausedDetached(chatJid: String, media: String = "") {
        scope.launch { sendChatPresence(chatJid, "paused", media) }
    }

    fun sendMessage(chatJid: String, text: String, replyToId: String? = null, replyToSenderJid: String? = null, replyToText: String? = null): Boolean {
        val tempId = "local_${System.nanoTime()}"
        val nowSeconds = System.currentTimeMillis() / 1000
        scope.launch {
            db.messageDao().insert(
                MessageEntity(
                    messageId = tempId, chatJid = chatJid, senderJid = client.getOwnJID(),
                    text = text, timestamp = nowSeconds, fromMe = true, isRead = true,
                    quotedId = replyToId, quotedText = replyToText, status = "sending"
                )
            )
        }
        scope.launch {
            try {
                val result = if (replyToId != null && replyToSenderJid != null) {
                    client.sendReplyMessage(chatJid, text, replyToId, replyToSenderJid, replyToText ?: "")
                } else {
                    client.sendTextMessage(chatJid, text)
                }
                val parts = result.split("|")
                val realId = parts.getOrNull(0)
                val realTimestamp = parts.getOrNull(1)?.toLongOrNull()
                if (realId != null && realTimestamp != null) {
                    db.messageDao().reconcileMessageId(tempId, realId, realTimestamp)
                    db.messageDao().setStatus(realId, "sent")
                }
            } catch (e: Exception) { _events.tryEmit(LoginEvent.Error(e.message ?: "send_failed")) }
        }
        return true
    }

    private fun newLocalId() = "local_${System.nanoTime()}"
    private fun nowSec() = System.currentTimeMillis() / 1000

    private suspend fun runUpload(tempId: String, tag: String, send: suspend () -> String) {
        cancelledUploads.remove(tempId)
        _uploadProgress.update { it + (tempId to 0) }          // before service start
        SendProgressService.start(appContext)
        try {
            val parts = send().split("|")
            val realId = parts.getOrNull(0)
            val ts = parts.getOrNull(1)?.toLongOrNull()
            if (realId != null && ts != null) {
                db.messageDao().reconcileMessageId(tempId, realId, ts)
                db.messageDao().setStatus(realId, "sent")
            }
        } catch (e: Exception) {
            if (cancelledUploads.remove(tempId)) {
                db.messageDao().deleteById(tempId)              // user cancelled → remove bubble
            } else {
                Log.e("ChatSession", "$tag failed", e)
                db.messageDao().setStatus(tempId, "failed")
                _events.tryEmit(LoginEvent.Error(e.message ?: tag))
            }
        } finally {
            _uploadProgress.update { it - tempId }
        }
    }

    fun cancelSend(messageId: String) {
        if (_uploadProgress.value.containsKey(messageId)) {
            cancelledUploads.add(messageId)
            client.cancelUpload(messageId)
        } else {
            scope.launch { db.messageDao().setStatus(messageId, "failed") }  // stuck "sending" → allow retry
        }
    }

    fun cancelAllUploads() { _uploadProgress.value.keys.toList().forEach { cancelSend(it) } }

    fun retrySend(messageId: String) {
        if (!messageId.startsWith("local_")) return
        scope.launch {
            val e = db.messageDao().getById(messageId) ?: return@launch
            if (e.isForwarded) client.markForwarded(messageId)
            val path = e.mediaLocalPath
            if (path == null || !File(path).exists()) {
                _events.tryEmit(LoginEvent.Error("File no longer available"))
                return@launch
            }
            db.messageDao().setStatus(messageId, "sending")
            runUpload(messageId, "retry") {
                when (e.mediaType) {
                    "image" -> client.sendImageMessage(e.chatJid, path, e.mediaCaption ?: "", e.mediaThumbnail ?: ByteArray(0), messageId)
                    "video" -> client.sendVideoMessage(e.chatJid, path, e.mediaCaption ?: "", e.mediaThumbnail ?: ByteArray(0), messageId)
                    "document" -> client.sendDocumentMessage(e.chatJid, path, e.mediaFileName ?: "document", e.mediaMimetype ?: "", e.mediaCaption ?: "", messageId)
                    "voice" -> client.sendAudioMessage(e.chatJid, path, (e.mediaSeconds ?: 1).toLong(), true, e.mediaWaveform ?: ByteArray(0), "audio/ogg; codecs=opus", messageId)
                    "audio" -> client.sendAudioMessage(e.chatJid, path, (e.mediaSeconds ?: 0).toLong(), false, e.mediaWaveform ?: ByteArray(0), e.mediaMimetype ?: "", messageId)
                    else -> error("unsupported media type")
                }
            }
        }
    }

    fun sendImageMessage(chatJid: String, localFilePath: String, caption: String, thumbnail: ByteArray?): Boolean {
        val tempId = newLocalId()
        scope.launch {
            db.messageDao().insert(
                MessageEntity(
                    messageId = tempId, chatJid = chatJid, senderJid = client.getOwnJID(),
                    text = caption, timestamp = nowSec(), fromMe = true, isRead = true,
                    mediaType = "image", mediaCaption = caption.ifBlank { null },
                    mediaLocalPath = localFilePath, mediaThumbnail = thumbnail, status = "sending"
                )
            )
            runUpload(tempId, "send_image") {
                client.sendImageMessage(chatJid, localFilePath, caption, thumbnail ?: ByteArray(0), tempId)
            }
        }
        return true
    }

    fun sendVideoMessage(chatJid: String, localFilePath: String, caption: String, thumbnail: ByteArray?): Boolean {
        val tempId = newLocalId()
        scope.launch {
            db.messageDao().insert(
                MessageEntity(
                    messageId = tempId, chatJid = chatJid, senderJid = client.getOwnJID(),
                    text = caption, timestamp = nowSec(), fromMe = true, isRead = true,
                    mediaType = "video", mediaCaption = caption.ifBlank { null },
                    mediaLocalPath = localFilePath, mediaThumbnail = thumbnail, status = "sending"
                )
            )
            runUpload(tempId, "send_video") {
                client.sendVideoMessage(chatJid, localFilePath, caption, thumbnail ?: ByteArray(0), tempId)
            }
        }
        return true
    }

    fun sendDocumentMessage(chatJid: String, localFilePath: String, fileName: String, mimeType: String, caption: String): Boolean {
        val tempId = newLocalId()
        scope.launch {
            db.messageDao().insert(
                MessageEntity(
                    messageId = tempId, chatJid = chatJid, senderJid = client.getOwnJID(),
                    text = caption, timestamp = nowSec(), fromMe = true, isRead = true,
                    mediaType = "document", mediaCaption = caption.ifBlank { null },
                    mediaMimetype = mimeType.ifBlank { null },
                    mediaLocalPath = localFilePath, mediaFileName = fileName, status = "sending"
                )
            )
            runUpload(tempId, "send_document") {
                client.sendDocumentMessage(chatJid, localFilePath, fileName, mimeType, caption, tempId)
            }
        }
        return true
    }

    fun sendAudioMessage(chatJid: String, localFilePath: String, seconds: Int, waveform: ByteArray): Boolean {
        val tempId = newLocalId()
        scope.launch {
            db.messageDao().insert(
                MessageEntity(
                    messageId = tempId, chatJid = chatJid, senderJid = client.getOwnJID(),
                    text = "", timestamp = nowSec(), fromMe = true, isRead = true,
                    mediaType = "voice", mediaLocalPath = localFilePath,
                    mediaMimetype = "audio/ogg; codecs=opus",
                    mediaSeconds = seconds, mediaWaveform = waveform, status = "sending"
                )
            )
            runUpload(tempId, "send_audio") {
                client.sendAudioMessage(chatJid, localFilePath, seconds.toLong(), true, waveform, "audio/ogg; codecs=opus", tempId)
            }
        }
        return true
    }

    fun sendAudioFileMessage(chatJid: String, localFilePath: String, fileName: String, mimeType: String, seconds: Int, waveform: ByteArray?): Boolean {
        val tempId = newLocalId()
        val mime = mimeType.ifBlank { "audio/mpeg" }
        scope.launch {
            db.messageDao().insert(
                MessageEntity(
                    messageId = tempId, chatJid = chatJid, senderJid = client.getOwnJID(),
                    text = "", timestamp = nowSec(), fromMe = true, isRead = true,
                    mediaType = "audio", mediaLocalPath = localFilePath, mediaMimetype = mime,
                    mediaFileName = fileName.ifBlank { "audio" },
                    mediaSeconds = seconds, mediaWaveform = waveform?.takeIf { it.isNotEmpty() },
                    status = "sending"
                )
            )
            runUpload(tempId, "send_audio_file") {
                client.sendAudioMessage(chatJid, localFilePath, seconds.toLong(), false, waveform ?: ByteArray(0), mime, tempId)
            }
        }
        return true
    }

    fun downloadMedia(
        mediaType: String, url: String, directPath: String,
        mediaKey: ByteArray, fileEncSha256: ByteArray, fileSha256: ByteArray,
        fileLength: Long, messageId: String, outPath: String
    ): Boolean {
        return try {
            client.downloadMedia(mediaType, url, directPath, mediaKey, fileEncSha256, fileSha256, fileLength, outPath)
            scope.launch {
                db.messageDao().setLocalPath(messageId, outPath)
                if (mediaType == "sticker") db.stickerDao().setLocalPath(fileEncSha256.toHex(), outPath)
            }
            true
        } catch (e: Exception) { false }
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    // ---- delete ----
    fun deleteForMe(ids: List<String>) {
        scope.launch {
            db.withTransaction {
                db.messageDao().deleteByIds(ids)
                db.receiptDao().deleteFor(ids)
            }
        }
    }

    fun deleteForEveryone(chatJid: String, targets: List<RevokeTarget>) {
        scope.launch {
            var failed = 0
            for (t in targets) {
                if (t.messageId.startsWith("local_")) { db.messageDao().deleteByIds(listOf(t.messageId)); continue }
                try {
                    client.deleteForEveryone(chatJid, if (t.fromMe) "" else t.senderJid, t.messageId)
                    db.messageDao().markDeleted(t.messageId, System.currentTimeMillis() / 1000)
                } catch (e: Exception) {
                    failed++
                    Log.e("ChatSession", "revoke failed", e)
                }
            }
            if (failed > 0) _events.tryEmit(LoginEvent.Error("Couldn't delete $failed message(s)"))
        }
    }

    // ---- pin ----
    fun pinMessage(chatJid: String, messageId: String, senderJid: String, fromMe: Boolean, durationSecs: Long) {
        scope.launch {
            try {
                client.pinMessage(chatJid, if (fromMe) "" else senderJid, messageId, true, durationSecs)
                val now = System.currentTimeMillis()
                db.messageDao().setPinned(messageId, true, now, now + durationSecs * 1000)
            } catch (e: Exception) {
                Log.e("ChatSession", "pin failed", e)
                _events.tryEmit(LoginEvent.Error("Couldn't pin message"))
            }
        }
    }

    fun unpinMessage(chatJid: String, messageId: String, senderJid: String, fromMe: Boolean) {
        scope.launch {
            try {
                client.pinMessage(chatJid, if (fromMe) "" else senderJid, messageId, false, 0L)
                db.messageDao().setPinned(messageId, false, 0L, 0L)
            } catch (e: Exception) {
                Log.e("ChatSession", "unpin failed", e)
                _events.tryEmit(LoginEvent.Error("Couldn't unpin message"))
            }
        }
    }

    // ---- forward ----
    fun forwardMessages(ids: List<String>, targets: List<String>) {
        scope.launch {
            val sources = db.messageDao().getByIds(ids)            // oldest first
            for (target in targets) for (src in sources) {
                if (src.isDeleted) continue
                if (src.mediaType == null) forwardText(src, target) else forwardMedia(src, target)
            }
        }
    }

    private suspend fun forwardText(src: MessageEntity, target: String) {
        val tempId = newLocalId()
        db.messageDao().insert(
            MessageEntity(
                messageId = tempId, chatJid = target, senderJid = client.getOwnJID(),
                text = src.text, timestamp = nowSec(), fromMe = true, isRead = true,
                status = "sending", isForwarded = true
            )
        )
        try {
            val parts = client.sendForwardedText(target, src.text).split("|")
            val realId = parts.getOrNull(0)
            val ts = parts.getOrNull(1)?.toLongOrNull()
            if (realId != null && ts != null) {
                db.messageDao().reconcileMessageId(tempId, realId, ts)
                db.messageDao().setStatus(realId, "sent")
            }
        } catch (e: Exception) {
            db.messageDao().setStatus(tempId, "failed")
            _events.tryEmit(LoginEvent.Error(e.message ?: "forward_failed"))
        }
    }

    private fun extFor(type: String?, fileName: String?): String = when (type) {
        "video" -> "mp4"
        "voice" -> "ogg"
        "sticker" -> "webp"
        "audio", "document" ->
            fileName?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() } ?: if (type == "audio") "ogg" else "bin"
        else -> "jpg"
    }

    private fun ensureLocalMedia(src: MessageEntity): String? {
        src.mediaLocalPath?.takeIf { File(it).exists() }?.let { return it }
        val type = src.mediaType ?: return null
        val key = src.mediaKey ?: return null
        val out = File(appContext.cacheDir, "fwd_src_${src.messageId}.${extFor(type, src.mediaFileName)}")
        val ok = downloadMedia(
            type, src.mediaUrl ?: "", src.mediaDirectPath ?: "", key,
            src.mediaFileEncSha256 ?: ByteArray(0), src.mediaFileSha256 ?: ByteArray(0),
            src.mediaFileLength ?: 0L, src.messageId, out.absolutePath
        )
        return if (ok) out.absolutePath else null
    }

    private suspend fun forwardMedia(src: MessageEntity, target: String) {
        if (src.mediaType == "sticker") { forwardSticker(src, target); return }

        val source = ensureLocalMedia(src)
        if (source == null) {
            _events.tryEmit(LoginEvent.Error("Media is no longer available to forward"))
            return
        }
        val copy = File(appContext.cacheDir, "fwd_${System.nanoTime()}.${extFor(src.mediaType, src.mediaFileName)}")
        runCatching { File(source).copyTo(copy, overwrite = true) }
            .onFailure { _events.tryEmit(LoginEvent.Error("Couldn't prepare file")); return }

        val tempId = newLocalId()
        db.messageDao().insert(
            MessageEntity(
                messageId = tempId, chatJid = target, senderJid = client.getOwnJID(),
                text = src.mediaCaption ?: "", timestamp = nowSec(), fromMe = true, isRead = true,
                mediaType = src.mediaType, mediaCaption = src.mediaCaption, mediaMimetype = src.mediaMimetype,
                mediaLocalPath = copy.absolutePath, mediaThumbnail = src.mediaThumbnail,
                mediaFileName = src.mediaFileName, mediaSeconds = src.mediaSeconds,
                mediaWaveform = src.mediaWaveform, status = "sending", isForwarded = true
            )
        )
        val path = copy.absolutePath
        val thumb = src.mediaThumbnail ?: ByteArray(0)
        runUpload(tempId, "forward") {
            client.markForwarded(tempId)
            when (src.mediaType) {
                "image" -> client.sendImageMessage(target, path, src.mediaCaption ?: "", thumb, tempId)
                "video" -> client.sendVideoMessage(target, path, src.mediaCaption ?: "", thumb, tempId)
                "document" -> client.sendDocumentMessage(target, path, src.mediaFileName ?: "document", src.mediaMimetype ?: "", src.mediaCaption ?: "", tempId)
                "voice" -> client.sendAudioMessage(target, path, (src.mediaSeconds ?: 1).toLong(), true, src.mediaWaveform ?: ByteArray(0), "audio/ogg; codecs=opus", tempId)
                "audio" -> client.sendAudioMessage(target, path, (src.mediaSeconds ?: 0).toLong(), false, src.mediaWaveform ?: ByteArray(0), src.mediaMimetype ?: "", tempId)
                else -> error("unsupported media type")
            }
        }
    }

    private suspend fun forwardSticker(src: MessageEntity, target: String) {
        val path = ensureLocalMedia(src)
        if (path == null) {
            _events.tryEmit(LoginEvent.Error("Sticker is no longer available to forward"))
            return
        }
        val info = StickerFiles.info(path)
        val tempId = newLocalId()
        db.messageDao().insert(
            MessageEntity(
                messageId = tempId, chatJid = target, senderJid = client.getOwnJID(),
                text = "", timestamp = nowSec(), fromMe = true, isRead = true,
                mediaType = "sticker", mediaMimetype = "image/webp",
                mediaLocalPath = path, mediaAnimated = info.animated, status = "sending"
            )
        )
        try {
            val parts = client.sendStickerMessage(
                target, path, info.animated, info.width.toLong(), info.height.toLong()
            ).split("|")
            val realId = parts.getOrNull(0)
            val ts = parts.getOrNull(1)?.toLongOrNull()
            if (realId != null && ts != null) {
                db.messageDao().reconcileMessageId(tempId, realId, ts)
                db.messageDao().setStatus(realId, "sent")
            }
        } catch (e: Exception) {
            db.messageDao().setStatus(tempId, "failed")
            _events.tryEmit(LoginEvent.Error(e.message ?: "forward_failed"))
        }
    }

    private fun saveSticker(
        url: String, directPath: String, mediaKey: ByteArray?, encSha: ByteArray?, sha: ByteArray?,
        fileLength: Long, mimetype: String, animated: Boolean, width: Long, height: Long, favorite: Boolean
    ) {
        if (encSha == null || encSha.isEmpty()) return
        val id = encSha.toHex()
        scope.launch {
            runCatching {
                db.stickerDao().insertIgnore(StickerEntity(
                    id, url, directPath, mediaKey ?: ByteArray(0), encSha, sha ?: ByteArray(0), fileLength,
                    mimetype.ifBlank { "image/webp" }, animated, width.toInt(), height.toInt(),
                    isFavorite = favorite, receivedAt = System.currentTimeMillis()
                ))
                if (favorite) db.stickerDao().setFavorite(id, true)
            }.onFailure { Log.w("ChatSession", "saveSticker failed", it) }
        }
    }

    private suspend fun ensureStickerFile(sticker: StickerEntity): String? {
        sticker.localPath?.let { if (File(it).exists()) return it }
        val dir = File(appContext.filesDir, "stickers").apply { mkdirs() }
        val file = File(dir, "${sticker.id}.webp")
        if (!file.exists() || file.length() == 0L) {
            val ok = downloadMedia("sticker", sticker.url, sticker.directPath, sticker.mediaKey,
                sticker.fileEncSha256, sticker.fileSha256, sticker.fileLength, "sticker_${sticker.id}", file.absolutePath)
            if (!ok) return null
        }
        db.stickerDao().setLocalPath(sticker.id, file.absolutePath)
        return file.absolutePath
    }

    fun downloadSticker(sticker: StickerEntity) {
        if (!stickerDownloading.add(sticker.id)) return
        scope.launch { try { ensureStickerFile(sticker) } finally { stickerDownloading.remove(sticker.id) } }
    }

    fun sendSticker(chatJid: String, sticker: StickerEntity): Boolean {
        scope.launch {
            val path = ensureStickerFile(sticker)
            if (path == null) { _events.tryEmit(LoginEvent.Error("sticker_unavailable")); return@launch }
            val info = StickerFiles.info(path)
            val tempId = "local_${System.nanoTime()}"
            val now = System.currentTimeMillis() / 1000
            db.messageDao().insert(MessageEntity(messageId = tempId, chatJid = chatJid, senderJid = client.getOwnJID(),
                text = "", timestamp = now, fromMe = true, isRead = true, mediaType = "sticker", mediaMimetype = "image/webp",
                mediaLocalPath = path, mediaAnimated = info.animated, status = "sending"))
            try {
                val result = client.sendStickerMessage(chatJid, path, info.animated, info.width.toLong(), info.height.toLong())
                val parts = result.split("|")
                val realId = parts.getOrNull(0)
                val realTimestamp = parts.getOrNull(1)?.toLongOrNull()
                if (realId != null && realTimestamp != null) {
                    db.messageDao().reconcileMessageId(tempId, realId, realTimestamp)
                    db.messageDao().setStatus(realId, "sent")
                }
                db.stickerDao().touch(sticker.id, System.currentTimeMillis())
            } catch (e: Exception) {
                db.messageDao().setStatus(tempId, "failed")
                _events.tryEmit(LoginEvent.Error(e.message ?: "send_sticker_failed"))
            }
        }
        return true
    }

    fun observeStatuses(): Flow<List<StatusEntity>> = db.statusDao().observeAll()

    fun sendTextStatus(text: String, backgroundColor: Long): Boolean {
        if (text.isBlank()) return false
        scope.launch {
            try {
                val result = client.sendTextStatus(text, backgroundColor)
                val parts = result.split("|")
                val realId = parts.getOrNull(0)
                val realTimestamp = parts.getOrNull(1)?.toLongOrNull()
                if (realId != null && realTimestamp != null) {
                    db.statusDao().upsert(
                        StatusEntity(
                            id = realId,
                            senderJid = client.getOwnJID(),
                            senderName = client.getOwnName().ifBlank { "You" },
                            timestamp = realTimestamp,
                            fromMe = true,
                            mediaType = "text",
                            text = text,
                            backgroundColor = backgroundColor,
                            url = "", directPath = "",
                            mediaKey = ByteArray(0),
                            fileEncSHA256 = ByteArray(0),
                            fileSHA256 = ByteArray(0),
                            fileLength = 0,
                            mimetype = "",
                            thumbnail = ByteArray(0),
                            localPath = null,
                            viewed = true,
                            expiresAt = realTimestamp * 1000L + STATUS_TTL_MS
                        )
                    )
                }
            } catch (e: Exception) { _events.tryEmit(LoginEvent.Error(e.message ?: "send_status_failed")) }
        }
        return true
    }

    fun sendImageStatus(localFilePath: String, caption: String): Boolean {
        scope.launch {
            try {
                val result = client.sendImageStatus(localFilePath, caption)
                val parts = result.split("|")
                val realId = parts.getOrNull(0)
                val realTimestamp = parts.getOrNull(1)?.toLongOrNull()
                if (realId != null && realTimestamp != null) {
                    db.statusDao().upsert(
                        StatusEntity(
                            id = realId,
                            senderJid = client.getOwnJID(),
                            senderName = client.getOwnName().ifBlank { "You" },
                            timestamp = realTimestamp,
                            fromMe = true,
                            mediaType = "image",
                            text = caption,
                            backgroundColor = 0L,
                            url = "", directPath = "",
                            mediaKey = ByteArray(0),
                            fileEncSHA256 = ByteArray(0),
                            fileSHA256 = ByteArray(0),
                            fileLength = 0,
                            mimetype = "",
                            thumbnail = ByteArray(0),
                            localPath = localFilePath,
                            viewed = true,
                            expiresAt = realTimestamp * 1000L + STATUS_TTL_MS
                        )
                    )
                }
            } catch (e: Exception) { _events.tryEmit(LoginEvent.Error(e.message ?: "send_image_status_failed")) }
        }
        return true
    }

    fun sendVideoStatus(localFilePath: String, caption: String): Boolean {
        scope.launch {
            try {
                val result = client.sendVideoStatus(localFilePath, caption)
                val parts = result.split("|")
                val realId = parts.getOrNull(0)
                val realTimestamp = parts.getOrNull(1)?.toLongOrNull()
                if (realId != null && realTimestamp != null) {
                    db.statusDao().upsert(
                        StatusEntity(
                            id = realId,
                            senderJid = client.getOwnJID(),
                            senderName = client.getOwnName().ifBlank { "You" },
                            timestamp = realTimestamp,
                            fromMe = true,
                            mediaType = "video",
                            text = caption,
                            backgroundColor = 0L,
                            url = "", directPath = "",
                            mediaKey = ByteArray(0),
                            fileEncSHA256 = ByteArray(0),
                            fileSHA256 = ByteArray(0),
                            fileLength = 0,
                            mimetype = "",
                            thumbnail = ByteArray(0),
                            localPath = localFilePath,
                            viewed = true,
                            expiresAt = realTimestamp * 1000L + STATUS_TTL_MS
                        )
                    )
                }
            } catch (e: Exception) { _events.tryEmit(LoginEvent.Error(e.message ?: "send_video_status_failed")) }
        }
        return true
    }

    fun markStatusViewed(senderJid: String, statusIds: List<String>) {
        if (statusIds.isEmpty()) return
        if (senderJid == client.getOwnJID()) return
        scope.launch {
            try {
                client.markStatusViewed(senderJid, statusIds.joinToString(","))
                statusIds.forEach { db.statusDao().markViewed(it) }
            } catch (e: Exception) {
                Log.e("ChatSession", "markStatusViewed failed: ${e.message}")
            }
        }
    }


    fun downloadStatusMedia(status: StatusEntity) {
        if (status.mediaType.lowercase() !in setOf("image", "video")) return
        status.localPath?.let { if (File(it).exists()) return }
        scope.launch {
            val outDir = File(appContext.filesDir, "status_media").apply { mkdirs() }
            val ext = if (status.mediaType.lowercase() == "image") "jpg" else "mp4"
            val outFile = File(outDir, "${status.id}.$ext")
            try {
                client.downloadMedia(
                    status.mediaType, status.url, status.directPath,
                    status.mediaKey, status.fileEncSHA256, status.fileSHA256,
                    status.fileLength, outFile.absolutePath
                )
                db.statusDao().setLocalPath(status.id, outFile.absolutePath)
            } catch (e: Exception) {
                Log.e("ChatSession", "downloadStatusMedia failed: ${e.message}")
            }
        }
    }

    fun deleteStatus(status: StatusEntity) {
        scope.launch {
            if (status.fromMe) {
                runCatching { client.revokeStatus(status.id) }
                    .onFailure { Log.e("ChatSession", "revokeStatus failed: ${it.message}", it) }
            }
            db.statusDao().deleteById(status.id)
            status.localPath?.takeIf { it.isNotBlank() }?.let { path ->
                runCatching { File(path).delete() }
            }
        }
    }

    private var channelJob: Job? = null

    private val channelFetchedAt = ConcurrentHashMap<String, Long>()
    private val channelInfoAt = ConcurrentHashMap<String, Long>()

    suspend fun refreshChannelInfo(jid: String) {
        val last = channelInfoAt[jid] ?: 0L
        if (System.currentTimeMillis() - last < 5 * 60_000L) return
        withContext(Dispatchers.IO) {
            runCatching {
                val raw = client.getChannelInfoJSON(jid)
                Log.i("ChatSession", "channel info: $raw")
                val o = org.json.JSONObject(raw)
                val old = db.channelDao().getByJid(jid)
                db.channelDao().upsert(
                    com.whatsgo.app.core.db.ChannelEntity(
                        jid = jid,
                        name = o.optString("name").ifBlank { old?.name ?: "Channel" },
                        description = o.optString("description"),
                        subscribers = o.optLong("subscribers"),
                        verified = o.optBoolean("verified"),
                        muted = o.optBoolean("muted")
                    )
                )
                val pic = o.optString("pictureUrl")
                if (pic.isNotBlank()) saveChannelAvatar(jid, pic)
                channelInfoAt[jid] = System.currentTimeMillis()
            }.onFailure { Log.w("ChatSession", "channel info failed: ${it.message}") }
        }
    }

    private val _channelLoading = MutableStateFlow(false)
    val channelLoading: StateFlow<Boolean> = _channelLoading

    fun openChannel(jid: String) {
        channelJob?.cancel()
        channelJob = scope.launch {
            // wait for Go connect (cold start). max ~20s
            var waited = 0
            while (isActive && !connected.get() && waited < 20_000) {
                _channelLoading.value = true
                delay(300); waited += 300
            }

            val stale = System.currentTimeMillis() - (channelFetchedAt[jid] ?: 0L) > 60_000L
            if (stale) {
                launch {
                    _channelLoading.value = true
                    var attempt = 0
                    while (isActive && attempt < 3) {
                        val ok = runCatching { client.loadChannelHistory(jid, 30L) }
                            .onFailure { Log.w("ChatSession", "channel history failed: ${it.message}") }
                            .isSuccess
                        if (ok) { channelFetchedAt[jid] = System.currentTimeMillis(); break }
                        attempt++
                        delay(2000L * attempt)
                    }
                    _channelLoading.value = false
                }
            } else {
                _channelLoading.value = false
            }

            while (isActive) {
                val secs = runCatching { client.subscribeChannelLive(jid) }
                    .onFailure { Log.w("ChatSession", "channel live failed: ${it.message}") }
                    .getOrDefault(0L)
                delay(if (secs > 15) (secs - 10) * 1000L else 30_000L)
            }
        }
    }

    fun closeChannel() {
        channelJob?.cancel()
        channelJob = null
        _channelLoading.value = false
    }

    private suspend fun syncChannels() {
        runCatching {
            val json = withContext(Dispatchers.IO) { client.getSubscribedChannelsJSON() }
            val arr = org.json.JSONArray(json)
            val keep = ArrayList<String>(arr.length())
            val pics = ArrayList<Pair<String, String>>()
            db.withTransaction {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val jid = o.optString("jid")
                    if (jid.isBlank()) continue
                    val name = o.optString("name").ifBlank { "Channel" }
                    db.contactDao().upsertName(jid, name)
                    val old = db.channelDao().getByJid(jid)
                    db.channelDao().upsert(
                        com.whatsgo.app.core.db.ChannelEntity(
                            jid = jid, name = name,
                            description = o.optString("description").ifBlank { old?.description ?: "" },
                            subscribers = o.optLong("subscribers").takeIf { it > 0 } ?: old?.subscribers ?: 0L,
                            verified = o.optBoolean("verified"),
                            muted = o.optBoolean("muted")
                        )
                    )
                    keep.add(jid)
                    val pic = o.optString("pictureUrl")
                    if (pic.isNotBlank()) pics.add(jid to pic)
                }
                db.channelDao().deleteNotIn(keep)
            }
            for ((jid, url) in pics) saveChannelAvatar(jid, url)

            val missing = keep.filter { j ->
                val p = db.contactDao().getByJid(j)?.avatarPath
                p == null || !File(p).exists()
            }.take(20)
            if (missing.isNotEmpty()) {
                scope.launch {
                    for (j in missing) {
                        refreshChannelInfo(j)
                        delay(500)
                    }
                }
            }
        }.onFailure { Log.e("ChatSession", "syncChannels failed: ${it.message}") }
    }

    private suspend fun saveChannelAvatar(jid: String, url: String) {
        val existing = db.contactDao().getByJid(jid)?.avatarPath
        if (existing != null && File(existing).exists()) return
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = http.newCall(okhttp3.Request.Builder().url(url).build())
                    .execute().use { r ->
                        if (r.code != 200) error("avatar ${r.code}")
                        r.body?.bytes() ?: error("empty")
                    }
                val dir = File(appContext.filesDir, "avatars").apply { mkdirs() }
                val f = File(dir, jid.replace("@", "_at_").replace(".", "_dot_") + ".jpg")
                f.writeBytes(bytes)
                db.contactDao().updateAvatarPath(jid, f.absolutePath)
            }.onFailure { Log.w("ChatSession", "channel avatar failed: ${it.message}") }
        }
    }


    private fun enqueueAvatar(jid: String) {
        if (jid.isBlank()) return
        if (jid.endsWith("@broadcast")) return
        if (jid.endsWith("@newsletter")) return
        if (!avatarQueued.add(jid)) return
        avatarRefreshQueue.trySend(jid)
    }


    private fun runPostConnectTasks() {
        scope.launch {
            delay(3000)
            prefetchProfile()
            syncSavedContacts()
            syncChannels()
            syncGroupsWithRetry()
        }
    }

    private suspend fun syncGroupsWithRetry() {
        if (prefs.getLong("groups_synced_at", 0L) > 0L) return
        repeat(4) { attempt ->
            val ok = runCatching {
                val json = withContext(Dispatchers.IO) { client.getJoinedGroupsJSON() }
                val arr = org.json.JSONArray(json)
                val ids = ArrayList<String>(arr.length())
                db.withTransaction {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val jid = obj.optString("jid")
                        val name = obj.optString("name")
                        if (jid.isNotBlank()) {
                            db.contactDao().upsertName(jid, name)
                            ids.add(jid)
                        }
                    }
                }
                prefs.edit().putLong("groups_synced_at", System.currentTimeMillis()).apply()
                Log.i("ChatSession", "Synced ${ids.size} groups")
            }.onFailure {
                Log.e("ChatSession", "syncGroups attempt $attempt failed: ${it.message}")
            }.isSuccess
            if (ok) return
            delay(5000L * (attempt + 1)) // backoff, covers 515 reconnect
        }
    }

    suspend fun getContactAbout(jid: String): String = withContext(Dispatchers.IO) {
        runCatching { client.getContactAbout(jid) }.getOrDefault("")
    }
    private fun prefetchProfile() {
        scope.launch {
            val name = runCatching { client.getOwnName() }.getOrNull()
            val phone = runCatching { client.getOwnPhone() }.getOrNull()
            val avatarUrl = runCatching { client.fetchOwnProfilePictureURL() }.getOrNull()
            val current = db.profileDao().observe().first() ?: com.whatsgo.app.core.db.ProfileEntity()
            db.profileDao().upsert(
                current.copy(
                    name = name?.takeIf { it.isNotBlank() } ?: current.name,
                    phone = phone?.takeIf { it.isNotBlank() } ?: current.phone,
                    avatarUri = avatarUrl?.takeIf { it.isNotBlank() } ?: current.avatarUri
                )
            )
        }
    }

    private val groupInfoCache = ConcurrentHashMap<String, GroupInfoData>()

    fun cachedGroupInfo(jid: String): GroupInfoData? = groupInfoCache[jid]

    private suspend fun displayMember(jid: String, goName: String): String {
        if (goName.isNotBlank()) return goName
        db.contactDao().getName(jid)?.takeIf { it.isNotBlank() }?.let { return it }
        return if (jid.endsWith("@lid")) "Unknown" else PhoneFormatter.format(appContext, jid)
    }

    private class ResolvedMember(val name: String, val kind: Int)

    private fun resolveMember(
        jid: String,
        goName: String,
        saved: Boolean,
        dbNames: Map<String, String>
    ): ResolvedMember {
        if (goName.isNotBlank() && !PhoneFormatter.isPhoneLike(goName)) {
            return ResolvedMember(goName, if (saved) 2 else 3)
        }
        dbNames[jid]?.takeIf { it.isNotBlank() && !PhoneFormatter.isPhoneLike(it) }?.let {
            return ResolvedMember(it, 3)
        }
        val shown = if (jid.endsWith("@lid")) "Unknown" else PhoneFormatter.format(appContext, jid)
        return ResolvedMember(shown, 4)
    }

    suspend fun fetchGroupInfo(jid: String): GroupInfoData? = withContext(Dispatchers.IO) {
        runCatching {
            val o = org.json.JSONObject(client.getGroupInfoJSON(jid))
            val own = client.getOwnJID().substringBefore(":").substringBefore("@")
            val arr = o.optJSONArray("members") ?: org.json.JSONArray()

            val dbNames = HashMap<String, String>()
            (0 until arr.length())
                .map { arr.getJSONObject(it).optString("jid") }
                .chunked(500)
                .forEach { chunk ->
                    db.contactDao().namesFor(chunk).forEach { dbNames[it.jid] = it.name }
                }

            val rows = ArrayList<Triple<GroupMember, Int, Long>>(arr.length())
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val mj = m.optString("jid")
                val user = mj.substringBefore(":").substringBefore("@")
                val isMe = own.isNotBlank() && user == own
                val admin = m.optBoolean("admin")
                val superAdmin = m.optBoolean("superAdmin")
                val r = resolveMember(mj, m.optString("name"), m.optBoolean("saved"), dbNames)
                val tier = when {
                    isMe -> 0
                    admin || superAdmin -> 1
                    else -> r.kind
                }
                rows.add(
                    Triple(
                        GroupMember(
                            jid = mj,
                            name = r.name,
                            isAdmin = admin,
                            isSuperAdmin = superAdmin,
                            isMe = isMe
                        ),
                        tier,
                        user.toLongOrNull() ?: Long.MAX_VALUE
                    )
                )
            }
            rows.sortWith(
                compareBy<Triple<GroupMember, Int, Long>> { it.second }
                    .thenByDescending { it.second == 1 && it.first.isSuperAdmin }
                    .thenBy { if (it.second == 4) "" else it.first.name.lowercase() }
                    .thenBy { it.third }
            )
            val members = ArrayList(rows.map { it.first })

            val owner = o.optString("owner")
            val topicBy = o.optString("topicSetBy")
            val ownerName = if (owner.isBlank()) "" else
                members.firstOrNull { it.jid == owner }?.name
                    ?: displayMember(owner, runCatching { client.getContactName(owner) }.getOrDefault(""))
            val topicByName = if (topicBy.isBlank()) "" else
                members.firstOrNull { it.jid == topicBy }?.name
                    ?: displayMember(topicBy, runCatching { client.getContactName(topicBy) }.getOrDefault(""))

            val name = o.optString("name")
            if (name.isNotBlank()) runCatching { db.contactDao().upsertName(jid, name) }

            GroupInfoData(
                jid = jid,
                name = name,
                description = o.optString("topic"),
                descriptionBy = topicByName,
                descriptionAt = o.optLong("topicSetAt"),
                createdAt = o.optLong("created"),
                createdBy = ownerName,
                locked = o.optBoolean("locked"),
                announce = o.optBoolean("announce"),
                adminAddOnly = o.optBoolean("adminAddOnly"),
                ephemeralSeconds = if (o.optBoolean("ephemeral")) o.optLong("timer") else 0L,
                isCommunity = o.optBoolean("isParent"),
                inviteLink = o.optString("invite"),
                members = members
            ).also { groupInfoCache[jid] = it }
        }.onFailure { Log.w("ChatSession", "fetchGroupInfo failed: ${it.message}") }
            .getOrNull()
    }
}
