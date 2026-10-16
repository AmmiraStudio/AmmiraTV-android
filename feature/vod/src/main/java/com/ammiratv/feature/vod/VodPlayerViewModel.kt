package com.ammiratv.feature.vod

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ammiratv.core.common.ApplicationScope
import com.ammiratv.core.datastore.AmmiraTvPreferences
import com.ammiratv.core.model.PlayerState
import com.ammiratv.core.model.WatchProgress
import com.ammiratv.core.player.PlayerEngine
import com.ammiratv.domain.usecase.ObserveActiveProfileUseCase
import com.ammiratv.domain.usecase.SaveWatchProgressUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VodPlayerUiState(
    val isReady: Boolean = false,
    val title: String = "",
    val subtitle: String? = null,
    val controlsVisible: Boolean = true,
    val audioMenu: Boolean = false,
    val subtitleMenu: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class VodPlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playerEngine: PlayerEngine,
    private val saveProgress: SaveWatchProgressUseCase,
    private val observeActiveProfile: ObserveActiveProfileUseCase,
    private val preferences: AmmiraTvPreferences,
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {

    private val contentKey: String = checkNotNull(savedStateHandle.get<String>("key"))

    private val _state = MutableStateFlow(VodPlayerUiState())
    val state: StateFlow<VodPlayerUiState> = _state.asStateFlow()

    val playerState: StateFlow<PlayerState> = playerEngine.state
    val engine: PlayerEngine get() = playerEngine

    private var progress: WatchProgress? = null
    private var languagesApplied = false

    init {
        viewModelScope.launch {
            val profileId = observeActiveProfile().first()?.id ?: 1L
            val p = saveProgress.get(profileId, contentKey)
            if (p == null) {
                _state.update { it.copy(errorMessage = "Contenuto non trovato") }
                return@launch
            }
            progress = p
            _state.update { it.copy(isReady = true, title = p.title, subtitle = p.subtitle) }

            playerEngine.setNowPlaying(p.title, p.subtitle)

            playerEngine.prepare(p.streamUrl)
            // Resume from the exact point; a finished title was reset to 0 when it completed.
            if (p.positionMs > 0) playerEngine.seekTo(p.positionMs)
            playerEngine.play()

            launch { autoApplyLanguages() }
            launch { saveLoop() }
            launch { hideControlsLater() }
        }
    }

    /** "Lingue automatiche": pick the preferred audio/subtitle once the tracks are known. */
    private suspend fun autoApplyLanguages() {
        val audioPref = preferences.preferredAudioLanguage.first()
        val subPref = preferences.preferredSubtitleLanguage.first()
        if (audioPref.isBlank() && subPref.isBlank()) return

        while (!languagesApplied) {
            val s = playerEngine.state.value
            if (s.audioTracks.isNotEmpty() || s.subtitleTracks.isNotEmpty()) {
                if (audioPref.isNotBlank()) {
                    s.audioTracks.firstOrNull { it.language.equals(audioPref, ignoreCase = true) }
                        ?.let { playerEngine.setAudioTrack(it.index) }
                }
                if (subPref.isNotBlank()) {
                    s.subtitleTracks.firstOrNull { it.language.equals(subPref, ignoreCase = true) }
                        ?.let { playerEngine.setSubtitleTrack(it.index) }
                }
                languagesApplied = true
            }
            delay(500)
        }
    }

    private suspend fun saveLoop() {
        while (true) {
            delay(SAVE_INTERVAL_MS)
            persist()
        }
    }

    private suspend fun hideControlsLater() {
        delay(4_000)
        _state.update { it.copy(controlsVisible = false) }
    }

    private suspend fun persist() {
        val base = progress ?: return
        val s = playerEngine.state.value
        if (s.durationMs <= 0) return
        saveProgress(base, s.currentPositionMs, s.durationMs)
    }

    fun togglePlayPause() {
        if (playerState.value.isPlaying) playerEngine.pause() else playerEngine.play()
        showControls()
    }

    fun seekRelative(deltaMs: Long) {
        val s = playerState.value
        val target = (s.currentPositionMs + deltaMs).coerceIn(0, if (s.durationMs > 0) s.durationMs else Long.MAX_VALUE)
        playerEngine.seekTo(target)
        showControls()
    }

    fun seekToFraction(fraction: Float) {
        val d = playerState.value.durationMs
        if (d > 0) playerEngine.seekTo((d * fraction.coerceIn(0f, 1f)).toLong())
    }

    fun showControls() {
        _state.update { it.copy(controlsVisible = true) }
        viewModelScope.launch {
            delay(4_000)
            if (!_state.value.audioMenu && !_state.value.subtitleMenu) {
                _state.update { it.copy(controlsVisible = false) }
            }
        }
    }

    fun toggleAudioMenu() = _state.update { it.copy(audioMenu = !it.audioMenu, subtitleMenu = false) }
    fun toggleSubtitleMenu() = _state.update { it.copy(subtitleMenu = !it.subtitleMenu, audioMenu = false) }
    fun dismissMenus() = _state.update { it.copy(audioMenu = false, subtitleMenu = false) }

    fun setAudioTrack(index: Int) {
        playerEngine.setAudioTrack(index)
        dismissMenus()
    }

    fun setSubtitleTrack(index: Int) {
        playerEngine.setSubtitleTrack(index)
        dismissMenus()
    }

    /** Called when leaving the screen: stop playback and write the final position. */
    fun onLeave() {
        val base = progress
        val s = playerEngine.state.value
        playerEngine.stop()
        if (base != null && s.durationMs > 0) {
            // viewModelScope may already be cancelled here; the app scope finishes the write
            appScope.launch { saveProgress(base, s.currentPositionMs, s.durationMs) }
        }
    }

    private companion object {
        const val SAVE_INTERVAL_MS = 10_000L
    }
}
