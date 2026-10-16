package com.ammiratv.domain.usecase

import com.ammiratv.core.model.Episode
import com.ammiratv.core.model.Playlist
import com.ammiratv.core.model.SeriesDetails
import com.ammiratv.core.model.VodCategory
import com.ammiratv.core.model.VodDetails
import com.ammiratv.core.model.VodItem
import com.ammiratv.core.model.VodKind
import com.ammiratv.core.model.WatchProgress
import com.ammiratv.domain.repository.PlaylistRepository
import com.ammiratv.domain.repository.VodRepository
import com.ammiratv.domain.repository.WatchProgressRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** A catalogue page: categories plus every item, loaded together. */
data class VodCatalog(
    val categories: List<VodCategory>,
    val items: List<VodItem>
)

class GetVodCatalogUseCase @Inject constructor(
    private val vodRepository: VodRepository
) {
    suspend operator fun invoke(playlist: Playlist, kind: VodKind): VodCatalog =
        VodCatalog(
            categories = vodRepository.getCategories(playlist, kind),
            items = vodRepository.getItems(playlist, kind)
        )
}

class GetVodDetailsUseCase @Inject constructor(
    private val vodRepository: VodRepository
) {
    suspend fun movie(playlist: Playlist, movieId: Long): VodDetails =
        vodRepository.getMovieDetails(playlist, movieId)

    suspend fun series(playlist: Playlist, seriesId: Long): SeriesDetails =
        vodRepository.getSeriesDetails(playlist, seriesId)
}

class ResolveVodStreamUseCase @Inject constructor(
    private val vodRepository: VodRepository
) {
    fun movie(playlist: Playlist, movie: VodItem): String? = vodRepository.movieUrl(playlist, movie)
    fun episode(playlist: Playlist, episode: Episode): String? = vodRepository.episodeUrl(playlist, episode)
}

class ObserveContinueWatchingUseCase @Inject constructor(
    private val repository: WatchProgressRepository
) {
    operator fun invoke(profileId: Long): Flow<List<WatchProgress>> =
        repository.observeContinueWatching(profileId)
}

class ObserveWatchProgressUseCase @Inject constructor(
    private val repository: WatchProgressRepository
) {
    operator fun invoke(profileId: Long): Flow<Map<String, WatchProgress>> = repository.observeAll(profileId)
}

class SaveWatchProgressUseCase @Inject constructor(
    private val repository: WatchProgressRepository
) {
    /** Persists [positionMs]; viewings shorter than [WatchProgress.MIN_POSITION_MS] are ignored. */
    suspend operator fun invoke(base: WatchProgress, positionMs: Long, durationMs: Long) {
        if (positionMs < WatchProgress.MIN_POSITION_MS) return
        repository.save(base.withPosition(positionMs, durationMs, System.currentTimeMillis()))
    }

    suspend fun get(profileId: Long, contentKey: String): WatchProgress? = repository.get(profileId, contentKey)

    /**
     * Makes sure a row exists before playback starts, so the player can find the stream url
     * and title from the content key alone. Existing progress is left untouched.
     */
    suspend fun ensure(base: WatchProgress): WatchProgress =
        repository.get(base.profileId, base.contentKey)
            ?: base.copy(updatedAt = System.currentTimeMillis()).also { repository.save(it) }

    suspend fun markWatched(profileId: Long, contentKey: String, watched: Boolean) =
        repository.setWatched(profileId, contentKey, watched)

    /** Inserts a row when the title was never started, so "segna come visto" works from the list. */
    suspend fun markWatched(base: WatchProgress, watched: Boolean) {
        val existing = repository.get(base.profileId, base.contentKey)
        if (existing == null) {
            repository.save(base.copy(isWatched = watched, positionMs = 0, updatedAt = System.currentTimeMillis()))
        } else {
            repository.setWatched(base.profileId, base.contentKey, watched)
        }
    }
}

/** Looks a playlist up by id; VOD screens receive only ids through navigation. */
class GetPlaylistByIdUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    suspend operator fun invoke(id: Long): Playlist? = playlistRepository.getPlaylistById(id)
}
