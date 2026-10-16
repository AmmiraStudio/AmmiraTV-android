package com.ammiratv.feature.vod

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ammiratv.core.model.Episode
import com.ammiratv.core.model.Playlist
import com.ammiratv.core.model.SeriesDetails
import com.ammiratv.core.model.VodDetails
import com.ammiratv.core.model.VodItem
import com.ammiratv.core.model.VodKind
import com.ammiratv.core.model.WatchProgress
import com.ammiratv.domain.usecase.GetPlaylistByIdUseCase
import com.ammiratv.domain.usecase.GetVodCatalogUseCase
import com.ammiratv.domain.usecase.GetVodDetailsUseCase
import com.ammiratv.domain.usecase.ObserveActiveProfileUseCase
import com.ammiratv.domain.usecase.ObserveWatchProgressUseCase
import com.ammiratv.domain.usecase.ResolveVodStreamUseCase
import com.ammiratv.domain.usecase.SaveWatchProgressUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VodDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val item: VodItem? = null,
    val details: VodDetails = VodDetails(),
    val seasons: Map<Int, List<Episode>> = emptyMap(),
    val selectedSeason: Int? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VodDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getPlaylistById: GetPlaylistByIdUseCase,
    private val getVodCatalog: GetVodCatalogUseCase,
    private val getVodDetails: GetVodDetailsUseCase,
    private val resolveStream: ResolveVodStreamUseCase,
    private val saveProgress: SaveWatchProgressUseCase,
    observeWatchProgress: ObserveWatchProgressUseCase,
    private val observeActiveProfile: ObserveActiveProfileUseCase
) : ViewModel() {

    // Route arguments: vod_detail/{kind}/{playlistId}/{itemId}
    private val kind = VodKind.valueOf(checkNotNull(savedStateHandle.get<String>("kind")))
    private val playlistId = checkNotNull(savedStateHandle.get<Long>("playlistId"))
    private val itemId = checkNotNull(savedStateHandle.get<Long>("itemId"))

    private val _state = MutableStateFlow(VodDetailUiState())
    val state: StateFlow<VodDetailUiState> = _state.asStateFlow()

    private var playlist: Playlist? = null

    val progressByKey: StateFlow<Map<String, WatchProgress>> = observeActiveProfile()
        .map { it?.id ?: 1L }
        .flatMapLatest { observeWatchProgress(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        load()
    }

    fun selectSeason(season: Int) = _state.update { it.copy(selectedSeason = season) }

    fun retry() = load()

    private fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val pl = getPlaylistById(playlistId) ?: error("Sorgente non trovata")
                playlist = pl

                // The catalogue is cached in memory, so this is free after the list screen
                val item = getVodCatalog(pl, kind).items.firstOrNull { it.id == itemId }
                    ?: error("Titolo non trovato nel catalogo")

                when (kind) {
                    VodKind.MOVIE -> {
                        val details = getVodDetails.movie(pl, itemId)
                        _state.update { it.copy(isLoading = false, item = item, details = details) }
                    }
                    VodKind.SERIES -> {
                        val series: SeriesDetails = getVodDetails.series(pl, itemId)
                        _state.update {
                            it.copy(
                                isLoading = false,
                                item = item,
                                details = series.details,
                                seasons = series.seasons,
                                selectedSeason = series.seasons.keys.firstOrNull()
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Errore di caricamento") }
            }
        }
    }

    // ── Playback entry points: each returns the content key the player screen loads ──

    fun playMovie(onReady: (contentKey: String) -> Unit) {
        val item = _state.value.item ?: return
        val pl = playlist ?: return
        viewModelScope.launch {
            val url = resolveStream.movie(pl, item) ?: return@launch
            val base = baseProgress(
                key = WatchProgress.movieKey(pl.id, item.id),
                kind = VodKind.MOVIE,
                title = item.name,
                subtitle = null,
                poster = item.posterUrl,
                url = url,
                seriesId = null
            )
            onReady(saveProgress.ensure(base).contentKey)
        }
    }

    fun playEpisode(episode: Episode, onReady: (contentKey: String) -> Unit) {
        val item = _state.value.item ?: return
        val pl = playlist ?: return
        viewModelScope.launch {
            val url = resolveStream.episode(pl, episode) ?: return@launch
            val base = baseProgress(
                key = WatchProgress.episodeKey(pl.id, episode.id),
                kind = VodKind.SERIES,
                title = item.name,
                subtitle = episodeLabel(episode),
                poster = episode.posterUrl ?: item.posterUrl,
                url = url,
                seriesId = item.id
            )
            onReady(saveProgress.ensure(base).contentKey)
        }
    }

    fun toggleMovieWatched() {
        val item = _state.value.item ?: return
        val pl = playlist ?: return
        val key = WatchProgress.movieKey(pl.id, item.id)
        viewModelScope.launch {
            val url = resolveStream.movie(pl, item) ?: return@launch
            val current = progressByKey.value[key]?.isWatched == true
            saveProgress.markWatched(
                baseProgress(key, VodKind.MOVIE, item.name, null, item.posterUrl, url, null),
                watched = !current
            )
        }
    }

    fun toggleEpisodeWatched(episode: Episode) {
        val item = _state.value.item ?: return
        val pl = playlist ?: return
        val key = WatchProgress.episodeKey(pl.id, episode.id)
        viewModelScope.launch {
            val url = resolveStream.episode(pl, episode) ?: return@launch
            val current = progressByKey.value[key]?.isWatched == true
            saveProgress.markWatched(
                baseProgress(key, VodKind.SERIES, item.name, episodeLabel(episode), episode.posterUrl ?: item.posterUrl, url, item.id),
                watched = !current
            )
        }
    }

    /** The next episode after the last watched/started one, so "Riprendi serie" needs no browsing. */
    fun nextEpisodeToWatch(): Episode? {
        val pl = playlist ?: return null
        val progress = progressByKey.value
        val all = _state.value.seasons.toSortedMap().values.flatten()
        return all.firstOrNull { ep ->
            progress[WatchProgress.episodeKey(pl.id, ep.id)]?.isWatched != true
        } ?: all.firstOrNull()
    }

    private suspend fun baseProgress(
        key: String,
        kind: VodKind,
        title: String,
        subtitle: String?,
        poster: String?,
        url: String,
        seriesId: Long?
    ): WatchProgress = WatchProgress(
        profileId = observeActiveProfile().first()?.id ?: 1L,
        contentKey = key,
        kind = kind,
        playlistId = playlistId,
        title = title,
        subtitle = subtitle,
        posterUrl = poster,
        streamUrl = url,
        seriesId = seriesId
    )

    private fun episodeLabel(e: Episode) = "S${e.season} E${e.number} · ${e.title}"
}
