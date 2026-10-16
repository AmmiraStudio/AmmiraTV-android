package com.ammiratv.domain.repository

import com.ammiratv.core.model.Episode
import com.ammiratv.core.model.Playlist
import com.ammiratv.core.model.SeriesDetails
import com.ammiratv.core.model.VodCategory
import com.ammiratv.core.model.VodDetails
import com.ammiratv.core.model.VodItem
import com.ammiratv.core.model.VodKind
import com.ammiratv.core.model.WatchProgress
import kotlinx.coroutines.flow.Flow

/**
 * Movie / series catalogue of a playlist. Only Xtream sources expose one; for the others the
 * methods return empty results. Network failures are thrown to the caller.
 */
interface VodRepository {
    suspend fun getCategories(playlist: Playlist, kind: VodKind): List<VodCategory>
    suspend fun getItems(playlist: Playlist, kind: VodKind): List<VodItem>
    suspend fun getMovieDetails(playlist: Playlist, movieId: Long): VodDetails
    suspend fun getSeriesDetails(playlist: Playlist, seriesId: Long): SeriesDetails
    fun movieUrl(playlist: Playlist, movie: VodItem): String?
    fun episodeUrl(playlist: Playlist, episode: Episode): String?
}

interface WatchProgressRepository {
    fun observeContinueWatching(profileId: Long): Flow<List<WatchProgress>>
    fun observeAll(profileId: Long): Flow<Map<String, WatchProgress>>
    suspend fun get(profileId: Long, contentKey: String): WatchProgress?
    suspend fun save(progress: WatchProgress)
    suspend fun setWatched(profileId: Long, contentKey: String, watched: Boolean)
}
