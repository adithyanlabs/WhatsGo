package com.whatsgo.app.core.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.whatsgo.app.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val version: String, val progress: Float) : DownloadState
    data class Ready(val version: String, val path: String) : DownloadState
    data class Failed(val message: String) : DownloadState
}

class UpdateDownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            cancelDownload()
            return START_NOT_STICKY
        }
        if (job?.isActive == true) return START_NOT_STICKY

        val version = intent?.getStringExtra(EXTRA_VERSION)
        val url = intent?.getStringExtra(EXTRA_URL)
        if (version == null || url == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val info = UpdateInfo(version, "", url, intent.getLongExtra(EXTRA_SIZE, 0L))

        createChannels()
        ServiceCompat.startForeground(
            this, PROGRESS_ID, progressNotification(version, 0),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        )
        _state.value = DownloadState.Downloading(version, 0f)

        job = scope.launch {
            try {
                var lastPct = -1
                val file = Updater.download(applicationContext, info) { p ->
                    val pct = (p * 100).toInt()
                    if (pct != lastPct) {
                        lastPct = pct
                        _state.value = DownloadState.Downloading(version, p)
                        nm().notify(PROGRESS_ID, progressNotification(version, pct))
                    }
                }
                _state.value = DownloadState.Ready(version, file.absolutePath)
                finish()
                nm().notify(READY_ID, readyNotification(version, file.absolutePath))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = DownloadState.Failed(e.message ?: "unknown error")
                finish()
                nm().notify(READY_ID, failedNotification())
            }
        }
        return START_NOT_STICKY
    }

    private fun cancelDownload() {
        job?.cancel()
        _state.value = DownloadState.Idle
        finish()
    }

    private fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        job?.cancel()
        _state.value = DownloadState.Failed("Download timed out")
        finish()
    }

    override fun onDestroy() {
        scope.cancel()
        if (_state.value is DownloadState.Downloading) _state.value = DownloadState.Idle
        super.onDestroy()
    }

    private fun nm() = getSystemService(NotificationManager::class.java)

    private fun openApp(action: String? = null, path: String? = null): PendingIntent {
        val i = Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        if (action != null) i.setAction(action).putExtra(EXTRA_PATH, path)
        return PendingIntent.getActivity(
            this, if (action == null) 10 else 11, i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun progressNotification(version: String, pct: Int): Notification {
        val cancel = PendingIntent.getService(
            this, 12,
            Intent(this, UpdateDownloadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading WhatsGo $version")
            .setContentText("$pct%")
            .setProgress(100, pct, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openApp())
            .addAction(0, "Cancel", cancel)
            .build()
    }

    private fun readyNotification(version: String, path: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_READY)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Update ready")
            .setContentText("WhatsGo $version downloaded. Tap to install.")
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(ACTION_INSTALL, path))
            .build()

    private fun failedNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_READY)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Update failed")
            .setContentText("Couldn't download the update. Open WhatsGo to try again.")
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = nm()
        if (nm.getNotificationChannel(CHANNEL_PROGRESS) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_PROGRESS, "Update download", NotificationManager.IMPORTANCE_LOW)
            )
        }
        if (nm.getNotificationChannel(CHANNEL_READY) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_READY, "Update ready", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    companion object {
        const val ACTION_INSTALL = "com.whatsgo.app.action.INSTALL_UPDATE"
        const val EXTRA_PATH = "update_path"
        private const val ACTION_CANCEL = "com.whatsgo.app.action.CANCEL_UPDATE"
        private const val EXTRA_VERSION = "version"
        private const val EXTRA_URL = "url"
        private const val EXTRA_SIZE = "size"
        private const val CHANNEL_PROGRESS = "update_progress"
        private const val CHANNEL_READY = "update_ready"
        private const val PROGRESS_ID = 4301
        private const val READY_ID = 4302

        private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
        val state: StateFlow<DownloadState> = _state.asStateFlow()

        fun start(context: Context, info: UpdateInfo) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, UpdateDownloadService::class.java)
                    .putExtra(EXTRA_VERSION, info.version)
                    .putExtra(EXTRA_URL, info.apkUrl)
                    .putExtra(EXTRA_SIZE, info.sizeBytes)
            )
        }

        fun cancel(context: Context) {
            context.startService(
                Intent(context, UpdateDownloadService::class.java).setAction(ACTION_CANCEL)
            )
        }
    }
}