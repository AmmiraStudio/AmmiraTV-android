package com.ammiratv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ammiratv.core.datastore.AmmiraTvPreferences
import com.ammiratv.core.model.PlaylistType
import com.ammiratv.core.network.localimport.ImportRequest
import com.ammiratv.core.network.localimport.LocalImportServer
import com.ammiratv.domain.usecase.EpgSyncCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import com.ammiratv.domain.usecase.ImportM3uPlaylistUseCase
import com.ammiratv.domain.usecase.ImportProgress
import com.ammiratv.domain.usecase.ImportXtreamPlaylistUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddPlaylistUiState(
    val selectedType: PlaylistType = PlaylistType.M3U,
    val name: String = "",
    val m3uUrl: String = "",
    val xtreamServerUrl: String = "",
    val xtreamUsername: String = "",
    val xtreamPassword: String = "",
    val progress: ImportProgress? = null,
    /** Address the phone must open; non-null while the QR dialog is showing. */
    val phoneImportUrl: String? = null,
    val phoneImportError: String? = null
)

@HiltViewModel
class AddPlaylistViewModel @Inject constructor(
    private val importM3uPlaylistUseCase: ImportM3uPlaylistUseCase,
    private val importXtreamPlaylistUseCase: ImportXtreamPlaylistUseCase,
    private val epgSyncCoordinator: EpgSyncCoordinator,
    private val numberingCoordinator: com.ammiratv.domain.usecase.NumberingCoordinator,
    private val localImportServer: LocalImportServer,
    private val preferences: AmmiraTvPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddPlaylistUiState())
    val uiState: StateFlow<AddPlaylistUiState> = _uiState.asStateFlow()

    private var serverJob: Job? = null

    /** Shows a QR code; the phone's web form fills in this screen and starts the import. */
    fun startPhoneImport() {
        if (serverJob?.isActive == true) return
        val ip = LocalImportServer.localIpAddress()
        if (ip == null) {
            _uiState.update { it.copy(phoneImportError = "Nessuna rete locale trovata: collega la TV al Wi-Fi o alla LAN") }
            return
        }

        val token = localImportServer.newToken()
        _uiState.update { it.copy(phoneImportError = null) }
        serverJob = viewModelScope.launch {
            try {
                localImportServer.serve(
                    token = token,
                    onReady = { port ->
                        _uiState.update { it.copy(phoneImportUrl = "http://$ip:$port/$token") }
                    },
                    onRequest = { request -> viewModelScope.launch { applyPhoneRequest(request) } }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(phoneImportError = "Impossibile avviare l'importazione: ${e.localizedMessage}") }
            } finally {
                _uiState.update { it.copy(phoneImportUrl = null) }
            }
        }
    }

    fun stopPhoneImport() {
        serverJob?.cancel()
        serverJob = null
    }

    private fun applyPhoneRequest(request: ImportRequest) {
        stopPhoneImport()
        _uiState.update {
            if (request.type == "XTREAM") {
                it.copy(
                    selectedType = PlaylistType.XTREAM,
                    name = request.name,
                    xtreamServerUrl = request.url,
                    xtreamUsername = request.username,
                    xtreamPassword = request.password,
                    progress = null
                )
            } else {
                it.copy(
                    selectedType = PlaylistType.M3U,
                    name = request.name,
                    m3uUrl = request.url,
                    progress = null
                )
            }
        }
        importPlaylist()
    }

    fun setType(type: PlaylistType) {
        _uiState.update { it.copy(selectedType = type, progress = null) }
    }

    fun setName(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun setM3uUrl(url: String) {
        _uiState.update { it.copy(m3uUrl = url) }
    }

    fun setXtreamServer(url: String) {
        _uiState.update { it.copy(xtreamServerUrl = url) }
    }

    fun setXtreamUsername(user: String) {
        _uiState.update { it.copy(xtreamUsername = user) }
    }

    fun setXtreamPassword(pass: String) {
        _uiState.update { it.copy(xtreamPassword = pass) }
    }

    fun importPlaylist() {
        val state = _uiState.value
        viewModelScope.launch {
            when (state.selectedType) {
                PlaylistType.M3U -> {
                    if (state.m3uUrl.isBlank()) {
                        _uiState.update { it.copy(progress = ImportProgress.Error("Inserisci l'URL della playlist M3U")) }
                        return@launch
                    }
                    importM3uPlaylistUseCase(
                        playlistName = state.name.ifBlank { "Playlist M3U" },
                        source = state.m3uUrl.trim()
                    ).collect { progress -> onImportProgress(progress) }
                }
                PlaylistType.XTREAM -> {
                    if (state.xtreamServerUrl.isBlank() || state.xtreamUsername.isBlank() || state.xtreamPassword.isBlank()) {
                        _uiState.update { it.copy(progress = ImportProgress.Error("Compila tutti i campi Xtream Codes")) }
                        return@launch
                    }
                    importXtreamPlaylistUseCase(
                        playlistName = state.name.ifBlank { "Account Xtream" },
                        serverUrls = state.xtreamServerUrl.trim(),
                        username = state.xtreamUsername.trim(),
                        password = state.xtreamPassword.trim()
                    ).collect { progress -> onImportProgress(progress) }
                }
                else -> {}
            }
        }
    }

    private suspend fun onImportProgress(progress: ImportProgress) {
        _uiState.update { it.copy(progress = progress) }
        if (progress is ImportProgress.Success) {
            preferences.setActivePlaylistId(progress.playlistId)
            // Runs on the app scope: the user is free to leave this screen while it downloads.
            progress.epgUrl?.let(epgSyncCoordinator::sync)
            numberingCoordinator.recalculate(progress.playlistId)
        }
    }
}
