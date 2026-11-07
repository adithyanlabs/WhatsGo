package com.whatsgo.app.ui.login

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
import com.whatsgo.app.core.LoginEvent
import com.whatsgo.app.core.SessionHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service shown while a pairing code is waiting to be entered on the phone.
 * Keeps the process alive in background so the pairing handshake is not killed.
 * Notification shows the code, a Copy button and a Dismiss button (which stops the service).
 * Stops itself when login succeeds or the session logs out.
 */
class PairingForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var code: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        scope.launch {
            SessionHolder.get(applicationContext).events.collect { event ->
                if (event is LoginEvent.Connected || event is LoginEvent.LoggedOut) {
                    shutDown()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutDown()
                return START_NOT_STICKY
            }
            ACTION_COPY -> {
                if (code.isNotEmpty()) copyCode()
            }
            else -> {
                code = intent?.getStringExtra(EXTRA_CODE) ?: code
                if (code.isEmpty()) {
                    shutDown()
                    return START_NOT_STICKY
                }
                val notification = buildNotification()
                val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else 0
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun shutDown() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun copyCode() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Pairing code", code))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, "Code copied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pairing code",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows the WhatsApp pairing code while linking this device"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, PairingForegroundService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            ),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("WhatsApp pairing code")
            .setContentText(code)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$code\nEnter this on your phone: Linked Devices → Link with phone number.")
            )
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(0, "Copy", serviceIntent(ACTION_COPY, 1))
            .addAction(0, "Dismiss", serviceIntent(ACTION_STOP, 2))
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "pairing_code"
        private const val NOTIFICATION_ID = 4107
        private const val EXTRA_CODE = "extra_code"
        private const val ACTION_COPY = "com.whatsgo.app.action.COPY_PAIRING_CODE"
        private const val ACTION_STOP = "com.whatsgo.app.action.STOP_PAIRING"

        fun start(context: Context, code: String) {
            val intent = Intent(context, PairingForegroundService::class.java)
                .putExtra(EXTRA_CODE, code)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PairingForegroundService::class.java))
        }
    }
}