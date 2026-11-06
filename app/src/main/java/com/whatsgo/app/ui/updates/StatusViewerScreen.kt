package com.whatsgo.app.ui.updates

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.whatsgo.app.core.db.StatusEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

private const val STATUS_DURATION_MS = 5000L

@Composable
fun StatusViewerScreen(
    statuses: List<StatusEntity>,
    startIndex: Int = 0,
    onViewed: (StatusEntity) -> Unit,
    onDownload: (StatusEntity) -> Unit,
    onDelete: (StatusEntity) -> Unit,
    onClose: () -> Unit
) {
    if (statuses.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, statuses.lastIndex)
    ) { statuses.size }
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var videoProgress by remember { mutableStateOf(0f) }
    var videoEnded by remember { mutableStateOf(false) }
    var deleteConfirmTarget by remember { mutableStateOf<StatusEntity?>(null) }

    LaunchedEffect(pagerState.currentPage, statuses.size) {
        if (statuses.isEmpty()) return@LaunchedEffect
        if (pagerState.currentPage > statuses.lastIndex) {
            pagerState.scrollToPage(statuses.lastIndex)
            return@LaunchedEffect
        }
        val current = statuses.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        onViewed(current)
        progress.snapTo(0f)
        videoProgress = 0f
        videoEnded = false

        val isVideo = current.mediaType.lowercase() == "video"
        if (isVideo) {
            while (!videoEnded) {
                progress.snapTo(videoProgress.coerceIn(0f, 1f))
                delay(50)
            }
            if (pagerState.currentPage < statuses.lastIndex) {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            } else {
                onClose()
            }
        } else {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(STATUS_DURATION_MS.toInt(), easing = LinearEasing)
            )
            if (pagerState.currentPage < statuses.lastIndex) {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            } else {
                onClose()
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (statuses.isEmpty()) return@LaunchedEffect
        val current = statuses.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        val needsDownload = !current.fromMe &&
            current.mediaType.lowercase() in setOf("image", "video") &&
            current.localPath == null
        if (needsDownload) onDownload(current)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = true,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            statuses.getOrNull(page)?.let { status ->
                StatusContent(
                    status = status,
                    onVideoProgress = { videoProgress = it },
                    onVideoEnded = { videoEnded = true },
                    onDownload = { onDownload(status) }
                )
            }
        }

        Row(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (pagerState.currentPage > 0) {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        }
                    }
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (pagerState.currentPage < statuses.lastIndex) {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            onClose()
                        }
                    }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                statuses.forEachIndexed { idx, _ ->
                    val fraction = when {
                        idx < pagerState.currentPage -> 1f
                        idx > pagerState.currentPage -> 0f
                        else -> progress.value
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.35f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction)
                                .background(Color.White)
                        )
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            statuses.getOrNull(pagerState.currentPage)?.takeIf { it.fromMe }?.let { current ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { deleteConfirmTarget = current },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete status",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val current = statuses.getOrNull(pagerState.currentPage) ?: statuses.first()
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = current.senderName.firstOrNull()?.uppercase() ?: "?",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    current.senderName.ifBlank { "Unknown" },
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    relativeTime(current.timestamp),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }

        deleteConfirmTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { deleteConfirmTarget = null },
                title = { Text("Delete status?") },
                text = {
                    Text(
                        if (target.fromMe)
                            "This will delete the status for everyone. This cannot be undone."
                        else
                            "Removes this status from Updates on this device."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        onDelete(target)
                        deleteConfirmTarget = null
                    }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { deleteConfirmTarget = null }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun StatusContent(
    status: StatusEntity,
    onVideoProgress: (Float) -> Unit,
    onVideoEnded: () -> Unit,
    onDownload: () -> Unit
) {
    when (status.mediaType.lowercase()) {
        "text" -> TextStatusContent(status)
        "image" -> ImageStatusContent(status)
        "video" -> VideoStatusContent(status, onDownload, onVideoProgress, onVideoEnded)
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Unsupported status", color = Color.White)
        }
    }
}

@Composable
private fun TextStatusContent(status: StatusEntity) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = status.text.ifBlank { "" },
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp)
        )
    }
}

@Composable
private fun ImageStatusContent(status: StatusEntity) {
    Box(Modifier.fillMaxSize()) {
        val bitmap = remember(status.id, status.localPath, status.thumbnail) {
            decodeStatusBitmap(status)
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LoadingIndicator()
        }
        if (status.localPath == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(56.dp, 0.dp)
                    .size(18.dp)
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
            }
        }
        if (status.text.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(16.dp)
            ) {
                Text(status.text, color = Color.White, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun VideoStatusContent(
    status: StatusEntity,
    onDownload: () -> Unit,
    onProgress: (Float) -> Unit,
    onEnded: () -> Unit
) {
    val context = LocalContext.current
    val localPath = status.localPath

    if (localPath == null || !File(localPath).exists()) {
        LaunchedEffect(status.id) { onDownload() }
        Box(Modifier.fillMaxSize()) {
            val thumb = remember(status.id, status.thumbnail) { decodeStatusBitmap(status) }
            if (thumb != null) {
                Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(56.dp)
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
            }
        }
        return
    }

    val exoPlayer = remember(localPath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(localPath))))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onEnded()
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer) {
        while (isActive) {
            val dur = exoPlayer.duration
            if (dur > 0) {
                onProgress((exoPlayer.currentPosition.toFloat() / dur.toFloat()).coerceIn(0f, 1f))
            }
            delay(100)
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        if (status.text.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(16.dp)
            ) {
                Text(status.text, color = Color.White, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.White)
    }
}

private fun decodeStatusBitmap(status: StatusEntity): android.graphics.Bitmap? {
    status.localPath?.let { path ->
        runCatching {
            val f = File(path)
            if (f.exists()) return BitmapFactory.decodeFile(f.absolutePath)
        }
    }
    if (status.thumbnail.isNotEmpty()) {
        runCatching {
            return BitmapFactory.decodeByteArray(status.thumbnail, 0, status.thumbnail.size)
        }
    }
    return null
}
