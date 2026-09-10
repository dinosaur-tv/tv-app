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
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.dinotv.app.domain.MusicRemote
import com.dinotv.app.domain.TvRemote
import com.dinotv.app.domain.TvRemoteCommand
import com.dinotv.app.domain.EventReminders
import android.media.AudioManager
import com.dinotv.app.domain.IncomingEvent
import com.dinotv.app.domain.NowPlayingTrack
import com.dinotv.app.domain.TvPower
import com.dinotv.app.domain.TvWakePolicy
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TvWakeService : Service() {
    private val worker = Executors.newSingleThreadScheduledExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val wakeToken = Any()
    private var latestTrack: NowPlayingTrack? = null
    private var lastMediaCheckAt = 0L
    private var lastNowPlayingPayload = ""
    private var lastNowPlayingPostAt = 0L
    @Volatile private var stopped = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val notification = notification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        worker.scheduleWithFixedDelay({ poll() }, 0, 400, TimeUnit.MILLISECONDS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopped = true
        worker.shutdownNow()
        // This Handler belongs only to the service; remove command-chain callbacks
        // as well as wake callbacks so a dead service cannot be retained.
        main.removeCallbacksAndMessages(null)
        RemoteCursor.hide()
        EventOverlay.hide()
        LivingRoomOverlay.hide()
        super.onDestroy()
    }

    private fun poll() {
        if (stopped) return
        val session = TvPrefs.session(this)
        if (session.isBlank()) return
        try {
            NotificationAccess.ensureEnabled(this)
            val now = SystemClock.elapsedRealtime()
            if (now - lastMediaCheckAt >= MEDIA_CHECK_MS) {
                latestTrack = NowPlayingDesk.current(this)
                lastMediaCheckAt = now
                reportNowPlaying(latestTrack, now)
            }
            val connection = URL(SNAPSHOT_URL).openConnection() as HttpURLConnection
            val body = try {
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $session")
                connection.setRequestProperty("X-Dino-Visible", if (TvForeground.visible) "1" else "0")
                connection.connectTimeout = 4_000
                connection.readTimeout = 4_000
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
            if (stopped) return
            val json = JSONObject(body)
            val remoteEnabled = json.optJSONObject("features")?.optBoolean("tvRemote", false) == true
            TvPrefs.saveRemoteEnabled(this, remoteEnabled)
            if (remoteEnabled) RemoteAccess.ensureEnabled(this)
            maybeCue(json)
            maybeMusicCommand(json)
            if (remoteEnabled) maybeTvCommand(json)
            val power = json.optString("power", "on")
            val powerAt = json.optString("powerAt", "")
            if (!TvPower.shouldApply(TvPrefs.powerAt(this), powerAt)) return
            TvPrefs.savePowerAt(this, powerAt)
            if (power != "on") {
                cancelDinoWake()
                onMain { LivingRoomOverlay.hide() }
                return
            }
            val track = latestTrack
            if (TvWakePolicy.keepHostPlaying(TvAudio.isPlaying(this), Settings.canDrawOverlays(this), track != null)) {
                onMain { LivingRoomOverlay.show(this) }
                return
            }
            onMain { LivingRoomOverlay.hide() }
            requestForeground()
        } catch (_: Exception) {
            // The remote can retry; a quiet miss is better than crashing the watchman.
        }
    }

    private fun maybeTvCommand(json: JSONObject) {
        val lastAt = TvPrefs.tvCommandAt(this)
        val parsed = mutableListOf<TvRemoteCommand>()
        val queued = json.optJSONArray("tvCommands")
        if (queued != null) {
            for (i in 0 until queued.length()) {
                val cmd = queued.optJSONObject(i) ?: continue
                val item = TvRemote.take(
                    cmd.optString("action"),
                    cmd.optString("at"),
                    app = cmd.optString("app").ifBlank { null },
                    key = cmd.optString("key").ifBlank { null },
                ) ?: continue
                if (item.at > lastAt) parsed += item
            }
        } else {
            val cmd = json.optJSONObject("tvCommand") ?: return
            val item = TvRemote.take(
                cmd.optString("action"),
                cmd.optString("at"),
                app = cmd.optString("app").ifBlank { null },
                key = cmd.optString("key").ifBlank { null },
            ) ?: return
            if (item.at > lastAt) parsed += item
        }
        if (parsed.isEmpty()) return
        val ordered = parsed.sortedBy { it.at }
        TvPrefs.saveTvCommandAt(this, ordered.last().at)
        onMain { playTvCommands(ordered, 0) }
    }

    private fun playTvCommands(commands: List<TvRemoteCommand>, index: Int) {
        if (stopped || index >= commands.size) return
        val command = commands[index]
        applyTvCommand(command.action, command.app, command.key)
        val delay = when {
            command.action == "launch" -> 450L
            command.action == "key" -> 80L
            else -> 0L
        }
        main.postDelayed({ playTvCommands(commands, index + 1) }, delay)
    }

    private fun applyTvCommand(action: String, app: String?, key: String?) {
        if (!TvPrefs.remoteEnabled(this)) return
        when (action) {
            "launch" -> {
                cancelDinoWake()
                RemoteCursor.hide()
                LivingRoomOverlay.hide()
                if (app == "dino") {
                    requestForeground()
                    return
                }
                val open = app?.let { TvApps.intent(this, it) } ?: return
                // Background launches are flaky on Android TV — hit several privileged paths.
                startLaunchIntent(open)
                scheduleAlarmClock(open)
                main.postAtTime({ startLaunchIntent(open) }, wakeToken, SystemClock.uptimeMillis() + 400)
                main.postAtTime({ startLaunchIntent(open) }, wakeToken, SystemClock.uptimeMillis() + 1_200)
            }
            "key" -> when (key) {
                "volume_up" -> TvAudio.adjustVolume(this, AudioManager.ADJUST_RAISE)
                "volume_down" -> TvAudio.adjustVolume(this, AudioManager.ADJUST_LOWER)
                "mute" -> TvAudio.toggleMute(this)
                "play_pause" -> NowPlayingDesk.apply(this, "toggle", null)
                "home" -> {
                    cancelDinoWake()
                    RemoteCursor.hide()
                    LivingRoomOverlay.hide()
                    if (!RemoteAccessibilityService.press("home")) {
                        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        scheduleAlarmClock(home)
                        startLaunchIntent(home)
                    }
                }
                "back", "up", "down", "left", "right", "ok" -> {
                    if (!RemoteAccessibilityService.connected()) RemoteAccess.ensureEnabled(this)
                    RemoteCursor.hide()
                    // Exactly one incoming command becomes exactly one remote action.
                    // Retrying through both moveFocus() and press() could advance twice.
                    RemoteAccessibilityService.press(key)
                }
            }
        }
    }

    private fun maybeMusicCommand(json: JSONObject) {
        val cmd = json.optJSONObject("musicCommand") ?: return
        val volume = if (cmd.has("volume") && !cmd.isNull("volume")) cmd.optInt("volume") else null
        val command = MusicRemote.take(cmd.optString("action"), cmd.optString("at"), volume) ?: return
        if (command.at <= TvPrefs.musicCommandAt(this)) return
        TvPrefs.saveMusicCommandAt(this, command.at)
        onMain { NowPlayingDesk.apply(this, command.action, command.volume) }
    }

    private fun reportNowPlaying(track: NowPlayingTrack?, now: Long) {
        val session = TvPrefs.session(this)
        if (session.isBlank()) return
        try {
            val musicNotifs = NowPlayingListener.activeNotifications()
                ?.filter { NowPlayingListener.isMusicPackage(it.packageName) }
            // Empty {"title":""} clears the phone desk — only report a clear when we
            // can see there is no music notification and audio is idle.
            val payload = when {
                track != null -> track.toReportJson()
                musicNotifs != null && musicNotifs.isEmpty() && !TvAudio.isPlaying(this) ->
                    """{"title":""}"""
                else -> return
            }
            if (payload == lastNowPlayingPayload && now - lastNowPlayingPostAt < MEDIA_HEARTBEAT_MS) return
            val connection = URL(NOW_PLAYING_URL).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer $session")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 4_000
                connection.readTimeout = 4_000
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
            lastNowPlayingPayload = payload
            lastNowPlayingPostAt = now
        } catch (_: Exception) {
            // The remote can wait for the next beat.
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
        // While Dino itself is on screen the WebView card handles the cue.
        if (TvForeground.visible) return
        if (!TvPrefs.markCueShown(this, cue.id)) return
        onMain { EventOverlay.show(this, cue) }
    }

    private fun onMain(action: () -> Unit) {
        main.post { if (!stopped) action() }
    }

    private fun cancelDinoWake() {
        main.removeCallbacksAndMessages(wakeToken)
    }

    private fun requestForeground() {
        main.postAtTime({ bringDinoToFront() }, wakeToken, SystemClock.uptimeMillis())
        main.postAtTime({ bringDinoToFront() }, wakeToken, SystemClock.uptimeMillis() + 700)
        main.postAtTime({ bringDinoToFront() }, wakeToken, SystemClock.uptimeMillis() + 1_800)
    }

    private fun bringDinoToFront() {
        if (stopped) return
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
        val pending = activityPending(requestCodeFor(open, 3), open)
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
                activityPending(requestCodeFor(open, 1), open).send()
            } catch (_: Exception) {
                // Try a direct start below.
            }
            try {
                val options = ActivityOptions.makeBasic().apply {
                    setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                    )
                }
                startActivity(open, options.toBundle())
                return
            } catch (_: Exception) {
                // Fall through.
            }
        }
        try {
            startActivity(open)
        } catch (_: Exception) {
            // Full-screen notification / alarm clock remain as backups.
        }
    }

    private fun requestCodeFor(open: Intent, salt: Int): Int {
        val seed = open.component?.flattenToShortString() ?: open.`package` ?: open.action ?: "dino"
        return 1_000 + salt * 31 + (seed.hashCode() and 0x0fff)
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
            .setContentText("Связь с домашней консолью")
            .addAction(0, "Остановить", PendingIntent.getService(
                this, 9, Intent(this, TvWakeService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ))
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val ACTION_STOP = "com.dinotv.app.STOP_MONITORING"
        private const val CHANNEL_ID = "dino_tv_wake"
        private const val WAKE_CHANNEL_ID = "dino_tv_open"
        private const val NOTIFICATION_ID = 7
        private const val WAKE_NOTIFICATION_ID = 8
        private const val MEDIA_CHECK_MS = 2_000L
        private const val MEDIA_HEARTBEAT_MS = 15_000L
        private const val SNAPSHOT_URL = BuildConfig.API_BASE_URL + "/v1/display/snapshot"
        private const val NOW_PLAYING_URL = BuildConfig.API_BASE_URL + "/v1/display/now-playing"
    }
}
