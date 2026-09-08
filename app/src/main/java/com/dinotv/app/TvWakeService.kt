package com.dinotv.app

import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.dinotv.app.domain.EventReminders
import com.dinotv.app.domain.IncomingEvent
import com.dinotv.app.domain.TvPower
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TvWakeService : Service() {
    private val worker = Executors.newSingleThreadScheduledExecutor()
    private val main = Handler(Looper.getMainLooper())

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
        main.removeCallbacksAndMessages(null)
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
            maybeCue(json)
            val power = json.optString("power", "on")
            val powerAt = json.optString("powerAt", "")
            if (!TvPower.shouldApply(TvPrefs.powerAt(this), powerAt)) return
            TvPrefs.savePowerAt(this, powerAt)
            if (power != "on") return
            requestForeground()
        } catch (_: Exception) {
            // The remote can retry; a quiet miss is better than crashing the watchman.
        }
    }

    private fun maybeCue(json: JSONObject) {
        val display = json.optJSONObject("display") ?: JSONObject()
        val days = json.optJSONArray("days") ?: return
        val events = mutableListOf<IncomingEvent>()
        for (i in 0 until days.length()) {
            val day = days.optJSONObject(i) ?: continue
            val list = day.optJSONArray("events") ?: continue
            for (j in 0 until list.length()) {
                val event = list.optJSONObject(j) ?: continue
                events += IncomingEvent(
                    id = event.optString("id"),
                    title = event.optString("title"),
                    start = event.optString("start"),
                    allDay = event.optBoolean("allDay", false),
                    owner = event.optString("ownerName").ifBlank { event.optString("calendarName") },
                )
            }
        }
        val cue = EventReminders.next(
            events,
            System.currentTimeMillis(),
            TvPrefs.shownCues(this),
            display.optBoolean("privacy", false),
            display.optBoolean("showCalendar", true),
        ) ?: return
        if (!TvPrefs.markCueShown(this, cue.id)) return
        if (TvForeground.visible) return
        main.post { EventOverlay.show(this, cue) }
    }

    private fun requestForeground() {
        main.post { bringDinoToFront() }
        main.postDelayed({ bringDinoToFront() }, 700)
        main.postDelayed({ bringDinoToFront() }, 1_800)
    }

    private fun bringDinoToFront() {
        val open = TvLaunch.intent(this)
        moveOwnTasksToFront()
        scheduleAlarmClock(open)
        startLaunchIntent(open)
        pingFullScreen(open)
    }

    private fun moveOwnTasksToFront() {
        try {
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            for (task in am.appTasks) {
                task.moveToFront()
            }
        } catch (_: Exception) {
            // Fall through to a launch intent if the existing task cannot be raised.
        }
    }

    private fun scheduleAlarmClock(open: Intent) {
        val alarm = getSystemService(AlarmManager::class.java) ?: return
        val pending = activityPending(3, open)
        val at = System.currentTimeMillis() + 250
        try {
            alarm.setAlarmClock(AlarmManager.AlarmClockInfo(at, pending), pending)
        } catch (_: Exception) {
            try {
                alarm.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 250,
                    pending,
                )
            } catch (_: Exception) {
                // Locked-down firmware still has the launch intent and full-screen notification.
            }
        }
    }

    private fun startLaunchIntent(open: Intent) {
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                activityPending(1, open).send()
                return
            } catch (_: Exception) {
                // Try a direct start below.
            }
        }
        try {
            startActivity(open)
        } catch (_: Exception) {
            // Full-screen notification is the last attempt.
        }
    }

    private fun activityPending(requestCode: Int, open: Intent): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        if (Build.VERSION.SDK_INT >= 34) {
            val options = ActivityOptions.makeBasic().apply {
                setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                )
            }
            return PendingIntent.getActivity(this, requestCode, open, flags, options.toBundle())
        }
        return PendingIntent.getActivity(this, requestCode, open, flags)
    }

    private fun pingFullScreen(open: Intent) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(WAKE_CHANNEL_ID, "Открытие Dino TV", NotificationManager.IMPORTANCE_HIGH),
        )
        manager.notify(
            WAKE_NOTIFICATION_ID,
            NotificationCompat.Builder(this, WAKE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_dino)
                .setContentTitle("Dino TV")
                .setContentText("Открываю домашний экран")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(activityPending(2, open), true)
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
