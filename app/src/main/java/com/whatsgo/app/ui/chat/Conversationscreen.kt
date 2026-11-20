package com.whatsgo.app.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.whatsgo.app.ui.chat.models.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import kotlin.time.Duration.Companion.milliseconds
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.platform.LocalContext
import com.whatsgo.app.core.PhoneFormatter
import com.whatsgo.app.core.SessionHolder
import com.whatsgo.app.core.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flowOf
import android.net.Uri

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.filled.Person

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConversationScreen(
    chatJid: String,
    contactName: String,
    contactStatus: String,
    contactAvatarPath: String? = null,
    readOnly: Boolean = false,
    onBack: () -> Unit = {},
    viewModel: ChatViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }

    val liveContact by remember(chatJid) {
        db.contactDao().observeByJid(chatJid)
    }.collectAsState(initial = null)

    val channel by remember(chatJid, readOnly) {
        if (readOnly) db.channelDao().observeByJid(chatJid) else flowOf(null)
    }.collectAsState(initial = null)

    val groupInfo by viewModel.groupInfo.collectAsState()
    val favoriteStickers by viewModel.favoriteStickers.collectAsState()
    val recentStickers by viewModel.recentStickers.collectAsState()
    val receivedStickers by viewModel.receivedStickers.collectAsState()

    LaunchedEffect(chatJid, readOnly) {
        if (readOnly) SessionHolder.get(context).refreshChannelInfo(chatJid)
    }

    val avatarPath = liveContact?.avatarPath ?: contactAvatarPath

    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val isKeyboardOpen = WindowInsets.isImeVisible

    DisposableEffect(chatJid) {
        viewModel.open(chatJid)
        runCatching { SessionHolder.get(context).requestAvatarRefresh(chatJid) }
        onDispose {
            viewModel.close()
        }
    }

    val phone by produceState(initialValue = "", chatJid) {
        value = if (chatJid.endsWith("@s.whatsapp.net")) {
            withContext(Dispatchers.Default) { PhoneFormatter.format(context, chatJid) }
        } else ""
    }

    val liveSubtitle by viewModel.presenceSubtitle.collectAsState()
    val channelStatus = channel?.let { "Channel · ${formatCount(it.subscribers)} followers" }
    val groupStatus = groupInfo?.let { "${it.memberCount} members" }
    val displayStatus = liveSubtitle ?: channelStatus ?: groupStatus ?: ""
    val isOnline = displayStatus.equals("online", ignoreCase = true) ||
            displayStatus.startsWith("typing", ignoreCase = true) ||
            displayStatus.startsWith("recording", ignoreCase = true)

    val shownName = channel?.name
        ?: groupInfo?.name?.takeIf { it.isNotBlank() }
        ?: contactName
    val profile = remember(shownName, displayStatus, isOnline, avatarPath, phone) {
        ChatProfile(
            name = shownName,
            avatarLetter = shownName.take(1).uppercase(),
            statusText = displayStatus,
            isOnline = isOnline,
            phone = phone,
            avatarPath = avatarPath
        )
    }

    val messages by viewModel.messages.collectAsState()
    var replyingTo by remember { mutableStateOf<ChatMessage?>(null) }
    var showMediaSheet by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var showChannelProfile by remember { mutableStateOf(false) }
    var showGroupProfile by remember { mutableStateOf(false) }
    var viewingMedia by remember { mutableStateOf<ChatMessage?>(null) }

    val listState = rememberLazyListState()

    var backProgress by remember { mutableStateOf(0f) }
    var isBackInProgress by remember { mutableStateOf(false) }
    val isGroup by viewModel.isGroup.collectAsState()

    PredictiveBackHandler(enabled = true) { progressFlow ->
        try {
            isBackInProgress = true
            progressFlow.collect { backEvent -> backProgress = backEvent.progress }
            onBack()
        } catch (_: CancellationException) {
            backProgress = 0f
        } finally {
            isBackInProgress = false
        }
    }

    val newest = messages.firstOrNull()
    val newestId = newest?.messageId
    val newestIsMine = newest?.isMine == true

    LaunchedEffect(newestId) {
        if (newestId != null && (newestIsMine || listState.firstVisibleItemIndex < 2)) {
            listState.animateScrollToItem(0)
        }
    }

    val scaleFactor = 1f - (backProgress * 0.06f)
    val translationXFactor = backProgress * 80f
    val cornerRadius = (backProgress * 32).dp

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        uris.forEach { uri ->
            val mimeType = context.contentResolver.getType(uri) ?: ""
            val isVideo = mimeType.startsWith("video")
            val localPath = copyUriToCacheFile(context, uri, if (isVideo) "mp4" else "jpg")
            if (localPath != null) {
                val thumb = if (isVideo) generateVideoThumbnail(localPath) else generateImageThumbnail(localPath)
                if (isVideo) viewModel.sendVideo(localPath, thumbnail = thumb)
                else viewModel.sendImage(localPath, thumbnail = thumb)
            }
        }
    }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { documentUri ->
            val localPath = copyUriToCacheFile(context, documentUri, "bin")
            if (localPath != null) {
                val fileName = getFileNameFromUri(context, documentUri)
                viewModel.sendDocument(localPath, fileName)
            }
        }
    }

    val recording by viewModel.recording.collectAsState()
    val recordSeconds by viewModel.recordSeconds.collectAsState()
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startRecording() }
    val startVoice = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.startRecording()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .graphicsLayer {
                scaleX = scaleFactor
                scaleY = scaleFactor
                translationX = translationXFactor
                shape = RoundedCornerShape(cornerRadius)
                clip = backProgress > 0f
            }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            topBar = {
                ConversationTopBar(
                    profile = profile,
                    onBack = onBack,
                    onProfileClick = {
                        android.util.Log.d("ChannelUI", "profile click readOnly=$readOnly")
                        coroutineScope.launch {
                            if (isKeyboardOpen) {
                                keyboardController?.hide()
                                delay(290.milliseconds)
                            }
                            when {
                                readOnly -> showChannelProfile = true
                                isGroup -> showGroupProfile = true
                                else -> showProfile = true
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (!readOnly) {
                    MessageInput(
                        favoriteStickers = favoriteStickers,
                        recentStickers = recentStickers,
                        receivedStickers = receivedStickers,
                        onStickerSend = viewModel::sendSticker,
                        onStickerFavorite = viewModel::toggleFavoriteSticker,
                        onStickerDownload = viewModel::downloadSticker,
                        replyingTo = replyingTo,
                        onDismissReply = { replyingTo = null },
                        isRecording = recording,
                        recordSeconds = recordSeconds,
                        onVoiceStart = { startVoice() },
                        onVoiceCancel = { viewModel.finishRecording(send = false) },
                        onVoiceSend = { viewModel.finishRecording(send = true) },
                        onAttachClick = {
                            coroutineScope.launch {
                                if (isKeyboardOpen) {
                                    keyboardController?.hide()
                                    delay(290.milliseconds)
                                }
                                showMediaSheet = true
                            }
                        },
                        onTyping = { text ->
                            viewModel.onUserTyping(text)
                        },
                        onSend = { text ->
                            viewModel.send(text, replyTo = replyingTo)
                            viewModel.onUserTyping("")
                            replyingTo = null
                            coroutineScope.launch { listState.animateScrollToItem(0) }
                        }
                    )
                }
            }
        ) { padding ->
            MessageList(
                messages = messages,
                listState = listState,
                onReplySwipe = { replyingTo = it },
                showSenderNames = isGroup,
                onLoadMore = viewModel::loadMore,
                onMediaClick = { msg ->
                    val media = msg.media
                    when {
                        media == null -> Unit
                        media.type == MediaType.VOICE -> viewModel.toggleVoice(msg)
                        media.isDownloaded -> viewingMedia = msg
                        else -> viewModel.downloadMedia(msg)
                    }
                },
                modifier = Modifier.padding(padding)
            )
        }

        val channelLoading by viewModel.channelLoading.collectAsState()
        if (readOnly && messages.isEmpty() && channelLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        if (showMediaSheet) {
            MediaPickerSheet(
                onDismiss = { showMediaSheet = false },
                onOptionSelected = { option ->
                    showMediaSheet = false
                    when (option) {
                        MediaOption.GALLERY -> galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                        MediaOption.DOCUMENT -> documentLauncher.launch(arrayOf("*/*"))
                        else -> Unit
                    }
                }
            )
        }

        viewingMedia?.let { msg ->
            msg.media?.let { media ->
                MediaViewerScreen(
                    mediaType = media.type,
                    filePath = media.urlOrPath,
                    onBack = { viewingMedia = null }
                )
            }
        }

        androidx.activity.compose.BackHandler(enabled = viewingMedia != null) {
            viewingMedia = null
        }

        if (showProfile) {
            val session = remember { SessionHolder.get(context) }
            val fetchAbout: suspend () -> String = remember(chatJid, session) {
                { session.getContactAbout(chatJid) }
            }
            ChatProfileSheet(
                profile = profile,
                onDismiss = { showProfile = false },
                fetchAbout = fetchAbout
            )
        }
        if (showChannelProfile) {
            android.util.Log.d("ChannelUI", "showChannelProfile=$showChannelProfile")
            ChannelProfileSheet(
                name = shownName,
                avatarPath = avatarPath,
                channel = channel,
                onDismiss = { showChannelProfile = false }
            )
        }

        if (showGroupProfile) {
            GroupProfileSheet(
                name = shownName,
                avatarPath = avatarPath,
                info = groupInfo,
                onDismiss = { showGroupProfile = false }
            )
        }

    }
}

private fun copyUriToCacheFile(context: android.content.Context, uri: Uri, extension: String): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val file = java.io.File(context.cacheDir, "share_${System.currentTimeMillis()}.$extension")
        file.outputStream().use { output ->
            inputStream.copyTo(output)
        }
        file.absolutePath
    } catch (e: Exception) {
        null
    }
}

private fun getFileNameFromUri(context: android.content.Context, uri: Uri): String {
    return try {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) it.getString(nameIndex) ?: "document"
                else "document"
            } else "document"
        } ?: "document"
    } catch (e: Exception) {
        "document"
    }
}
@Composable
fun ConversationTopBar(
    profile: ChatProfile,
    onBack: () -> Unit,
    onProfileClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surfaceContainer, tonalElevation = 2.dp, shadowElevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onProfileClick)
                    .padding(vertical = 8.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    val avatarFile = profile.avatarPath?.let { java.io.File(it) }
                    if (avatarFile != null && avatarFile.exists()) {
                        coil3.compose.AsyncImage(
                            model = avatarFile,
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(cs.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = null,
                                modifier = Modifier.size(26.dp),
                                tint = cs.onSecondaryContainer
                            )
                        }
                    }
                    if (profile.isOnline) {
                        PulsingOnlineIndicator(
                            color = cs.tertiary,
                            backgroundColor = cs.surfaceContainer,
                            modifier = Modifier.align(Alignment.BottomEnd)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                var lastStatus by remember { mutableStateOf("") }
                if (profile.statusText.isNotBlank()) lastStatus = profile.statusText

                Column(modifier = Modifier.animateContentSize()) {
                    Text(profile.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    AnimatedVisibility(
                        visible = profile.statusText.isNotBlank(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Text(lastStatus, fontSize = 13.sp, color = if (profile.isOnline) cs.primary else cs.onSurfaceVariant)
                    }
                }
            }
            IconButton(onClick = { /* TODO: Open More Menu */ }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.MoreVert, "More options")
            }
        }
    }
}

@Composable
private fun PulsingOnlineIndicator(
    color: androidx.compose.ui.graphics.Color,
    backgroundColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(animation = tween(1200), repeatMode = RepeatMode.Reverse),
        label = "scale"
    )
    Box(
        modifier = modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(color)
                .scale(scale)
        )
    }
}

private fun copyUriToCacheFile(context: android.content.Context, uri: Uri, extension: String): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val file = java.io.File(context.cacheDir, "share_${System.currentTimeMillis()}.$extension")
        file.outputStream().use { output ->
            inputStream.copyTo(output)
        }
        file.absolutePath
    } catch (e: Exception) {
        null
    }
}

private fun getFileNameFromUri(context: android.content.Context, uri: Uri): String {
    return try {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) it.getString(nameIndex) ?: "document"
                else "document"
            } else "document"
        } ?: "document"
    } catch (e: Exception) {
        "document"
    }
}
