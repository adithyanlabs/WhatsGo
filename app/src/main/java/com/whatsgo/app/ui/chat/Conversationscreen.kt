package com.whatsgo.app.ui.chat

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.platform.LocalContext
import com.whatsgo.app.core.SessionHolder
import com.whatsgo.app.core.db.AppDatabase

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConversationScreen(
    chatJid: String,
    contactName: String,
    contactStatus: String,
    contactAvatarPath: String? = null,
    onBack: () -> Unit = {},
    viewModel: ChatViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }

    val liveContact by remember(chatJid) {
        db.contactDao().observeByJid(chatJid)
    }.collectAsState(initial = null)

    val avatarPath = liveContact?.avatarPath ?: contactAvatarPath

    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val isKeyboardOpen = WindowInsets.isImeVisible

    LaunchedEffect(chatJid) {
        viewModel.open(chatJid)
        runCatching { SessionHolder.get(context).requestAvatarRefresh(chatJid) }
    }

    val phone = remember(chatJid) {
        if (chatJid.endsWith("@s.whatsapp.net")) {
            "+" + chatJid.substringBefore("@")
        } else ""
    }

    val profile = remember(contactName, contactStatus, avatarPath, phone) {
        ChatProfile(
            name = contactName,
            avatarLetter = contactName.take(1).uppercase(),
            statusText = contactStatus,
            isOnline = contactStatus.equals("online", ignoreCase = true),
            phone = phone,
            avatarPath = avatarPath
        )
    }

    val messages by viewModel.messages.collectAsState()
    var replyingTo by remember { mutableStateOf<ChatMessage?>(null) }
    var showMediaSheet by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
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

    LaunchedEffect(messages.size) {
        if (listState.firstVisibleItemIndex < 2) {
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
                        coroutineScope.launch {
                            if (isKeyboardOpen) {
                                keyboardController?.hide()
                                delay(290.milliseconds)
                            }
                            showProfile = true
                        }
                    }
                )
            },
            bottomBar = {
                MessageInput(
                    replyingTo = replyingTo,
                    onDismissReply = { replyingTo = null },
                    onAttachClick = {
                        coroutineScope.launch {
                            if (isKeyboardOpen) {
                                keyboardController?.hide()
                                delay(290.milliseconds)
                            }
                            showMediaSheet = true
                        }
                    },
                    onSend = { text ->
                        viewModel.send(text, replyTo = replyingTo)
                        replyingTo = null
                        coroutineScope.launch { listState.animateScrollToItem(0) }
                    }
                )
            }
        ) { padding ->
            MessageList(
                messages = messages,
                listState = listState,
                onReplySwipe = { replyingTo = it },
                showSenderNames = isGroup,
                onMediaClick = { msg ->
                    val media = msg.media
                    if (media != null && media.isDownloaded) {
                        viewingMedia = msg
                    } else {
                        viewModel.downloadMedia(msg)
                    }
                },
                modifier = Modifier.padding(padding)
            )
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
                Icon(Icons.Filled.ArrowBack, "Back")
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
                            Text(
                                profile.avatarLetter,
                                color = cs.onSecondaryContainer,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
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
                Column {
                    Text(profile.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(profile.statusText, fontSize = 13.sp, color = if (profile.isOnline) cs.primary else cs.onSurfaceVariant)
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