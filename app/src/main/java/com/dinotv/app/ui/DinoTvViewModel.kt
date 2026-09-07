package com.dinotv.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dinotv.app.data.DashboardRepository
import com.dinotv.app.data.DemoDashboardRepository
import com.dinotv.app.domain.DashboardMode
import com.dinotv.app.domain.DashboardState
import com.dinotv.app.domain.DisplayTheme
import com.dinotv.app.domain.NowPlaying
import com.dinotv.app.media.YandexMusicObserver
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class DinoTvViewModel(
    private val repository: DashboardRepository = DemoDashboardRepository(),
) : ViewModel() {
    var state by mutableStateOf(repository.dashboard(LocalDateTime.now(), DashboardMode.NOW, DisplayTheme.FOREST))
        private set

    private var nowPlaying: NowPlaying? = null

    init {
        viewModelScope.launch {
            YandexMusicObserver.playback.collectLatest { playing ->
                nowPlaying = playing
                refresh()
            }
        }
        viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(1_000)
            }
        }
    }

    fun selectMode(mode: DashboardMode) {
        state = repository.dashboard(LocalDateTime.now(), mode, state.theme).copy(nowPlaying = nowPlaying)
    }

    fun selectTheme(theme: DisplayTheme) {
        state = repository.dashboard(LocalDateTime.now(), state.mode, theme).copy(nowPlaying = nowPlaying)
    }

    fun toggleMusic() = YandexMusicObserver.togglePlayPause()
    fun nextTrack() = YandexMusicObserver.skipNext()
    fun changeVolume(delta: Int) = YandexMusicObserver.adjustVolume(delta)

    private fun refresh() {
        state = repository.dashboard(LocalDateTime.now(), state.mode, state.theme).copy(nowPlaying = nowPlaying)
    }
}
