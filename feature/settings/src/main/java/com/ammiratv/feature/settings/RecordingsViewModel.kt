package com.ammiratv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ammiratv.core.model.Recording
import com.ammiratv.core.model.VodKind
import com.ammiratv.core.model.WatchProgress
import com.ammiratv.domain.usecase.ManageRecordingsUseCase
import com.ammiratv.domain.usecase.ObserveActiveProfileUseCase
import com.ammiratv.domain.usecase.ObserveRecordingsUseCase
import com.ammiratv.domain.usecase.SaveWatchProgressUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordingsViewModel @Inject constructor(
    observeRecordings: ObserveRecordingsUseCase,
    private val manageRecordings: ManageRecordingsUseCase,
    private val saveProgress: SaveWatchProgressUseCase,
    private val observeActiveProfile: ObserveActiveProfileUseCase
) : ViewModel() {

    val recordings: StateFlow<List<Recording>> = observeRecordings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun stop(id: Long) {
        viewModelScope.launch { manageRecordings.stop(id) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { manageRecordings.delete(id) }
    }

    /**
     * Recorded files play in the same player as VOD, which keeps position/resume for free:
     * a progress row keyed "recording:<id>" carries the file URL.
     */
    fun play(recording: Recording, onReady: (contentKey: String) -> Unit) {
        val path = recording.filePath ?: return
        viewModelScope.launch {
            val profileId = observeActiveProfile().first()?.id ?: 1L
            val base = WatchProgress(
                profileId = profileId,
                contentKey = "recording:${recording.id}",
                kind = VodKind.MOVIE,
                playlistId = 0,
                title = recording.title,
                subtitle = recording.channelName,
                streamUrl = "file://$path"
            )
            onReady(saveProgress.ensure(base).contentKey)
        }
    }
}
