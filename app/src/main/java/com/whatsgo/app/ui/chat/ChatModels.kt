package com.whatsgo.app.ui.chat.models

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class MessageStatus { SENDING, SENT, DELIVERED, READ }
enum class MediaType { IMAGE, VIDEO, VOICE, DOCUMENT, STICKER }

@Immutable
data class MediaAttachment(
    val type: MediaType,
    val urlOrPath: String,
    val fileName: String? = null,
    val durationText: String? = null,
    val isDownloaded: Boolean = false,
    val thumbnail: ByteArray? = null,
    val remoteUrl: String? = null,
    val directPath: String? = null,
    val mediaKey: ByteArray? = null,
    val fileEncSha256: ByteArray? = null,
    val fileSha256: ByteArray? = null,
    val fileLength: Long? = null,
    val waveform: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MediaAttachment) return false
        return type == other.type &&
                urlOrPath == other.urlOrPath &&
                fileName == other.fileName &&
                durationText == other.durationText &&
                isDownloaded == other.isDownloaded &&
                remoteUrl == other.remoteUrl &&
                directPath == other.directPath &&
                fileLength == other.fileLength &&
                thumbnail.contentEquals(other.thumbnail) &&
                waveform.contentEquals(other.waveform) &&
                mediaKey.contentEquals(other.mediaKey) &&
                fileEncSha256.contentEquals(other.fileEncSha256) &&
                fileSha256.contentEquals(other.fileSha256)
    }

    override fun hashCode(): Int {
        var h = type.hashCode()
        h = 31 * h + urlOrPath.hashCode()
        h = 31 * h + isDownloaded.hashCode()
        h = 31 * h + (thumbnail?.contentHashCode() ?: 0)
        return h
    }
}

data class ReplyPreview(
    val senderName: String,
    val text: String,
    val hasMedia: Boolean = false
)

data class ChatMessage(
    val id: Int,
    val messageId: String,
    val senderJid: String,
    val senderName: String = "",
    val text: String,
    val time: String,
    val isMine: Boolean,
    val status: MessageStatus = MessageStatus.SENT,
    val replyTo: ReplyPreview? = null,
    val media: MediaAttachment? = null
)

data class ChatProfile(
    val name: String,
    val avatarLetter: String,
    val statusText: String,
    val isOnline: Boolean,
    val phone: String = "",
    val avatarPath: String? = null
)

enum class MediaOption(val label: String, val icon: ImageVector, val color: Color) {
    GALLERY("Gallery", Icons.Filled.PhotoLibrary, Color(0xFF9C8FFF)),
    CAMERA("Camera", Icons.Filled.CameraAlt, Color(0xFFFF6B8B)),
    DOCUMENT("Document", Icons.Filled.Description, Color(0xFF5CA7FF)),
    AUDIO("Audio", Icons.Filled.Headphones, Color(0xFFFF9E66)),
    LOCATION("Location", Icons.Filled.Place, Color(0xFF4DD980)),
    CONTACT("Contact", Icons.Filled.Person, Color(0xFFB388FF))
}
