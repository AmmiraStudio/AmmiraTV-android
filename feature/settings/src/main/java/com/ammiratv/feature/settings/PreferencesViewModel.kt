package com.ammiratv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ammiratv.core.datastore.AmmiraTvPreferences
import com.ammiratv.core.player.PlayerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PreferencesViewModel @Inject constructor(
    private val preferences: AmmiraTvPreferences,
    private val playerEngine: PlayerEngine
) : ViewModel() {

    val audioLanguage: StateFlow<String> = preferences.preferredAudioLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val subtitleLanguage: StateFlow<String> = preferences.preferredSubtitleLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val cinemaMode: StateFlow<Boolean> = preferences.cinemaMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val bufferMs: StateFlow<Int> = preferences.bufferSizeMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2000)

    val mergeDuplicates: StateFlow<Boolean> = preferences.mergeDuplicates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setMergeDuplicates(enabled: Boolean) {
        viewModelScope.launch { preferences.setMergeDuplicates(enabled) }
    }

    val backgroundPlayback: StateFlow<Boolean> = preferences.backgroundPlayback
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setBackgroundPlayback(enabled: Boolean) {
        viewModelScope.launch { preferences.setBackgroundPlayback(enabled) }
    }

    val officialFallback: StateFlow<Boolean> = preferences.officialFallback
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setOfficialFallback(enabled: Boolean) {
        viewModelScope.launch { preferences.setOfficialFallback(enabled) }
    }

    val adaptiveBuffer: StateFlow<Boolean> = preferences.adaptiveBuffer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setAdaptiveBuffer(enabled: Boolean) {
        viewModelScope.launch { preferences.setAdaptiveBuffer(enabled) }
    }

    val volumeBoost: StateFlow<Boolean> = preferences.volumeBoost
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setVolumeBoost(enabled: Boolean) {
        viewModelScope.launch { preferences.setVolumeBoost(enabled) }
    }

    val fontScale: StateFlow<Float> = preferences.fontScale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1f)

    fun setFontScale(scale: Float) {
        viewModelScope.launch { preferences.setFontScale(scale) }
    }

    suspend fun exportBackup(): String = preferences.exportSettings()

    fun importBackup(text: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(preferences.importSettings(text)) }
    }

    fun setAudioLanguage(code: String) {
        viewModelScope.launch { preferences.setPreferredAudioLanguage(code) }
    }

    fun setSubtitleLanguage(code: String) {
        viewModelScope.launch { preferences.setPreferredSubtitleLanguage(code) }
    }

    fun setCinemaMode(enabled: Boolean) {
        viewModelScope.launch { preferences.setCinemaMode(enabled) }
    }

    fun setBufferMs(ms: Int) {
        viewModelScope.launch {
            preferences.setBufferSizeMs(ms)
            // The load control is fixed at player creation: dropping the player makes the
            // next channel start with the new buffer.
            playerEngine.release()
        }
    }
}
