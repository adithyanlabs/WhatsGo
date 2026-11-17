package com.whatsgo.app.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.whatsgo.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HistorySyncService : Service() {

    enum class Phase { LINKING, SYNCING, OFFLINE }

    data class Ui(
        val phase: Phase = Phase.LINKING,
        val code: String? = null,
        val progress: Int? = null,
        val chats: Int = 0,
        val total: Int = 0,
        val done: Int = 0,
        val finished: Boolean = false
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watchdog: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        createChannel()

        scope.launch {
            ui.collect {
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIF_ID, buildNotification(it))
                delay(500)
            }
        }
        scope.launch { ui.collect { armWatchdog(it) } }

        scope.launch {
            SessionHolder.get(applicationContext).events.collect { e ->
                when (e) {
                    is LoginEvent.Connected ->
                        ui.update { it.copy(phase = Phase.SYNCING, code = null) }
                    is LoginEvent.LoggedOut -> shutDown()
                    else -> Unit
                }
            }
        }

        scope.launch {
            ui.collect {
                if (it.phase == Phase.OFFLINE && it.finished) {
                    shutDown()
                    return@collect
                }
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIF_ID, buildNotification(it))
                delay(500)
            }
        }
        scope.launch { ui.collect { armWatchdog(it) } }

        scope.launch {
            SessionHolder.get(applicationContext).events.collect { e ->
                when (e) {
                    is LoginEvent.Connected ->
                        ui.update {
                            if (it.phase == Phase.LINKING) it.copy(phase = Phase.SYNCING, code = null) else it
                        }
                    is LoginEvent.LoggedOut -> shutDown()
                    else -> Unit
                }
            }
        }

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_COPY -> {
                ui.value.code?.let { copyCode(it) }
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                shutDown()
                return START_NOT_STICKY
            }
        }
        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification(ui.value),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        )
        return START_NOT_STICKY
    }

    private fun armWatchdog(s: Ui) {
        val ms = when {
            s.phase == Phase.LINKING -> LINK_TIMEOUT_MS
            s.phase == Phase.OFFLINE -> OFFLINE_IDLE_TIMEOUT_MS
            s.progress != null && s.progress >= 100 -> 3_000L
            s.progress == null && s.chats == 0 -> FIRST_CHUNK_TIMEOUT_MS
            else -> IDLE_TIMEOUT_MS
        }
        watchdog?.cancel()
        watchdog = scope.launch {
            delay(ms)
            shutDown()
        }
    }

    override fun onTimeout(startId: Int, fgsType: Int) = shutDown()

    private fun shutDown() {
        scope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        ui.value = Ui()
        super.onDestroy()
    }

    private fun copyCode(code: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Pairing code", code))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, "Code copied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildNotification(s: Ui): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            ),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)

        when (s.phase) {
            Phase.LINKING -> {
                if (s.code != null) {
                    b.setContentTitle("WhatsApp pairing code")
                        .setContentText(s.code)
                        .setStyle(
                            NotificationCompat.BigTextStyle().bigText(
                                "${s.code}\nPhone: Linked Devices → Link with phone number."
                            )
                        )
                        .addAction(0, "Copy", actionIntent(ACTION_COPY, 1))
                } else {
                    b.setContentTitle("Linking WhatsApp")
                        .setContentText("Waiting for you to link this device…")
                }
                b.setProgress(0, 0, true)
            }
            Phase.SYNCING -> {
                val p = s.progress
                b.setContentTitle("Syncing chats")
                    .setContentText(
                        buildString {
                            if (s.chats > 0) append("${s.chats} chats")
                            if (p != null) {
                                if (isNotEmpty()) append(" · ")
                                append("$p%")
                            }
                            if (isEmpty()) append("Getting your messages…")
                        }
                    )
                    .setProgress(100, p ?: 0, p == null)
            }
            Phase.OFFLINE -> {
                b.setContentTitle("Loading new messages")
                    .setContentText(
                        if (s.total > 0) "${minOf(s.done, s.total)} / ${s.total}" else "Catching up…"
                    )
                    .setProgress(s.total, minOf(s.done, s.total), s.total <= 0)
            }
        }
        return b.build()
    }

    private fun actionIntent(action: String, req: Int): PendingIntent =
        PendingIntent.getService(
            this, req,
            Intent(this, HistorySyncService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Linking & sync", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "link_sync"
        private const val NOTIF_ID = 4201
        private const val ACTION_COPY = "com.whatsgo.app.action.COPY_PAIRING_CODE"
        private const val ACTION_STOP = "com.whatsgo.app.action.STOP_SYNC"

        private const val LINK_TIMEOUT_MS = 10 * 60_000L
        private const val FIRST_CHUNK_TIMEOUT_MS = 90_000L
        private const val IDLE_TIMEOUT_MS = 30_000L
        private const val OFFLINE_IDLE_TIMEOUT_MS = 60_000L

        @Volatile var running = false
            private set

        private val ui = MutableStateFlow(Ui())

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, HistorySyncService::class.java)
            )
        }

        fun setPairingCode(code: String?) {
            if (running) ui.update { it.copy(code = code) }
        }

        fun markConnected() {
            ui.update {
                if (it.phase == Phase.LINKING) it.copy(phase = Phase.SYNCING, code = null) else it
            }
        }

        fun report(p: Int?, chatDone: Boolean = false) {
            if (!running) return
            ui.update { s ->
                s.copy(
                    phase = Phase.SYNCING,
                    code = null,
                    progress = when {
                        p == null -> s.progress
                        else -> maxOf(p, s.progress ?: 0)
                    },
                    chats = if (chatDone) s.chats + 1 else s.chats
                )
            }
        }

        fun startOffline(context: Context, total: Int) {
            if (running && ui.value.phase != Phase.OFFLINE) return
            ui.value = Ui(phase = Phase.OFFLINE, total = total)
            runCatching { start(context) }
        }

        fun reportOffline(n: Int) {
            ui.update { if (it.phase == Phase.OFFLINE) it.copy(done = it.done + n) else it }
        }

        fun finishOffline() {
            ui.update { if (it.phase == Phase.OFFLINE) it.copy(finished = true) else it }
        }
    }
}