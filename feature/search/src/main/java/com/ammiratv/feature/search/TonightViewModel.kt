package com.ammiratv.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ammiratv.core.model.Channel
import com.ammiratv.domain.usecase.KeywordRulesUseCase
import com.ammiratv.domain.usecase.ObserveActiveProfileUseCase
import com.ammiratv.domain.usecase.ProgramAction
import com.ammiratv.domain.usecase.ProgramActions
import com.ammiratv.domain.usecase.RecordingOutcome
import com.ammiratv.domain.usecase.StartRecordingUseCase
import com.ammiratv.domain.usecase.TonightItem
import com.ammiratv.domain.usecase.WhatToWatchTonightUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TonightUiState(
    val isLoading: Boolean = true,
    val items: List<TonightItem> = emptyList(),
    val message: String? = null
)

@HiltViewModel
class TonightViewModel @Inject constructor(
    private val whatToWatch: WhatToWatchTonightUseCase,
    private val keywordRules: KeywordRulesUseCase,
    private val startRecording: StartRecordingUseCase,
    private val observeActiveProfile: ObserveActiveProfileUseCase,
    private val reminderScheduler: com.ammiratv.domain.repository.ReminderScheduler
) : ViewModel() {

    private val _state = MutableStateFlow(TonightUiState())
    val state: StateFlow<TonightUiState> = _state.asStateFlow()

    val keywords: StateFlow<Set<String>> = keywordRules.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val profileId = observeActiveProfile().first()?.id ?: 1L
            val items = runCatching { whatToWatch(profileId) }.getOrDefault(emptyList())

            // Keyword rules ("avvisami quando compare X"): every future hit gets a reminder.
            // The scheduler is idempotent per programme, so refreshing never duplicates alerts.
            val now = System.currentTimeMillis()
            val alerts = items.count { item ->
                val channel = item.channel ?: return@count false
                val program = item.recommendation.program
                val isKeywordHit = item.recommendation.reasons.any { it.startsWith("Contiene") }
                isKeywordHit && program.startTime > now && reminderScheduler.schedule(
                    channelName = channel.cleanName,
                    programTitle = program.title,
                    startTimeMs = program.startTime,
                    requestKey = "${channel.id}:${program.startTime}"
                )
            }
            _state.update {
                it.copy(
                    isLoading = false,
                    items = items,
                    message = if (alerts > 0) "Avvisi impostati per $alerts programmi con le tue parole chiave" else it.message
                )
            }
        }
    }

    fun addKeyword(keyword: String) {
        viewModelScope.launch {
            keywordRules.add(keyword)
            refresh()
        }
    }

    fun removeKeyword(keyword: String) {
        viewModelScope.launch {
            keywordRules.remove(keyword)
            refresh()
        }
    }

    fun activate(item: TonightItem, onWatchLive: (Channel) -> Unit) {
        val channel = item.channel
        if (channel == null) {
            _state.update { it.copy(message = "Nessun canale della tua playlist trasmette questo programma") }
            return
        }
        viewModelScope.launch {
            when (ProgramActions.forProgram(item.recommendation.program, System.currentTimeMillis())) {
                ProgramAction.WATCH_LIVE -> onWatchLive(channel)
                ProgramAction.RECORD -> {
                    val text = when (val r = startRecording(channel, item.recommendation.program, watchingLive = false)) {
                        is RecordingOutcome.Scheduled -> "Registrazione programmata: ${item.recommendation.program.title}"
                        is RecordingOutcome.NoFreeConnection -> "Connessioni esaurite (${r.maxConnections})"
                        is RecordingOutcome.Rejected -> r.reason
                    }
                    _state.update { it.copy(message = text) }
                }
                ProgramAction.WATCH_CATCHUP -> Unit // finished programmes are never recommended
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
