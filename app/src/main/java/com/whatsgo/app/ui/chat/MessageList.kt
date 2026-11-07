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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.border
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent

@Composable
fun MessageList(
    messages: List<ChatMessage>,
    listState: LazyListState,
    onReplySwipe: (ChatMessage) -> Unit,
    showSenderNames: Boolean,
    onMediaClick: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(messages, key = { it.id }) { msg ->
            SwipeableMessageBubble(message = msg, onReplyTriggered = { onReplySwipe(msg) }, showSenderName = showSenderNames, onMediaClick = onMediaClick)
        }
    }
}

@Composable
fun SwipeableMessageBubble(message: ChatMessage, onReplyTriggered: () -> Unit, showSenderName: Boolean, onMediaClick: (ChatMessage) -> Unit) {
    val maxDragPx = 120f
    val offsetX = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val dragProgress = (abs(offsetX.value) / maxDragPx).coerceIn(0f, 1f)
    val iconScale = 0.6f + (dragProgress * 0.4f)

    Box(modifier = Modifier.fillMaxWidth()) {
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
                .pointerInput(message.isMine) {
                    var dragTriggered = false
                    detectHorizontalDragGestures(
                        onDragStart = { dragTriggered = false },
                        onDragEnd = {
                            if (abs(offsetX.value) > maxDragPx * 0.5f) {
                                dragTriggered = true
                                onReplyTriggered()
                            }
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
                MessageBubble(message = message, showSenderName = showSenderName, onMediaClick = onMediaClick)
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
fun MessageBubble(message: ChatMessage, showSenderName: Boolean, onMediaClick: (ChatMessage) -> Unit) {
    val isMine = message.isMine
    val cs = MaterialTheme.colorScheme
    val bubbleShape = expressiveBubbleShape(isMine)

    Surface(
        shape = bubbleShape,
        color = if (isMine) cs.primaryContainer else cs.surfaceContainerHigh,
        tonalElevation = if (isMine) 0.dp else 1.dp,
        modifier = Modifier.widthIn(max = 300.dp)
    ) {
        Column(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {

        if (!isMine && showSenderName && message.senderName.isNotBlank()) {
                Text(
                    text = message.senderName,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.primary,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }

            message.media?.let { media ->
                MediaRenderer(media = media, isMine = isMine, onClick = { onMediaClick(message) })
                if (message.text.isNotEmpty()) Spacer(Modifier.height(6.dp))
            }

            message.replyTo?.let { reply ->
                ReplySnippet(reply = reply, isMine = isMine)
                Spacer(Modifier.height(6.dp))
            }

            if (message.text.isNotEmpty()) {
                val density = LocalDensity.current
                val gutterDp = if (isMine) 58.dp else 44.dp
                val gutterSp = with(density) { gutterDp.toSp() }
                val lineSp = with(density) { 14.dp.toSp() }

                val annotated = buildAnnotatedString {
                    append(message.text)
                    appendInlineContent("footer", " ")
                }
                val inlineContent = mapOf(
                    "footer" to InlineTextContent(
                        Placeholder(
                            width = gutterSp,
                            height = lineSp,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.Bottom
                        )
                    ) { }
                )

                Box {
                    Text(
                        text = annotated,
                        inlineContent = inlineContent,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        color = if (isMine) cs.onPrimaryContainer else cs.onSurface
                    )
                    TimestampRow(
                        message = message,
                        isMine = isMine,
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            } else {
                TimestampRow(message = message, isMine = isMine, modifier = Modifier.align(Alignment.End))
            }
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
private fun StatusDot(status: MessageStatus, isMine: Boolean, modifier: Modifier = Modifier) {
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
        Text(
            text = message.time.to12h(),
            fontSize = 10.sp,
            color = (if (isMine) cs.onPrimaryContainer else cs.onSurfaceVariant).copy(alpha = 0.6f)
        )
        if (isMine) StatusDot(status = message.status, isMine = isMine)
    }
}

@Composable
fun MediaRenderer(media: MediaAttachment, isMine: Boolean, onClick: () -> Unit) {
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
                    .clickable(onClick = onClick),
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
            }
        }
        MediaType.VIDEO -> {
            Box(
                modifier = Modifier
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
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(overlayIcon, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }
        MediaType.VOICE -> VoiceMessagePlayer(media = media, contentColor = contentColor)
        MediaType.DOCUMENT -> {
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(contentColor.copy(alpha = 0.06f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(contentColor.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = contentColor, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(media.fileName ?: "Document", color = contentColor, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        MediaType.STICKER -> {
            Icon(Icons.Filled.EmojiEmotions, null, modifier = Modifier.size(96.dp), tint = contentColor)
        }
    }
}

@Composable
fun VoiceMessagePlayer(media: MediaAttachment, contentColor: androidx.compose.ui.graphics.Color) {
    val waveformHeights = listOf(0.3f, 0.5f, 0.8f, 1.0f, 0.6f, 0.4f, 0.7f, 0.9f, 0.5f, 0.3f, 0.6f, 0.8f, 1.0f, 0.7f, 0.4f, 0.2f)
    Row(modifier = Modifier.widthIn(min = 200.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(contentColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, "Play", tint = contentColor, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            waveformHeights.forEach { fraction ->
                Box(modifier = Modifier.width(3.dp).height((24.dp * fraction).coerceAtLeast(4.dp)).clip(CircleShape).background(contentColor.copy(alpha = 0.5f)))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(text = media.durationText ?: "0:12", color = contentColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
