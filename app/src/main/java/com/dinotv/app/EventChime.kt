package com.dinotv.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dinotv.app.domain.ReminderChime

/** One short mixed-in cue. Never requests audio focus, changes TV volume or controls the film. */
internal object EventChime {
    private val main = Handler(Looper.getMainLooper())
    private val samples by lazy { ReminderChime.samples() }
    private var track: AudioTrack? = null
    private val cleanup = Runnable { stop() }

    fun play() {
        stop()
        try {
            val sound = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    // On TVs, use the same volume/mute and output route as the film.
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(ReminderChime.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * 2)
                .build()
            track = sound
            if (sound.write(samples, 0, samples.size) != samples.size) {
                stop()
                return
            }
            sound.setVolume(0.8f)
            sound.play()
            // Static PCM plays once; release the native buffer even if the overlay remains visible.
            main.postDelayed(cleanup, ReminderChime.DURATION_MS + 350L)
        } catch (_: Exception) {
            stop()
            Log.w("EventChime", "Reminder sound unavailable; keeping the visual reminder")
        }
    }

    fun stop() {
        main.removeCallbacks(cleanup)
        val sound = track ?: return
        track = null
        runCatching {
            if (sound.playState == AudioTrack.PLAYSTATE_PLAYING) sound.stop()
        }
        runCatching { sound.release() }
    }
}
