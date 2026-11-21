package com.whatsgo.app.ui.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.whatsgo.app.core.OggOpusWriter
import com.whatsgo.app.core.PhoneFormatter
import com.whatsgo.app.core.SessionHolder
import com.whatsgo.app.core.db.AppDatabase
import com.whatsgo.app.ui.chat.models.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.widget.Toast
import kotlinx.coroutines.cancel
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import androidx.compose.animation.Crossfade
import com.whatsgo.app.ui.home.ChatListViewModel

private suspend fun getAudioDuration(context: Context, filePath: String): Int =
    withContext(Dispatchers.IO) {
        runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            val durationStr =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            durationStr?.toLongOrNull()?.div(1000)?.toInt() ?: 0
        }.getOrDefault(0)
    }

private suspend fun generateWaveform(filePath: String): ByteArray? =
    withContext(Dispatchers.IO) {
        runCatching {
            ByteArray(50) { (Math.random() * 100).toInt().toByte() }
        }.getOrNull()
    }

private suspend fun convertAudioToOpus(context: Context, inputPath: String): String? =
    withContext(Dispatchers.IO) {
        val outputFile = File(context.cacheDir, "voice_${System.currentTimeMillis()}.ogg")
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var ogg: OggOpusWriter? = null
        try {
            extractor.setDataSource(inputPath)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return@withContext null
            extractor.selectTrack(track)
            val inFmt = extractor.getTrackFormat(track)
            val inMime = inFmt.getString(MediaFormat.KEY_MIME)!!
            var srcRate = inFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var srcCh = inFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)

            // Fast path: already mono Ogg/Opus → no work
            if (inMime == "audio/opus" && srcCh == 1 &&
                (inputPath.endsWith(".ogg", true) || inputPath.endsWith(".opus", true))
            ) return@withContext inputPath

            val dec = MediaCodec.createDecoderByType(inMime)
            decoder = dec
            dec.configure(inFmt, null, null, 0)
            dec.start()

            val encFmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, 48000, 1)
            encFmt.setInteger(MediaFormat.KEY_BIT_RATE, 24000)
            val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
            encoder = enc
            enc.configure(encFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            enc.start()

            val writer = OggOpusWriter(outputFile, 48000)
            ogg = writer
            val eInfo = MediaCodec.BufferInfo()
            var packets = 0
            var encDone = false
            var pts = 0L

            fun drain(timeoutUs: Long) {
                while (true) {
                    val o = enc.dequeueOutputBuffer(eInfo, timeoutUs)
                    if (o < 0) return
                    if (eInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && eInfo.size > 0) {
                        val b = enc.getOutputBuffer(o)!!
                        b.position(eInfo.offset); b.limit(eInfo.offset + eInfo.size)
                        val pkt = ByteArray(eInfo.size)
                        b.get(pkt)
                        writer.packet(pkt)
                        packets++
                    }
                    enc.releaseOutputBuffer(o, false)
                    if (eInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        encDone = true
                        return
                    }
                }
            }

            val frame = ShortArray(960)           // 20 ms @ 48 kHz
            var fill = 0
            fun queueFrame(len: Int, eos: Boolean) {
                while (true) {
                    val idx = enc.dequeueInputBuffer(2_000)
                    if (idx >= 0) {
                        val buf = enc.getInputBuffer(idx)!!
                        buf.clear(); buf.order(ByteOrder.LITTLE_ENDIAN)
                        for (k in 0 until len) buf.putShort(frame[k])
                        enc.queueInputBuffer(
                            idx, 0, len * 2, pts,
                            if (eos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                        )
                        pts += len * 1_000_000L / 48000
                        return
                    }
                    drain(0)
                }
            }
            fun emit(s: Short) {
                frame[fill++] = s
                if (fill == 960) { queueFrame(960, false); fill = 0; drain(0) }
            }

            // streaming linear resampler state
            var step = srcRate / 48000.0
            var p = 0.0
            var prev: Short = 0
            var mono = ShortArray(8192)

            val info = MediaCodec.BufferInfo()
            var inDone = false
            var outDone = false
            while (!outDone) {
                ensureActive()
                if (!inDone) {
                    val i = dec.dequeueInputBuffer(2_000)
                    if (i >= 0) {
                        val n = extractor.readSampleData(dec.getInputBuffer(i)!!, 0)
                        if (n < 0) {
                            dec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inDone = true
                        } else {
                            dec.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val o = dec.dequeueOutputBuffer(info, 2_000)
                when {
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = dec.outputFormat
                        srcRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        srcCh = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                        step = srcRate / 48000.0
                    }
                    o >= 0 -> {
                        if (info.size > 0) {
                            val bb = dec.getOutputBuffer(o)!!
                            bb.position(info.offset); bb.limit(info.offset + info.size)
                            val sb = bb.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val frames = sb.remaining() / srcCh
                            if (frames > 0) {
                                if (mono.size < frames) mono = ShortArray(frames)
                                if (srcCh == 1) sb.get(mono, 0, frames)
                                else for (k in 0 until frames) {
                                    var sum = 0
                                    for (c in 0 until srcCh) sum += sb.get().toInt()
                                    mono[k] = (sum / srcCh).toShort()
                                }
                                while (p < frames - 1) {
                                    val i0 = Math.floor(p).toInt()
                                    val fr = p - i0
                                    val a = if (i0 < 0) prev.toInt() else mono[i0].toInt()
                                    val b = mono[i0 + 1].toInt()
                                    emit((a + (b - a) * fr).toInt().toShort())
                                    p += step
                                }
                                p -= frames
                                prev = mono[frames - 1]
                            }
                        }
                        dec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                    }
                }
            }

            queueFrame(fill, true)                // flush tail + EOS
            while (!encDone) drain(10_000)

            writer.close()
            ogg = null
            if (packets > 0) outputFile.absolutePath else { outputFile.delete(); null }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.e("AudioConvert", "convert failed", e)
            runCatching { ogg?.close() }
            outputFile.delete()
            null
        } finally {
            runCatching { encoder?.stop() }; runCatching { encoder?.release() }
            runCatching { decoder?.stop() }; runCatching { decoder?.release() }
            runCatching { extractor.release() }
        }
    }

data class AudioFileData(
    val localPath: String,
    val fileName: String,
    val mimeType: String,
    val duration: Int,
    val waveform: ByteArray?
)


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
    val selected by viewModel.selected.collectAsState()
    val selectionMode = selected.isNotEmpty()
    val pinned by viewModel.pinned.collectAsState()
    val selfIsAdmin by viewModel.selfIsAdmin.collectAsState()
    val selectedMsgs = remember(messages, selected) { messages.filter { it.messageId in selected } }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showForwardSheet by remember { mutableStateOf(false) }
    var infoFor by remember { mutableStateOf<ChatMessage?>(null) }

    LaunchedEffect(Unit) {
        viewModel.toasts.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    var replyingTo by remember { mutableStateOf<ChatMessage?>(null) }
    var showMediaSheet by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var showChannelProfile by remember { mutableStateOf(false) }
    var showGroupProfile by remember { mutableStateOf(false) }
    var viewingMedia by remember { mutableStateOf<ChatMessage?>(null) }
    var searchOpen by rememberSaveable(chatJid) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(chatJid) { mutableStateOf("") }

    val visibleMessages = remember(messages, searchQuery) {
        if (searchQuery.isBlank()) messages
        else messages.filter { it.text.contains(searchQuery, ignoreCase = true) }
    }

    val listState = rememberLazyListState()

    var backProgress by remember { mutableStateOf(0f) }
    var isBackInProgress by remember { mutableStateOf(false) }
    val isGroup by viewModel.isGroup.collectAsState()

    PredictiveBackHandler(enabled = !selectionMode) { progressFlow ->
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
    BackHandler(enabled = selectionMode) { viewModel.clearSelection() }

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
                val thumb =
                    if (isVideo) generateVideoThumbnail(localPath) else generateImageThumbnail(localPath)
                if (isVideo) viewModel.sendVideo(localPath, thumbnail = thumb)
                else viewModel.sendImage(localPath, thumbnail = thumb)
            }
        }
    }

    val recording by viewModel.recording.collectAsState()

    var showAudioTypeDialog by remember { mutableStateOf<AudioFileData?>(null) }
    var isConverting by remember { mutableStateOf(false) }
    var pendingOgg by remember { mutableStateOf<Deferred<String?>?>(null) }

    // Coroutine scope for audio processing
    val audioProcessScope = remember { CoroutineScope(Dispatchers.IO + SupervisorJob()) }

    val audioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            audioProcessScope.launch {
                val copied = copyDocumentToCache(context, uri)
                if (copied != null) {
                    val (localPath, fileName) = copied
                    val mimeType = context.contentResolver.getType(uri) ?: "audio/mpeg"
                    val duration = getAudioDuration(context, localPath)
                    val waveform = generateWaveform(localPath)
                    showAudioTypeDialog =
                        AudioFileData(localPath, fileName, mimeType, duration, waveform)
                    pendingOgg = audioProcessScope.async {
                        convertAudioToOpus(context, localPath)
                    }
                }
            }
        }
    }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            audioProcessScope.launch {          // IO scope, file copy off main thread
                val copied = copyDocumentToCache(context, uri)
                if (copied != null) {
                    val (localPath, fileName) = copied
                    val mimeType = context.contentResolver.getType(uri)
                        ?: "application/octet-stream"
                    viewModel.sendDocument(localPath, fileName, mimeType)
                } else {
                    android.util.Log.e("DocPicker", "copyDocumentToCache returned null for $uri")
                }
            }
        }
    }

    DisposableEffect(Unit) { onDispose { audioProcessScope.cancel() } }

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
                Column {
                    Crossfade(targetState = selectionMode, animationSpec = tween(150), label = "topbar") { selecting ->
                        if (selecting) {
                            val single = selectedMsgs.singleOrNull()
                            val liveOk = selectedMsgs.none { it.messageId.startsWith("local_") }
                            SelectionTopBar(
                                count = selected.size,
                                canPin = !readOnly && single != null && !single.isDeleted && liveOk,
                                isPinned = single?.isPinned == true,
                                canInfo = single != null && (single.isMine || single.isDeleted) && liveOk && !readOnly,
                                canForward = selectedMsgs.isNotEmpty() && liveOk &&
                                        selectedMsgs.all { !it.isDeleted },
                                onClose = { viewModel.clearSelection() },
                                onPin = {
                                    single?.let { if (it.isPinned) viewModel.unpin(it) else showPinDialog = true }
                                },
                                onInfo = { infoFor = single; viewModel.clearSelection() },
                                onForward = { showForwardSheet = true },
                                onDelete = { showDeleteDialog = true },

                                )
                        } else {
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
                                },
                                searchOpen = searchOpen,
                                searchQuery = searchQuery,
                                onSearchQueryChange = { searchQuery = it },
                                onSearchClick = { searchOpen = true },
                                onCloseSearch = {
                                    searchOpen = false
                                    searchQuery = ""
                                }
                            )
                        }
                    }
                    PinnedBanner(pinned) { p ->
                        coroutineScope.launch {
                            val idx = messages.indexOfFirst { it.messageId == p.messageId }
                            if (idx >= 0) listState.animateScrollToItem(idx)
                            else Toast.makeText(context, "Scroll up to load that message", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
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
                messages = visibleMessages,
                listState = listState,
                onReplySwipe = { replyingTo = it },
                showSenderNames = isGroup,
                onLoadMore = viewModel::loadMore,
                selected = selected,
                onLongPress = { if (!readOnly) viewModel.startSelect(it.messageId) },
                onToggle = { viewModel.toggleSelect(it.messageId) },
                onMediaClick = { msg ->
                    val media = msg.media
                    when {
                        media == null -> Unit
                        msg.isMine && msg.status == MessageStatus.SENDING -> viewModel.cancelSend(msg)
                        msg.status == MessageStatus.FAILED -> viewModel.retrySend(msg)
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
                        MediaOption.AUDIO -> audioLauncher.launch(arrayOf("audio/*"))
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

        BackHandler(enabled = viewingMedia != null) {
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

        if (showDeleteDialog) {
            val nowSec = System.currentTimeMillis() / 1000
            val canEveryone = selectedMsgs.isNotEmpty() && selectedMsgs.all { m ->
                !m.isDeleted && !m.messageId.startsWith("local_") && !readOnly &&
                        if (m.isMine) nowSec - m.timestamp <= DELETE_WINDOW_SEC
                        else isGroup && selfIsAdmin
            }
            DeleteMessagesDialog(
                count = selected.size,
                canForEveryone = canEveryone,
                onForMe = { showDeleteDialog = false; viewModel.deleteForMe(selected) },
                onForEveryone = { showDeleteDialog = false; viewModel.deleteForEveryone(selectedMsgs) },
                onDismiss = { showDeleteDialog = false }
            )
        }

        if (showPinDialog) {
            PinDurationDialog(
                onPin = { secs ->
                    showPinDialog = false
                    selectedMsgs.singleOrNull()?.let { viewModel.pin(it, secs) }
                },
                onDismiss = { showPinDialog = false }
            )
        }

        if (showForwardSheet) {
            val listVm: ChatListViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val allChats by listVm.chats.collectAsState()
            ForwardSheet(
                chats = allChats,
                count = selected.size,
                onDismiss = { showForwardSheet = false },
                onSend = { targets ->
                    showForwardSheet = false
                    viewModel.forward(selected, targets)
                }
            )
        }

        infoFor?.let { m ->
            val live = messages.firstOrNull { it.messageId == m.messageId } ?: m
            val rows by remember(m.messageId) { viewModel.observeInfo(m.messageId) }
                .collectAsState(initial = emptyList())
            MessageInfoSheet(message = live, isGroup = isGroup, rows = rows, onDismiss = { infoFor = null })
        }

        showAudioTypeDialog?.let { audio ->
            AlertDialog(
                onDismissRequest = {
                    pendingOgg?.cancel()
                    pendingOgg = null
                    if (!isConverting) showAudioTypeDialog = null
                },
                title = { Text("Send audio") },
                text = {
                    Text(
                        if (isConverting) "Converting…"
                        else if (audio.duration > 300) "Too long for voice note.\nAudio file = sent as attachment."
                        else "${audio.fileName}\nVoice note = plays like recorded message. Audio file = sent as attachment."
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = !isConverting && audio.duration <= 300,
                        onClick = {
                            isConverting = true
                            coroutineScope.launch {
                                val ogg = pendingOgg?.await()
                                if (ogg != null) {
                                    viewModel.sendAudioMessage(
                                        ogg,
                                        audio.duration.coerceAtLeast(1),
                                        audio.waveform ?: ByteArray(50) { 10 }
                                    )
                                } else {
                                    Toast.makeText(context, "Convert failed", Toast.LENGTH_SHORT).show()
                                }
                                isConverting = false
                                pendingOgg = null
                                showAudioTypeDialog = null
                            }
                        }
                    ) { Text("Voice note") }
                },
                dismissButton = {
                    TextButton(
                        enabled = !isConverting,
                        onClick = {
                            pendingOgg?.cancel()
                            pendingOgg = null
                            viewModel.sendAudioFile(
                                audio.localPath, audio.fileName, audio.mimeType,
                                audio.duration, audio.waveform
                            )
                            showAudioTypeDialog = null
                        }
                    ) { Text("Audio file") }
                }
            )
        }
    }
}

@Composable
fun ConversationTopBar(
    profile: ChatProfile,
    onBack: () -> Unit,
    onProfileClick: () -> Unit,
    searchOpen: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onCloseSearch: () -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    var moreExpanded by remember { mutableStateOf(false) }
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
            if (searchOpen) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Search messages") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = cs.surfaceContainerHighest,
                        unfocusedContainerColor = cs.surfaceContainerHighest,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                IconButton(onClick = onCloseSearch, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Close, "Close search")
                }
            } else {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onProfileClick)
                        .padding(vertical = 8.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                Box {
                    val avatarFile = profile.avatarPath?.let { File(it) }
                    if (avatarFile != null && avatarFile.exists()) {
                        AsyncImage(
                            model = avatarFile,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
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
                    Text(
                        profile.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    AnimatedVisibility(
                        visible = profile.statusText.isNotBlank(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Text(
                            lastStatus,
                            fontSize = 13.sp,
                            color = if (profile.isOnline) cs.primary else cs.onSurfaceVariant
                        )
                    }
                }
                }
                Box {
                    IconButton(onClick = { moreExpanded = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.MoreVert, "More options")
                    }
                    DropdownMenu(
                        expanded = moreExpanded,
                        onDismissRequest = { moreExpanded = false },
                        modifier = Modifier.widthIn(min = 196.dp),
                        offset = DpOffset(0.dp, 4.dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Search") },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = "Search messages"
                                )
                            },
                            onClick = {
                                moreExpanded = false
                                onSearchClick()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PulsingOnlineIndicator(
    color: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
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
