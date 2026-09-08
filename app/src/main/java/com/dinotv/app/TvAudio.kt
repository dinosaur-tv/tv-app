package com.dinotv.app

import android.content.Context
import android.media.AudioManager
import com.dinotv.app.domain.TvVolume

object TvAudio {
    fun isPlaying(context: Context): Boolean {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audio.isMusicActive
    }

    fun volumePercent(context: Context): Int {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return TvVolume.percent(audio.getStreamVolume(AudioManager.STREAM_MUSIC), audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
    }

    fun setVolumePercent(context: Context, percent: Int) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, TvVolume.streamValue(percent, max), 0)
    }

    fun abandon(context: Context) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        @Suppress("DEPRECATION")
        audio.abandonAudioFocus(null)
    }
}
