package com.whatsgo.app.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE chatJid = :chatJid ORDER BY timestamp ASC")
    fun messagesForChat(chatJid: String): Flow<List<MessageEntity>>

    @Query("SELECT DISTINCT chatJid FROM messages ORDER BY timestamp DESC")
    fun allChatJids(): Flow<List<String>>

    @Query("""
    SELECT m.chatJid AS chatJid,
           m.text AS lastMessage,
           MAX(m.timestamp) AS timestamp,
           m.fromMe AS fromMe,
           m.senderJid AS senderJid,
           c.name AS senderName,
           m.status AS status,
           m.mediaType AS mediaType,
           m.isDeleted AS isDeleted,
           (SELECT COUNT(*) FROM messages u
            WHERE u.chatJid = m.chatJid AND u.fromMe = 0 AND u.isRead = 0) AS unread
    FROM messages m
    LEFT JOIN contacts c ON c.jid = m.senderJid
    GROUP BY m.chatJid
    ORDER BY timestamp DESC
""")
    fun observeChatSummaries(): Flow<List<ChatSummary>>
    @Query("UPDATE messages SET isRead = 1 WHERE chatJid = :chatJid AND fromMe = 0")
    suspend fun markChatRead(chatJid: String)

    @Query("""
UPDATE messages SET isRead = 1
WHERE fromMe = 0 AND isRead = 0
AND chatJid = (SELECT chatJid FROM messages WHERE messageId = :id LIMIT 1)
AND timestamp <= (SELECT timestamp FROM messages WHERE messageId = :id LIMIT 1)
""")
    suspend fun markReadUpTo(id: String)

    @Query("SELECT * FROM messages WHERE messageId = :messageId LIMIT 1")
    suspend fun getById(messageId: String): MessageEntity?

    @Query("UPDATE messages SET messageId = :newId, timestamp = :newTimestamp WHERE messageId = :oldId")
    suspend fun reconcileMessageId(oldId: String, newId: String, newTimestamp: Long)

    @Query("UPDATE messages SET mediaLocalPath = :path WHERE messageId = :messageId")
    suspend fun setLocalPath(messageId: String, path: String)

    @Query("UPDATE messages SET status = :status WHERE messageId = :messageId")
    suspend fun setStatus(messageId: String, status: String)

    @Query("UPDATE messages SET status = 'read' WHERE chatJid = :chatJid AND fromMe = 1 AND status != 'read'")
    suspend fun markAllSentRead(chatJid: String)

    @Query("DELETE FROM messages WHERE messageId = :id")
    suspend fun deleteById(id: String)

    // app killed mid-upload -> would show "sending" forever
    @Query("""UPDATE messages SET status = 'failed'
              WHERE status = 'sending' AND fromMe = 1 AND mediaType IS NOT NULL""")
    suspend fun failStaleSending()

    @Query("""
    SELECT messageId, senderJid FROM messages
    WHERE chatJid = :chatJid AND fromMe = 0 AND isRead = 0
""")
    suspend fun getUnreadIncoming(chatJid: String): List<UnreadMessageRef>

    @Query("""
    UPDATE messages SET isRead = 1
    WHERE chatJid = :chatJid AND fromMe = 0 AND isRead = 0
    AND messageId NOT IN (
        SELECT messageId FROM messages
        WHERE chatJid = :chatJid AND fromMe = 0
        ORDER BY timestamp DESC LIMIT :unread
    )
""")
    suspend fun applyHistoryUnread(chatJid: String, unread: Int)

    @Query("SELECT messageId FROM messages WHERE isRead = 1 AND messageId IN (:ids)")
    suspend fun readIds(ids: List<String>): List<String>

    @Query("UPDATE messages SET isRead = 1 WHERE chatJid = :chatJid AND fromMe = 0 AND isRead = 0 AND timestamp <= :ts")
    suspend fun markChatReadUpTo(chatJid: String, ts: Long)

    @Query("""
    SELECT m.messageId AS messageId, m.chatJid AS chatJid, m.senderJid AS senderJid,
           m.text AS text, m.timestamp AS timestamp, m.fromMe AS fromMe,
           m.isRead AS isRead, m.status AS status,
           m.quotedId AS quotedId, m.quotedText AS quotedText, m.quotedSenderJid AS quotedSenderJid,
           m.mediaType AS mediaType, m.mediaCaption AS mediaCaption,
           m.mediaLocalPath AS mediaLocalPath, m.mediaThumbnail AS mediaThumbnail,
           m.mediaSeconds AS mediaSeconds, m.mediaWaveform AS mediaWaveform, m.mediaAnimated AS mediaAnimated,
           m.mediaFileName AS mediaFileName, m.mediaMimetype AS mediaMimetype,
           m.isDeleted AS isDeleted, m.isForwarded AS isForwarded, m.isPinned AS isPinned,
           c.name AS senderName,
           q.text AS origText, q.fromMe AS origFromMe, q.senderJid AS origSenderJid,
           q.mediaType AS origMediaType, qc.name AS origSenderName
    FROM messages m
    LEFT JOIN contacts c  ON c.jid = m.senderJid
    LEFT JOIN messages q  ON q.messageId = m.quotedId
    LEFT JOIN contacts qc ON qc.jid = q.senderJid
    WHERE m.chatJid = :chatJid
    ORDER BY m.timestamp DESC, m.rowid DESC
    LIMIT :limit
""")
    fun observeRecent(chatJid: String, limit: Int): Flow<List<MessageRow>>

    data class LocalState(
        val messageId: String, val mediaLocalPath: String?, val status: String, val isRead: Boolean,
        val isDeleted: Boolean, val isForwarded: Boolean, val isPinned: Boolean,
        val pinnedAt: Long, val pinExpiresAt: Long
    )

    @Query("SELECT messageId, mediaLocalPath, status, isRead, isDeleted, isForwarded, isPinned, pinnedAt, pinExpiresAt FROM messages WHERE messageId IN (:ids)")
    suspend fun localState(ids: List<String>): List<LocalState>

    @Query("""UPDATE messages SET isDeleted = 1, text = '', mediaCaption = NULL, mediaType = NULL,
    mediaLocalPath = NULL, mediaThumbnail = NULL, mediaUrl = NULL, mediaDirectPath = NULL,
    mediaKey = NULL, mediaWaveform = NULL, mediaFileName = NULL, isPinned = 0
    WHERE messageId = :id""")
    suspend fun markDeleted(id: String)

    @Query("DELETE FROM messages WHERE messageId IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT * FROM messages WHERE messageId IN (:ids) ORDER BY timestamp ASC")
    suspend fun getByIds(ids: List<String>): List<MessageEntity>

    @Query("UPDATE messages SET isForwarded = 1 WHERE messageId = :id")
    suspend fun setForwarded(id: String)

    @Query("UPDATE messages SET isPinned = :pinned, pinnedAt = :at, pinExpiresAt = :expires WHERE messageId = :id")
    suspend fun setPinned(id: String, pinned: Boolean, at: Long, expires: Long)

    @Query("UPDATE messages SET isPinned = 0 WHERE isPinned = 1 AND pinExpiresAt > 0 AND pinExpiresAt <= :now")
    suspend fun expirePins(now: Long)

    @Query("""SELECT COUNT(*) FROM messages WHERE chatJid = :chatJid AND isPinned = 1 AND isDeleted = 0
    AND (pinExpiresAt = 0 OR pinExpiresAt > :now)""")
    suspend fun countPinned(chatJid: String, now: Long): Int

    data class PinRow(
        val messageId: String, val text: String, val mediaType: String?, val mediaCaption: String?,
        val mediaFileName: String?, val fromMe: Boolean, val pinExpiresAt: Long
    )

    @Query("""SELECT messageId, text, mediaType, mediaCaption, mediaFileName, fromMe, pinExpiresAt
    FROM messages WHERE chatJid = :chatJid AND isPinned = 1 AND isDeleted = 0 ORDER BY pinnedAt DESC""")
    fun observePinned(chatJid: String): Flow<List<PinRow>>
}
