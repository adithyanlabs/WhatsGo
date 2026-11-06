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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
}

private const val STATUS_TTL_MS = 24L * 60 * 60 * 1000

class ChatSession(dbPath: String, context: Context) {

    private val appContext = context.applicationContext
    private val _events = MutableStateFlow<LoginEvent>(LoginEvent.Idle)
    val events: StateFlow<LoginEvent> = _events.asStateFlow()

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

    private val incoming = Channel<Incoming>(Channel.UNLIMITED)

    private sealed class Op {
        class Msg(val m: Incoming) : Op()
        class Status(val id: String, val status: String) : Op()
        class StatusMsg(val s: IncomingStatus) : Op()
        class StatusViewed(val statusId: String, val viewerJid: String) : Op()
    }

    private val ops = Channel<Op>(Channel.UNLIMITED)
    private val checkedGroups = HashSet<String>()
    private val avatarRefreshQueue = Channel<String>(Channel.UNLIMITED)
    private val avatarQueued = HashSet<String>()
    private val prefs = appContext.getSharedPreferences("whatsgo_sync", Context.MODE_PRIVATE)

    private val callback = object : LoginCallback {
        override fun onQrCode(payload: String) { _events.value = LoginEvent.QrReady(payload) }
        override fun onPairingCode(code: String) { _events.value = LoginEvent.PairingReady(code) }
        override fun onConnected() {
            _events.value = LoginEvent.Connected
            prefetchProfile()
            enqueueAllAvatars()
            syncGroupsIfNeeded()
        }
        override fun onOwnName(name: String) { _events.value = LoginEvent.OwnNameUpdated(name) }
        override fun onOwnAbout(about: String) { _events.value = LoginEvent.OwnAboutUpdated(about) }
        override fun onOwnAvatarChanged(pictureID: String, removed: Boolean) { _events.value = LoginEvent.OwnAvatarChanged(removed) }
        override fun onLoggedOut(reason: String) { _events.value = LoginEvent.LoggedOut(reason) }
        override fun onError(msg: String) { _events.value = LoginEvent.Error(msg) }
        override fun onMessageStatus(messageID: String, status: String) { ops.trySend(Op.Status(messageID, status)) }

        override fun onMessage(
            chatJid: String, senderJid: String, messageId: String, text: String,
            timestamp: Long, fromMe: Boolean, quotedId: String, quotedText: String,
            senderPushName: String, quotedSenderJid: String
        ) {
            incoming.trySend(
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
            incoming.trySend(
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
                        mediaThumbnail = thumbnail?.takeIf { it.isNotEmpty() },
                        mediaLocalPath = null
                    ),
                    chatJid, senderJid, fromMe, senderPushName
                )
            )
        }

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
    }

    private val client: Client = Engine.newClient(dbPath, callback)

    init {
        scope.launch {
            for (message in incoming) {
                ops.send(Op.Msg(message))
            }
        }

        scope.launch {
            val batch = ArrayList<Incoming>(100)
            for (first in ops) {
                try {
                    var cur: Op? = first
                    while (cur != null) {
                        when (cur) {
                            is Op.Msg -> {
                                batch.add(cur.m)
                                if (batch.size >= 100) flush(batch)
                            }
                            is Op.Status -> {
                                flush(batch)
                                if (cur.status == "read_self") {
                                    db.messageDao().markReadUpTo(cur.id)
                                } else {
                                    db.messageDao().setStatus(cur.id, cur.status)
                                }
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
                        }
                        cur = ops.tryReceive().getOrNull()
                    }
                    flush(batch)
                } catch (t: Throwable) {
                    Log.e("ChatSession", "ops failed", t)
                    batch.clear()
                }
            }
        }

        scope.launch {
            for (jid in avatarRefreshQueue) {
                avatarQueued.remove(jid)
                if (jid.isBlank() ||
                    jid.endsWith("@g.us") ||
                    jid.endsWith("@broadcast") ||
                    jid.endsWith("@newsletter")
                ) continue

                val existing = runCatching { db.contactDao().getByJid(jid) }.getOrNull()
                val existingPath = existing?.avatarPath
                if (existingPath != null && File(existingPath).exists()) continue

                runCatching {
                    val url = client.getProfilePictureURL(jid)
                    if (url.isNullOrBlank()) {
                        db.contactDao().updateAvatarPath(jid, null)
                        return@runCatching
                    }

                    val bytes = okhttp3.OkHttpClient()
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
            runCatching {
                db.contactDao().upsert(ContactEntity(jid = s.senderJid, name = s.senderName))
            }
        }
    }

    private suspend fun flush(batch: ArrayList<Incoming>) {
        if (batch.isEmpty()) return
        db.withTransaction { batch.forEach { db.messageDao().insert(it.entity) } }
        saveNames(batch)
        batch.clear()
    }

    private suspend fun saveNames(batch: List<Incoming>) {
        val names = HashMap<String, String>()
        for (m in batch) {
            val isGroup = runCatching { client.isGroupJID(m.chatJid) }.getOrDefault(false)
            if (isGroup) {
                if (checkedGroups.add(m.chatJid)) {
                    val existing = db.contactDao().getName(m.chatJid)
                    if (existing.isNullOrBlank()) {
                        val g = runCatching { client.getGroupName(m.chatJid) }.getOrNull()
                        if (!g.isNullOrBlank()) names[m.chatJid] = g
                    }
                }
                if (!m.fromMe && m.pushName.isNotBlank()) names[m.senderJid] = m.pushName
            } else if (!m.fromMe && m.pushName.isNotBlank()) {
                names[m.chatJid] = m.pushName
            }
        }
        names.forEach { (jid, name) ->
            db.contactDao().upsertName(jid, name)
            enqueueAvatar(jid)
        }
    }

    fun messagesForChat(chatJid: String) = db.messageDao().messagesForChat(chatJid)
    fun allChatJids() = db.messageDao().allChatJids()

    fun beginQrLogin() {
        try { client.startQrLogin() } catch (e: Exception) { _events.value = LoginEvent.Error(e.message ?: "qr_login_failed") }
    }
    fun beginPairingLogin(phoneDigits: String) {
        try { client.startPairingLogin(phoneDigits) } catch (e: Exception) { _events.value = LoginEvent.Error(e.message ?: "pairing_failed") }
    }
    fun isLoggedIn(): Boolean = client.isLoggedIn()
    fun reconnectIfNeeded() {
        try { client.reconnect() } catch (e: Exception) { _events.value = LoginEvent.Error(e.message ?: "reconnect_failed") }
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

            _events.value = LoginEvent.LoggedOut("user_requested")
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
        } catch (e: Exception) { false }
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
            } catch (e: Exception) { _events.value = LoginEvent.Error(e.message ?: "send_failed") }
        }
        return true
    }

    fun sendImageMessage(chatJid: String, localFilePath: String, caption: String, thumbnail: ByteArray?): Boolean {
        val tempId = "local_${System.nanoTime()}"
        val nowSeconds = System.currentTimeMillis() / 1000
        scope.launch {
            db.messageDao().insert(
                MessageEntity(
                    messageId = tempId, chatJid = chatJid, senderJid = client.getOwnJID(),
                    text = caption, timestamp = nowSeconds, fromMe = true, isRead = true,
                    mediaType = "image", mediaCaption = caption.ifBlank { null },
                    mediaLocalPath = localFilePath, mediaThumbnail = thumbnail
                )
            )
        }
        scope.launch {
            try {
                val result = client.sendImageMessage(chatJid, localFilePath, caption, thumbnail ?: ByteArray(0))
                val parts = result.split("|")
                val realId = parts.getOrNull(0)
                val realTimestamp = parts.getOrNull(1)?.toLongOrNull()
                if (realId != null && realTimestamp != null) db.messageDao().reconcileMessageId(tempId, realId, realTimestamp)
            } catch (e: Exception) { _events.value = LoginEvent.Error(e.message ?: "send_image_failed") }
        }
        return true
    }

    fun sendVideoMessage(chatJid: String, localFilePath: String, caption: String, thumbnail: ByteArray?): Boolean {
        val tempId = "local_${System.nanoTime()}"
        val nowSeconds = System.currentTimeMillis() / 1000
        scope.launch {
            db.messageDao().insert(
                MessageEntity(
                    messageId = tempId, chatJid = chatJid, senderJid = client.getOwnJID(),
                    text = caption, timestamp = nowSeconds, fromMe = true, isRead = true,
                    mediaType = "video", mediaCaption = caption.ifBlank { null },
                    mediaLocalPath = localFilePath, mediaThumbnail = thumbnail
                )
            )
        }
        scope.launch {
            try {
                val result = client.sendVideoMessage(chatJid, localFilePath, caption, thumbnail ?: ByteArray(0))
                val parts = result.split("|")
                val realId = parts.getOrNull(0)
                val realTimestamp = parts.getOrNull(1)?.toLongOrNull()
                if (realId != null && realTimestamp != null) db.messageDao().reconcileMessageId(tempId, realId, realTimestamp)
            } catch (e: Exception) { _events.value = LoginEvent.Error(e.message ?: "send_video_failed") }
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
            scope.launch { db.messageDao().setLocalPath(messageId, outPath) }
            true
        } catch (e: Exception) { false }
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
            } catch (e: Exception) { _events.value = LoginEvent.Error(e.message ?: "send_status_failed") }
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
            } catch (e: Exception) { _events.value = LoginEvent.Error(e.message ?: "send_image_status_failed") }
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
            } catch (e: Exception) { _events.value = LoginEvent.Error(e.message ?: "send_video_status_failed") }
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
    private fun enqueueAllAvatars() {
        scope.launch {
            db.contactDao().observeAll().first().forEach { enqueueAvatar(it.jid) }
        }
    }
    private fun enqueueAvatar(jid: String) {
        if (jid.isBlank()) return
        if (jid.endsWith("@g.us")) return
        if (jid.endsWith("@broadcast")) return
        if (jid.endsWith("@newsletter")) return
        if (!avatarQueued.add(jid)) return
        avatarRefreshQueue.trySend(jid)
    }

    private fun syncGroupsIfNeeded() {
        if (prefs.getLong("groups_synced_at", 0L) > 0L) return
        scope.launch {
            runCatching {
                val json = withContext(Dispatchers.IO) {
                    client.getJoinedGroupsJSON()
                }
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
                ids.forEach { enqueueAvatar(it) }
                prefs.edit().putLong("groups_synced_at", System.currentTimeMillis()).apply()
                Log.i("ChatSession", "Synced ${ids.size} groups")
            }.onFailure {
                Log.e("ChatSession", "syncGroups failed: ${it.message}", it)
            }
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
}
