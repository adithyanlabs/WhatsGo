package com.whatsgo.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.whatsgo.app.ui.chat.models.ChatMessage
import com.whatsgo.app.ui.chat.models.MediaAttachment
import com.whatsgo.app.ui.chat.models.MediaType
import com.whatsgo.app.ui.chat.models.MessageStatus
import com.whatsgo.app.ui.home.ChatItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val MAX_PINS = 3                      // WhatsApp allows 3 pinned messages per chat
const val MAX_FORWARD_CHATS = 5             // WhatsApp forwards to at most 5 chats at once
const val DELETE_WINDOW_SEC = 216_000L      // 2 days 12 hours

data class PinnedMessage(
    val messageId: String,
    val preview: String,
    val fromMe: Boolean,
    val expiresAt: Long                     // epoch ms, 0 = never
)

data class InfoRow(
    val jid: String,
    val name: String,
    val deliveredAt: Long,                  // epoch seconds, 0 = not yet
    val readAt: Long
)

fun mediaLabel(media: MediaAttachment?): String = when (media?.type) {
    MediaType.IMAGE -> "Photo"
    MediaType.VIDEO -> "Video"
    MediaType.VOICE -> "Voice message"
    MediaType.DOCUMENT -> media.fileName ?: "Document"
    MediaType.STICKER -> "Sticker"
    null -> ""
}

// ---------------------------------------------------------------------------
// Selection top bar
// ---------------------------------------------------------------------------

@Composable
fun SelectionTopBar(
    count: Int,
    canPin: Boolean,
    isPinned: Boolean,
    canInfo: Boolean,
    canForward: Boolean,
    onClose: () -> Unit,
    onPin: () -> Unit,
    onInfo: () -> Unit,
    onForward: () -> Unit,
    onDelete: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    // Same surface, same 76 dp height as ConversationTopBar, so the swap does not shift the list.
    Surface(color = cs.surfaceContainer, tonalElevation = 2.dp, shadowElevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(76.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Close, "Cancel selection", tint = cs.onSurface)
            }
            Text(
                "$count selected",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = cs.primary,
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
            if (canPin) IconButton(onClick = onPin) {
                Icon(
                    if (isPinned) Icons.Outlined.PushPin else Icons.Filled.PushPin,
                    if (isPinned) "Unpin" else "Pin",
                    tint = cs.onSurface
                )
            }
            if (canInfo) IconButton(onClick = onInfo) {
                Icon(Icons.Filled.Info, "Message info", tint = cs.onSurface)
            }
            if (canForward) IconButton(onClick = onForward) {
                Icon(Icons.AutoMirrored.Filled.Forward, "Forward", tint = cs.onSurface)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "Delete", tint = cs.onSurface)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Pinned banner (tap = jump to message, then show the next pin)
// ---------------------------------------------------------------------------

@Composable
fun PinnedBanner(pins: List<PinnedMessage>, onClick: (PinnedMessage) -> Unit) {
    if (pins.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    var index by remember(pins.size) { mutableIntStateOf(0) }
    val cur = pins[index.coerceIn(0, pins.lastIndex)]

    Surface(
        color = cs.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick(cur)
                index = (index + 1) % pins.size
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (pins.size > 1) {
                Column(
                    modifier = Modifier.height(32.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    pins.forEachIndexed { i, _ ->
                        Box(
                            Modifier
                                .width(3.dp)
                                .weight(1f)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (i == index) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.3f))
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (pins.size > 1) "Pinned message · ${index + 1} of ${pins.size}" else "Pinned message",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.primary
                )
                Text(
                    cur.preview.ifBlank { "Message" },
                    fontSize = 13.sp,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Filled.PushPin, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Delete dialog
// ---------------------------------------------------------------------------

@Composable
fun DeleteMessagesDialog(
    count: Int,
    canForEveryone: Boolean,
    onForMe: () -> Unit,
    onForEveryone: () -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = cs.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(top = 20.dp, bottom = 8.dp)) {
                Text(
                    if (count == 1) "Delete message?" else "Delete $count messages?",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
                Spacer(Modifier.height(8.dp))
                if (canForEveryone) DeleteAction("Delete for everyone", cs.error, onForEveryone)
                DeleteAction("Delete for me", cs.onSurface, onForMe)
                DeleteAction("Cancel", cs.primary, onDismiss)
            }
        }
    }
}

@Composable
private fun DeleteAction(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

// ---------------------------------------------------------------------------
// Pin duration dialog
// ---------------------------------------------------------------------------

@Composable
fun PinDurationDialog(onPin: (Long) -> Unit, onDismiss: () -> Unit) {
    val options = listOf("24 hours" to 86_400L, "7 days" to 604_800L, "30 days" to 2_592_000L)
    var selected by remember { mutableLongStateOf(604_800L) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose how long your pin lasts") },
        text = {
            Column {
                Text(
                    "You can unpin at any time.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                options.forEach { (label, secs) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selected = secs }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == secs, onClick = { selected = secs })
                        Text(label, fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPin(selected) }) { Text("Pin") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---------------------------------------------------------------------------
// Forward sheet
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardSheet(
    chats: List<ChatItem>,
    count: Int,
    onDismiss: () -> Unit,
    onSend: (List<String>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cs = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(listOf<String>()) }
    val shown = remember(chats, query) {
        chats.filter { !it.isChannel && it.name.contains(query.trim(), ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = cs.surfaceContainerLow
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            Text(
                if (count == 1) "Forward message to…" else "Forward $count messages to…",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search chats") },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            )
            LazyColumn(Modifier.weight(1f)) {
                items(shown, key = { it.id }) { chat ->
                    val isPicked = chat.id in picked
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                picked = when {
                                    isPicked -> picked - chat.id
                                    picked.size >= MAX_FORWARD_CHATS -> picked
                                    else -> picked + chat.id
                                }
                            }
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val file = chat.avatarPath?.let { File(it) }
                        if (file != null && file.exists()) {
                            AsyncImage(
                                model = file, contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(44.dp).clip(CircleShape)
                            )
                        } else {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).background(cs.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (chat.isGroup) Icons.Filled.Group else Icons.Filled.Person, null,
                                    tint = cs.onSecondaryContainer, modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(
                            chat.name, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Checkbox(checked = isPicked, onCheckedChange = null)
                    }
                }
            }
            Button(
                onClick = { onSend(picked) },
                enabled = picked.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    if (picked.size >= MAX_FORWARD_CHATS) "Send (max $MAX_FORWARD_CHATS chats)"
                    else if (picked.isEmpty()) "Select chats"
                    else "Send to ${picked.size}"
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Message info sheet
// ---------------------------------------------------------------------------

private val infoFmt get() = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
private fun fmtSec(sec: Long) = infoFmt.format(Date(sec * 1000))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageInfoSheet(
    message: ChatMessage,
    isGroup: Boolean,
    rows: List<InfoRow>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cs = MaterialTheme.colorScheme
    val readTick = Color(0xFF34B7F1)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = cs.surfaceContainerLow
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 40.dp)
        ) {
            item {
                Text("Message info", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Surface(shape = RoundedCornerShape(16.dp), color = cs.primaryContainer) {
                    Text(
                        message.text.ifBlank { mediaLabel(message.media) },
                        color = cs.onPrimaryContainer,
                        fontSize = 15.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                InfoLine("Sent", if (message.timestamp > 0) fmtSec(message.timestamp) else message.time, null)
                Spacer(Modifier.height(8.dp))
            }

            if (!isGroup) {
                item {
                    val r = rows.firstOrNull()
                    val read = r?.readAt ?: 0L
                    val delivered = r?.deliveredAt ?: 0L
                    val isRead = read > 0 || message.status == MessageStatus.READ
                    val isDelivered = delivered > 0 || isRead || message.status == MessageStatus.DELIVERED
                    InfoLine(
                        "Read",
                        if (read > 0) fmtSec(read) else if (isRead) "Yes" else "—",
                        if (isRead) readTick else null, doubleTick = true
                    )
                    InfoLine(
                        "Delivered",
                        if (delivered > 0) fmtSec(delivered) else if (isDelivered) "Yes" else "—",
                        null, doubleTick = true
                    )
                }
            } else {
                val readBy = rows.filter { it.readAt > 0 }.sortedByDescending { it.readAt }
                val deliveredOnly = rows.filter { it.readAt == 0L && it.deliveredAt > 0 }
                    .sortedByDescending { it.deliveredAt }
                item { SectionTitle("Read by", readTick, readBy.size) }
                if (readBy.isEmpty()) item { EmptyHint() }
                items(readBy, key = { "r_" + it.jid }) { MemberLine(it.name, fmtSec(it.readAt)) }
                item { Spacer(Modifier.height(12.dp)); SectionTitle("Delivered to", null, deliveredOnly.size) }
                if (deliveredOnly.isEmpty()) item { EmptyHint() }
                items(deliveredOnly, key = { "d_" + it.jid }) { MemberLine(it.name, fmtSec(it.deliveredAt)) }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String, tint: Color?, doubleTick: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (label != "Sent") {
            Icon(
                if (doubleTick) Icons.Rounded.DoneAll else Icons.Rounded.Check, null,
                tint = tint ?: cs.onSurfaceVariant, modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(label, fontWeight = FontWeight.Medium, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(title: String, tint: Color?, count: Int) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.DoneAll, null, tint = tint ?: cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text("$title · $count", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = cs.primary)
    }
}

@Composable
private fun MemberLine(name: String, time: String) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(cs.secondaryContainer),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Person, null, tint = cs.onSecondaryContainer, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Text(name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(time, fontSize = 12.sp, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun EmptyHint() {
    Text(
        "Nobody yet",
        fontSize = 13.sp, fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, bottom = 4.dp)
    )
}