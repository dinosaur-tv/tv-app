package com.dinotv.app

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.util.Base64
import com.dinotv.app.domain.NowPlayingMeta
import com.dinotv.app.domain.NowPlayingTrack
import java.io.ByteArrayOutputStream

object NowPlayingDesk {
    fun json(context: Context): String = current(context)?.toJson().orEmpty()

    fun reportJson(context: Context): String = current(context)?.toReportJson() ?: """{"title":""}"""

    fun current(context: Context): NowPlayingTrack? {
        NotificationAccess.ensureEnabled(context)
        return fromController(activeController(context), context)
            ?: fromNotification(context)
            ?: if (TvAudio.isPlaying(context)) {
                NowPlayingMeta.from(
                    "ru.kinopoisk.tv",
                    null,
                    null,
                    null,
                    true,
                    TvAudio.volumePercent(context),
                    100,
                    allowUntitled = true,
                )
            } else {
                null
            }
    }

    fun apply(context: Context, action: String, volume: Int?) {
        if (action == "volume") {
            if (volume != null) TvAudio.setVolumePercent(context, volume)
            return
        }
        val controller = activeController(context) ?: controllerFromNotification(context) ?: return
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
            val track = fromController(controller, context) ?: return@mapNotNull null
            controller to track
        }
        return ranked.firstOrNull { (controller, track) ->
            track.isPlaying && isMusicPackage(controller.packageName)
        }?.first
            ?: ranked.firstOrNull { (_, track) -> track.isPlaying }?.first
            ?: ranked.firstOrNull { (controller, _) -> isMusicPackage(controller.packageName) }?.first
            ?: ranked.firstOrNull()?.first
    }

    private fun controllerFromNotification(context: Context): MediaController? {
        val notifications = NowPlayingListener.activeNotifications() ?: return null
        for (notification in notifications) {
            if (!isMusicPackage(notification.packageName)) continue
            val token = mediaToken(notification.notification) ?: continue
            return try {
                MediaController(context, token)
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    private fun fromNotification(context: Context): NowPlayingTrack? {
        val notifications = NowPlayingListener.activeNotifications() ?: return null
        var best: NowPlayingTrack? = null
        for (notification in notifications) {
            if (!isMusicPackage(notification.packageName)) continue
            val n = notification.notification
            val extras = n.extras ?: continue
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
            val token = mediaToken(n)
            val controller = token?.let {
                try {
                    MediaController(context, it)
                } catch (_: Exception) {
                    null
                }
            }
            val fromSession = controller?.let { fromController(it, context) }
            // Kinopoisk often keeps a MediaSession token with empty metadata while the
            // MediaStyle notification still has the real title/artist — prefer extras.
            val merged = mergeNotificationTrack(
                packageName = notification.packageName,
                title = title,
                artist = text,
                playingHint = TvAudio.isPlaying(context) ||
                    n.category == Notification.CATEGORY_TRANSPORT ||
                    extras.containsKey(Notification.EXTRA_MEDIA_SESSION),
                volumePercent = TvAudio.volumePercent(context),
                artworkUrl = artworkFromNotification(n).ifBlank { fromSession?.artworkUrl.orEmpty() },
                session = fromSession,
            ) ?: continue
            if (merged.isPlaying && merged.title.isNotBlank()) return merged
            best = best ?: merged
        }
        return best
    }

    private fun mergeNotificationTrack(
        packageName: String,
        title: String,
        artist: String,
        playingHint: Boolean,
        volumePercent: Int,
        artworkUrl: String,
        session: NowPlayingTrack?,
    ): NowPlayingTrack? {
        val resolvedTitle = title.ifBlank {
            session?.title?.takeUnless { it == NowPlayingMeta.sourceFor(packageName) }.orEmpty()
        }
        val resolvedArtist = artist.ifBlank { session?.artist.orEmpty() }
        val playing = when {
            session?.isPlaying == true -> true
            playingHint -> true
            session != null -> session.isPlaying
            else -> false
        }
        return NowPlayingMeta.from(
            packageName,
            resolvedTitle.ifBlank { null },
            resolvedArtist.ifBlank { null },
            null,
            playing,
            volumePercent,
            100,
            artworkUrl.ifBlank { session?.artworkUrl.orEmpty() },
            allowUntitled = playing,
        )
    }

    private fun isMusicPackage(packageName: String): Boolean =
        NowPlayingListener.isMusicPackage(packageName)

    private fun mediaToken(notification: Notification): MediaSession.Token? {
        val extras = notification.extras ?: return null
        return if (Build.VERSION.SDK_INT >= 33) {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION) as? MediaSession.Token
        }
    }

    private fun artworkFromNotification(notification: Notification): String {
        val extras = notification.extras ?: return ""
        val bitmap = if (Build.VERSION.SDK_INT >= 33) {
            extras.getParcelable(Notification.EXTRA_LARGE_ICON, Bitmap::class.java)
                ?: extras.getParcelable(Notification.EXTRA_LARGE_ICON_BIG, Bitmap::class.java)
        } else {
            @Suppress("DEPRECATION")
            (extras.getParcelable(Notification.EXTRA_LARGE_ICON) as? Bitmap)
                ?: @Suppress("DEPRECATION")
                (extras.getParcelable(Notification.EXTRA_LARGE_ICON_BIG) as? Bitmap)
        } ?: return ""
        return bitmap.toDataUrl()
    }

    private fun fromController(controller: MediaController?, context: Context): NowPlayingTrack? {
        if (controller == null) return null
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
        return try {
            val bytes = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 72, bytes)
            "data:image/jpeg;base64," + Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
        } finally {
            // createScaledBitmap allocates native pixel memory. The MediaSession owns
            // the source bitmap, but this temporary copy is ours and must be released.
            if (scaled !== this && !scaled.isRecycled) scaled.recycle()
        }
    }
}
