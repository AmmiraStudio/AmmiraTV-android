package com.ammiratv.data.repository

import com.ammiratv.core.model.Episode
import com.ammiratv.core.model.Playlist
import com.ammiratv.core.model.PlaylistType
import com.ammiratv.core.model.SeriesDetails
import com.ammiratv.core.model.VodCategory
import com.ammiratv.core.model.VodDetails
import com.ammiratv.core.model.VodItem
import com.ammiratv.core.model.VodKind
import com.ammiratv.core.network.xtream.XtreamClient
import com.ammiratv.domain.repository.VodRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Xtream VOD/series catalogue. The lists are fetched on demand and kept in memory for the
 * process lifetime (they can be tens of thousands of rows; a Room copy would only add a
 * migration burden and a stale-data problem). The cache key includes `lastUpdated`, so
 * refreshing a playlist naturally invalidates it.
 */
@Singleton
class VodRepositoryImpl @Inject constructor(
    private val xtreamClient: XtreamClient
) : VodRepository {

    private val categoryCache = ConcurrentHashMap<String, List<VodCategory>>()
    private val itemCache = ConcurrentHashMap<String, List<VodItem>>()

    private fun key(playlist: Playlist, kind: VodKind) = "${playlist.id}:${playlist.lastUpdated}:$kind"

    private fun credentials(playlist: Playlist): Pair<String, String>? {
        if (playlist.type != PlaylistType.XTREAM) return null
        val user = playlist.username ?: return null
        val pass = playlist.password ?: return null
        return user to pass
    }

    override suspend fun getCategories(playlist: Playlist, kind: VodKind): List<VodCategory> {
        val (user, pass) = credentials(playlist) ?: return emptyList()
        return categoryCache.getOrPutSuspend(key(playlist, kind)) {
            when (kind) {
                VodKind.MOVIE -> xtreamClient.getVodCategories(playlist.url, user, pass)
                VodKind.SERIES -> xtreamClient.getSeriesCategories(playlist.url, user, pass)
            }
        }
    }

    override suspend fun getItems(playlist: Playlist, kind: VodKind): List<VodItem> {
        val (user, pass) = credentials(playlist) ?: return emptyList()
        return itemCache.getOrPutSuspend(key(playlist, kind)) {
            when (kind) {
                VodKind.MOVIE -> xtreamClient.getMovies(playlist.url, user, pass, playlist.id)
                VodKind.SERIES -> xtreamClient.getSeries(playlist.url, user, pass, playlist.id)
            }
        }
    }

    override suspend fun getMovieDetails(playlist: Playlist, movieId: Long): VodDetails {
        val (user, pass) = credentials(playlist) ?: return VodDetails()
        return xtreamClient.getVodInfo(playlist.url, user, pass, movieId)
    }

    override suspend fun getSeriesDetails(playlist: Playlist, seriesId: Long): SeriesDetails {
        val (user, pass) = credentials(playlist) ?: return SeriesDetails(VodDetails(), emptyMap())
        return xtreamClient.getSeriesInfo(playlist.url, user, pass, seriesId)
    }

    override fun movieUrl(playlist: Playlist, movie: VodItem): String? {
        val (user, pass) = credentials(playlist) ?: return null
        return xtreamClient.buildMovieUrl(playlist.url, user, pass, movie.id, movie.containerExtension ?: "mp4")
    }

    override fun episodeUrl(playlist: Playlist, episode: Episode): String? {
        val (user, pass) = credentials(playlist) ?: return null
        return xtreamClient.buildEpisodeUrl(playlist.url, user, pass, episode.id, episode.containerExtension)
    }

    private suspend fun <V> ConcurrentHashMap<String, V>.getOrPutSuspend(k: String, load: suspend () -> V): V =
        get(k) ?: load().also { put(k, it) }
}
