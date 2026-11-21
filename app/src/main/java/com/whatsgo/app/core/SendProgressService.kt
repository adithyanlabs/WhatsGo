package com.whatsgo.app.core

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
import android.os.SystemClock
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Shows one ongoing notification while any media upload is in flight.
 * Driven entirely by ChatSession.uploadProgress. Stops itself when map empties.
 */
class SendProgressService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var lastNotifyAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val session = SessionHolder.get(applicationContext)

        if (intent?.action == ACTION_CANCEL_ALL) {
            session.cancelAllUploads()
            return START_NOT_STICKY
        }

        createChannel()
        ServiceCompat.startForeground(
            this, NOTIF_ID, build(session.uploadProgress.value),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        )

        if (job?.isActive != true) {
            job = scope.launch {
                session.uploadProgress.collectLatest { m ->
                    if (m.isEmpty()) {
                        delay(800)          // new upload within 800 ms cancels this
                        shutDown()
                    } else {
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastNotifyAt >= 400) {   // notify() is rate limited
                            lastNotifyAt = now
                            nm().notify(NOTIF_ID, build(m))
                        }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) = shutDown()

    private fun shutDown() {
        job = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun nm() = getSystemService(NotificationManager::class.java)

    private fun build(m: Map<String, Int>): Notification {
        val n = m.size
        val avg = if (n == 0) 0 else m.values.sum() / n

        val open = PendingIntent.getActivity(
            this, 20,
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            ),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val cancel = PendingIntent.getService(
            this, 21,
            Intent(this, SendProgressService::class.java).setAction(ACTION_CANCEL_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(if (n <= 1) "Sending file" else "Sending $n files")
            .setContentText(if (avg > 0) "$avg%" else "Preparing…")
            .setProgress(100, avg, avg == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(0, "Cancel", cancel)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = nm()
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Sending media", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "send_progress"
        private const val NOTIF_ID = 4401
        private const val ACTION_CANCEL_ALL = "com.whatsgo.app.action.CANCEL_ALL_UPLOADS"

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context, Intent(context, SendProgressService::class.java)
                )
            }.onFailure { android.util.Log.w("SendProgress", "start failed", it) }
        }
    }
}