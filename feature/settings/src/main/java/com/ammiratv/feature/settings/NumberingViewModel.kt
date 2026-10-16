package com.ammiratv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ammiratv.core.datastore.AmmiraTvPreferences
import com.ammiratv.core.model.Channel
import com.ammiratv.domain.usecase.GetChannelsToVerifyUseCase
import com.ammiratv.domain.usecase.GetNumberingInfoUseCase
import com.ammiratv.domain.usecase.NumberingInfo
import com.ammiratv.domain.usecase.RecalculateChannelNumbersUseCase
import com.ammiratv.domain.usecase.SearchNumberableChannelsUseCase
import com.ammiratv.domain.usecase.SetManualChannelNumberUseCase
import com.ammiratv.domain.usecase.UpdateNumberingDataUseCase
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

data class NumberingUiState(
    val info: NumberingInfo? = null,
    val isBusy: Boolean = false,
    val message: String? = null,
    val query: String = "",
    val results: List<Channel> = emptyList(),
    /** Italian live channels the catalogue did not recognise. */
    val toVerify: List<Channel> = emptyList(),
    val showToVerify: Boolean = false,
    val numberedCount: Int? = null
)

@HiltViewModel
class NumberingViewModel @Inject constructor(
    private val preferences: AmmiraTvPreferences,
    private val recalculate: RecalculateChannelNumbersUseCase,
    private val setManualNumber: SetManualChannelNumberUseCase,
    private val searchChannels: SearchNumberableChannelsUseCase,
    private val getToVerify: GetChannelsToVerifyUseCase,
    private val updateData: UpdateNumberingDataUseCase,
    private val getInfo: GetNumberingInfoUseCase
) : ViewModel() {

    val enabled: StateFlow<Boolean> = preferences.numberingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val unofficial: StateFlow<Boolean> = preferences.unofficialNumbers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val sortByNumber: StateFlow<Boolean> = preferences.sortByNumber
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val timeoutMs: StateFlow<Int> = preferences.numberEntryTimeoutMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1800)
    val remoteUrl: StateFlow<String> = preferences.numberingRemoteUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _state = MutableStateFlow(NumberingUiState())
    val state: StateFlow<NumberingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val info = runCatching { getInfo() }.getOrNull()
            _state.update { it.copy(info = info) }
        }
    }

    private suspend fun activePlaylist(): Long = preferences.activePlaylistId.first()

    private fun withBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null) }
            try {
                block()
            } finally {
                _state.update { it.copy(isBusy = false) }
            }
        }
    }

    fun setEnabled(value: Boolean) = withBusy {
        preferences.setNumberingEnabled(value)
        val count = recalculate(activePlaylist())
        _state.update { it.copy(numberedCount = count, message = if (value) "Numerazione attiva" else "Numerazione disattivata") }
    }

    fun setUnofficial(value: Boolean) = withBusy {
        preferences.setUnofficialNumbers(value)
        val count = recalculate(activePlaylist())
        _state.update { it.copy(numberedCount = count) }
        refreshVerifyIfShown()
    }

    fun setSortByNumber(value: Boolean) {
        viewModelScope.launch { preferences.setSortByNumber(value) }
    }

    fun setTimeoutMs(ms: Int) {
        viewModelScope.launch { preferences.setNumberEntryTimeoutMs(ms) }
    }

    fun setRemoteUrl(url: String) {
        viewModelScope.launch { preferences.setNumberingRemoteUrl(url) }
    }

    fun recalculateNow() = withBusy {
        val count = recalculate(activePlaylist())
        _state.update { it.copy(numberedCount = count, message = "Numerazione ricalcolata: $count canali numerati") }
        refreshVerifyIfShown()
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        viewModelScope.launch {
            val results = if (query.trim().length >= 2) searchChannels(activePlaylist(), query).take(30) else emptyList()
            _state.update { it.copy(results = results) }
        }
    }

    fun toggleToVerify() {
        val show = !_state.value.showToVerify
        _state.update { it.copy(showToVerify = show) }
        if (show) withBusy { loadToVerify() }
    }

    private suspend fun loadToVerify() {
        val list = getToVerify(activePlaylist())
        _state.update { it.copy(toVerify = list) }
    }

    private suspend fun refreshVerifyIfShown() {
        if (_state.value.showToVerify) loadToVerify()
    }

    /** @param number null restores the original number. */
    fun setManual(channel: Channel, number: Int?) = withBusy {
        setManualNumber(channel, number)
        _state.update { it.copy(message = if (number == null) "Numero originale ripristinato" else "${channel.cleanName} ora e' il $number") }
        // Re-read the rows so the new number shows up
        val query = _state.value.query
        if (query.trim().length >= 2) {
            _state.update { it.copy(results = searchChannels(activePlaylist(), query).take(30)) }
        }
        refreshVerifyIfShown()
    }

    fun updateDatabase() = withBusy {
        val message = updateData()
        val info = runCatching { getInfo() }.getOrNull()
        _state.update { it.copy(message = message, info = info) }
    }
}
