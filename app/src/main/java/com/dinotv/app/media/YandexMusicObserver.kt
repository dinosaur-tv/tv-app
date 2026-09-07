package com.dinotv.app.media

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import com.dinotv.app.domain.NowPlaying
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reads only the active Android MediaSession published by Yandex Music on this TV.
 * The system binds this service only after the household grants notification access.
 */
object YandexMusicObserver {
    private val mutablePlayback = MutableStateFlow<NowPlaying?>(null)
    val playback: StateFlow<NowPlaying?> = mutablePlayback.asStateFlow()

    private var controller: MediaController? = null

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) = publish()
    }

    internal fun useController(newController: MediaController?) {
        if (controller?.sessionToken == newController?.sessionToken) {
            publish()
            return
        }
        controller?.unregisterCallback(callback)
        controller = newController
        controller?.registerCallback(callback)
        publish()
    }

    fun togglePlayPause() {
        val active = controller ?: return
        if (active.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING) {
            active.transportControls.pause()
        } else {
            active.transportControls.play()
        }
    }

    fun skipNext() = controller?.transportControls?.skipToNext()

    fun adjustVolume(delta: Int) {
        val direction = if (delta >= 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        controller?.adjustVolume(direction, 0)
        publish()
    }

    private fun publish() {
        val active = controller
        val metadata = active?.metadata
        if (active == null || metadata == null) {
            mutablePlayback.value = null
            return
        }
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        if (title.isNullOrBlank()) {
            mutablePlayback.value = null
            return
        }
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: "Яндекс Музыка"
        val playbackInfo = active.playbackInfo
        val volume = playbackInfo?.let { info ->
            if (info.maxVolume > 0) (info.currentVolume * 100 / info.maxVolume) else 0
        } ?: 0
        mutablePlayback.value = NowPlaying(
            title = title,
            artist = artist,
            source = "Яндекс Музыка",
            isPlaying = active.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING,
            volumePercent = volume,
        )
    }
}

class YandexMusicObserverService : NotificationListenerService() {
    private lateinit var sessionManager: MediaSessionManager
    private val listenerComponent by lazy { ComponentName(this, YandexMusicObserverService::class.java) }

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        selectYandexController(controllers.orEmpty())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        sessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        sessionManager.addOnActiveSessionsChangedListener(sessionsChanged, listenerComponent)
        refresh()
    }

    override fun onListenerDisconnected() {
        YandexMusicObserver.useController(null)
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        if (::sessionManager.isInitialized) sessionManager.removeOnActiveSessionsChangedListener(sessionsChanged)
        YandexMusicObserver.useController(null)
        super.onDestroy()
    }

    private fun refresh() {
        val controllers = try {
            sessionManager.getActiveSessions(listenerComponent)
        } catch (_: SecurityException) {
            emptyList()
        }
        selectYandexController(controllers)
    }

    private fun selectYandexController(controllers: List<MediaController>) {
        YandexMusicObserver.useController(
            controllers.firstOrNull { it.packageName.contains("yandex", ignoreCase = true) },
        )
    }
}
