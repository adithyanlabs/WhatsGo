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
        SELECT m1.chatJid as chatJid,
               m1.text as lastMessage,
               m1.timestamp as timestamp,
               m1.fromMe as fromMe,
               (SELECT COUNT(*) FROM messages m2
                WHERE m2.chatJid = m1.chatJid AND m2.isRead = 0 AND m2.fromMe = 0) as unread
        FROM messages m1
        WHERE m1.timestamp = (SELECT MAX(m3.timestamp) FROM messages m3 WHERE m3.chatJid = m1.chatJid)
        GROUP BY m1.chatJid
        ORDER BY m1.timestamp DESC
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
        val messageId: String,
        val mediaLocalPath: String?,
        val status: String,
        val isRead: Boolean
    )

    @Query("SELECT messageId, mediaLocalPath, status, isRead FROM messages WHERE messageId IN (:ids)")
    suspend fun localState(ids: List<String>): List<LocalState>
}