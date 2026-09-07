package com.dinotv.app

import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.dinotv.app.domain.TvPower
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TvWakeService : Service() {
    private val worker = Executors.newSingleThreadScheduledExecutor()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val notification = notification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        worker.scheduleWithFixedDelay({ poll() }, 0, 1, TimeUnit.SECONDS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun poll() {
        val session = TvPrefs.session(this)
        if (session.isBlank()) return
        try {
            val connection = URL(SNAPSHOT_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $session")
            connection.setRequestProperty("X-Dino-Visible", "0")
            connection.connectTimeout = 4_000
            connection.readTimeout = 4_000
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val json = JSONObject(body)
            val power = json.optString("power", "on")
            val powerAt = json.optString("powerAt", "")
            if (!TvPower.shouldApply(TvPrefs.powerAt(this), powerAt)) return
            TvPrefs.savePowerAt(this, powerAt)
            if (power != "on") return
            bringDinoToFront()
        } catch (_: Exception) {
            // The remote can retry; a quiet miss is better than crashing the watchman.
        }
    }

    private fun bringDinoToFront() {
        try {
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            for (task in am.appTasks) {
                task.moveToFront()
            }
        } catch (_: Exception) {
            // Fall through to a launch intent if the existing task cannot be raised.
        }
        startLaunchIntent()
    }

    private fun startLaunchIntent() {
        val open = launchIntent()
        if (Build.VERSION.SDK_INT >= 34) {
            val pending = PendingIntent.getActivity(
                this,
                1,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val options = ActivityOptions.makeBasic().apply {
                setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                )
            }
            try {
                pending.send(this, 0, null, null, null, null, options.toBundle())
                return
            } catch (_: Exception) {
                pingFullScreen(open)
            }
        }
        try {
            startActivity(open)
        } catch (_: Exception) {
            pingFullScreen(open)
        }
    }

    private fun launchIntent(): Intent {
        val open = packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)
        open.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
        )
        return open
    }

    private fun pingFullScreen(open: Intent) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(WAKE_CHANNEL_ID, "Открытие Dino TV", NotificationManager.IMPORTANCE_HIGH),
        )
        val pending = PendingIntent.getActivity(
            this,
            2,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            WAKE_NOTIFICATION_ID,
            NotificationCompat.Builder(this, WAKE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_dino)
                .setContentTitle("Dino TV")
                .setContentText("Открываю домашний экран")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(pending, true)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Dino TV", NotificationManager.IMPORTANCE_MIN),
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dino)
            .setContentTitle("Dino TV")
            .setContentText("Слушает пульт")
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "dino_tv_wake"
        private const val WAKE_CHANNEL_ID = "dino_tv_open"
        private const val NOTIFICATION_ID = 7
        private const val WAKE_NOTIFICATION_ID = 8
        private const val SNAPSHOT_URL = "https://api.dym-dino.ru/v1/display/snapshot"
    }
}
