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
}