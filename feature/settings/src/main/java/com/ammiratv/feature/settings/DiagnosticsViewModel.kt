package com.ammiratv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ammiratv.core.datastore.AmmiraTvPreferences
import com.ammiratv.core.model.NetworkVerdict
import com.ammiratv.core.model.NetworkVerdictKind
import com.ammiratv.core.model.PlayerState
import com.ammiratv.core.model.ProviderReport
import com.ammiratv.core.player.PlayerEngine
import com.ammiratv.domain.usecase.ChannelTestProgress
import com.ammiratv.domain.usecase.GetProviderReportUseCase
import com.ammiratv.domain.usecase.TestChannelsUseCase
import kotlinx.coroutines.Job
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiagnosticsUiState(
    val report: ProviderReport? = null,
    val verdict: NetworkVerdictKind = NetworkVerdictKind.NOT_ENOUGH_DATA,
    val recentProblems: Int = 0
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val getProviderReport: GetProviderReportUseCase,
    private val testChannels: TestChannelsUseCase,
    private val preferences: AmmiraTvPreferences,
    private val playerEngine: PlayerEngine
) : ViewModel() {

    private val _testProgress = MutableStateFlow<ChannelTestProgress?>(null)
    val testProgress: StateFlow<ChannelTestProgress?> = _testProgress.asStateFlow()
    private var testJob: Job? = null

    /** Probes duplicated channels one at a time; refused when it would steal the viewing slot. */
    fun startChannelTest() {
        if (testJob?.isActive == true) return
        testJob = viewModelScope.launch {
            val playlistId = preferences.activePlaylistId.first()
            testChannels(playlistId, isWatchingLive = playerEngine.state.value.isPlaying)
                .collect { _testProgress.value = it }
        }
    }

    fun cancelChannelTest() {
        testJob?.cancel()
        _testProgress.value = null
    }

    private val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())

    /** Bytes downloaded by the player today / this month (as seen by the player, not the OS). */
    val dataToday: StateFlow<Long> = preferences.dataUsageOfDay(today)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val dataMonth: StateFlow<Long> = preferences.dataUsageOfMonth(today.take(6))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _report = MutableStateFlow(DiagnosticsUiState())

    /** Live numbers come from the player; the report from the stored history. */
    val state: StateFlow<DiagnosticsUiState> = _report.asStateFlow()
    val player: StateFlow<PlayerState> = playerEngine.state

    init {
        viewModelScope.launch {
            val playlistId = preferences.activePlaylistId.first()
            val report = getProviderReport(playlistId)
            val problems = getProviderReport.recentProblems(playlistId, hours = 1)
            _report.update { it.copy(report = report, recentProblems = problems) }
        }

        // The verdict follows the live player estimate as it changes
        viewModelScope.launch {
            combine(_report, playerEngine.state) { r, p ->
                NetworkVerdict.evaluate(p.bandwidthBps, p.videoBitrateBps, r.recentProblems)
            }.collect { verdict -> _report.update { it.copy(verdict = verdict) } }
        }
    }
}
