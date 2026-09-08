package com.dinotv.app

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Base64
import com.dinotv.app.domain.NowPlayingMeta
import com.dinotv.app.domain.NowPlayingTrack
import java.io.ByteArrayOutputStream

object NowPlayingDesk {
    fun json(context: Context): String = current(context)?.toJson().orEmpty()

    fun reportJson(context: Context): String = current(context)?.toReportJson() ?: """{"title":""}"""

    fun current(context: Context): NowPlayingTrack? = activeController(context)?.let { from(it, context) }

    fun apply(context: Context, action: String, volume: Int?) {
        if (action == "volume") {
            if (volume != null) TvAudio.setVolumePercent(context, volume)
            return
        }
        val controller = activeController(context) ?: return
        val controls = controller.transportControls
        when (action) {
            "play" -> controls.play()
            "pause" -> controls.pause()
            "next" -> controls.skipToNext()
            "previous" -> controls.skipToPrevious()
            "toggle" -> {
                val state = controller.playbackState?.state
                val playing = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
                if (playing) controls.pause() else controls.play()
            }
        }
    }

    private fun activeController(context: Context): MediaController? {
        NotificationAccess.ensureEnabled(context)
        val manager = context.getSystemService(MediaSessionManager::class.java) ?: return null
        val listener = ComponentName(context, NowPlayingListener::class.java)
        val sessions = try {
            manager.getActiveSessions(listener)
        } catch (_: SecurityException) {
            NotificationAccess.ensureEnabled(context)
            emptyList()
        }
        if (sessions.isEmpty()) return null
        val ranked = sessions.mapNotNull { controller ->
            val track = from(controller, context) ?: return@mapNotNull null
            controller to track
        }
        return ranked.firstOrNull { (controller, track) ->
            track.isPlaying && isMusicPackage(controller.packageName)
        }?.first
            ?: ranked.firstOrNull { (_, track) -> track.isPlaying }?.first
            ?: ranked.firstOrNull { (controller, _) -> isMusicPackage(controller.packageName) }?.first
            ?: ranked.firstOrNull()?.first
    }

    private fun isMusicPackage(packageName: String): Boolean {
        val name = packageName.lowercase()
        return name.contains("kinopoisk") || name.contains("yandex") || name.contains("music")
    }

    private fun from(controller: MediaController, context: Context): NowPlayingTrack? {
        val meta = controller.metadata
        val state = controller.playbackState?.state
        val playing = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
        val title = meta?.string(
            MediaMetadata.METADATA_KEY_TITLE,
            MediaMetadata.METADATA_KEY_DISPLAY_TITLE,
        ) ?: meta?.description?.title?.toString()?.trim()?.ifBlank { null }
        val artist = meta?.string(
            MediaMetadata.METADATA_KEY_ARTIST,
            MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
            MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
        ) ?: meta?.description?.subtitle?.toString()?.trim()?.ifBlank { null }
        val album = meta?.string(
            MediaMetadata.METADATA_KEY_ALBUM,
            MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION,
        ) ?: meta?.description?.description?.toString()?.trim()?.ifBlank { null }
        // Kinopoisk sometimes exposes a playing session with empty title — still report so the
        // phone/overlay know music is live (volume + transport stay available).
        return NowPlayingMeta.from(
            controller.packageName,
            title,
            artist,
            album,
            playing,
            TvAudio.volumePercent(context),
            100,
            meta?.let { artwork(it) }.orEmpty(),
            allowUntitled = playing || TvAudio.isPlaying(context),
        )
    }

    private fun MediaMetadata.string(vararg keys: String): String? {
        for (key in keys) {
            val value = getString(key)?.trim().orEmpty()
            if (value.isNotEmpty()) return value
        }
        return null
    }

    private fun artwork(meta: MediaMetadata): String {
        val uri = meta.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
        if (!uri.isNullOrBlank() && (uri.startsWith("http://") || uri.startsWith("https://"))) return uri
        val bitmap = meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: return ""
        return bitmap.toDataUrl()
    }

    private fun Bitmap.toDataUrl(): String {
        val scaled = if (width <= 160 && height <= 160) this else {
            val longest = maxOf(width, height).coerceAtLeast(1)
            Bitmap.createScaledBitmap(this, width * 160 / longest, height * 160 / longest, true)
        }
        val bytes = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 72, bytes)
        return "data:image/jpeg;base64," + Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
    }
}
