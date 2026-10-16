package com.ammiratv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ammiratv.core.datastore.AmmiraTvPreferences
import com.ammiratv.core.model.Playlist
import com.ammiratv.core.model.PlaylistType
import com.ammiratv.domain.usecase.DeletePlaylistUseCase
import com.ammiratv.domain.usecase.GetPlaylistsUseCase
import com.ammiratv.domain.usecase.PlaylistRefreshCoordinator
import com.ammiratv.domain.usecase.RefreshProviderInfoUseCase
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

@HiltViewModel
class SourcesViewModel @Inject constructor(
    private val getPlaylistsUseCase: GetPlaylistsUseCase,
    private val deletePlaylistUseCase: DeletePlaylistUseCase,
    private val refreshProviderInfoUseCase: RefreshProviderInfoUseCase,
    private val refreshCoordinator: PlaylistRefreshCoordinator,
    private val preferences: AmmiraTvPreferences
) : ViewModel() {

    /** Playlists being re-imported right now (runs on the app scope, survives leaving the screen). */
    val reimporting: StateFlow<Set<Long>> = refreshCoordinator.refreshing
    val refreshErrors: StateFlow<Map<Long, String>> = refreshCoordinator.errors

    fun reimport(playlist: Playlist) = refreshCoordinator.refresh(playlist)

    val playlists: StateFlow<List<Playlist>> = getPlaylistsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activePlaylistId: StateFlow<Long> = preferences.activePlaylistId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    /** Ids of the playlists whose provider info is being refreshed right now. */
    private val _refreshing = MutableStateFlow<Set<Long>>(emptySet())
    val refreshing: StateFlow<Set<Long>> = _refreshing.asStateFlow()

    /**
     * Asks every Xtream provider for fresh expiry / connection data. Done one account at a
     * time; results land in the database and reach the UI through [playlists].
     */
    fun refreshProviderInfo() {
        viewModelScope.launch {
            // Read straight from the database: `playlists` may still hold its initial empty value.
            getPlaylistsUseCase().first()
                .filter { it.type == PlaylistType.XTREAM }
                .forEach { playlist ->
                    _refreshing.update { it + playlist.id }
                    refreshProviderInfoUseCase(playlist)
                    _refreshing.update { it - playlist.id }
                }
        }
    }

    fun setActive(playlistId: Long) {
        viewModelScope.launch { preferences.setActivePlaylistId(playlistId) }
    }

    fun delete(playlistId: Long) {
        viewModelScope.launch {
            val wasActive = activePlaylistId.value == playlistId
            val fallback = getPlaylistsUseCase().first().firstOrNull { it.id != playlistId }?.id ?: 0L
            deletePlaylistUseCase(playlistId)
            if (wasActive) preferences.setActivePlaylistId(fallback)
        }
    }
}
