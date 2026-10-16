package com.ammiratv.domain.usecase

import com.ammiratv.core.common.ApplicationScope
import com.ammiratv.core.model.Playlist
import com.ammiratv.domain.repository.PlaylistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Refreshes playlists on the application scope, one at a time. Used both by the manual
 * "Aggiorna" button and by the automatic refresh of stale playlists at app start.
 */
@Singleton
class PlaylistRefreshCoordinator @Inject constructor(
    private val refreshPlaylist: RefreshPlaylistUseCase,
    private val playlistRepository: PlaylistRepository,
    private val epgSyncCoordinator: EpgSyncCoordinator,
    private val numberingCoordinator: NumberingCoordinator,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val mutex = Mutex()

    private val _refreshing = MutableStateFlow<Set<Long>>(emptySet())
    val refreshing: StateFlow<Set<Long>> = _refreshing.asStateFlow()

    /** Last error per playlist id, cleared when a refresh starts. */
    private val _errors = MutableStateFlow<Map<Long, String>>(emptyMap())
    val errors: StateFlow<Map<Long, String>> = _errors.asStateFlow()

    fun refresh(playlist: Playlist) {
        if (playlist.id in _refreshing.value) return
        _refreshing.update { it + playlist.id }
        _errors.update { it - playlist.id }

        scope.launch {
            mutex.withLock {
                try {
                    refreshPlaylist(playlist).collect { progress ->
                        when (progress) {
                            is ImportProgress.Success -> {
                                progress.epgUrl?.let(epgSyncCoordinator::sync)
                                // The refresh recreated the channel rows: numbers must be recomputed
                                numberingCoordinator.recalculate(progress.playlistId)
                            }
                            is ImportProgress.Error -> _errors.update { it + (playlist.id to progress.message) }
                            else -> Unit
                        }
                    }
                } finally {
                    _refreshing.update { it - playlist.id }
                }
            }
        }
    }

    /** Refreshes URL-based playlists not updated for [maxAgeMs] (default 24h). */
    fun refreshStale(maxAgeMs: Long = DEFAULT_MAX_AGE_MS) {
        scope.launch {
            val now = System.currentTimeMillis()
            playlistRepository.getPlaylists().first()
                .filter { it.isActive && now - it.lastUpdated > maxAgeMs }
                .filter { it.url.startsWith("http", ignoreCase = true) }
                .forEach(::refresh)
        }
    }

    private companion object {
        const val DEFAULT_MAX_AGE_MS = 24 * 3600 * 1000L
    }
}
