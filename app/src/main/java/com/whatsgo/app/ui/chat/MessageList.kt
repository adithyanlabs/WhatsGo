@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.whatsgo.app.ui.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import com.whatsgo.app.ui.chat.models.*
import androidx.compose.foundation.clickable
import com.whatsgo.app.ui.chat.StickerImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.border
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.material.icons.automirrored.filled.Forward
@Composable
fun MessageList(
    messages: List<ChatMessage>,
    listState: LazyListState,
    onReplySwipe: (ChatMessage) -> Unit,
    showSenderNames: Boolean,
    onMediaClick: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier,
    onLoadMore: () -> Unit = {},
    selected: Set<String> = emptySet(),
    onLongPress: (ChatMessage) -> Unit = {}, onToggle: (ChatMessage) -> Unit = {}
) {
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 8
        }
    }
    LaunchedEffect(nearEnd, messages.size) {
        if (nearEnd) onLoadMore()
    }

    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = messages,
            key = { it.messageId },
            contentType = { if (it.media != null) 1 else 0 }
        ) { msg ->
            SwipeableMessageBubble(
                message = msg,
                onReplyTriggered = { onReplySwipe(msg) },
                showSenderName = showSenderNames,
                selected = msg.messageId in selected,
                selectionMode = selected.isNotEmpty(),
                onToggle = { onToggle(msg) },
                onLongClick = { onLongPress(msg) },
                onMediaClick = onMediaClick
            )
        }
    }
}

@Composable
fun SwipeableMessageBubble(
    message: ChatMessage,
    onReplyTriggered: () -> Unit,
    showSenderName: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    onToggle: () -> Unit,
    onLongClick: () -> Unit,
    onMediaClick: (ChatMessage) -> Unit
) {
    val maxDragPx = 120f
    val offsetX = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val dragProgress = (abs(offsetX.value) / maxDragPx).coerceIn(0f, 1f)
    val iconScale = 0.6f + (dragProgress * 0.4f)
    val highlight = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent

    Box(modifier = Modifier.fillMaxWidth().background(highlight)) {
        Icon(
            Icons.AutoMirrored.Filled.Reply, "Reply",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(if (message.isMine) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 16.dp)
                .size(24.dp)
                .scale(iconScale)
                .alpha(dragProgress)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(message.isMine, selectionMode, message.isDeleted) {
                    if (selectionMode || (message.isDeleted && !message.showDeletedContent)) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (abs(offsetX.value) > maxDragPx * 0.5f) onReplyTriggered()
                            coroutineScope.launch { offsetX.animateTo(0f, spring()) }
                        },
                        onDragCancel = { coroutineScope.launch { offsetX.animateTo(0f, spring()) } },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val proposed = offsetX.value + dragAmount
                            val clamped = if (message.isMine) proposed.coerceIn(-maxDragPx, 0f) else proposed.coerceIn(0f, maxDragPx)
                            coroutineScope.launch { offsetX.snapTo(clamped) }
                        }
                    )
                },
            horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start
        ) {
            Box(modifier = Modifier.offset { IntOffset(offsetX.value.roundToInt(), 0) }) {
                MessageBubble(
                    message = message, showSenderName = showSenderName,
                    selectionMode = selectionMode, onToggle = onToggle, onLongClick = onLongClick,
                    onMediaClick = onMediaClick
                )
            }
        }
    }
}

private fun expressiveBubbleShape(isMine: Boolean): RoundedCornerShape = if (isMine) {
    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
} else {
    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
}

@Composable
fun MessageBubble(
    message: ChatMessage,
    showSenderName: Boolean,
    selectionMode: Boolean,
    onToggle: () -> Unit,
    onLongClick: () -> Unit,
    onMediaClick: (ChatMessage) -> Unit
) {
    val isMine = message.isMine
    val cs = MaterialTheme.colorScheme
    val bubbleShape = expressiveBubbleShape(isMine)

    Box {
        Surface(
            shape = bubbleShape,
            color = if (isMine) cs.primaryContainer else cs.surfaceContainerHigh,
            tonalElevation = if (isMine) 0.dp else 1.dp,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .combinedClickable(onClick = { if (selectionMode) onToggle() }, onLongClick = onLongClick)
        ) {
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (!isMine && showSenderName && message.senderName.isNotBlank()) {
                    Text(
                        text = message.senderName, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                        color = cs.primary, modifier = Modifier.padding(bottom = 3.dp)
                    )
                }

                if (message.isDeleted && !message.showDeletedContent) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Block, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isMine) "You deleted this message" else "This message was deleted",
                            fontStyle = FontStyle.Italic, fontSize = 14.sp, color = cs.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    TimestampRow(message = message, isMine = isMine, modifier = Modifier.align(Alignment.End))
                    return@Column
                }

                if (message.showDeletedContent) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 5.dp)
                    ) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = "Deleted message",
                            tint = cs.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "Message deleted",
                            fontStyle = FontStyle.Italic,
                            fontSize = 11.sp,
                            color = cs.error
                        )
                    }
                }

                if (message.isForwarded) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Forward, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Forwarded", fontStyle = FontStyle.Italic, fontSize = 11.sp, color = cs.onSurfaceVariant)
                    }
                }

                message.media?.let { media ->
                    MediaRenderer(
                        media = media, messageId = message.messageId, isMine = isMine,
                        sendStatus = message.status, sendProgress = message.sendProgress,
                        onClick = { onMediaClick(message) }, onLongClick = onLongClick
                    )
                    if (message.text.isNotEmpty()) Spacer(Modifier.height(6.dp))
                }

                message.replyTo?.let { reply ->
                    ReplySnippet(reply = reply, isMine = isMine)
                    Spacer(Modifier.height(6.dp))
                }

                if (message.text.isNotEmpty()) {
                    val density = LocalDensity.current
                    // Reserve a little more room so the M3 timestamp/status cluster
                    // does not sit on top of the final line of message text.
                    val gutterDp = if (isMine) 66.dp else 52.dp
                    val gutterSp = with(density) { (gutterDp + if (message.isPinned) 14.dp else 0.dp).toSp() }
                    val lineSp = with(density) { 18.dp.toSp() }
                    val annotated = buildAnnotatedString {
                        append(message.text)
                        appendInlineContent("footer", " ")
                    }
                    val inlineContent = mapOf(
                        "footer" to InlineTextContent(
                            Placeholder(width = gutterSp, height = lineSp, placeholderVerticalAlign = PlaceholderVerticalAlign.Bottom)
                        ) { }
                    )
                    Box {
                        Text(
                            text = annotated, inlineContent = inlineContent,
                            fontSize = 15.sp, lineHeight = 20.sp,
                            color = if (isMine) cs.onPrimaryContainer else cs.onSurface
                        )
                        TimestampRow(message = message, isMine = isMine, modifier = Modifier.align(Alignment.BottomEnd))
                    }
                } else {
                    TimestampRow(message = message, isMine = isMine, modifier = Modifier.align(Alignment.End))
                }
            }
        }
        if (selectionMode) {
            Box(
                Modifier.matchParentSize().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null, onClick = onToggle
                )
            )
        }
    }
}

@Composable
private fun ReplySnippet(reply: ReplyPreview, isMine: Boolean) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .background((if (isMine) cs.onPrimaryContainer else cs.primary).copy(alpha = 0.08f))
            .padding(vertical = 8.dp, horizontal = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isMine) cs.onPrimaryContainer else cs.primary)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                reply.senderName,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isMine) cs.onPrimaryContainer else cs.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (reply.hasMedia) {
                    Icon(Icons.Filled.Image, null, modifier = Modifier.size(12.dp), tint = cs.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    reply.text.ifEmpty { "Media" },
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }
    }
}

private val ReadTickBlue = androidx.compose.ui.graphics.Color(0xFF34B7F1)

@Composable
fun StatusDot(status: MessageStatus, isMine: Boolean, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val baseColor = cs.onPrimaryContainer

    val color: androidx.compose.ui.graphics.Color
    val filled: Boolean

    when (status) {
        MessageStatus.SENDING -> {
            color = baseColor.copy(alpha = 0.3f)
            filled = false
        }
        MessageStatus.SENT -> {
            color = baseColor.copy(alpha = 0.5f)
            filled = true
        }
        MessageStatus.DELIVERED -> {
            color = baseColor.copy(alpha = 0.8f)
            filled = true
        }
        MessageStatus.READ -> {
            color = ReadTickBlue
            filled = true
        }
        MessageStatus.FAILED -> { color = MaterialTheme.colorScheme.error; filled = true }
    }

    val scale by animateFloatAsState(
        targetValue = if (status == MessageStatus.READ) 1.2f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "dotScale"
    )

    Box(
        modifier = modifier
            .size(6.dp)
            .scale(scale)
            .clip(CircleShape)
            .then(
                if (filled) Modifier.background(color)
                else Modifier.border(1.dp, color, CircleShape)
            )
    )
}

private fun String.to12h(): String {
    val t = trim()
    if (t.contains("am", true) || t.contains("pm", true)) return t.lowercase()
    val p = t.split(":")
    val h = p.getOrNull(0)?.toIntOrNull() ?: return t
    val m = p.getOrNull(1)?.take(2)?.toIntOrNull() ?: return t
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d %s".format(h12, m, if (h < 12) "am" else "pm")
}

@Composable
private fun TimestampRow(message: ChatMessage, isMine: Boolean, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (message.isPinned) Icon(
            Icons.Filled.PushPin, "Pinned",
            tint = (if (isMine) cs.onPrimaryContainer else cs.onSurfaceVariant).copy(alpha = 0.6f),
            modifier = Modifier.size(11.dp)
        )
        Text(
            text = message.time.to12h(),
            fontSize = 10.sp,
            color = (if (isMine) cs.onPrimaryContainer else cs.onSurfaceVariant).copy(alpha = 0.6f)
        )
        if (isMine) StatusDot(status = message.status, isMine = isMine)
    }
}

@Composable
fun MediaRenderer(
    media: MediaAttachment, messageId: String, isMine: Boolean,
    sendStatus: MessageStatus = MessageStatus.SENT, sendProgress: Int? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val busy = isMine && (sendStatus == MessageStatus.SENDING || sendStatus == MessageStatus.FAILED)
    val cs = MaterialTheme.colorScheme
    val contentColor = if (isMine) cs.onPrimaryContainer else cs.onSurface

    when (media.type) {
        MediaType.IMAGE -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4 / 3f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(contentColor.copy(alpha = 0.08f))
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick),
                contentAlignment = Alignment.Center
            ) {
                val imageModel = if (media.isDownloaded && media.urlOrPath.isNotBlank()) media.urlOrPath else media.thumbnail
                if (imageModel != null) {
                    coil3.compose.AsyncImage(
                        model = imageModel,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    if (!media.isDownloaded) {
                        Box(
                            modifier = Modifier.size(48.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Download, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                } else {
                    Icon(Icons.Filled.Download, null, modifier = Modifier.size(48.dp), tint = contentColor.copy(alpha = 0.8f))
                }
                if (busy) UploadBadge(sendStatus, sendProgress)
            }
        }
        MediaType.VIDEO -> {
            Box(
                modifier = Modifier
                    .widthIn(min = 240.dp, max = 276.dp)
                    .fillMaxWidth()
                    .aspectRatio(4 / 3f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(contentColor.copy(alpha = 0.08f))
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                if (media.thumbnail != null) {
                    coil3.compose.AsyncImage(
                        model = media.thumbnail,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
                val overlayIcon = if (media.isDownloaded) Icons.Filled.PlayCircle else Icons.Filled.Download
                if (busy) UploadBadge(sendStatus, sendProgress) else {
                    Box(
                        modifier = Modifier.size(48.dp).clip(CircleShape)
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            overlayIcon,
                            null,
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
        MediaType.VOICE -> VoiceMessagePlayer(
            media = media,
            messageId = messageId,
            contentColor = contentColor,
            onClick = onClick,
            sendStatus = if (busy) sendStatus else MessageStatus.SENT, sendProgress = sendProgress,
            onLongClick = onLongClick
        )

        MediaType.DOCUMENT -> {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(contentColor.copy(alpha = 0.06f)).clickable(onClick = onClick).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(contentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (busy) InlineUploadRing(sendStatus, sendProgress, contentColor, 28.dp)
                    else Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = contentColor, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f, fill = false)) {
                    Text(media.fileName ?: "Document", color = contentColor, fontWeight = FontWeight.Medium,
                        fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (busy) sendLabel(sendStatus, sendProgress)?.let {
                        Text(it, color = contentColor.copy(alpha = 0.6f), fontSize = 11.sp)
                    }
                }
            }
        }
        MediaType.STICKER -> {
            StickerImage(
                path = if (media.isDownloaded && media.urlOrPath.isNotBlank()) media.urlOrPath else null,
                modifier = Modifier.size(160.dp)
            )
        }
    }
}

@Composable
fun VoiceMessagePlayer(
    media: MediaAttachment,
    messageId: String,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    sendStatus: MessageStatus = MessageStatus.SENT,
    sendProgress: Int? = null,
    onLongClick: () -> Unit = {}
) {
    val busy = sendStatus == MessageStatus.SENDING || sendStatus == MessageStatus.FAILED
    val playback by VoicePlayer.state.collectAsState()
    val active = playback.messageId == messageId
    val progress = if (active && playback.durationMs > 0) {
        playback.positionMs / playback.durationMs.toFloat()
    } else 0f
    val bars = remember(media.waveform) {
        val waveform = media.waveform
        if (waveform == null || waveform.isEmpty()) {
            List(28) { 0.3f + 0.7f * ((it * 7) % 10) / 10f }
        } else {
            List(28) { index ->
                val from = index * waveform.size / 28
                val to = ((index + 1) * waveform.size / 28)
                    .coerceAtLeast(from + 1)
                    .coerceAtMost(waveform.size)
                ((from until to).maxOf { waveform[it].toInt() } / 100f).coerceIn(0.1f, 1f)
            }
        }
    }
    val icon = when {
        !media.isDownloaded -> Icons.Filled.Download
        active && playback.playing -> Icons.Filled.Pause
        else -> Icons.Filled.PlayArrow
    }
    val label = if (active && playback.playing) {
        val seconds = playback.positionMs / 1000
        "%d:%02d".format(seconds / 60, seconds % 60)
    } else {
        media.durationText ?: "0:00"
    }

    // Stop playback when this composable leaves composition
    DisposableEffect(messageId) {
        onDispose {
            val current = VoicePlayer.state.value
            if (current.messageId == messageId && current.playing) {
                VoicePlayer.stop()
            }
        }
    }

    Row(
        modifier = Modifier.widthIn(min = 220.dp).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(contentColor.copy(alpha = 0.15f))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            contentAlignment = Alignment.Center
        ) {
            if (busy) InlineUploadRing(sendStatus, sendProgress, contentColor, 32.dp)
            else Icon(icon, "Play voice message", tint = contentColor, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Row(
            modifier = Modifier.weight(1f).height(28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            bars.forEachIndexed { index, fraction ->
                val played = (index + 1) / bars.size.toFloat() <= progress
                Box(
                    Modifier
                        .weight(1f)
                        .height((24.dp * fraction).coerceAtLeast(3.dp))
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = if (played) 1f else 0.4f))
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            when {
                sendStatus == MessageStatus.FAILED -> "Failed"
                busy -> "${sendProgress ?: 0}%"
                else -> label
            },
            color = contentColor, fontSize = 12.sp, fontWeight = FontWeight.Medium
        )
    }
}


@Composable
private fun BoxScope.UploadBadge(status: MessageStatus, progress: Int?) {
    Box(
        Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.28f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            if (status == MessageStatus.FAILED) {
                Icon(Icons.Filled.Refresh, "Retry", tint = Color.White, modifier = Modifier.size(26.dp))
            } else {
                val p = progress ?: 0
                if (p <= 0) CircularProgressIndicator(
                    Modifier.size(44.dp), color = Color.White, strokeWidth = 3.dp
                ) else CircularProgressIndicator(
                    progress = { p / 100f }, modifier = Modifier.size(44.dp),
                    color = Color.White, trackColor = Color.White.copy(alpha = 0.25f), strokeWidth = 3.dp
                )
                Icon(Icons.Filled.Close, "Cancel", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun InlineUploadRing(status: MessageStatus, progress: Int?, tint: Color, ringSize: Dp) {
    Box(Modifier.size(ringSize), contentAlignment = Alignment.Center) {
        if (status == MessageStatus.FAILED) {
            Icon(Icons.Filled.Refresh, "Retry", tint = tint, modifier = Modifier.size(ringSize * 0.6f))
        } else {
            val p = progress ?: 0
            if (p <= 0) CircularProgressIndicator(Modifier.fillMaxSize(), color = tint, strokeWidth = 2.5.dp)
            else CircularProgressIndicator(
                progress = { p / 100f }, modifier = Modifier.fillMaxSize(),
                color = tint, trackColor = tint.copy(alpha = 0.2f), strokeWidth = 2.5.dp
            )
            Icon(Icons.Filled.Close, "Cancel", tint = tint, modifier = Modifier.size(ringSize * 0.4f))
        }
    }
}

private fun sendLabel(status: MessageStatus, progress: Int?): String? = when (status) {
    MessageStatus.FAILED -> "Failed · tap to retry"
    MessageStatus.SENDING -> if ((progress ?: 0) > 0) "Sending $progress%" else "Sending…"
    else -> null
}
